package com.bluetooth.ble_terminal;

import android.annotation.TargetApi;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static com.bluetooth.ble_terminal.Terminal.BATTERY_LEVEL_UUID;
import static com.bluetooth.ble_terminal.Terminal.INDICATION_CHARACTERISTIC_UUID;
import static com.bluetooth.ble_terminal.Terminal.NOTIFICATION_CHARACTERISTIC_UUID;

/**
 * Background service for interfacing with Bluetooth radio. Provides a number of functions related to BLE device discovery and connection.
 *
 * \date    10/18/2016
 */

public class BLEService extends Service {

    private static final String TAG             = "BLE Service";
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothGatt mBluetoothGatt;
    private ScanSettings settings;
    private BluetoothLeScanner mLEScanner;
    private List<ScanFilter> filterList;
    private IBinder mBinder = new LocalBinder();

    /**
     * @brief Intent on successful connection to GATT server hosted on remote device
     * \hideinitializer
     */
    public static final String ACTION_GATT_CONNECTED    = "com.bluetooth.ble_terminal.ACTION_GATT_CONNECTED";
    /**
     * @brief Intent broadcast when remote GATT server is no longer connected
     * \hideinitializer
     */
    public static final String ACTION_GATT_DISCONNECTED = "com.bluetooth.ble_terminal.ACTION_GATT_DISCONNECTED";
    /**
     * @brief Intent broadcast when remote device service discovery is finished
     * \hideinitializer
     */
    public static final String ACTION_GATT_SERVICES     = "com.bluetooth.ble_terminal.ACTION_GATT_SERVICES";
    /**
     * @brief Intent broadcast on data received from remote device
     * \hideinitializer
     */
    public static final String ACTION_DATA_RECEIVED     = "com.bluetooth.ble_terminal.ACTION_DATA_RECEIVED";
    /**
     * @brief Intent broadcast when descriptor wrote
     * \hideinitializer
     */
    public static final String ACTION_DESCRIPTOR_WROTE = "com.bluetooth.ble_terminal.ACTION_DESCRIPTOR_WROTE";



    /**
     * Custom binder class for connecting to this service
     */
    public class LocalBinder extends Binder {
        BLEService getService() {
            return BLEService.this;
        }
    }

    /**
     * Function called when application binds to this service
     * @param intent Explicit intent referencing this class
     * @return Binder to this service
     */
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    /**
     * Function called when application unbinds from this service. Service will be destroyed if
     * no more connections are present.
     * @param intent Explicit intent referencing this class
     */
    @Override
    public boolean onUnbind(Intent intent) {

        return super.onUnbind(intent);
    }

    /**
     * Service initialisation. Acquires Bluetooth components and checks hardware states.
     * @return  False if a component is not available
     */
    public boolean initialiseService(){

            Log.d(TAG, "Initalize service");
            // Acquire system service
            final BluetoothManager bluetoothManager = (BluetoothManager)getSystemService(Context.BLUETOOTH_SERVICE);
            if (bluetoothManager==null){
                Log.e(TAG, "Bluetooth manager not available");
                Toast.makeText(this, "Bluetooth not available",Toast.LENGTH_SHORT).show();
                return false;
            }

            // Acquire Bluetooth adapter
            mBluetoothAdapter = bluetoothManager.getAdapter();
            if (mBluetoothAdapter == null){
                Log.e(TAG, "Bluetooth adapter not available");
                Toast.makeText(this, "Bluetooth not available on this device",Toast.LENGTH_SHORT).show();
                return false;
            }

            // Check if BLE supported (can also be checked via manifest)
            if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)){
                Log.e(TAG,"Bluetooth LE not supported");
                Toast.makeText(this, "Bluetooth LE not supported",Toast.LENGTH_SHORT).show();
            return false;
        }


        mLEScanner = mBluetoothAdapter.getBluetoothLeScanner();
        settings = new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build();
        filterList = new ArrayList<>();
        Log.d(TAG, "Bluetooth OK");
        return true;
    }

    /**
     * Initiates scanning for discoverable Bluetooth devices. No timeout.
     * @param scanCallback: Callback object to notify on device discovery
     */
    @TargetApi(23)
    public void startScan(final ScanCallback scanCallback){

        Log.d(TAG , "START SCAN");
        final ScanFilter scanFilter =new ScanFilter.Builder().build();
        ScanSettings settings =new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build();
        if(mLEScanner != null) {
            mLEScanner.startScan(Arrays.asList(scanFilter), settings, scanCallback);
        }
    }

    /**
     * Stop a previously initiated scan.
     * @param scanCallback Callback object used to initiate scan
     */
    @TargetApi(23)
    public void stopScan(final ScanCallback scanCallback){
        Log.d(TAG, "STOP SCAN");
        if(mLEScanner != null) {
            mLEScanner.stopScan(scanCallback);
        }
    }

    /**
     * Connect to the remote device at the specified address
     * @param address MAC identifier of the device to connect to
     * @return Bluetooth Gatt instance for accessing device specific methods
     */
    public BluetoothGatt connectToDevice(String address){
        final BluetoothDevice mDevice;

        mDevice = mBluetoothAdapter.getRemoteDevice(address);
        if (mDevice == null) {
            Log.w(TAG, "Device not found.  Unable to connect.");
            return null;
        }

        // Cancel any current connection or connection attempts
        if (mBluetoothGatt != null){
            mBluetoothGatt.disconnect();
        }

        // Callback is local to the service so it can broadcast to activities on connection/device events
//        mBluetoothGatt = mDevice.connectGatt(this, false, mGattCallback);
        mBluetoothGatt = mDevice.connectGatt(this, true, mGattCallback);
        return mBluetoothGatt;
    }

    /**
     * Disconnect from the currently connected device
     */
    public void disconnectDevice(){
        if (mBluetoothGatt != null){
            mBluetoothGatt.disconnect();
            mBluetoothGatt = null;
        }
    }

    /**
     *  Callback for GATT events (device specific). Broadcasts intents to application.
     */
    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {

        // Broadcasts that descriptor write event is finished
        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status){
            UUID characteristic_uuid = descriptor.getCharacteristic().getUuid();

            if(characteristic_uuid.equals(NOTIFICATION_CHARACTERISTIC_UUID)){
                Log.i(TAG, "Wrote descriptor: " + NOTIFICATION_CHARACTERISTIC_UUID.toString());
                Intent intent = new Intent(ACTION_DESCRIPTOR_WROTE);
                intent.putExtra("Type","Notification");
                sendBroadcast(intent);
            } else if(characteristic_uuid.equals(INDICATION_CHARACTERISTIC_UUID)){
                Log.i(TAG, "Wrote descriptor: " + INDICATION_CHARACTERISTIC_UUID);
                Intent intent = new Intent(ACTION_DESCRIPTOR_WROTE);
                intent.putExtra("Type","Indication");
                sendBroadcast(intent);
            } else if(characteristic_uuid.equals(BATTERY_LEVEL_UUID)){
                Log.i(TAG, "Wrote descriptor: " + BATTERY_LEVEL_UUID);
            }

        }

        // Broadcasts connected/disconnected events
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int state) {

            if (state == BluetoothProfile.STATE_CONNECTED) {
                // Alert application that we have connected to a device
                // Begin service discovery on device
                Log.i(TAG, "Connected to GATT server.");
                sendBroadcast(new Intent(ACTION_GATT_CONNECTED));
                mBluetoothGatt.discoverServices();

            } else if (state == BluetoothProfile.STATE_DISCONNECTED) {
                // Alert application that we disconnected from a device
                Log.i(TAG, "Disconnected from GATT server.");
                sendBroadcast(new Intent(ACTION_GATT_DISCONNECTED));
                mBluetoothGatt=null;
            }

        }

        // Broadcasts that device service discovery is finished
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                for (BluetoothGattService svc : gatt.getServices()){
                    Log.d(TAG,svc.getUuid().toString());
                }
                sendBroadcast(new Intent(ACTION_GATT_SERVICES));
            } else {
                Log.w(TAG, "onServicesDiscovered received: " + status);
            }
        }

        // Broadcasts that data has been received for a characteristic
        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {
            Log.i(TAG, "onCharacteristicChanged UUID: " + characteristic.getUuid().toString());
            Intent intent = new Intent(ACTION_DATA_RECEIVED);
            intent.putExtra("CHAR", characteristic.getUuid());  // Characteristic UUID
            intent.putExtra("DATA", characteristic.getValue()); // Data received for this UUID
            Log.d(TAG, "Rx: " + characteristic.getStringValue(0));
            sendBroadcast(intent);
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt,
                                          BluetoothGattCharacteristic characteristic,
                                          int status){

            if (status != BluetoothGatt.GATT_SUCCESS){
                Log.e(TAG, "Tx: " + characteristic.getStringValue(0));
            } else {
                Log.d(TAG, "Tx: " + characteristic.getStringValue(0));
 				Log.i(TAG, "onCharacteristicWrite UUID: " + characteristic.getUuid().toString());
            }
         
        }
    };

    /**
     * Get an instance of the currently connected device GATT
     * @return Bluetooth Gatt instance for accessing device specific methods
     */
    public BluetoothGatt getGattInstance(){
        return mBluetoothGatt;
    }

    /**
     * Get a list of discovered services for the currently connected peripheral
     * @return List of services associated with the connected peripheral
     */
    public List<BluetoothGattService> getServices(){
        if (mBluetoothGatt == null){
            return null;
        }
        return mBluetoothGatt.getServices();
    }

    /**
     * Get the peripheral service associated with a specific UUID
     * @param uuid UUID of the service to retrieve
     * @return BluetoothGattService, or null if not present on peripheral
     */
    public BluetoothGattService getService(UUID uuid){
        if (mBluetoothGatt == null){
            return null;
        }
        return mBluetoothGatt.getService(uuid);
    }
}
