## Features
- Measure Bluetooth LE usage
- Anonnymously collect data and generate global Bluetooth usage statistics.

## Setup BTBeat
To add BTBeat to your application add BTBeat.jar to your projects libs folder, import cz.septemberprojects.btbeatandroid.BTBeatManager and  cz.septemberprojects.btbeatandroid.BTBeatManager.Event and instantiate class BTBeatManager by BTBeatManager(Context context, String appName, String appVersion)

 ## Events
 
```
BTBeatManager.allowAutomaticEvents(boolean allow)
```

Automatically check if BlueTooth is turned on and off. By default, this option is on.

```
BTBeatManager.addEvent(Event.eventName);
```

 Predefined events:
 - *BTBEAT_EVENT_BT_UNSUPPORTED*
 - *BTBEAT_EVENT_BT_UNAUTHORISED*
 - *BTBEAT_EVENT_BT_AUTHORISATION_REQUEST_SENT*
 - *BTBEAT_EVENT_BT_AUTHORISED*
 - *BTBEAT_EVENT_BT_TURNED_ON*
 - *BTBEAT_EVENT_BT_TURNED_OFF*
 - *BTBEAT_EVENT_NOTIFICATION_SENT*
 - *BTBEAT_EVENT_NOTIFICATION_CONVERSION*

*BTBEAT_EVENT_BT_TURNED_ON*, *BTBEAT_EVENT_BT_TURNED_OFF*, *BTBEAT_EVENT_BT_UNSUPPORTED* and *BTBEAT_EVENT_BT_UNAUTHORISED*  are added automatically if automatic events are allowed.

 ## Interaction with server

Default option is to send data every time onCreate() is called. For this, call BTBeatManager.onCreate() at your main activity onCreate(); 

To turn this option on call BTBeatManager.setAutomaticDataSubmit(); By default, this option is on.

Or use BTBeatManager.setSubmitDataPeriodicallyWithDelay(int delayMilis) to send data in selected intervals.

Last option is to send collected data to server manually by calling BTBeatManager.sendData();



