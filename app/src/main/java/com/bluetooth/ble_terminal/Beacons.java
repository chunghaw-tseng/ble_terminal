package com.bluetooth.ble_terminal;

import java.io.Serializable;



class Beacon implements Serializable {

//    RSSI, Name, MacAddress, ScanResponse Data, type
    int rssi, type;
    byte[] beacondata;
    String name, macaddress, lastbeacontime;

    Beacon (String name, String macaddress, String lastbeacontime, int rssi, int type, byte[] beacondata){
        this.name = name;
        this.macaddress = macaddress;
        this.rssi = rssi;
        this.beacondata = beacondata;
        this.type = type;
        this.lastbeacontime = lastbeacontime;
    }


    public void setLastbeacontime(String lastbeacontime) {
        this.lastbeacontime = lastbeacontime;
    }

    public String getLastbeacontime() {
        return lastbeacontime;
    }

    public void setRssi(int rssi) {
        this.rssi = rssi;
    }

    public void setBeacondata(byte[] beacondata) {
        this.beacondata = beacondata;
    }

    public byte[] getBeacondata() {
        return beacondata;
    }

    public String getMacaddress() {
        return macaddress;
    }

    public int getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public int getRssi() {
        return rssi;
    }

}
