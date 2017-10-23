package xyz.a831337.colorbuzz;

import android.support.v4.content.LocalBroadcastManager;
import android.bluetooth.*;
import android.util.*;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.content.*;
import java.util.*;

public class BuzzService extends NotificationListenerService {

    private BuzzNotificationReceiver rcv;
    private BuzzConfigurationReceiver confRcv;
    private boolean rcvReady = false;
    private BluetoothDevice btDevice;
    private BluetoothGattCharacteristic operationChar;
    private BluetoothGatt gattInstance;
    private String charString = "6e400002-b5a3-f393-e0a9-e50e24dcca9d";
    private String notificationIntentName = "xyz.a831337.colorbuzz.buzz";
    private String configurationIntentName = "xyz.a831337.colorbuzz.conf";

    public class BuzzNotificationReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            buzz(0x3f);
        }
    }

    public void setOperationChar(BluetoothGattCharacteristic charac) {
        this.operationChar = charac;
    }

    public void setOperationGatt(BluetoothGatt gatt) {
        this.gattInstance = gatt;
    }

    public void connectDevice(BluetoothDevice dev) {
        this.btDevice = dev;
        final UUID writeCharUuid = UUID.fromString(this.charString);
        final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
            private boolean connected = false;
            private BluetoothGattCharacteristic charObj;
            @Override
            public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                switch (newState) {
                    case BluetoothProfile.STATE_CONNECTED:
                        connected = true;
                        Log.i("gattCallback", "STATE_CONNECTED");
                        gatt.discoverServices();
                        break;
                    case BluetoothProfile.STATE_DISCONNECTED:
                        if(connected) {
                            connected = false;
                            BuzzService.this.btDevice.connectGatt((Context) BuzzService.this, false, this);
                        }
                        Log.e("gattCallback", "STATE_DISCONNECTED");
                        break;
                    default:
                        Log.e("gattCallback", "STATE_OTHER");
                }
            }
            @Override
            public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                BuzzService.this.setOperationGatt(gatt);
                List<BluetoothGattService> services = gatt.getServices();
                for(int i=0; i<services.size();i++) {
                    List<BluetoothGattCharacteristic> chars = services.get(i).getCharacteristics();
                    for(int k=0;k<chars.size();k++) {
                        charObj = chars.get(k);
                        if(charObj.getUuid().compareTo(writeCharUuid) == 0) {
                            Log.i("BuzzListener", "Device Connected");
                            BuzzService.this.setOperationChar(charObj);
                            BuzzService.this.buzz(0x04);
                        }
                    }
                }
            }
        };
        dev.connectGatt(this, false, gattCallback);
    }

    public class BuzzConfigurationReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            BluetoothDevice dev = intent.getExtras().getParcelable("bt_device");
            BuzzService.this.connectDevice(dev);
        }
    }

    public void buzz(int cmd) {
        final byte[] charCommand = new byte[1];
        charCommand[0] = (byte) cmd;
        operationChar.setValue(charCommand);
        gattInstance.writeCharacteristic(operationChar);
    }

    public void setupReceiver(){
        if(!this.rcvReady) {
            this.rcv = new BuzzService.BuzzNotificationReceiver();
            this.confRcv = new BuzzService.BuzzConfigurationReceiver();
            this.rcvReady = true;
            registerReceiver(this.rcv, new IntentFilter(notificationIntentName));
            LocalBroadcastManager.getInstance(this).registerReceiver(this.confRcv, new IntentFilter(configurationIntentName));
        }
    }

    @Override
    public void onListenerConnected() {
        Log.i("BuzzListener", "Connected");
        this.setupReceiver();
        super.onListenerConnected();
    }

    @Override
    public void onDestroy() {
        if(this.rcvReady) {
            unregisterReceiver(this.rcv);
            LocalBroadcastManager.getInstance(this).unregisterReceiver(this.confRcv);
        }
        this.rcvReady = false;
        super.onDestroy();
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        Intent i = new Intent(notificationIntentName);
        String pkg = sbn.getPackageName();
        if(!pkg.equalsIgnoreCase("com.huawei.colorbands")) {
            i.putExtra("notification_event", sbn.getPackageName());
            sendBroadcast(i);
        }
    }
}
