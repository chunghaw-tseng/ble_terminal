package com.bluetooth.ble_terminal;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;


public class BeaconFragment extends Fragment {

    private String TAG = "Beacon Fragment";
//    private ArrayList<BluetoothDevice> deviceList;
    private CustomAdapter deviceAdapter;

    private ArrayList<Beacon> beaconList;

//    private HashMap<String, Integer> RSSI;
//    private HashMap<String, byte[]> beacon_info;


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.device_list, container, false);
        beaconList = (ArrayList<Beacon>) getArguments().getSerializable("beacondevices");
        deviceAdapter = new CustomAdapter(getContext(), beaconList);
        ListView listView = (ListView) rootView.findViewById(R.id.device_list);
        listView.setAdapter(deviceAdapter);
        listView.setOnItemClickListener(listenerItemSelected);

        return rootView;
    }

    public void updateBeacons(ArrayList<Beacon> beacons){
        if(deviceAdapter != null){
            beaconList = beacons;
            deviceAdapter.notifyDataSetChanged();
        }
    }

    // Listener for when a device is selected from the list. Connects to device and initiates service discovery
    private AdapterView.OnItemClickListener listenerItemSelected = new AdapterView.OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            final Beacon device = beaconList.get(position);
            Log.d(TAG, "Device " + device.getName() + " selected");
            // There is no way to determine with reliability whether a device is connectable in earlier Android versions
            // We can choose to either try to connect to a beacon and hope for the best, or not allow connections
//            ((DeviceSelection)getActivity()).connectDevice(device.getAddress());
            switch (parent.getId()){
                case R.layout.ble_beacon:
                    break;
                case R.layout.beacon_device:
                    Toast.makeText(getActivity().getApplicationContext(), "Connecting to beacons is not allowed",Toast.LENGTH_SHORT).show();
                    break;
            }
        }
    };

    static String turnBytesToHex(byte[] bytes){
        String hexbytes = "";
        for(byte b : bytes){
            hexbytes += String.format("%02x",b);
        }
        return hexbytes;
    }

    /**
     * \brief This class extends the BaseAdapter to hold a list of Bluetooth devices discovered during
     * scanning. New devices are allocated a 'device' layout which is populated and added to the list.
     */
    public class CustomAdapter extends BaseAdapter {
        private Context context;
        private List<Beacon> deviceList;
        private String P_UUID;
        private String Major, Minor;
        private String Measured_power;
        private short typeId, sequence_num, temp, hum, light,co2, air, sound, accX, accY, accZ, electric, txpower;


        CustomAdapter(Context context, List<Beacon> deviceList) {
            this.context = context;
            this.deviceList = deviceList;
        }

        private void parseiBeaconData(byte[] data){
            int i = 9;
            P_UUID = "";

            //Proximity UUID
            while(i < 25){
                P_UUID += String.format("%02x",data[i]);
                i++;
            }
            //Major
            Major = String.format("%02x",data[25]);
            Major += String.format("%02x",data[26]);
            //Minor
            Minor = String.format("%02x",data[27]);
            Minor += String.format("%02x",data[28]);
            //Measured power
            Measured_power = String.format("%02x",data[29]);
        }


        private void parseBLEBeaconData(byte[] data){
//            Log.d(TAG, "Beacon Data " + turnBytesToHex(data));
            typeId = data[0];
            sequence_num = parseBytesToInt_LittleEndian(Arrays.copyOfRange(data, 1,2+1));
            temp = parseBytesToInt_LittleEndian(Arrays.copyOfRange(data, 3,4+1));
            hum = parseBytesToInt_LittleEndian(Arrays.copyOfRange(data, 5,6+1));
            light = parseBytesToInt_LittleEndian(Arrays.copyOfRange(data, 7,8+1));
            co2 = parseBytesToInt_LittleEndian(Arrays.copyOfRange(data, 9,10+1));
            air = parseBytesToInt_LittleEndian(Arrays.copyOfRange(data, 11,12+1));
            sound = parseBytesToInt_LittleEndian(Arrays.copyOfRange(data, 13,14+1));
            accX = parseBytesToInt_LittleEndian(Arrays.copyOfRange(data, 15,16+1));
            accY = parseBytesToInt_LittleEndian(Arrays.copyOfRange(data, 17,18+1));
            accZ = parseBytesToInt_LittleEndian(Arrays.copyOfRange(data, 19,20+1));
            electric = parseBytesToInt_LittleEndian(Arrays.copyOfRange(data, 21,22+1));
            txpower = data[23];
        }

        private short parseBytesToInt_LittleEndian(byte[] unparsed){
            ByteBuffer wrapped = ByteBuffer.wrap(unparsed).order(ByteOrder.LITTLE_ENDIAN);
            return wrapped.getShort();
        }

        @Override
        public int getItemViewType(int position) {
           return  deviceList.get(position).getType();
        }

        @Override
        public int getViewTypeCount() {
            return 2;
        }

        @Override
        public Beacon getItem(int position) {
            return this.deviceList.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public int getCount() {
            return deviceList.size();
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            LayoutInflater mInflater = (LayoutInflater) context
                    .getSystemService(Activity.LAYOUT_INFLATER_SERVICE);

            int type = getItemViewType(position);

            Beacon thisDevice = getItem(position);

            if(type == DeviceSelection.iBEACON) {
                convertView = mInflater.inflate(R.layout.beacon_device, null);

                byte[] data = thisDevice.getBeacondata();

                // TODO iBeacon Data parsing is done here
                parseiBeaconData(data);

                ((TextView) convertView.findViewById(com.bluetooth.ble_terminal.R.id.device_name)).setText(thisDevice.getName());
                ((TextView) convertView.findViewById(com.bluetooth.ble_terminal.R.id.device_address)).setText(thisDevice.getMacaddress());
                ((TextView) convertView.findViewById(com.bluetooth.ble_terminal.R.id.device_rssi)).setText(String.format(Locale.getDefault(), "%d dBm", thisDevice.getRssi()));
                ((TextView) convertView.findViewById(R.id.p_uuid)).setText(String.format(Locale.getDefault(), "Proximity UUID: %s", P_UUID));
                ((TextView) convertView.findViewById(R.id.major)).setText(String.format(Locale.getDefault(), "Major: %d", Integer.valueOf(Major,16)));
                ((TextView) convertView.findViewById(R.id.minor)).setText(String.format(Locale.getDefault(), "Minor: %d", Integer.valueOf(Minor,16)));
                ((TextView) convertView.findViewById(R.id.power)).setText(String.format(Locale.getDefault(), "Measured power : %d",Integer.valueOf(Measured_power,16)));
            }else{

                convertView = mInflater.inflate(R.layout.ble_beacon, null);

                byte[] data = thisDevice.getBeacondata();


                ((TextView) convertView.findViewById(R.id.ble_beacon_name)).setText(thisDevice.getName());
                ((TextView) convertView.findViewById(R.id.beacon_mac)).setText(thisDevice.getMacaddress());
                ((TextView) convertView.findViewById(R.id.beacon_rssi)).setText(String.format(Locale.getDefault(), "%d dBm", thisDevice.getRssi()));

                // TODO Beacon Data parsing is done here
                parseBLEBeaconData(data);
                ((TextView) convertView.findViewById(R.id.time_lbl)).setText(thisDevice.getLastbeacontime());
                ((TextView) convertView.findViewById(R.id.beacon_sequence)).setText(String.format(Locale.getDefault(), "%d", sequence_num));
                ((TextView) convertView.findViewById(R.id.temp_lbl)).setText(String.format(Locale.getDefault(), "%.2f", (float) temp / 100));
                ((TextView) convertView.findViewById(R.id.hum_lbl)).setText(String.format(Locale.getDefault(), "%d", hum));
                ((TextView) convertView.findViewById(R.id.light_lbl)).setText(String.format(Locale.getDefault(), "%d", light));
                ((TextView) convertView.findViewById(R.id.co2_lbl)).setText(String.format(Locale.getDefault(), "%d", co2));
                ((TextView) convertView.findViewById(R.id.accx_lbl)).setText(String.format(Locale.getDefault(), "%.2f", (float) accX / 100));
                ((TextView) convertView.findViewById(R.id.accy_lbl)).setText(String.format(Locale.getDefault(), "%.2f", (float) accY / 100));
                ((TextView) convertView.findViewById(R.id.accZ_lbl)).setText(String.format(Locale.getDefault(), "%.2f", (float) accZ / 100));
                ((TextView) convertView.findViewById(R.id.electricity_lbl)).setText(String.format(Locale.getDefault(), "%d", electric));
                ((TextView) convertView.findViewById(R.id.sound_lbl)).setText(String.format(Locale.getDefault(), "%d", sound));
                ((TextView) convertView.findViewById(R.id.air_lbl)).setText(String.format(Locale.getDefault(), "%d", air));
                ((TextView) convertView.findViewById(R.id.txpower_lbl)).setText(String.format(Locale.getDefault(), "%d", txpower));

            }

            return convertView;
        }
    }



}
