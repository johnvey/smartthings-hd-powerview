/**
 * Hunter Douglas PowerView Hub SmartApp (service manager)
 * Copyright (c) 2017 Johnvey Hwang
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

definition(
  name: "Hunter Douglas PowerView",
  namespace: "johnvey",
  author: "Johnvey Hwang",
  description: "Controls shades and scenes managed by your PowerView hub",
  category: "My Apps",
  iconUrl: "https://silver-saint.netlify.com/assets/powerview-icon.png",
  iconX2Url: "https://silver-saint.netlify.com/assets/powerview-icon-2x.png",
  iconX3Url: "https://silver-saint.netlify.com/assets/powerview-icon-3x.png"
)

preferences {
    page(name: "singlePagePref")
}

def singlePagePref() {
    return dynamicPage(
        name: "singlePagePref", 
        install: canInstall(), 
        uninstall: true, 
        refreshInterval: getPrefInterval()
    ) {
        // setup basic connection to hub
        section("Hub setup") {
            input(
                name: "hubIP", 
                title: "IP Address", 
                type: "text", 
                required: false, 
                submitOnChange: true
            )
            if (hubIP) {
                if (state.hubMAC) {
                    paragraph(title: "Hub name", "${state.hubName} (${state.hubMAC})")
                } else {
                    paragraph "Fetching hub info..."
                }
            }
        }

        // manage hub details
        if (hubIP) {
            fetchHubInfo()
            fetchAllShades()
            fetchAllScenes()
            fetchAllSceneCollections()
            def foundShades = getDiscoveredShadeList()
            def foundScenes = getDiscoveredSceneList()
            def foundSceneCollections = getDiscoveredSceneCollectionList()
            def shadeCount = foundShades.size()
            def sceneCount = foundScenes.size()
            def sceneCollectionCount = foundSceneCollections.size()
            log.info("pref.singlePagePref - shadeCount=$shadeCount, sceneCount=$sceneCount, sceneCollectionCount=$sceneCollectionCount")

            section("Shades") {
                if (shadeCount > 0) {
                    input(
                        name: "selectedShades", 
                        title: "Linked shades (${shadeCount} available)", 
                        type: "enum", 
                        options: foundShades, 
                        multiple: true, 
                        required: false
                    )
                } else {
                    paragraph "Searching for installed shades..."
                }
            }

            section("Scenes") {
                if (sceneCount > 0) {
                    input(
                        name: "selectedScenes", 
                        title: "Linked scenes (${sceneCount} available)", 
                        type: "enum", 
                        options: foundScenes,
                        multiple: true, 
                        required: false
                    )
                } else {
                    paragraph "Searching for installed scenes..."
                }
            }

            section("SceneCollections") {
                if (sceneCollectionCount > 0) {
                    input(
                        name: "selectedSceneCollections", 
                        title: "Linked scene collections (${sceneCollectionCount} available)", 
                        type: "enum", 
                        options: foundSceneCollections,
                        multiple: true, 
                        required: false
                    )
                } else {
                    paragraph "Searching for installed scene collections..."
                }
            }
		}
    }
}

// show the "Done" action only when user has input an IP
def canInstall() {
    return state.hubIP ? true : false
}

// TODO: should this back off the refresh rate if we have an IP?
def getPrefInterval() {
    return 5
    // state.hubIP ? 5 : 15
}


// ----------------------------------------------------------------------------
// utility methods
// ----------------------------------------------------------------------------

// returns the currently active hub ID
def getHubID() {
    def hubID
    if (myHub) {
        hubID = myHub.id
    } else {
        def hubs = location.hubs.findAll { 
            it.type == physicalgraph.device.HubType.PHYSICAL 
        } 
        if (hubs.size() == 1) hubID = hubs[0].id 
    }
    return hubID
}

/**
 * Generates a device network ID based that is unique to the active PV hub
 */
def getDeviceId(deviceType, pvId) {
    switch (deviceType) {
        case 'shade':
        case 'scene':
        case 'scenecollection':
            // valid
            break
        default:
            log.error("got invalid deviceType: $deviceType")
            return
    }
    return "$deviceType;${state.hubMAC};${pvId}"
}

/**
 * Extracts the device type from the child device DNI, as defined above in
 * getDeviceId().
 */
def parseDeviceType(deviceNetworkId) {
    return deviceNetworkId.tokenize(';')[0]
}


// ----------------------------------------------------------------------------
// discovery methods
// ----------------------------------------------------------------------------

/**
 * Fetches PV hub information
 * 
 * Requires that the user input the `hubIP` value
 */
def fetchHubInfo() {
    log.info("fetchHubInfo()")

    def DEFAULT_HUB_PORT = 80

    if (settings.hubIP) {
        state.hubIP = settings.hubIP
        state.hubPort = DEFAULT_HUB_PORT
        sendRequest('GET', '/api/userdata', null, _fetchHubInfoCallback)
    } else {
        log.debug("no hubIP set, skipping fetch")
    }
}

/**
 * Handles base hub info response
 */
def _fetchHubInfoCallback(response) {
    def userData = response.json.userData
    state.hubName = new String(userData.hubName.decodeBase64())
    state.hubMAC = userData.macAddress.replaceAll(":", "")
    log.info("_fetchHubInfoCallback(status=${response.status}) hubName=${state.hubName} hubMAC=${state.hubMAC}")
}

/**
 * Fetches all managed shade configs
 */
def fetchAllShades() {
    return sendRequest('GET', '/api/shades', null, _fetchAllShadesCallback)
}

/**
 * Handles response for shade configs
 */
def _fetchAllShadesCallback(response) {
    state.discoveredShades = [:]

    response.json.shadeData?.each {
        // start with the config info from PV
        def shadeConfig = it.clone()

        // add our custom keys
        def shadeLabel
        try {
          shadeLabel = new String(it.name.decodeBase64())
        } catch (e) {
          log.error "Error decoding shade ${it.id} with name ${it.name}"
          log.error e
          shadeLabel = "${it.id} ${it.name}"
        }
        def enumLabel = "${shadeLabel} (${it.id})"
        shadeConfig.label = "Blind ${shadeLabel}" // plain english name
        shadeConfig.enumLabel = enumLabel // awkward label for use with prefs
        shadeConfig.deviceNetworkId = getDeviceId('shade', it.id)

        state.discoveredShades[enumLabel] = shadeConfig
    }
	log.info "_fetchAllShadesCallback(status=${response.status}) scenes=${state.discoveredShades.keySet()}"
    return state.discoveredShades
}

/**
 * Returns a flat list of shade names that can be rendered by a list control
 */
def getDiscoveredShadeList() {
    def ret = []
    state.discoveredShades?.each { key, value ->
        ret.add(value.enumLabel)
    }
    return ret
}

/**
 * Returns the device ID associated with an enumLabel
 */
def shadeEnumToId(enumLabel) {
    return state.discoveredShades[enumLabel]?.deviceNetworkId
}

/**
 * Fetches all managed scene configs
 */
def fetchAllScenes() {
    return sendRequest('GET', '/api/scenes', null, _fetchAllScenesCallback)
}

/**
 * Handles response for scene configs
 */
def _fetchAllScenesCallback(response) {
    state.discoveredScenes = [:]

    response.json.sceneData?.each {
        // start with the config info from PV
        def sceneConfig = it.clone()

        // add our custom keys
        def sceneLabel
        try {
          sceneLabel = new String(it.name.decodeBase64())
        } catch (e) {
          log.error "Error decoding scene ${it.id} with name ${it.name}"
          log.error e
          sceneLabel = "${it.id} ${it.name}"
        }

        def enumLabel = "${sceneLabel} (${it.id})"
        sceneConfig.label = "Blinds ${sceneLabel}" // plain english name
        sceneConfig.enumLabel = enumLabel // awkward label for use with prefs
        sceneConfig.deviceNetworkId = getDeviceId('scene', it.id)

        state.discoveredScenes[enumLabel] = sceneConfig
    }
	log.info "_fetchAllScenesCallback(status=${response.status}) scenes=${state.discoveredScenes.keySet()}"
    return state.discoveredScenes
}

/**
 * Returns a flat list of scene names that can be rendered by a list control
 */
def getDiscoveredSceneList() {
    def ret = []
    state.discoveredScenes?.each { key, value ->
        ret.add(value.enumLabel)
    }
    return ret
}

/**
 * Returns the device ID associated with an enumLabel
 */
def sceneEnumToId(enumLabel) {
    return state.discoveredScenes[enumLabel]?.deviceNetworkId
}

/**
 * Fetches all managed scene configs
 */
def fetchAllSceneCollections() {
    return sendRequest('GET', '/api/scenecollections', null, _fetchAllSceneCollectionsCallback)
}

/**
 * Handles response for scene configs
 */
def _fetchAllSceneCollectionsCallback(response) {
    state.discoveredSceneCollections = [:]

    response.json.sceneCollectionData?.each {
        // start with the config info from PV
        def sceneCollectionConfig = it.clone()

        // add our custom keys
        def sceneCollectionLabel
        try {
          sceneCollectionLabel = new String(it.name.decodeBase64())
        } catch (e) {
          log.error "Error decoding scene collection ${it.id} with name ${it.name}"
          log.error e
          sceneCollectionLabel = "${it.id} ${it.name}"
        }

        def enumLabel = "${sceneCollectionLabel} (${it.id})"
        sceneCollectionConfig.label = "Blinds ${sceneCollectionLabel}" // plain english name
        sceneCollectionConfig.enumLabel = enumLabel // awkward label for use with prefs
        sceneCollectionConfig.deviceNetworkId = getDeviceId('scenecollection', it.id)

        state.discoveredSceneCollections[enumLabel] = sceneCollectionConfig
    }
	log.info "_fetchAllSceneCollectionsCallback(status=${response.status}) scenes=${state.discoveredSceneCollections.keySet()}"
    return state.discoveredSceneCollections
}

/**
 * Returns a flat list of scene names that can be rendered by a list control
 */
def getDiscoveredSceneCollectionList() {
    def ret = []
    state.discoveredSceneCollections?.each { key, value ->
        ret.add(value.enumLabel)
    }
    return ret
}

/**
 * Returns the device ID associated with an enumLabel
 */
def sceneCollectionEnumToId(enumLabel) {
    return state.discoveredSceneCollections[enumLabel]?.deviceNetworkId
}

// ----------------------------------------------------------------------------
// device handler methods
// ----------------------------------------------------------------------------

/**
 * Install the shades that the user selected in the config
 */
def installSelectedShades() {
    log.info("installSelectedShades() selectedShades=${settings.selectedShades}")

    // remove the shades that are installed but are not checked by the user
    def toRemove = getDiscoveredShadeList() - settings.selectedShades
    toRemove?.each {
        def deviceId = shadeEnumToId(it)
        log.info("Remove shade deviceId=$deviceId")
        try {
            deleteChildDevice(deviceId)
        } catch (e) {
            log.warn(e)
        }
    }
    // state.addedShadeIds = []

    // iterate over the enum label
    settings.selectedShades?.each {
        installShade(it)
    }
}

/**
 * Installs an individual shade device handler
 */
def installShade(enumLabel) {
    // get shade info already fetched
    def shadeInfo = state.discoveredShades.get(enumLabel)
    if (!shadeInfo) {
        log.error("installShade failed; did not find $enumLabel in state.discoveredShades")
        return
    }

    // check if device is already installed
    def currentChildDevices = getChildDevices()
    def selectedDevice = currentChildDevices.find { shadeInfo.deviceNetworkId }
    def dev
    if (selectedDevice) {
        dev = getChildDevices()?.find {
            it.deviceNetworkId == shadeInfo.deviceNetworkId
        }
    }

    if (!dev) {
        def addedDevice = addChildDevice(
            "johnvey", 
            "Hunter Douglas PowerView Shade", 
            shadeInfo.deviceNetworkId,
            getHubID(),
            [name: shadeInfo.id, label: shadeInfo.label, completedSetup: true]
        )
        log.info "ADDED: label=${shadeInfo.label} deviceId=${shadeInfo.deviceNetworkId}"
    } else {
        log.info("SKIP: deviceId=${shadeInfo.deviceNetworkId}")
    }
}

/**
 * Install the scenes that the user selected in the config
 */
def installSelectedScenes() {
    log.info("installSelectedScenes() selectedScenes=${settings.selectedScenes}")

    // remove the scenes that are installed but are not checked by the user
    def toRemove = getDiscoveredSceneList() - settings.selectedScenes
    toRemove?.each {
        def deviceId = sceneEnumToId(it)
        log.info("Remove scene deviceId=$deviceId")
        try {
            deleteChildDevice(deviceId)
        } catch (e) {
            log.warn(e)
        }
    }

    // iterate over the enum label
    settings.selectedScenes?.each {
        installScene(it)
    }
}

/**
 * Installs an individual scene device handler
 */
def installScene(enumLabel) {
    // get scene info already fetched
    def sceneInfo = state.discoveredScenes.get(enumLabel)
    if (!sceneInfo) {
        log.error("installScene failed; did not find $enumLabel in state.discoveredScenes")
        return
    }

    // check if device is already installed
    def currentChildDevices = getChildDevices()
    log.debug("current child devices: ${currentChildDevices}")
    def selectedDevice = currentChildDevices.find { sceneInfo.deviceNetworkId }
    def dev
    if (selectedDevice) {
        dev = getChildDevices()?.find {
            it.deviceNetworkId == sceneInfo.deviceNetworkId
        }
    }

    if (!dev) {
        def addedDevice = addChildDevice(
            "johnvey", 
            "Hunter Douglas PowerView Scene", 
            sceneInfo.deviceNetworkId,
            getHubID(),
            [name: sceneInfo.id, label: sceneInfo.label, completedSetup: true]
        )
        // log the ID to the app for later use
        state.addedSceneIds = state.addedSceneIds ?: []
        state.addedSceneIds.add(sceneInfo.deviceNetworkId)
        log.info "ADDED: label=${sceneInfo.label} deviceId=${sceneInfo.deviceNetworkId}"
    } else {
        log.debug("SKIP: deviceId=${sceneInfo.deviceNetworkId}")
    }
}

def removeAddedScenes() {
    state.addedSceneIds?.each {
        log.info("Remove scene deviceId=$it")
        deleteChildDevice(it)
    }
    state.addedSceneIds = []
}

/**
 * Install the scene collections that the user selected in the config
 */
def installSelectedSceneCollections() {
    log.info("installSelectedSceneCollections() selectedSceneCollections=${settings.selectedSceneCollections}")

    // remove the scene collections that are installed but are not checked by the user
    def toRemove = getDiscoveredSceneCollectionList() - settings.selectedSceneCollections
    toRemove?.each {
        def deviceId = sceneCollectionEnumToId(it)
        log.info("Remove scene collection deviceId=$deviceId")
        try {
            deleteChildDevice(deviceId)
        } catch (e) {
            log.warn(e)
        }
    }

    // iterate over the enum label
    settings.selectedSceneCollections?.each {
        installSceneCollection(it)
    }
}

/**
 * Installs an individual scene collection device handler
 */
def installSceneCollection(enumLabel) {
    // get scene info already fetched
    def sceneCollectionInfo = state.discoveredSceneCollections.get(enumLabel)
    if (!sceneCollectionInfo) {
        log.error("installSceneCollection failed; did not find $enumLabel in state.discoveredSceneCollections")
        return
    }

    // check if device is already installed
    def currentChildDevices = getChildDevices()
    log.debug("current child devices: ${currentChildDevices}")
    def selectedDevice = currentChildDevices.find { sceneCollectionInfo.deviceNetworkId }
    def dev
    if (selectedDevice) {
        dev = getChildDevices()?.find {
            it.deviceNetworkId == sceneCollectionInfo.deviceNetworkId
        }
    }

    if (!dev) {
        def addedDevice = addChildDevice(
            "johnvey", 
            "Hunter Douglas PowerView Scene Collection", 
            sceneCollectionInfo.deviceNetworkId,
            getHubID(),
            [name: sceneCollectionInfo.id, label: sceneCollectionInfo.label, completedSetup: true]
        )
        // log the ID to the app for later use
        state.addedSceneCollectionIds = state.addedSceneCollectionIds ?: []
        state.addedSceneCollectionIds.add(sceneCollectionInfo.deviceNetworkId)
        log.info "ADDED: label=${sceneCollectionInfo.label} deviceId=${sceneCollectionInfo.deviceNetworkId}"
    } else {
        log.debug("SKIP: deviceId=${sceneCollectionInfo.deviceNetworkId}")
    }
}

def removeAddedSceneCollections() {
    state.addedSceneCollectionIds?.each {
        log.info("Remove scene collection deviceId=$it")
        deleteChildDevice(it)
    }
    state.addedSceneCollectionIds = []
}

// ----------------------------------------------------------------------------
// HTTP methods
// ----------------------------------------------------------------------------

private sendRequest(method, path, body='', callbackFn) {

    def host = state.hubIP
    def port = state.hubPort

    def hubAction = new physicalgraph.device.HubAction(
        [
            method: method,
            path: path,
            HOST: "$host:$port",
            headers: [
                'HOST': "$host:$port",
                'Content-Type': 'application/json'
            ],
            body: body
        ],
        null,
        [ callback: callbackFn ]
    )
    log.debug("sendRequest: $method $host:$port$path")
    sendHubCommand(hubAction)
}


// ----------------------------------------------------------------------------
// app lifecycle hooks
// ----------------------------------------------------------------------------

/**
 * called when SmartApp is first added; is a no-op here becuase we handle
 * everything via updated()
 */
def installed() {
    log.info "CMD installed"
}

/**
 * called when SmartApp pref pane clicked 'done'
 */
def updated() {
    log.info "CMD updated"
    installSelectedShades()
    installSelectedScenes()
    installSelectedSceneCollections()
}

/**
 * Called when SmartApp is removed
 */
def uninstalled() {
    log.info "CMD uninstalled"
    getChildDevices().each {
        deleteChildDevice(it.deviceNetworkId)
    }
}