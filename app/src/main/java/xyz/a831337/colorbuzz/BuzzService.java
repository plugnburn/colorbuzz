package xyz.a831337.colorbuzz;

import android.content.res.Resources;
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
    private String charString;
    private String notificationIntentName;
    private String configurationIntentName;
    private String[] appBlacklist;
    public int mainBuzzSignal;
    public int connectBuzzSignal;
    public boolean activated = false;

    public class BuzzNotificationReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if(BuzzService.this.activated)
                buzz(BuzzService.this.mainBuzzSignal);
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
                        BuzzService.this.activated = true;
                        Log.i("BuzzService", "Device connected");
                        gatt.discoverServices();
                        break;
                    case BluetoothProfile.STATE_DISCONNECTED:
                        if(connected) {
                            connected = false;
                            if(BuzzService.this.activated)
                                BuzzService.this.btDevice.connectGatt((Context) BuzzService.this, false, this);
                            else
                                gatt.close();
                        }
                        Log.e("BuzzService", "Device disconnected");
                        break;
                    default:
                        Log.e("BuzzService", "Device entered unknown state");
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
                            BuzzService.this.buzz(BuzzService.this.connectBuzzSignal);
                        }
                    }
                }
            }
        };
        dev.connectGatt(this, false, gattCallback);
    }

    public void disconnectDevice() {
        this.activated = false;
        this.gattInstance.disconnect();
    }

    public class BuzzConfigurationReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            boolean activate = intent.getBooleanExtra("activate", false);
            if(activate) {
                BluetoothDevice dev = intent.getExtras().getParcelable("bt_device");
                BuzzService.this.connectDevice(dev);
            }
            else {
                BuzzService.this.disconnectDevice();
            }
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
        Log.i("BuzzListener", "Service ready");
        configurationIntentName = getString(R.string.configuration_intent);
        notificationIntentName = getString(R.string.notification_intent);
        charString = getString(R.string.operation_char_uuid);
        Resources resources = getResources();
        appBlacklist = resources.getStringArray(R.array.app_blacklist);
        mainBuzzSignal = resources.getInteger(R.integer.main_buzz_signal);
        connectBuzzSignal = resources.getInteger(R.integer.connect_buzz_signal);
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

        if(BuzzService.this.activated && !Arrays.asList(appBlacklist).contains(pkg.toLowerCase())) {
            i.putExtra("notification_event", sbn.getPackageName());
            sendBroadcast(i);
        }
    }
}
