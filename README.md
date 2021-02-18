
# BLE Terminal Android App

Android application that connects to specific BLE device and shows specific beacons in the area.
This current application contains a scanner for BLE devices as well as an interface to be able to send different serial commands to the specific device.
In order to use this application, you will need to know the basics of bluetooth connectivity and to have the information of the bluetooth devices.

# How to use

1.  The main component of this application is the BLE Service. It contains all the code related to BLE connectivity and all the connection states.
2.  Application will start on the device selection page, where bluetooth permissions need to be accepted.
3.  The device selection will show 2 tabs, one with the beacons and the other with the ble devices. Please change the company ID to your own device ID.
"""
    // Please check the ID in the bluetooth website
    static int CompanyID = 0x0000;
"""
4. Scanning of devices will be started as soon as the permissions are accepted.
5. Found devices will be shown on the tabs. To be able to connect BLE devices please edit the UUIDs parameters to the UUIDs of your device.
"""
        // Characteristics as well as services are needed
        private static final UUID CCCD                  = UUID.fromString("");
        public static final UUID DEVICE_SERVICE_UUID        = UUID.fromString("");    //  Service UUID
        public static final UUID NOTIFICATION_CHARACTERISTIC_UUID = UUID.fromString("");    // Read Notification Characteristic
        public static final UUID WRITE_NO_RESPONSE_CHARACTERISTIC_UUID = UUID.fromString(""); //  Write No Response Characteristic
        public static final UUID INDICATION_CHARACTERISTIC_UUID = UUID.fromString(""); //  Read Indication Characteristic
        public static final UUID WRITE_CHARACTERISTIC_UUID = UUID.fromString("");    // Write Characteristic
""""
6. All the terminal communication will be handled in this activity. (Terminal.java)
7. For the beacons, please check the BeaconFragment activity. This current application will detect ibeacon and beacons that belong to company ID, set on the DeviceSelection activity.
8. To edit how to show the beacon data, change the functions parseiBeaconData and parseBLEBeaconData in the BeaconFragment activity.