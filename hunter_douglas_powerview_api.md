# Hunter Douglas PowerView Hub REST API (Gen 1)

This API information was gathered from a PowerView hub (revision=1, subRevision=1, build=837 and 855). Gen 2 hubs seem to be incompatible with gen 1 API. I do not have a gen 2 hub to inspect so any help would be appreciated.

## API notes
* The hub exposes an unprotected HTTP server so there is no authentication. As such, it should never be exposed to the internet.
* Almost all user-configurable "name" fields are Base64 encoded. Use whatever standard library call is in your language to decode this.

## Hub Info

### Registration info
Returns current PowerView account info associated with the Hub
```
GET /api/register
```
Unregistered Sample response, status=200
```
{
    "emailAddress": null
}
```
Registered response
```
{
    "emailAddress": "user@example.org"
}
```
### Firmware info
Returns the hardware information for the hub
```
GET /api/fwversion
```
Sample response, status=200
```
{
    "firmware": {
        "mainProcessor": {
            "name": "PowerView Hub",
            "revision": 1,
            "subRevision": 1,
            "build": 847
        }
    }
}
```

### Register hub
Contacts the Hunter Douglas registration server to associate the hub with the account
```
POST /api/register

// POST body payload is raw JSON blob
{"pvKey":"YOUR_PV_KEY"}
```
* `YOUR_PV_KEY`: the account token issued by https://homeauto.hunterdouglas.com/. Looks to be a hex string.

Unsuccessful association, status=404
```
{
    "error": null
}
```

### Hub configuration
Returns current configuration information
```
GET /api/userdata
```
Sample response, status=200
```
{
    "userData": {
        "serialNumber": "201ADACD0000",
        "rfID": "0x87D1",
        "rfIDInt": 34769,
        "rfStatus": 0,
        "hubName": "TXaakgaHViIDE=",
        "macAddress": "00:26:74:17:d8:ff",
        "roomCount": 1,
        "shadeCount": 3,
        "groupCount": 1,
        "sceneCount": 3,
        "sceneMemberCount": 9,
        "multiSceneCount": 0,
        "multiSceneMemberCount": 0,
        "scheduledEventCount": 2,
        "sceneControllerCount": 0,
        "sceneControllerMemberCount": 0,
        "accessPointCount": 0,
        "localTimeDataSet": true,
        "enableScheduledEvents": true,
        "remoteConnectEnabled": false,
        "editingEnabled": true,
        "setupCompleted": false,
        "gateway": "192.168.1.1",
        "mask": "255.255.255.0",
        "ip": "192.168.1.51",
        "dns": "192.168.1.1",
        "staticIp": false,
        "addressKind": "newPrimary",
        "unassignedShadeCount": 0,
        "undefinedShadeCount": 0
    }
}
```

### Hub discovery broadcast
Upon startup, the hub will broadcast its presence via UDP onto the network. Because I am working with SmartThings and their UDP support is not fully baked, I didn't implement any kind of listener. However, I did capture the initial packets if you are interested in building something to discover the hub.

Sample from [Text dump from Wireshark](./pcap/powerview-beacon.txt) | [PCAP file](./pcap/powerview-beacon.pcapng)
```
No.     Time           Source                Destination           Protocol Length Info
 290346 252.392885     192.168.86.61         255.255.255.255       UDP      102    344 → 344 Len=60

Frame 290346: 102 bytes on wire (816 bits), 102 bytes captured (816 bits) on interface 0
Ethernet II, Src: Electron_c6:94:ce (00:26:74:c6:94:ce), Dst: Broadcast (ff:ff:ff:ff:ff:ff)
Internet Protocol Version 4, Src: 192.168.86.61, Dst: 255.255.255.255
User Datagram Protocol, Src Port: 344, Dst Port: 344
    Source Port: 344
    Destination Port: 344
    Length: 68
    Checksum: 0xa45b [unverified]
    [Checksum Status: Unverified]
    [Stream index: 210]
Data (60 bytes)

0000  01 69 73 73 75 65 20 62 65 61 63 6f 6e 00 00 00   .issue beacon...
0010  00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00   ................
0020  00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00   ................
0030  00 00 00 00 00 00 00 00 00 00 00 00               ............
    Data: 01697373756520626561636f6e0000000000000000000000...
    [Length: 60]
```

### Shade discovery ###
The hub can discover new shades that are within RF range. The sequence appears to be:

Client requests that the hub enter discovery mode:
```
GET /api/rfnetwork/discovershades
```
```
{}
```

The client then polls the hub for its discovery status:
```
GET /api/rfnetwork/
```
```
{
    "rfNetwork": {
        "serialNumber":"8886023B22C4276A",
        "rfID":"0x87D1",
        "rfIDInt":34769,
        "rfStatus":1, // NOTE that this is active
        "shadeIds":[],
        "discoveryActive":true // NOTE that this is true
    }
}
```

The above GET request is repeated until the response payload changes as follows:
```
{
    "rfNetwork": {
        "serialNumber":"8886023B22C4276A",
        "rfID":"0x87D1",
        "rfIDInt":34769,
        "rfStatus":0, // NOTE that this is inactive
        "shadeIds":[],
        "discoveryActive":false // NOTE that this is false
    }
}
```

At this point the client will ask the user to stop the discovery process and issue the following:
```
GET /api/rfnetwork/stopdiscovershades
```
```
{}
```


## Shades

### API notes

The `shadeData[].positions` key can take on multiple states depending on the family of shade under control (see the `shadeData[].type` field). These examples are based on Sillhouette shades so not all of these values may apply to yours.
1. The `positions` dict will only ever show you one type of `posKind1`: either 1 or 3. This is because the shade cannot physically exist with both shade and vane open (to any degree). Therefore one can make the assumption that if there is a non-zero `position1` while `posKind1 == 1`, then `position1 == 0` for the implied `posKind1 == 3`.
1. The range of integer values for `position1` depends on `posKind1`:
   * `0-65535` if `posKind1 == 1`
   * `0-32767` if `posKind1 == 3`

Shade fully open
```
"positions": {
    "position1": 65535,
    "posKind1": 1
}
```
Shade fully closed AND vane fully closed
```
"positions": {
    "position1": 0,
    "posKind1": 1
}

--- OR ---

"positions": {
    "position1": 0,
    "posKind1": 3
}
```
Shade fully closed and vane fully open
```
"positions": {
    "position1": 32767,
    "posKind1": 3
}
```

### All shades
Returns all configured shades
```
GET /api/shades
```
Sample response, status=200
```
{
    "shadeIds": [
        1694,
        29460,
        22246
    ],
    "shadeData": [
        {
            "id": 1694,
            "name": "TWFzdGVyIHNpZGU=",
            "roomId": 56192,
            "groupId": 19103,
            "order": 0,
            "type": 23,
            "batteryStrength": 170,
            "batteryStatus": 3,
            "positions": {
                "position1": 32767,
                "posKind1": 3
            }
        },
        {
            "id": 29460,
            "name": "TWFzdGVyIGRvb3IgcmlnaHQ=",
            "roomId": 56192,
            "groupId": 19103,
            "order": 1,
            "type": 23,
            "batteryStrength": 170,
            "batteryStatus": 3,
            "positions": {
                "position1": 32767,
                "posKind1": 3
            }
        },
        {
            "id": 22246,
            "name": "TWFzdGVyIGRvb3IgbGVmdA==",
            "roomId": 56192,
            "groupId": 19103,
            "order": 2,
            "type": 23,
            "batteryStrength": 171,
            "batteryStatus": 3,
            "positions": {
                "position1": 32767,
                "posKind1": 3
            }
        }
    ]
}
```

### Single shade
Returns configuration data for a specific shade
```
GET /api/shades/<SHADE_ID>
```
Querystring args:
* `refresh=true` - forces hub to poll shade for latest info
* `updateBatteryLevel=true` - forces refresh of battery level information. NOTE: this will also cause the shade to jog once

These two params do not like to be combined.

Sample response, status=200
```
{
    "shade": {
        "id": 1694,
        "name": "TWFzdGVyIHNpZGU=",
        "roomId": 56192,
        "groupId": 19103,
        "order": 0,
        "type": 23,
        "timedOut": false,
        "batteryStrength": 170,
        "batteryStatus": 3,
        "positions": {
            "position1": 32767,
            "posKind1": 3
        }
    }
}
```

### Set single shade
Updates the settings for a single shade
```
PUT /api/shades/<SHADE_ID>

// PUT body is raw JSON
{"shade":{"id":<SHADE_ID>,"positions":{"position1":32767,"posKind1":3}}}
```
Sample response, status=200
```
{
    "shade": {
        "id": 1694,
        "name": "TWFzdGVyIHNpZGU=",
        "roomId": 56192,
        "groupId": 19103,
        "order": 0,
        "type": 23,
        "batteryStrength": 170,
        "batteryStatus": 3,
        "positions": {
            "position1": 32767,
            "posKind1": 3
        }
    }
}
```


### Jog single shade
Instructs the shade to partially open and close in order to physically identify the shade under control
```
PUT /api/shades/<SHADE_ID>

// PUT body is raw JSON
{"shade":{"motion":"jog"}}
```
Response payload is same as above


### Calibrate single shade
Recalibrates the stop limits of the shade by fully opening and then closing the shade to establish its physical bounds
```
PUT /api/shades/<SHADE_ID>

// PUT body is raw JSON
{"shade":{"motion":"calibrate"}}
```
Response payload is same as above


## Scenes

### All scenes
Returns configuration for all scenes
```
GET /api/scenes
```
Sample response, status=200
```
{
    "sceneIds": [
        4102,
        34865,
        59338
    ],
    "sceneData": [
        {
            "id": 4102,
            "networkNumber": 0,
            "name": "U2hhZGVzIHN1bnJpc2U=",
            "roomId": 56192,
            "order": 0,
            "colorId": 5,
            "iconId": 96
        },
        {
            "id": 34865,
            "networkNumber": 1,
            "name": "U2hhZGVzIGRheQ==",
            "roomId": 56192,
            "order": 1,
            "colorId": 9,
            "iconId": 95
        },
        {
            "id": 59338,
            "networkNumber": 2,
            "name": "U2hhZGVzIHN1bnNldA==",
            "roomId": 56192,
            "order": 2,
            "colorId": 2,
            "iconId": 97
        }
    ]
}
```

### Single scene
Returns configuration for a specific scene
```
GET /api/scenes/<SCENE_ID>
```
Sample response, status=200
```
{
    "scene": {
        "id": 4102,
        "networkNumber": 0,
        "name": "U2hhZGVzIHN1bnJpc2U=",
        "roomId": 56192,
        "order": 0,
        "colorId": 5,
        "iconId": 96
    },
    "sceneData": [
        {
            "id": 34865,
            "networkNumber": 1,
            "name": "U2hhZGVzIGRheQ==",
            "roomId": 56192,
            "order": 1,
            "colorId": 9,
            "iconId": 95
        },
        {
            "id": 59338,
            "networkNumber": 2,
            "name": "U2hhZGVzIHN1bnNldA==",
            "roomId": 56192,
            "order": 2,
            "colorId": 2,
            "iconId": 97
        }
    ]
}
```


### Activate scene
Activates a specific scene
```
GET /api/scenes?sceneid=<SCENE_ID>
```
* `SCENE_ID`: numeric ID of the scene to activate

Sample response, status=200
```
// Example: GET api/scenes?sceneid=11983
{
    "scene": {
        "shadeIds": [
            29460,
            1694,
            22246
        ]
    }
}
```
Unknown scene, status=404
```
// Example: GET api/scenes?sceneid=999
{
    "scene": {
        "shadeIds": []
    }
}
```
## Scene Collections

### All scene collections
```
GET /api/scenecollections
```
Sample response, status=200
```
{
    "sceneCollectionIds": [
        27745
    ],
    "sceneCollectionData": [
        {
            "id": 27745,
            "name": "TXVsdGk=",
            "order": 0,
            "colorId": 0,
            "iconId": 0
        }
    ]
}
```

### Single scene collection
```
GET /api/scenecollections/<SCENE_COLLECTION_ID>
```
* `SCENE_COLLECTION_ID`: numeric ID of the scene collection to view

Sample response, status=200
```
{
    "sceneCollection": {
        "id": 27745,
        "name": "TXVsdGk=",
        "order": 0,
        "colorId": 0,
        "iconId": 0
    },
    "sceneCollectionData": []
}
```

### Activate a scene collection
```
GET /api/scenecollections?sceneCollectionId=<SCENE_COLLECTION_ID>
```
* `SCENE_COLLECTION_ID`: numeric ID of the scene collection to activate

Sample response, status=200
```
// Example: GET /api/scenecollections?sceneCollectionId=27745
{}
```

## Rooms

### All rooms
```
GET /api/rooms
```
Sample response, status=200
```
{
    "roomIds": [
        56192
    ],
    "roomData": [
        {
            "id": 56192,
            "name": "TWFzdGVy",
            "order": 0,
            "colorId": 0,
            "iconId": 0
        }
    ]
}
```

### Single room
```
GET /api/rooms?roomid=56192
```
Sample response, status=200
```
{
    "room": {
        "id": 56192,
        "name": "TWFzdGVy",
        "order": 0,
        "colorId": 0,
        "iconId": 0
    },
    "roomData": []
}
```

## Other
These are other endpoints that I did not use

```
GET /api/scheduledevents
```
Sample response, status=200
```
{
    "scheduledEventIds": [],
    "scheduledEventData": []
}
```
