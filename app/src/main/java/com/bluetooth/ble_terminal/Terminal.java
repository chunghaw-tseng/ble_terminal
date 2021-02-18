package com.bluetooth.ble_terminal;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.UUID;

/**
 * This class presents a terminal-style UI to allow users to send and receive characters via BLE
 * to a remote device running the BLE terminal firmware. Device connection and service discovery
 * take place on activity launch. Connection succeeds only if the device reports the BLE
 * specific service and characteristic UUIDs. Users may select the input format type using the
 * selection box (default: Ascii) or exit the terminal and return to the device selection screen
 * using the 'Back' button. The activity also finishes if a GATT disconnection intent is received
 *
 * \date 10 /18/2016
 */
public class Terminal extends AppCompatActivity {

    private final String TAG = "TerminalWindow";
    private static final int REQUEST_ENABLE_BT                  = 1;

    // Internal intent for notifying that our background thread finished sending our message
    private static final String ACTION_TX_FINISHED   = "com.bluetooth.ble_terminal.ACTION_TX_FINISHED";
    private static final String ACTION_TX_ERROR      = "com.bluetooth.ble_terminal.ACTION_TX_ERROR";

    private enum FORMAT {Ascii,Hex};
    private enum EOL_TYPE {None,CR,LF,CRLF};

    private static FORMAT   input_format = FORMAT.Ascii;
    private static EOL_TYPE eol_type     = EOL_TYPE.None;

    // TODO
    /// Please edit here the different UUID for your specific device
    private static final UUID CCCD                  = UUID.fromString("");
    public static final UUID DEVICE_SERVICE_UUID        = UUID.fromString("");    //  Service UUID
    public static final UUID NOTIFICATION_CHARACTERISTIC_UUID = UUID.fromString("");    // Read Notification Characteristic
    public static final UUID WRITE_NO_RESPONSE_CHARACTERISTIC_UUID = UUID.fromString(""); //  Write No Response Characteristic
    public static final UUID INDICATION_CHARACTERISTIC_UUID = UUID.fromString(""); //  Read Indication Characteristic
    public static final UUID WRITE_CHARACTERISTIC_UUID = UUID.fromString("");    // Write Characteristic
    public static final UUID BATTERY_SERVICE_UUID = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb");
    public static final UUID BATTERY_LEVEL_UUID = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb");

    private final int MAX_MESSAGE_COUNT = 20;   // Number of messages to show in UI
    private String rxMessage;

    // UI
    private Handler mHandler=new Handler();
    private ListView messagePane;
    private QueueAdapter messageAdapter;
    private ArrayList<String> messageLog;
    private ArrayList<Character> direction;
    private ArrayAdapter<CharSequence> inputFormatSelector, lineFinishSelector;

    // Bluetooth
    private BluetoothGatt mBluetoothGatt;
    private BLEService mService;
    private BluetoothDevice device;
    private BluetoothGattService BLEGattService;
    private BluetoothGattService BatteryService;
    private BluetoothGattCharacteristic NotificationChar;
    private BluetoothGattCharacteristic WriteChar;
//    private BluetoothGattCharacteristic WriteNoResponseChar;
    private BluetoothGattCharacteristic IndicationChar;
    private BluetoothGattCharacteristic batteryLevelChar;


    /**
     *  @brief Initialises UI (partial until device connection succeeds) and connects to the BLE service.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");

        setContentView(com.bluetooth.ble_terminal.R.layout.activity_terminal);

        // Set Back button listener
        findViewById(com.bluetooth.ble_terminal.R.id.btn_back).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG,"Back");
                // Since we manually requested to close the application, we can tell the parent that we finished successfully
                setResult(RESULT_OK);
                Toast.makeText(getApplicationContext(), "Disconnected from device", Toast.LENGTH_SHORT).show();
                finish();
            }
        });

        // Bind to BLE service to access functions
        Intent binderIntent=new Intent(this,BLEService.class);
        bindService(binderIntent,mServiceConnection, Context.BIND_AUTO_CREATE);
    }

    /**
     *  @brief Checks the status of the Bluetooth adapter and device connection before resuming activity.
     */
    @Override
    protected void onResume(){
        super.onResume();
        // When the activity resumes we should check that our connection/bluetooth states are still valid
        checkBluetoothEnabled();
    }

    /**
     *  @brief Unregisters receivers and unbinds from BLE service
     */
    @Override
    protected void onDestroy(){
        super.onDestroy();

        try { // In case receiver wasn't registered
            unregisterReceiver(messageReceiver);
        } catch (IllegalArgumentException e){Log.w(TAG,"MessageStatusRx eception");}

        try { // In case receiver wasn't registered
            unregisterReceiver(gattStatusReceiver);
        } catch (IllegalArgumentException e){Log.w(TAG,"GattStatusRx exception");}

        if (mBluetoothGatt != null){
            mBluetoothGatt.close();
            mBluetoothGatt.disconnect();
        }
        unbindService(mServiceConnection);
    }


    /**
     * Interface to BLE service, registers receiver for GATT events (device connection, service discovery
     * etc.) and requests connection to device. If connection fails the activity is terminated.
     */
    private ServiceConnection mServiceConnection = new ServiceConnection() {

        public void onServiceConnected(ComponentName className, IBinder rawBinder) {
            try {
                mService = ((BLEService.LocalBinder) rawBinder).getService();
                Log.d(TAG, "onServiceConnected mService= " + mService);

                // Register receiver for connection status events
                IntentFilter filter = new IntentFilter();
                filter.addAction(BLEService.ACTION_GATT_DISCONNECTED);
                registerReceiver(gattStatusReceiver,filter);
                mBluetoothGatt = mService.getGattInstance();
                BLEGattService = mBluetoothGatt.getService(DEVICE_SERVICE_UUID);
                BatteryService = mBluetoothGatt.getService(BATTERY_SERVICE_UUID);
                NotificationChar = BLEGattService.getCharacteristic(NOTIFICATION_CHARACTERISTIC_UUID);
                IndicationChar = BLEGattService.getCharacteristic(INDICATION_CHARACTERISTIC_UUID);
                WriteChar = BLEGattService.getCharacteristic(WRITE_NO_RESPONSE_CHARACTERISTIC_UUID);
                batteryLevelChar = BatteryService.getCharacteristic(BATTERY_LEVEL_UUID);
                pageInit();

            }catch (Exception e){
                Log.e(TAG, "Cannot connect to device");
//              Disconnect to device
                finish();
            }
        }

        // When we unbind from the service
        public void onServiceDisconnected(ComponentName classname) {
            Log.d(TAG, "onServiceDisconnected");
            mService = null;
        }
    };

    /**
     * Receiver for BLE service intents related to GATT status
     *
     * ACTION_GATT_CONNECTED        On successful connection to devices, request device services.\n
     * ACTION_GATT_DISCONNECTED     Exit activity and return to device selection.\n
     * ACTION_GATT_SERVICES         On service discovery finished, check if device supports BLE
     *                                     UUIDs. If the device supports UART functionality, the messaging
     *                                     layout elements will be displayed.
     */
    BroadcastReceiver gattStatusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.d(TAG, action);
            if (action.equals(BLEService.ACTION_GATT_DISCONNECTED)){
                Log.d(TAG,"GATT Disconnected");
                Toast.makeText(getApplicationContext(),"Disconnected", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    };

    /**
     * Receiver for broadcasts related to GATT messages. Handles printing messages to UI.
     *
     * ACTION_DATA_RECEIVED     Notification of new data received from remote device.\n
     * ACTION_TX_FINISHED       Notification from background thread that transmission is finished.\n
     */
    BroadcastReceiver messageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(BLEService.ACTION_DATA_RECEIVED)){

                //Recieved battery level
                if(intent.getSerializableExtra("CHAR").equals(BATTERY_LEVEL_UUID)){
                    if (messageAdapter.getCurrentDirection() != 'B'){
                        messageAdapter.newMessage('B');
                    }

                    byte[] rx = intent.getByteArrayExtra("DATA");
                    int battery_level = rx[0] & 0xFF;
                    printMessageToScreen("Battery level: " + String.valueOf(battery_level)+" ");
                    return;
                }

                if (messageAdapter.getCurrentDirection() != 'R'){
                    messageAdapter.newMessage('R');
                }

                byte[] rx = intent.getByteArrayExtra("DATA");
                Log.i(TAG,"length:" + rx.length);
                switch(input_format){
                    case Hex:
                        // Hex mode
                        rx = ascii2hex(intent.getByteArrayExtra("DATA"));
                        break;
                }

                printMessageToScreen(new String(rx));
            }
            if (action.equals(ACTION_TX_FINISHED)){
                // Finished transmitting, display to screen and clear message
                if (messageAdapter.getCurrentDirection() != 'T'){
                    messageAdapter.newMessage('T');
                }
                printMessageToScreen(intent.getStringExtra("MESSAGE"));
                ((EditText) findViewById(com.bluetooth.ble_terminal.R.id.txt_txmessage)).setText("");
            }
            if (action.equals(ACTION_TX_ERROR)){
                // An error occurred during sending, display the message (don't clear text - allow user to edit)
                Toast.makeText(getApplicationContext(),intent.getStringExtra("MESSAGE"),Toast.LENGTH_SHORT).show();
            }

            if(action.equals(BLEService.ACTION_DESCRIPTOR_WROTE)){

                if (mBluetoothGatt.getService(DEVICE_SERVICE_UUID).getCharacteristic(INDICATION_CHARACTERISTIC_UUID) == null){
                    Log.d(TAG, "Old version");
                    // This version doesn't support latest characteristics
                    return;
                }

                if(intent.getStringExtra("Type").equals("Notification")) {
                    // Enable indication on receiving data
                    mBluetoothGatt.setCharacteristicNotification(IndicationChar, true);
                    BluetoothGattDescriptor indication_descriptor = IndicationChar.getDescriptor(CCCD);
                    indication_descriptor.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
                    mBluetoothGatt.writeDescriptor(indication_descriptor);
                }else if(intent.getStringExtra("Type").equals("Indication")){
                    // Enable notification on receiving data
                    mBluetoothGatt.setCharacteristicNotification(batteryLevelChar, true);
                    BluetoothGattDescriptor notification_descriptor = batteryLevelChar.getDescriptor(CCCD);
                    notification_descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                    mBluetoothGatt.writeDescriptor(notification_descriptor);
                }
            }
        }
    };

    private View.OnClickListener menu_click_listener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            PopupMenu popup = new PopupMenu(getApplicationContext(), view);
            popup.setOnMenuItemClickListener(menu_item_click_listener);
            MenuInflater inflater = popup.getMenuInflater();
            inflater.inflate(R.menu.menu_items, popup.getMenu());
            popup.getMenu().getItem(0).setTitle("Format: " + input_format.name());
            popup.getMenu().getItem(1).setTitle("EOL: " + eol_type.name());
            popup.show();
        }
    };

    private PopupMenu.OnMenuItemClickListener menu_item_click_listener = new PopupMenu.OnMenuItemClickListener() {
        @Override
        public boolean onMenuItemClick(MenuItem menuItem) {
            switch (menuItem.getItemId()){
                case R.id.opt_clear:
                    // Clear screen
					messageAdapter.clearMessages();
                    return true;
                case R.id.opt_inputformat:
                    // Open input format selection
                    showFormatSelect();
                    return true;
                case R.id.opt_eolselect:
                    showEOLSelect();
                    return true;
                default:
                    return false;
            }
        }
    };

    /**
     * Show an AlertDialog to select the data format (Ascii, Hex)
     */
    private void showFormatSelect(){

        CharSequence formats[] = new CharSequence[]{"Ascii", "Hex"};

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select format");
        builder.setItems(formats, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                input_format = FORMAT.values()[which];
                ((Button)findViewById(R.id.btn_format)).setText(input_format.name());
            }
        });
        builder.show();
    }

    /**
     * Show an AlertDialog to select the EOL character (None, CR, LF, CRLF)
     */
    private void showEOLSelect(){

        final CharSequence eol_types[] = new CharSequence[]{"None","CR","LF","CR+LF"};

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select format");
        builder.setItems(eol_types, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                eol_type = EOL_TYPE.values()[which];
                ((Button)findViewById(R.id.btn_eol)).setText("EOL: " + eol_type.name());
            }
        });
        builder.show();
    }

    /**
     * Initialises page elements upon successful device connection
     */
    private void pageInit(){

        ((TextView) findViewById(com.bluetooth.ble_terminal.R.id.lbl_subheading)).setText(mBluetoothGatt.getDevice().getName());

        // Set options listener
        (findViewById(R.id.btn_options_menu)).setOnClickListener(menu_click_listener);

        // Configure the message panel
        messageLog = new ArrayList<>();
        direction = new ArrayList<>();
        messagePane = (ListView) findViewById(com.bluetooth.ble_terminal.R.id.lv_messagepanel);
        messageAdapter = new QueueAdapter(getApplicationContext(), messageLog, direction);
        messagePane.setAdapter(messageAdapter);

        // Set Send button listener
        findViewById(com.bluetooth.ble_terminal.R.id.btn_send).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                EditText entry = (EditText) findViewById(com.bluetooth.ble_terminal.R.id.txt_txmessage);
                // Get contents of text field
                String message = entry.getText().toString();

                // Check if there was any text to send
                if (!message.equals("")) {
                    sendMessage(message);
                }
            }
        });

        // Set option buttons listeners
        findViewById(R.id.btn_format).setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                showFormatSelect();
            }
        });

        findViewById(R.id.btn_eol).setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                showEOLSelect();
            }
        });

        findViewById(R.id.btn_clear).setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                messageAdapter.clearMessages();
            }
        });


        rxMessage = "";

        // Register receiver for BLE service intents for message IO
        IntentFilter filter = new IntentFilter();
        filter.addAction(BLEService.ACTION_DATA_RECEIVED);
        filter.addAction(ACTION_TX_FINISHED);
        filter.addAction(ACTION_TX_ERROR);
        filter.addAction(BLEService.ACTION_DESCRIPTOR_WROTE);
        registerReceiver(messageReceiver,filter);

        // Enable notification on receiving data
        mBluetoothGatt.setCharacteristicNotification(NotificationChar,true);
        BluetoothGattDescriptor notification_descriptor = NotificationChar.getDescriptor(CCCD);
        notification_descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        mBluetoothGatt.writeDescriptor(notification_descriptor);
    }

    /**
     * @brief Handles delivery of message to connected device.
     * @param message Message to transmit
     *
     * Message is converted to specified format (if non-Ascii) and transmitted to remote device using
     * WriteChar characteristic. As BLE can only handle 20 byte data payloads, this function handles delivery
     * of sequential packets until entire message is successfully transmitted. The selected line endings
     * will be appended prior to transmission
     *
     * @note This function is called on a non-UI thread to avoid blocking user interaction.
     */
    private void sendMessage(String message){

        // Send message on a separate thread so we don't hold up the UI
        class MessageSender implements Runnable {
            String message;
            private MessageSender(String string){this.message = string;}
            public void run(){

                final int PACKET_SIZE = 20;
                String error_msg = "";

                try {
                    // Append any line finishers
                    switch(eol_type){
                        case CR:
                            message += '\r';
                            break;
                        case LF:
                            message += '\n';
                            break;
                        case CRLF:
                            message += "\r\n";
                            break;
                    }

                    // Convert our message to byte array in selected format
                    byte[] bMessage = message.getBytes("UTF-8");
                    switch(input_format) {
                        case Hex:
                            bMessage = hex2string(bMessage); // Requires NumberFormatException
                            if (bMessage == null) {
                                // Invalid message
                                Toast.makeText(getApplicationContext(),"Invalid characters",Toast.LENGTH_SHORT).show();
                                throw new NumberFormatException();
                            }
                            break;
                    }

                    // We can only transmit in packets of 20 bytes
                    int packet_start = 0;
                    while (packet_start < bMessage.length) {

                        // Trim the packet length if we have less than 20 to send
                        int packet_end = (bMessage.length < packet_start + PACKET_SIZE) ? bMessage.length : packet_start + PACKET_SIZE;

                        // Construct and send this packet

                            byte[] chunk = new byte[packet_end - packet_start];
                        for (int i = 0; i < chunk.length; i++) {
                            chunk[i] = bMessage[i + packet_start];
                        }
                            WriteChar.setValue(chunk);

                            if (!mBluetoothGatt.writeCharacteristic(WriteChar)) {
                                throw new Exception(new Throwable());
                            }
                            packet_start += PACKET_SIZE;
                            Thread.sleep(10);   // Requires InterruptedException

                    }

                    Log.d(TAG, "Tx: " + message);

                } catch (UnsupportedEncodingException e) {
                    Log.w(TAG, "Unsupported encoding exception");
                } catch (InterruptedException e){
                    Log.w(TAG, "Interrupted exception");
                } catch (NumberFormatException e){
                    error_msg = "Invalid message format";
                } catch (Exception e){
                    error_msg = "Failed to send data";
                }

                Intent intent;
                if (!error_msg.equals("")) {
                    intent = new Intent(ACTION_TX_ERROR);
                    intent.putExtra("MESSAGE",error_msg);
                } else {
                    intent = new Intent(ACTION_TX_FINISHED);
                    intent.putExtra("MESSAGE",message);
                }
                sendBroadcast(intent);
            }
        }
        Thread thread = new Thread(new MessageSender(message));
        thread.start();
    }

    /**
     * @brief Converts a byte array of ascii to hexadecimal
     * @param bytes byte array of ascii
     * @return Hex formatted string
     */
    private byte[] ascii2hex(byte[] bytes){

        byte[] result = new byte[3*bytes.length];
        int i=0;
        for (byte b : bytes){
            // Upper nibble
            byte un = (byte)((b >> 4)&0xF);
            result[i] = (byte)(un > 9 ? un + 0x37 : un + 0x30);
            // Lower nibble
            byte ln = (byte)(b&0xF);
            result[i+1] = (byte)(ln > 9 ? ln + 0x37 : ln + 0x30);
            result[i+2] = (byte)' ';
            i+=3;
        }
        return result;
    }

    /**
     * @brief Converts a byte array of hexadecimal values to characters
     * @param bytes Byte array of hex values
     * @return Ascii formatted string
     */
    private byte[] hex2string(byte[] bytes){
        ArrayList<Byte> in=new ArrayList<>(bytes.length+1);

        byte b;
        // Validate input
        for (int i = 0; i < bytes.length; i++) {
            b=getValidHex(bytes[i]);
            switch (b){
                case (byte)0xFF:
                    // Invalid character
                    return null;
                case (byte)0xFE:
                    // Ignore character
                    break;
                default:
                    // Valid
                    in.add(Byte.valueOf(b));
            }
        }

        if ((in.size() & 1) == 1)
            in.add(0,Byte.valueOf("0"));

        // Construct bytes
        byte[] out = new byte[in.size()/2];
        for (int i = 0; i < out.length; i++) {
            byte u = (byte)(in.get(i*2)<<4);
            byte l = in.get(2*i+1);
            out[i] = (byte)(u + l);
        }

        return out;
    }

    /**
     * Checks if a character is a valid hexadecimal character
     * @param b Hexadecimal value
     * @return Ascii value of character, 0xFE if special character, 0xFF if invalid
     */
    private byte getValidHex(byte b){
        if (b < '0') return (byte)0xFE;
        if ((b - '0') <= '9'-'0') return (byte)(b-'0');
        if ((b - 'A') <= 'F'-'A') return (byte)(b-'A'+10);
        if ((b - 'a') <= 'f'-'a') return (byte)(b-'a'+10);
        return (byte)0xFF;
    }

    /**
     * The message Queue adapter.
     */
// Adapter for displaying messages to and from connected device.
    class QueueAdapter extends BaseAdapter {
        /**
         * The Context.
         */
        Context context;
        /**
         * The Messages.
         */
        ArrayList<String> messages;
        /**
         * The Direction.
         */
        ArrayList<Character> direction;

        int currentMessageIndex;

        /**
         * Instantiates a new Queue adapter.
         *
         * @param context   the context
         * @param messages  the messages
         */
        private QueueAdapter (Context context, ArrayList<String> messages, ArrayList<Character> direction){
            this.context    = context;
            this.messages   = messages;
            this.direction  = direction;
            this.currentMessageIndex = -1;
        }

        @Override
        public String getItem(int position) {
            return messages.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public int getCount() {
            return messages.size();
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            LayoutInflater mInflater = (LayoutInflater) context
                    .getSystemService(Activity.LAYOUT_INFLATER_SERVICE);
            if (direction.get(position) == 'R'||direction.get(position) == 'B') {
                convertView = mInflater.inflate(com.bluetooth.ble_terminal.R.layout.message_default, null);
            } else {
                convertView = mInflater.inflate(com.bluetooth.ble_terminal.R.layout.message_bold, null);
            }
            ((TextView) convertView.findViewById(com.bluetooth.ble_terminal.R.id.message_contents)).setText(this.getItem(position));
            return convertView;
        }

        public void newMessage(char dir){
            if (currentMessageIndex < MAX_MESSAGE_COUNT) {
                currentMessageIndex++;
            } else {
                messageLog.remove(0);
                direction.remove(0);
            }
            direction.add(dir);
            messageLog.add("");
        }

        public char getCurrentDirection(){
            if (currentMessageIndex < 0) // First message
                return '.';
            return direction.get(currentMessageIndex);
        }

        public void clearMessages(){
            messageLog.clear();
            direction.clear();
            currentMessageIndex=-1;
            messageAdapter.notifyDataSetChanged();
        }
    }

    /**
     * @brief Adds a message to the adapter and updates the display
     * @param message String to display
     */
    private void printMessageToScreen(String message){

        try {
            // Append to current stream output
            String current = messageLog.get(messageAdapter.currentMessageIndex);
            messageLog.set(messageAdapter.currentMessageIndex,current + message);
        } catch (IndexOutOfBoundsException e){
            // This is a new message
            messageLog.add(message);
        }
        messageAdapter.notifyDataSetChanged();
        messagePane.smoothScrollToPosition(messageAdapter.getCount() - 1);
    }

    /**
     * @brief Check state of Bluetooth system service to ensure connection is still valid
     */
    private void checkBluetoothEnabled(){
        BluetoothAdapter btadapter = BluetoothAdapter.getDefaultAdapter();
        if (!btadapter.isEnabled()){
            Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(intent,REQUEST_ENABLE_BT);
        }
    }

    /**
     * @brief Checks the result of user request to enable Bluetooth system service
     */
    @Override
    protected void onActivityResult(int requestCode, int result, Intent data) {
        switch (requestCode) {
            case REQUEST_ENABLE_BT:
                if (result == RESULT_CANCELED) {
                    setResult(result, new Intent(DeviceSelection.REQUEST_ENABLE_BT_DENIED));
                    finish();
                }
        }
    }
}
