package xyz.a831337.colorbuzz;

import android.app.NotificationManager;
import android.content.res.Resources;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.bluetooth.*;
import android.util.*;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.content.*;
import android.app.PendingIntent;
import android.os.AsyncTask;

import java.util.*;

public class BuzzService extends NotificationListenerService {

    private BuzzNotificationReceiver rcv;
    private BuzzConfigurationReceiver confRcv;
    private ScreenActionReceiver scrRcv;
    private boolean rcvReady = false;
    private BluetoothDevice btDevice;
    private BluetoothGattCharacteristic operationChar;
    private BluetoothGattCharacteristic monitoringChar;
    private BluetoothGattDescriptor monitoringCharDescriptor;
    private BluetoothGatt gattInstance;
    private String charString;
    private String readCharString;
    private String notificationIntentName;
    private String configurationIntentName;
    private String[] appBlacklist;
    public int mainBuzzSignal;
    public int connectBuzzSignal;
    public boolean activated = false;
    private int notificationId = 999;
    private NotificationCompat.Builder notificationBuilder;
    private NotificationManager notificationMgr;

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

    public void setMonitoringChar(BluetoothGattCharacteristic charac) {
        this.monitoringChar = charac;
    }

    public void setOperationGatt(BluetoothGatt gatt) {
        this.gattInstance = gatt;
    }

    public void connectDevice(BluetoothDevice dev) {
        this.btDevice = dev;
        final UUID writeCharUuid = UUID.fromString(this.charString);
        final UUID readCharUuid = UUID.fromString(this.readCharString);
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
                                BuzzService.this.btDevice.connectGatt(BuzzService.this, false, this);
                            else
                                gatt.close();
                        }
                        Log.i("BuzzService", "Device disconnected");
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
                            AsyncTask.execute(new Runnable() {
                                @Override
                                public void run() {
                                    notificationMgr.notify(notificationId, notificationBuilder.build());
                                    BuzzService.this.buzz(BuzzService.this.connectBuzzSignal);
                                }
                            });
                        }
                        if(charObj.getUuid().compareTo(readCharUuid) == 0) {
                            UUID notifyUuid = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
                            monitoringCharDescriptor = charObj.getDescriptor(notifyUuid);
                            monitoringCharDescriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                            charObj.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
                            gatt.writeDescriptor(monitoringCharDescriptor);
                            gatt.setCharacteristicNotification(charObj, true);
                            BuzzService.this.setMonitoringChar(charObj);
                        }
                    }
                }
            }

            @Override
            public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic charObj) {
                if(charObj.getUuid().compareTo(readCharUuid) == 0) {
                    byte[] data = charObj.getValue();
                    if(data[0] == 0x44 && data[1] == 0x3B) {
                        int chargeLevel = (int)data[2]; //charge level
                        //int uvLevel = (int)data[3]; //UV level???
                        notificationBuilder.setContentText("Charge: " + chargeLevel + "%");
                        AsyncTask.execute(new Runnable() {
                            @Override
                            public void run() {
                                notificationMgr.notify(notificationId, notificationBuilder.build());
                            }
                        });
                    }
                }
            }
        };
        dev.connectGatt(this, false, gattCallback);
    }

    public void disconnectDevice() {
        if(this.activated) {
            this.buzz(this.connectBuzzSignal);
            this.activated = false;
            this.gattInstance.disconnect();
            AsyncTask.execute(new Runnable() {
                @Override
                public void run() {
                    notificationMgr.cancel(notificationId);
                }
            });
        }
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

    public void setupReceiver() {
        if(!this.rcvReady) {
            this.rcv = new BuzzService.BuzzNotificationReceiver();
            this.confRcv = new BuzzService.BuzzConfigurationReceiver();
            this.scrRcv = new BuzzService.ScreenActionReceiver();
            this.rcvReady = true;
            registerReceiver(this.rcv, new IntentFilter(notificationIntentName));
            IntentFilter scrFilter = new IntentFilter(Intent.ACTION_SCREEN_ON);
            scrFilter.addAction(Intent.ACTION_SCREEN_OFF);
            registerReceiver(this.scrRcv, scrFilter);
            LocalBroadcastManager.getInstance(this).registerReceiver(this.confRcv, new IntentFilter(configurationIntentName));
        }
    }

    @Override
    public void onListenerConnected() {
        Log.i("BuzzListener", "Service ready");
        configurationIntentName = getString(R.string.configuration_intent);
        notificationIntentName = getString(R.string.notification_intent);
        charString = getString(R.string.operation_char_uuid);
        readCharString = getString(R.string.monitored_char_uuid);
        Resources resources = getResources();
        appBlacklist = resources.getStringArray(R.array.app_blacklist);
        mainBuzzSignal = resources.getInteger(R.integer.main_buzz_signal);
        connectBuzzSignal = resources.getInteger(R.integer.connect_buzz_signal);
        notificationBuilder = new NotificationCompat.Builder(this)
                        .setSmallIcon(R.drawable.ic_stat_name)
                        .setContentTitle("Device connected")
                        .setContentText("Charge: estimating").setOngoing(true);
        Intent resultIntent = new Intent(this, ConnectActivity.class);
        PendingIntent resultPendingIntent = PendingIntent.getActivity(this, 0, resultIntent,
                PendingIntent.FLAG_UPDATE_CURRENT);
        notificationBuilder.setContentIntent(resultPendingIntent);
        notificationMgr = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        this.setupReceiver();
        super.onListenerConnected();
    }

    @Override
    public void onDestroy() {
        if(this.rcvReady) {
            unregisterReceiver(this.rcv);
            unregisterReceiver(this.scrRcv);
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

    public class ScreenActionReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if(BuzzService.this.activated) {
                if(intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
                    monitoringCharDescriptor.setValue(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
                    gattInstance.writeDescriptor(monitoringCharDescriptor);
                    gattInstance.setCharacteristicNotification(monitoringChar, false);
                } else if(intent.getAction().equals(Intent.ACTION_SCREEN_ON)) {
                    monitoringCharDescriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                    gattInstance.writeDescriptor(monitoringCharDescriptor);
                    gattInstance.setCharacteristicNotification(monitoringChar, true);
                }
            }
        }
    }

}
