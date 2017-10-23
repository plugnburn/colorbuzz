package xyz.a831337.colorbuzz;

import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.*;
import android.os.Bundle;
import android.os.Build;
import android.os.AsyncTask;
import android.bluetooth.*;
import android.bluetooth.le.*;
import android.content.*;
import android.widget.*;
import android.view.*;
import android.Manifest;
import android.content.DialogInterface.*;
import java.util.*;
import android.provider.Settings;

public class ConnectActivity extends AppCompatActivity {
    private final static int REQUEST_ENABLE_BT = 1;
    private static final int PERMISSION_REQUEST_COARSE_LOCATION = 337;
    private String configurationIntentName = "xyz.a831337.colorbuzz.conf";
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothManager bluetoothManager;
    private BluetoothLeScanner btScanner;
    private ListView devList;
    private ArrayList<String> foundDevices = new ArrayList<>();
    private ArrayAdapter<String> lAdapter;
    private Button scanBtn;
    private Button stopScanBtn;
    private EditText macAddrText;

    private void msgBox(String title, String text) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(text)
                .setCancelable(false)
                .setPositiveButton("ok", new OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // Whatever...
                    }
                }).show();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if(!Settings.Secure.getString(this.getContentResolver(),"enabled_notification_listeners").contains(getApplicationContext().getPackageName())) {
            getApplicationContext().startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"));
        }
        bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();
        if(mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }
        btScanner = mBluetoothAdapter.getBluetoothLeScanner();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_connect);
        scanBtn = (Button)findViewById(R.id.scanBtn);
        stopScanBtn = (Button)findViewById(R.id.stopScanBtn);
        devList = (ListView)findViewById(R.id.deviceList);
        macAddrText = (EditText)findViewById(R.id.macAddrText);
        lAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, foundDevices);
        devList.setAdapter(lAdapter);
        devList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, final View view, int position, long id) {
                final String item = (String) parent.getItemAtPosition(position);
                String itemMac = item.split("\n")[1];
                macAddrText.setText(itemMac);
            }
        });
    }

    private ScanCallback leScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            String serviceString = result.getDevice().getName() + "\n" + result.getDevice().getAddress();
            if(!foundDevices.contains(serviceString)) {
                foundDevices.add(serviceString);
                lAdapter.notifyDataSetChanged();
            }
        }
    };

    private void proceedWithScan() {
        foundDevices.clear();
        scanBtn.setVisibility(View.GONE);
        stopScanBtn.setVisibility(View.VISIBLE);
        btScanner.startScan(leScanCallback);
        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                btScanner.startScan(leScanCallback);
            }
        });
    }

    public void scanAction(View view) {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, PERMISSION_REQUEST_COARSE_LOCATION);
        }
        else proceedWithScan();
    }

    public void stopScanAction(View view) {
        stopScanBtn.setVisibility(View.GONE);
        scanBtn.setVisibility(View.VISIBLE);
        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                btScanner.stopScan(leScanCallback);
            }
        });
    }

    public void connectFrontendAction(View view) {
        String macAddress = macAddrText.getText().toString();
        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(macAddress);
        Intent i = new Intent(configurationIntentName);
        Bundle btBundle = new Bundle();
        btBundle.putParcelable("bt_device", device);
        i.putExtras(btBundle);
        LocalBroadcastManager.getInstance(this).sendBroadcast(i);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        if(requestCode == PERMISSION_REQUEST_COARSE_LOCATION) {
            int locationMode = Settings.Secure.getInt(
                    getContentResolver(),
                    Settings.Secure.LOCATION_MODE,
                    Settings.Secure.LOCATION_MODE_OFF
            );

            if(locationMode == Settings.Secure.LOCATION_MODE_OFF) {
                Intent enableLocIntent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                startActivityForResult(enableLocIntent, PERMISSION_REQUEST_COARSE_LOCATION);
            }

            proceedWithScan();
        }
        else
            msgBox("Location access denied", "Coarse location access denied, scanning disabled");
    }

}