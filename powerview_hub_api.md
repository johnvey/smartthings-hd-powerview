# PowerView Hub REST API

This API information was gathered from a PowerView hub (revision=1, subRevision=1, build=837).

## Hub Info

### Registration info
Returns current PowerView account info associated with the Hub
```
GET /api/register
```
Unregistered response, status=200
```
{
    "emailAddress": null
}
```
Registered response
```
{
   /* unknown */
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
Response, status=200
```
{
    "userData": {
        "serialNumber": "201ADACD2047",
        "rfID": "0x87D1",
        "rfIDInt": 34769,
        "rfStatus": 0,
        "hubName": "TXkgaHViIDE=",
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
        "gateway": "192.168.86.1",
        "mask": "255.255.255.0",
        "ip": "192.168.86.51",
        "dns": "192.168.86.1",
        "staticIp": false,
        "addressKind": "newPrimary",
        "unassignedShadeCount": 0,
        "undefinedShadeCount": 0
    }
}
```

## Scenes

### All scenes
Returns all configured scenes
```
GET /api/scenes
```
Response, status=200
```
{
    "sceneIds": [
        11983,
        58509,
        12744
    ],
    "sceneData": [
        {
            "id": 11983,
            "networkNumber": 0,
            "name": "U3VucmlzZQ==",
            "roomId": 43542,
            "order": 0,
            "colorId": 6,
            "iconId": 0
        },
        {
            "id": 58509,
            "networkNumber": 2,
            "name": "RGF5",
            "roomId": 43542,
            "order": 1,
            "colorId": 9,
            "iconId": 0
        },
        {
            "id": 12744,
            "networkNumber": 1,
            "name": "U3Vuc2V0",
            "roomId": 43542,
            "order": 2,
            "colorId": 3,
            "iconId": 0
        }
    ]
}
```

### Scene
Activate scene
```
GET /api/scenes/sceneid=<SCENE_ID>
```
* `SCENE_ID`: numeric ID of the scene to activate

Response, status=200
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

