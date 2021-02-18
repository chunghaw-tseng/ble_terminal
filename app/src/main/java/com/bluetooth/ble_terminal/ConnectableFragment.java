package com.bluetooth.ble_terminal;

import android.app.Activity;
import android.bluetooth.BluetoothDevice;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class ConnectableFragment extends Fragment {

    private String TAG = "Connectable Fragment";
    private ArrayList<BluetoothDevice> deviceList;
    private CustomAdapter deviceAdapter;
    private HashMap<String, Integer> RSSI;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View rootView = inflater.inflate(R.layout.device_list, container, false);
        Log.d(TAG, "Making Connectable Fragment");
        deviceList = getArguments().getParcelableArrayList("connectabledevices");
        deviceAdapter = new CustomAdapter(getContext(), deviceList);
        ListView listView = (ListView) rootView.findViewById(R.id.device_list);
        listView.setAdapter(deviceAdapter);
        listView.setOnItemClickListener(listenerItemSelected);
        return rootView;
    }

    public void updateConnectable(ArrayList<BluetoothDevice> newdevices, HashMap<String, Integer> newrssi) {
        if (deviceAdapter != null) {
            deviceList = newdevices;
            RSSI = newrssi;
            deviceAdapter.notifyDataSetChanged();
        }
    }

    // Listener for when a device is selected from the list. Connects to device and initiates service discovery
    private AdapterView.OnItemClickListener listenerItemSelected = new AdapterView.OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            final BluetoothDevice device = deviceList.get(position);
            Log.d(TAG, "Device " + device.getName() + " selected");
            ((DeviceSelection)getActivity()).connectDevice(device.getAddress());
        }
    };

    /**
     * \brief This class extends the BaseAdapter to hold a list of Bluetooth devices discovered during
     * scanning. New devices are allocated a 'device' layout which is populated and added to the list.
     */
    public class CustomAdapter extends BaseAdapter {
        private Context context;
        private List<BluetoothDevice> deviceList;

        CustomAdapter(Context context, List<BluetoothDevice> deviceList) {
            this.context = context;
            this.deviceList = deviceList;
        }

        @Override
        public BluetoothDevice getItem(int position) {
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
            convertView = mInflater.inflate(com.bluetooth.ble_terminal.R.layout.device, null);

            BluetoothDevice thisDevice = getItem(position);
            Integer rssi = RSSI.get(thisDevice.getAddress());

            ((TextView) convertView.findViewById(com.bluetooth.ble_terminal.R.id.device_name)).setText(thisDevice.getName());
            ((TextView) convertView.findViewById(com.bluetooth.ble_terminal.R.id.device_address)).setText(thisDevice.getAddress());
            ((TextView) convertView.findViewById(com.bluetooth.ble_terminal.R.id.device_rssi)).setText(String.valueOf(rssi) + " dBm");

            return convertView;
        }
    }


}
