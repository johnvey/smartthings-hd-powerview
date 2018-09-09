/**
 * Hunter Douglas PowerView Shade (device handler)
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

import groovy.transform.Field

// TODO update when out of beta
include 'asynchttp_v1'

// ----------------------------------------------------------------------------
// configuration
// ----------------------------------------------------------------------------

metadata {
    definition (
        name: "Hunter Douglas PowerView Shade", 
        namespace: "johnvey", 
        author: "Johnvey Hwang"
    ) {
        // tags
        capability "Actuator"
        capability "Sensor"

        // device capabilities
        capability "Battery"
        capability "Light"
        capability "Polling"
        capability "Refresh"
        capability "Switch"
        capability "Switch Level"
        capability "Window Shade"

        // custom commands
        command "jog"

    }

    simulator {
    }

    tiles(scale: 2) {
        // define top color status block
        multiAttributeTile(name:"windowShade", type: "lighting", width: 6, height: 4, canChangeIcon: true) {
            tileAttribute ("device.windowShade", key: "PRIMARY_CONTROL") {
                attributeState "open", label:'V. Open', action:"windowShade.close", icon:"st.Home.home9", backgroundColor:"#00a0dc", nextState:"closing"
                attributeState "partial_vane", label:'V. Partial', action:"windowShade.close", icon:"st.Home.home9", backgroundColor:"#00a0dc", nextState:"closing"
                attributeState "closed", label:'V. Closed', action:"windowShade.open", icon:"st.Home.home9", backgroundColor:"#ffffff", nextState:"opening"
                attributeState "opening", label:'Opening', action:"windowShade.close", icon:"st.Home.home9", backgroundColor:"#00a0dc", nextState:"open"
                attributeState "closing", label:'Closing', action:"windowShade.open", icon:"st.Home.home9", backgroundColor:"#ffffff", nextState:"opening"
            }
            tileAttribute ("device.level", key: "SLIDER_CONTROL") {
                attributeState "level", label: "${currentValue}", action:"switch level.setLevel"
            }
        }

        //
        // we arrange the tiles so that the up/down pairs make sense as when
        // are in a 3x2 grid
        // 

        //-- top row --
        // open shade
        standardTile("open", "device.switch", width: 2, height: 2,
                    inactiveLabel: false, decoration: "flat") {
            state("default", label:'Open shade', action:"on",
                icon:"st.doors.garage.garage-opening")
        }
        // open vanes
        standardTile("on", "device.windowShade", width: 2, height: 2,
                    inactiveLabel: false, decoration: "flat") {
            state("default", label:'Open vanes', action:"open",
                icon:"st.doors.garage.garage-opening")
        }
        // refresh
        standardTile("refresh", "device.switch", width: 2, height: 2,
                    inactiveLabel: false, decoration: "flat") {
            state("default", label:'Refresh', action:"refresh",
                icon:"st.secondary.refresh-icon")
        }

        //-- middle row --
        // close shade
        standardTile("close", "device.switch", width: 2, height: 2,
                    inactiveLabel: false, decoration: "flat") {
            state("default", label:'Close shade', action:"off",
                icon:"st.doors.garage.garage-closing")
        }
        // close vanes
        standardTile("off", "device.windowShade", width: 2, height: 2,
                    inactiveLabel: false, decoration: "flat") {
            state("default", label:'Close vanes', action:"close",
                icon:"st.doors.garage.garage-closing")
        }
        // battery level
        valueTile("battery", "device.battery", width: 2, height: 2, decoration: "flat") {
            state("battery", label:'${currentValue}%\nBattery', defaultState: true, backgroundColors: [
                [value: 0, color: "#C70039"],
                [value: 20, color: "#FFFFFF"]
            ])
        }

        //-- bottom row --
        // jog shade
        standardTile("jog", "device.windowShade", width: 2, height: 2,
                    inactiveLabel: false, decoration: "flat") {
            state("default", label:'Jog shade', action:"jog",
                icon:"st.motion.motion.inactive")
        }

        main(["windowShade"])
    }
}


// ----------------------------------------------------------------------------
// hub comm methods
// ----------------------------------------------------------------------------

// define controllable component types
// values map to PV API inputs for `posKind[N]` key
// set vanes open {"shade":{"id":1694,"positions":{"position1":32767,"posKind1":3}}}
// set vanes closed {"shade":{"id":1694,"positions":{"position1":0,"posKind1":3}}}
// set shade open {"shade":{"id":1694,"positions":{"position1":17508,"posKind1":1}}}
// set shade closed {"shade":{"id":1694,"positions":{"position1":0,"posKind1":1}}}
@Field def ShadeComponentType = [
    SHADE: 1, 
    VANE: 3
]

// define max (open) setting value
@Field def ShadeMaxPosition = [
    SHADE: 65535,
    VANE: 32767
]

/**
 * Returns a unique id for deviceNetworkId uses; prefix must coordinate with
 * the `getDeviceId()` method in powerview-manager.groovy
 */
private getDeviceId(pvId) {
    return "shade;${state.hubMAC};${pvId}"
}

/**
 * Sets the position of shade and vanes. This is the main request builder
 * that constructs the REST endpoint and JSON payload to dispatch to the hub.
 * @param {int} level The desired setting from 0-100
 * @param {int} type Specifies whether to move the shade or vane. Enum defined
 *              via ShadeMaxPosition map.
 */
private setPosition(int level, int type) {
    // log.debug "setPosition level=$level type=$type"

    // translate to the hub-specific values
    def rawPosition = 0
    if (type == ShadeComponentType.SHADE) {
        rawPosition = level/100 * ShadeMaxPosition.SHADE
    } else if (type == ShadeComponentType.VANE) {
        rawPosition = level/100 * ShadeMaxPosition.VANE
    }
    rawPosition = (int) rawPosition // round value
    def rawType = type

    def path = "/api/shades/${state.pvShadeId}"

    def builder = new groovy.json.JsonBuilder()
    builder.shade {
        id state.pvShadeId
        positions {
            position1 rawPosition
            posKind1 rawType
        }
    }
    def body = builder.toString()

    return sendRequest('PUT', path, body)
}

/**
 * Fetches current shade information
 * @param requestBatteryLevel Indicates if the hub should force request an
 *        update on the battery level. This request will cause the shade
 *        to jog once.
 */
private forceUpdate(boolean requestBatteryLevel = false) {
    def path = "/api/shades/${state.pvShadeId}"
    if (requestBatteryLevel) {
        path += '?updateBatteryLevel=true'
    } else {
        path += '?refresh=true'
    }
    return sendRequest('GET', path, '')
}

/**
 * Instructs the shade to briefly open and close in order to visually identify
 * the shade under control.
 {"shade":{"motion":"jog"}}
 */
private startJog() {
    def path = "/api/shades/${state.pvShadeId}"
    def builder = new groovy.json.JsonBuilder()
    builder.shade {
        motion "jog"
    }
    def body = builder.toString()
    return sendRequest('PUT', path, body)
}

/**
 * Returns a HTTP hubAction to be sent from the ST hub to the local PowerView
 * hub. The returned hubAction object must be handled either by the command
 * framework or an explicit sendHubCommand() call.
 * @param {string} method The HTTP verb to issue
 * @param {string} path The URI path component
 * @param {string} body The HTTP POST/PUT payload to include in the request.
 *                 Optional.
 */
private sendRequest(method, path, body=null) {
    def host = "${state.hubIP}:${state.hubPort}"
    def hubAction = new physicalgraph.device.HubAction(
        [
            method: method,
            path: path,
            HOST: host,
            headers: [
                'HOST': host,
                'Content-Type': 'application/json'
            ],
            body: body
        ],
        getDeviceId(state.pvShadeId),
        [
            callback: sendRequestCallback
        ]
    )
    // log.debug("sendRequest: ${method} ${host}${path}")
    return hubAction
}

/**
 * Handles the HTTP response from the hubAction generated in sendRequest()
 */
def sendRequestCallback(response) {
    if (response.error) {
        log.error "error $response.error"
        log.debug "status $response.status"
        log.debug "json $response.json"
    }

    // send the description over to the parse() method
    parseShadeData(response.json)
}

/**
 * Parses the JSON response from the PowerView hub.
 * 
 * Sample response:
 * {
 *     "shade": {
 *         "id": 1694,
 *         "name": "U2hhZGUgMw==",
 *         "roomId": 43542,
 *         "groupId": 64835,
 *         "order": 1,
 *         "type": 23,
 *         "batteryStrength": 174,
 *         "batteryStatus": 3,
 *         "positions": {
 *             "position1": 0,
 *             "posKind1": 3
 *         }
 *     }
 * }
*/
def parseShadeData(payload) {
    def shade = payload.shade

    // parse shade position info
    if (shade.positions.posKind1 == ShadeComponentType.SHADE) {
        def shadeLevel = (int) shade.positions.position1 / ShadeMaxPosition.SHADE * 100
        log.debug("Setting shade level: ${shadeLevel}")
        sendEvent(name: 'level', value: shadeLevel)
        if (shadeLevel > 0) {
            sendEvent(name: 'switch', value: 'on')
        } else {
            sendEvent(name: 'switch', value: 'off')            
        }
        // if shade level is reported, then vane is closed
        sendEvent(name: 'windowShade', value: 'closed')

    } else if (shade.positions.posKind1 == ShadeComponentType.VANE) {
        def vaneLevel = (int) shade.positions.position1 / ShadeMaxPosition.VANE * 100
        log.debug("Setting vane level: ${vaneLevel}")
        def stateName = ''
        if (vaneLevel >= 99) {
            stateName = 'open'
        } else if (vaneLevel > 1) {
            stateName = 'partial_vane'
        } else {
            stateName = 'closed'
        }
        sendEvent(name: 'windowShade', value: stateName)
        sendEvent(name: 'switch', value: 'off')
        // if vane level is reported, then shade is closed
        sendEvent(name: 'level', value: 0)
    }

    // parse shade battery level info
    if (shade.batteryStrength) {
        // TODO: the 255 max value is a guess; I have not verified this
        def batteryPerc = Math.round(shade.batteryStrength/255 * 100)
        //log.debug("Setting shade battery level: ${batteryPerc}")
        sendEvent(name: 'battery', value: batteryPerc)
    }
}


// ----------------------------------------------------------------------------
// handle commands
// ----------------------------------------------------------------------------

// parse hub response into attributes
def parse(String description) {
    log.error "parse() not implemented! Got: '${description}'"
}

def installed() {
    log.info('called shade installed()')
    setHubInfo()
    runEvery3Hours(refresh)
}

def updated() {
    log.info('called shade updated()')
    setHubInfo()
    runEvery3Hours(refresh)
}

def setHubInfo() {
    state.hubMAC = parent.state.hubMAC
    state.hubIP = parent.state.hubIP
    state.hubPort = parent.state.hubPort
    try {
        state.pvShadeId = device.name?.toInteger()
    } catch(Exception ex) {
        log.warn("could not cast shade ID to integer")
        state.pvShadeId = device.name
    }
    log.debug("called setHubInfo() - hubMAC=${state.hubMAC} hubIP=${state.hubIP} hubPort=${state.hubPort} pvShadeId=${state.pvShadeId}")
}

def poll() {
    log.debug "Executing 'poll'"
    refresh()
}

/**
 * Poll the hub to get the current state of the shade
 */
def refresh() {
    log.debug "Executing 'refresh'"
    // this is wrapped in the sendHubCommand() because scheduled calls that
    // are invoked via the runEvery*() methods don't care about hub actions
    // see: https://community.smartthings.com/t/using-physicalgraph-device-hubaction/77495/5
    return sendHubCommand(forceUpdate())
}

//
// switchLevel commands
//

/**
 * Fully opens the shade
 */
def on() {
    log.debug "Executing 'on'"
    return setPosition(100, ShadeComponentType.SHADE)
}

/**
 * Fully closes the shade, vanes closed
 */
def off() {
    log.debug "Executing 'off'"
    return setPosition(0, ShadeComponentType.SHADE)
}

/**
 * Opens the shade to relative position to open
 * @param level The desired shade level. 0=closed, 99=open
 */
def setLevel(level, rate=0) {
    log.debug "Executing 'setLevel'"
    return setPosition(Math.round(level), ShadeComponentType.SHADE)
}

//
// windowShade commands
//

/**
 * Full opens the vanes. If the shade level position is > 0, the shade will
 * first close before opening the vanes.
 */
def open() {
    log.debug "Executing 'open'"
    return setPosition(100, ShadeComponentType.VANE)
}

/**
 * Closes the vanes. If the shade level position is > 0, the shade will
 * close. (vanes are already closed)
 */
def close() {
    log.debug "Executing 'close'"
    return setPosition(0, ShadeComponentType.VANE)
}

/**
 * Sets the vanes to the halfway point. If the shade level position is > 0, the shade will
 * first close.
 * TODO: this is hard-coded to 50%. It should really be a preference setting.
 */
def presetPosition() {
    log.debug "Executing 'presetPosition'"
    return setPosition(50, ShadeComponentType.VANE)
}

def jog() {
    log.debug "Executing jog()"
    return startJog()
}