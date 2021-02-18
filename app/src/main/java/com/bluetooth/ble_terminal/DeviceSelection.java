package com.bluetooth.ble_terminal;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.app.Dialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.Settings;
import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.ViewGroup;
import android.widget.Toast;
import android.content.Intent;
import com.bluetooth.ble_terminal.BLEService.LocalBinder;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import static com.bluetooth.ble_terminal.Terminal.NOTIFICATION_CHARACTERISTIC_UUID;
import static com.bluetooth.ble_terminal.Terminal.DEVICE_SERVICE_UUID;
import static com.bluetooth.ble_terminal.Terminal.WRITE_NO_RESPONSE_CHARACTERISTIC_UUID;

/**
 * Main application activity launched at runtime. This activity connects to the BLEService and initiates
 * scanning for Bluetooth Low Energy compatible devices. Discovered devices will appear as selectable items
 * on the application UI, selection of a device will launch the Terminal activity.
 *
 * Activity will prompt user for permission to access location on first launch of application (necessary
 * due to known Android bug requiring location permission for background BLE scanning). Activity will
 * request for enabling Bluetooth system service if it is disabled. Not accepting this request will exit
 * the application.
 * \date    10/18/2016
 */
public class DeviceSelection extends AppCompatActivity{

    private static final String TAG = "TerminalApp";
    private static final int PERMISSION_REQUEST_COARSE_LOCATION = 0;

    private static final int REQUEST_ENABLE_BT = 1;
    private static final int REQUEST_OPEN_TERMINAL = 2;
    private static final int REQUEST_ENABLE_LOCATION = 3;

    /// Status notifying that user declined to enable Bluetooth service \hideinitializer
    public static final String REQUEST_ENABLE_BT_DENIED = "com.bluetooth.ble_terminal.BLUETOOTH_DENIED";

    private BLEService mService;
    private ArrayList<BluetoothDevice> connectabledeviceList;
//    private CustomAdapter deviceAdapter;

//    Adding new beacon array
    private ArrayList<Beacon> beaconsList;

    private HashMap<String, Integer> RSSI;

    // TODO Add the BLE company ID
    // Please check the ID in the bluetooth website
    static int CompanyID = 0x0000;

    static final int BLE_BEACON = 1;
    static final int iBEACON = 2;

    private ViewPager vpPager;
    private FragmentStatePagerAdapter adapterViewPager;
    private ConnectableFragment connectable;
    private BeaconFragment beacon;

    private boolean requestBluetooth = false;
    private boolean bindService = false;
    private boolean permissionAccepted = false;

    // Build the alert dialog
    AlertDialog.Builder builder;
    //iBeacon prefix array
    private static final short[] ibeacon_prefix = {0x02,0x01,0x06,0x1A,0xff,0x4C,0x00,0x02,0x15};


    /**
     * @brief Initialises UI components, performs a permissions check and binds to BLE service class.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");

        // Initialise layout
        setContentView(com.bluetooth.ble_terminal.R.layout.activity_main);

        connectabledeviceList = new ArrayList<>();
        beaconsList = new ArrayList<>();
        RSSI = new HashMap<>();

        vpPager = (ViewPager) findViewById(R.id.pager);
        vpPager.setOffscreenPageLimit(2);
        adapterViewPager = new MyPagerAdapter(getSupportFragmentManager(), DeviceSelection.this);
        vpPager.setAdapter(adapterViewPager);
        TabLayout tabLayout = (TabLayout) findViewById(R.id.sliding_tabs);
        tabLayout.setupWithViewPager(vpPager);

        builder = new AlertDialog.Builder(this);
        builder.setTitle("Location Services Not Active");
        builder.setMessage("Location Services are required for BLE scanning");
        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialogInterface, int i) {
            // Show location settings when the user acknowledges the alert dialog
                Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                startActivityForResult(intent, REQUEST_ENABLE_LOCATION);
            }
        });
        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                StartScanning();
            }
        });
        registerReceiver(HardwareStatusReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));

        // Check if we have the appropriate permissions for BLE background scanning. If not, wait for user
        // to select an option before we attempt to bind to the service
        checkPermissions();
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.d(TAG, "onStart");
    }

    ///Checks state of Bluetooth radio and resumes scanning for BLE devices.
    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");
//        deviceList.clear();
//        deviceAdapter.notifyDataSetChanged();

        // Start listening for Bluetooth/Location state updates
        IntentFilter hfilter = new IntentFilter();
        hfilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        hfilter.addAction(LocationManager.PROVIDERS_CHANGED_ACTION);
        registerReceiver(HardwareStatusReceiver, hfilter);
        registerReceiver(gattStatusReceiver, new IntentFilter(BLEService.ACTION_GATT_SERVICES));
        bindToService();

        StartScanning();
    }

    /// Halts BLE scanning while activity is not running in the foreground.
    @Override
    protected void onPause() {
        super.onPause();

        // Stop listening for Bluetooth updates
        try {
            unregisterReceiver(HardwareStatusReceiver);
        } catch (IllegalArgumentException e) {
        }
        try {
            unregisterReceiver(gattStatusReceiver);
        } catch (IllegalArgumentException e) {
        }

        if (mService != null) {
            StopScanning();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        try {
            unregisterReceiver(HardwareStatusReceiver);
        } catch (IllegalArgumentException e) {
        }
        try {
            unregisterReceiver(gattStatusReceiver);
        } catch (IllegalArgumentException e) {
        }
        if (mService != null) {
            StopScanning();
            unbindService(mServiceConnection);
        }
        Log.d(TAG, "Bye Bye");
    }

    @Override
    protected void onActivityResult(int requestCode, int result, Intent data) {
        switch (requestCode) {
            case REQUEST_ENABLE_BT:
                // We requested to enable the Bluetooth system service
                if (result != RESULT_OK) {
                    Toast.makeText(this, "Application requires Bluetooth", Toast.LENGTH_SHORT).show();
                }
                requestBluetooth = false;
                StartScanning();
                break;
            case REQUEST_OPEN_TERMINAL:
                // Our terminal activity returned

                // Restart scanning
                StartScanning();
                break;
            case REQUEST_ENABLE_LOCATION:
                StartScanning();
                break;
        }
    }

    /**
     * Handles results of permission requests
     *
     * @param requestCode  Value used to request permission dialog
     * @param permissions  List of permissions requested from user
     * @param grantResults Results of each permission request
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        // We only have one permission to deal with
        if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            // Start scanning for devices if we bound to the service while waiting for permission
            Log.d(TAG, "Permission Granted, Start Scanning");
            permissionAccepted = true;
            bindToService();

        } else {
            // Permission not granted, no point continuing the application. User can relaunch and accept.
            Toast.makeText(this, "Application requires permissions for operation", Toast.LENGTH_SHORT).show();
            finish();
        }
    }


    private void bindToService(){
        if(!bindService && permissionAccepted) {
            // Bind to BLE service so we can initiate scanning
            Log.d(TAG, "Bind to service");
            Intent intent = new Intent(this, BLEService.class);
            bindService(intent, mServiceConnection, Context.BIND_AUTO_CREATE);
        }
    }

    /**
     * Receiver for BluetoothAdapter state changes (Bluetooth service enabled/disabled)
     */
    private BroadcastReceiver HardwareStatusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            switch (intent.getAction()) {
                // Do a hardware check and start scanning on any hardware state change
                case BluetoothAdapter.ACTION_STATE_CHANGED:
                case LocationManager.PROVIDERS_CHANGED_ACTION:
                    StartScanning();
                    break;
            }
        }
    };

    BroadcastReceiver gattStatusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.d(TAG, action);
            if (action.equals(BLEService.ACTION_GATT_CONNECTED)) {

            }
            if (action.equals(BLEService.ACTION_GATT_DISCONNECTED)) {

            }
            if (action.equals(BLEService.ACTION_GATT_SERVICES)) {
                // Check if the required services are available
                // Check if this is a BLE device
                BluetoothGattService BLEService = mService.getService(DEVICE_SERVICE_UUID);
                if (BLEService == null) {
                    Log.d(TAG, "Not a BLE device");
                    Toast.makeText(getApplicationContext(), "Not a BLE device", Toast.LENGTH_SHORT).show();
                    mService.disconnectDevice();
                } else {
                    // Check TX and RX UUIDs
                    BluetoothGattCharacteristic RxChar = BLEService.getCharacteristic(NOTIFICATION_CHARACTERISTIC_UUID);
                    BluetoothGattCharacteristic TxChar = BLEService.getCharacteristic(WRITE_NO_RESPONSE_CHARACTERISTIC_UUID);
                    if (RxChar == null || TxChar == null) {
                        // BLE device doesn't support Tx/Rx communication
                        Log.d(TAG, "No Tx/Rx");
                        Toast.makeText(getApplicationContext(), "Device is not running terminal service", Toast.LENGTH_SHORT).show();
                    } else {
                        Intent terminal = new Intent(getApplicationContext(), Terminal.class);
                        // Stop Bluetooth scanning while the terminal activity is open
                        StopScanning();
                        // Start terminal application
                        startActivityForResult(terminal, REQUEST_OPEN_TERMINAL);
                    }
                }
            }

        }
    };

    // Class instance for handling service connection events
    private ServiceConnection mServiceConnection = new ServiceConnection() {
        /**
         *  Service is initialised upon successful connection and scanning for BLE devices is
         *  initiated. Prompts the user to enable Bluetooth system service if disabled.
         */
        public void onServiceConnected(ComponentName className, IBinder rawBinder) {
            mService = ((LocalBinder) rawBinder).getService();
            Log.d(TAG, "onServiceConnected mService= " + mService);
            bindService = true;
            // Initialise BLE service
            if (!mService.initialiseService()) {
                // There was an error configuring the Bluetooth hardware
                finish();
            } else {
                StartScanning();
            }
        }

        // When we unbind from the service
        public void onServiceDisconnected(ComponentName classname) {
            Log.d(TAG, "onServiceDisconnected");
            StopScanning();
            mService = null;
            bindService = false;
        }
    };

    private void StartScanning(){
        Log.d(TAG, "Try to scan");
        if (mService != null && isBluetoothEnabled() && isLocationEnabled()){
                mService.startScan(mScanCallback);
        }
    }

    private void StopScanning(){
            mService.stopScan(mScanCallback);
    }

    //Check if advertising data matches beacon format
//    Remake this function
    private boolean checkiBeacon(byte[] scanRecord){
        try {
            for (int i = 0; i < 9; i++) {
                //Company ID
                if(i==5||i==6){
                    continue;
                }
                if (!String.format("%02x",scanRecord[i]).equals(String.format("%02x",ibeacon_prefix[i]))) {
                    return false;
                }
            }
        }catch(ArrayIndexOutOfBoundsException e){
            return false;
        }

        return true;
    }

    @TargetApi(26)
    // Checks the Beacon data for correctness
    private boolean checkBLEBeacon(ScanRecord scanRecord){
        byte[] manufacturerData = scanRecord.getManufacturerSpecificData(CompanyID);
        if (manufacturerData != null){
            if(manufacturerData.length == 24){
                // Specific Beacon contains 24 data
                return true;
            }
        }
        return false;
    }


    // Callback for the scanning
    // API Above 26
    private ScanCallback mScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice btDevice = result.getDevice();
            if (btDevice.getName() != null) {
//                IMPORTANT Cannot check if connectable since it starts on Android 8
//                Check if iBEACON
                if (checkiBeacon(result.getScanRecord().getBytes())) {
                    addBeacon(btDevice, result.getScanRecord().getBytes(), result.getRssi(), iBEACON);
                } else {
//                    Check if BLE Beacon
                    if (checkBLEBeacon(result.getScanRecord())) {
                        Log.d(TAG, "Beacon");
                        addBeacon(btDevice, result.getScanRecord().getManufacturerSpecificData(CompanyID), result.getRssi(), BLE_BEACON);
                    } else {
                        addDevice(btDevice, result.getRssi());
                    }
                }
            }
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            for (ScanResult sr : results) {
                Log.i("ScanResult - Results", sr.toString());
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            Log.e("Scan Failed", "Error Code: " + errorCode);
        }
    };



    public void connectDevice(String address){
        mService.connectToDevice(address);
    }

    /**
     * @param device Bluetooth device to add/update
     * @param rssi   Signal strength of device
     * @brief Add a newly discovered device to the list so it can be displayed on the UI. Updates devices
     * already present
     */
    private void addDevice(BluetoothDevice device, int rssi) {
        // Check if device exists in list already
//        TODO Check here which of the fragments to send the device list
//        TODO
        if (!connectabledeviceList.contains(device)) {
            connectabledeviceList.add(device);
        }
        RSSI.put(device.getAddress(), rssi);       // Single RSSI reading
        connectable.updateConnectable(connectabledeviceList, RSSI);
    }


    private void addBeacon(BluetoothDevice device,byte[] beacondata,int rssi, int type){
        DateFormat dateFormat = new SimpleDateFormat("HH:mm:ss");
        for (Beacon b : beaconsList){
            if(b.getName().equals(device.getName()) && b.getMacaddress().equals(device.getAddress())){
//                Exists
                b.setRssi(rssi);
                b.setBeacondata(beacondata);
                b.setLastbeacontime(dateFormat.format(new Date()));
                beacon.updateBeacons(beaconsList);
                return;
            }
        }
        beaconsList.add(new Beacon(device.getName(), device.getAddress(),dateFormat.format(new Date()), rssi, type, beacondata));
        beacon.updateBeacons(beaconsList);
    }


    /**
     * @brief Checks if the user has previously accepted permissions request for this application. If not
     * a dialog window is displayed to request permission to access location services.
     * @note Required as of API level 23
     */
    private boolean checkPermissions() {
        Log.d(TAG, "Check Permissions");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (this.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, PERMISSION_REQUEST_COARSE_LOCATION);
                return false;
            }
        }
        permissionAccepted = true;
        return true;
    }

    /**
     * @return True if okay, false if we have to make a request
     * @brief Check state of Bluetooth system service to ensure connection is still valid
     */
    private boolean isBluetoothEnabled() {
        Log.d(TAG, "Checking bluetooth");
        if (!BluetoothAdapter.getDefaultAdapter().isEnabled() && !requestBluetooth) {
            Log.d(TAG, "---------------------- Enable Bluetooth");
            // Request user to enable
            requestBluetooth = true;
            startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), REQUEST_ENABLE_BT);
            return false;
        }
        Log.d(TAG, "Bluetooth On");
        return true;
    }

    /**
     * @return True if okay, false if we have to make a request
     * @brief Check state of Location system service to allow scanning (req'd in API >=23 - Marshmallow)
     */
    private boolean isLocationEnabled() {
        Log.d(TAG, "Checking Location");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Get Location Manager and check for GPS & Network location services
            LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
            boolean gps = lm.isProviderEnabled(LocationManager.GPS_PROVIDER);
            boolean net = lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
            if (!gps && !net) {
                Dialog alertDialog = builder.create();
                alertDialog.setCanceledOnTouchOutside(true);
                alertDialog.show();
                return false;
            }
        }
        Log.d(TAG, "Location On");
        return true;
    }

    /**
     *  ViewPager Class for the fragments
     **/

    public class MyPagerAdapter extends FragmentStatePagerAdapter {
        private int NUM_ITEMS = 2;
        private HashMap<Integer ,Fragment> registeredFragments = new HashMap<>();
        private String[] tabTitles = {"Connectable", "Beacons"};
        private Context context;

        public MyPagerAdapter(FragmentManager fragmentManager, Context context) {
            super(fragmentManager);
            this.context = context;
        }

        // Returns total number of pages
        @Override
        public int getCount() {
            return NUM_ITEMS;
        }

        // Returns the fragment to display for that page
        @Override
        public Fragment getItem(int position) {
            Bundle bundle = new Bundle();
            switch (position) {
                case 0: // Fragment # 0 - This will show FirstFragment
                    Log.d(TAG, "Creating new Connectable");
                    connectable = new ConnectableFragment();
                    bundle.putParcelableArrayList("connectabledevices", connectabledeviceList);
                    connectable.setArguments(bundle);
                    return connectable;
                case 1: // Fragment # 0 - This will show FirstFragment different title
                    //Need the title for the picture
                    Log.d(TAG, "Creating new Beacon");
                    beacon = new BeaconFragment();
                    bundle.putSerializable("beacondevices", beaconsList);
                    beacon.setArguments(bundle);
                    return beacon;
                default:
                    return null;
            }
        }

        // Returns the page title for the top indicator
        @Override
        public CharSequence getPageTitle(int position) {
            return tabTitles[position];
        }

        @Override
        public Object instantiateItem(ViewGroup container, int position) {
            Fragment fragment = (Fragment) super.instantiateItem(container, position);
            registeredFragments.put(position, fragment);
            return fragment;
        }

        @Override
        public int getItemPosition(Object object) {
            return PagerAdapter.POSITION_NONE;
        }

    }

}
