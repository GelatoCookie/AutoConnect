package com.zebra.rfid.demo.sdksample;

import static android.widget.Toast.LENGTH_SHORT;
import static android.widget.Toast.makeText;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.IntentCompat;

import com.zebra.rfid.api3.ENUM_TRANSPORT;
import com.zebra.rfid.api3.ReaderDevice;
import com.zebra.rfid.api3.TagData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity implements RFIDHandler.ResponseHandlerInterface {

    private static final String ACTION_USB_PERMISSION = "android.hardware.usb.action.USB_PERMISSION";
    private static final String SLED_ZEBRA_ATTACHED = "android.hardware.usb.action.USB_DEVICE_ATTACHED";
    private static final String SLED_ZEBRA_DETACHED = "android.hardware.usb.action.USB_DEVICE_DETACHED";
    private static final Integer RFID_VID = 1504;
    private static final String TAG = "RFID_SAMPLE";
    private static final String STATUS_CONNECTING = "Connecting...";
    private static final String STATUS_CONNECTED_ALREADY = "Connected Already";
    private static final String STATUS_DISCONNECTED = "Disconnected";
    private static final int BLUETOOTH_PERMISSION_REQUEST_CODE = 100;

    private TextView statusTextViewRFID;
    private ListView lstTags;
    private TagArrayAdapter adapter;
    private ArrayList<TagItem> tagList = new ArrayList<>();
    private HashMap<String, TagItem> tagMap = new HashMap<>();
    private TextView scanResult;
    private TextView textViewStatusrfid;
    private View btnStartInventory;
    private android.widget.ProgressBar inventoryProgress;
    RFIDHandler rfidHandler;

    private boolean bTesting = false;
    private boolean usbReceiverRegistered = false;
    private Boolean bluetoothPermissionOverrideForTests = null;
    private AlertDialog connectionDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // UI Initializations
        statusTextViewRFID = findViewById(R.id.textViewStatusrfid);
        textViewStatusrfid = findViewById(R.id.textViewStatusrfid);
        lstTags = findViewById(R.id.lstTags);
        scanResult = findViewById(R.id.scanResult);
        btnStartInventory = findViewById(R.id.TestButton);
        inventoryProgress = findViewById(R.id.inventoryProgress);

        adapter = new TagArrayAdapter(this, tagList);
        lstTags.setAdapter(adapter);

        Log.d(TAG, "MainActivity onCreate init");
        rfidHandler = new RFIDHandler();

        if (ensureBluetoothPermissionsForRfidInit()) {
            rfidHandler.onCreate(this);
        }

        requestPermission(null);
    }

    public void requestPermission(View view) {
        UsbManager manager = (UsbManager) getSystemService(Context.USB_SERVICE);
        if (manager == null) return;

        HashMap<String, UsbDevice> deviceList = manager.getDeviceList();
        UsbDevice zebraDevice = findZebraUsbDevice(deviceList);
        if (zebraDevice != null) {
            if (!manager.hasPermission(zebraDevice)) {
                manager.requestPermission(zebraDevice, createUsbPermissionPendingIntent());
            }
        }
    }

    private UsbDevice findZebraUsbDevice(HashMap<String, UsbDevice> deviceList) {
        for (UsbDevice device : deviceList.values()) {
            if (device.getVendorId() == RFID_VID) return device;
        }
        return null;
    }

    private PendingIntent createUsbPermissionPendingIntent() {
        Intent permissionIntent = new Intent(ACTION_USB_PERMISSION);
        permissionIntent.setPackage(getPackageName());
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return PendingIntent.getBroadcast(this, 0, permissionIntent, flags);
    }

    private void registerUsbReceiverIfNeeded() {
        if (usbReceiverRegistered) return;
        IntentFilter filter = new IntentFilter();
        filter.addAction(SLED_ZEBRA_ATTACHED);
        filter.addAction(SLED_ZEBRA_DETACHED);
        filter.addAction(ACTION_USB_PERMISSION);
        registerReceiver(usbReceiver, filter);
        usbReceiverRegistered = true;
    }

    private void unregisterUsbReceiverIfNeeded() {
        if (!usbReceiverRegistered) return;
        try {
            unregisterReceiver(usbReceiver);
        } catch (IllegalArgumentException ignored) {}
        usbReceiverRegistered = false;
    }

    private boolean ensureBluetoothPermissionsForRfidInit() {
        if (bluetoothPermissionOverrideForTests != null) return bluetoothPermissionOverrideForTests;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
            return true;
        }
        ActivityCompat.requestPermissions(this,
            new String[]{Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT},
            BLUETOOTH_PERMISSION_REQUEST_CODE);
        return false;
    }

    public void setBluetoothPermissionOverrideForTests(Boolean override) {
        this.bluetoothPermissionOverrideForTests = override;
    }

    public void InitRfidSDK() {
        if (ensureBluetoothPermissionsForRfidInit()) {
            if (rfidHandler == null) rfidHandler = new RFIDHandler();
            rfidHandler.onCreate(this);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == BLUETOOTH_PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted) {
                if (rfidHandler == null) rfidHandler = new RFIDHandler();
                rfidHandler.onCreate(this);
                requestPermission(null);
            } else {
                makeText(this, R.string.bluetooth_permission_not_granted, LENGTH_SHORT).show();
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (SLED_ZEBRA_ATTACHED.equals(action)) {
                if (rfidHandler != null) rfidHandler.onDestroy();
                InitRfidSDK();
            } else if (SLED_ZEBRA_DETACHED.equals(action)) {
                if (rfidHandler != null) rfidHandler.onDestroy();
                bt_usb_connect();
            } else if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbDevice device = IntentCompat.getParcelableExtra(intent, UsbManager.EXTRA_DEVICE, UsbDevice.class);
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if (device != null && isRfidVendorDevice(device.getVendorId())) {
                            InitRfidSDK();
                        }
                    }
                }
            }
        }
    };

    public boolean isRfidVendorDevice(int vendorId) {
        return vendorId == RFID_VID;
    }

    public void runAutoConnectDisconnectCycles(int repeatCount) {
        runAsyncTask(() -> {
            for (int i = 0; i < repeatCount; i++) {
                postToUiThread(() -> {
                    if (rfidHandler != null) rfidHandler.onDestroy();
                });
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                postToUiThread(this::InitRfidSDK);
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
    }

    private void runAsyncTask(Runnable action) {
        new Thread(action).start();
    }

    private void postToUiThread(Runnable action) {
        runOnUiThread(action);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    public void bt_usb_connect() {
        runOnUiThread(() -> {
            if (connectionDialog != null && connectionDialog.isShowing()) connectionDialog.dismiss();
            AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
            builder.setTitle(R.string.dialog_connection_title)
                   .setMessage(R.string.dialog_connection_message)
                   .setPositiveButton(R.string.dialog_button_bluetooth, (dialog, which) -> scheduleRfidInit(5000L))
                   .setNegativeButton(R.string.dialog_button_wait_usb, (dialog, which) -> dialog.dismiss())
                   .setCancelable(false);
            connectionDialog = builder.create();
            connectionDialog.show();
        });
    }

    public void updateStatus(final String val) {
        runOnUiThread(() -> {
            if (statusTextViewRFID != null) {
                statusTextViewRFID.setText(val);
                updateStatusColor(statusTextViewRFID, val);
            }
        });
    }

    public TextView getStatusTextViewRFID() {
        return statusTextViewRFID;
    }

    private void updateStatusColor(TextView tv, String status) {
        String lower = status.toLowerCase();
        int colorResId;
        if (lower.contains("connected") && !lower.contains("not") && !lower.contains("fail")) {
            colorResId = R.color.status_connected;
        } else if (lower.contains("disconnected") || lower.contains("fail")) {
            colorResId = R.color.status_disconnected;
        } else {
            colorResId = R.color.on_surface;
        }
        tv.setTextColor(ContextCompat.getColor(this, colorResId));
    }

    private void scheduleRfidInit(long delayMillis) {
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (!isFinishing() && !isDestroyed()) InitRfidSDK();
        }, delayMillis);
    }

    private boolean handleConnectAction() {
        if (!ensureBluetoothPermissionsForRfidInit()) return true;
        if (!rfidHandler.isReaderConnected()) {
            rfidHandler.onDestroy();
            rfidHandler.onCreate(this);
        } else {
            updateStatus(STATUS_CONNECTED_ALREADY);
        }
        return true;
    }

    private boolean handleDisconnectAction() {
        rfidHandler.onDestroy();
        updateStatus(STATUS_DISCONNECTED);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.Connect) return handleConnectAction();
        if (id == R.id.Disconnect) return handleDisconnectAction();
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onPostResume() {
        super.onPostResume();
        if (!ensureBluetoothPermissionsForRfidInit()) return;
        if (rfidHandler == null) {
            rfidHandler = new RFIDHandler();
            rfidHandler.onCreate(this);
        }
        updateStatus(rfidHandler.onResume());
    }

    @Override
    protected void onStart() {
        super.onStart();
        registerUsbReceiverIfNeeded();
    }

    @Override
    protected void onStop() {
        unregisterUsbReceiverIfNeeded();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        if (connectionDialog != null && connectionDialog.isShowing()) connectionDialog.dismiss();
        unregisterUsbReceiverIfNeeded();
        super.onDestroy();
        if (rfidHandler != null) {
            rfidHandler.onDestroy();
            rfidHandler = null;
        }
    }

    public void StartInventory(View view) {
        tagList.clear();
        tagMap.clear();
        adapter.notifyDataSetChanged();
        rfidHandler.performInventory();
    }

    public void StopInventory(View view) {
        rfidHandler.stopInventory();
    }

    @Override
    public void handleTagdata(TagData[] tagData) {
        runOnUiThread(() -> {
            for (TagData tagDatum : tagData) {
                updateOrAddTag(tagDatum);
            }
            adapter.notifyDataSetChanged();
        });
    }

    private void updateOrAddTag(TagData tagDatum) {
        String tagID = tagDatum.getTagID();
        if (tagMap.containsKey(tagID)) {
            TagItem item = tagMap.get(tagID);
            if (item != null) {
                item.incrementCount();
                item.setRssi(tagDatum.getPeakRSSI());
            }
        } else {
            TagItem newItem = new TagItem(tagID, tagDatum.getPeakRSSI());
            tagMap.put(tagID, newItem);
            tagList.add(newItem);
        }
    }

    @Override
    public void handleTriggerPress(boolean pressed) {
        if (pressed) {
            runOnUiThread(() -> {
                tagList.clear();
                tagMap.clear();
                adapter.notifyDataSetChanged();
            });
            rfidHandler.performInventory();
        } else {
            rfidHandler.stopInventory();
        }
    }

    private static class TagItem {
        private final String tagID;
        private int count;
        private int rssi;

        public TagItem(String tagID, int rssi) {
            this.tagID = tagID;
            this.rssi = rssi;
            this.count = 1;
        }
        public String getTagID() { return tagID; }
        public int getCount() { return count; }
        public int getRssi() { return rssi; }
        public void incrementCount() { this.count++; }
        public void setRssi(int rssi) { this.rssi = rssi; }
    }

    private class TagArrayAdapter extends ArrayAdapter<TagItem> {
        public TagArrayAdapter(Context context, List<TagItem> tags) {
            super(context, 0, tags);
        }

        @NonNull
        @Override
        public View getView(int position, View convertView, @NonNull ViewGroup parent) {
            TagItem item = getItem(position);
            if (convertView == null) {
                convertView = LayoutInflater.from(getContext()).inflate(R.layout.tag_list_item, parent, false);
            }
            TextView tvId = convertView.findViewById(R.id.tag_id);
            TextView tvCount = convertView.findViewById(R.id.tag_count);
            TextView tvRssi = convertView.findViewById(R.id.tag_rssi);

            if (item != null) {
                tvId.setText(item.getTagID());
                tvCount.setText(getContext().getString(R.string.tag_count_format, item.getCount()));
                tvRssi.setText(getContext().getString(R.string.tag_rssi_format, item.getRssi()));
            }
            return convertView;
        }
    }

    @Override
    @SuppressLint("SetTextI18n")
    public void barcodeData(String val) {
        runOnUiThread(() -> {
            scanResult.setVisibility(View.VISIBLE);
            scanResult.setText("Scan Result: " + val);
        });
    }

    @Override
    public void sendToast(String val) {
        runOnUiThread(() -> makeText(MainActivity.this, val, LENGTH_SHORT).show());
    }

    public void sendStatusText(String val) {
        updateStatus(val);
    }

    public void dismissConnectionDialog() {
        runOnUiThread(() -> {
            if (connectionDialog != null && connectionDialog.isShowing()) {
                connectionDialog.dismiss();
                connectionDialog = null;
            }
        });
    }

    @Override
    public void inventoryStartEvent(boolean started) {
        runOnUiThread(() -> {
            if (inventoryProgress != null) {
                inventoryProgress.setVisibility(started ? View.VISIBLE : View.GONE);
            }
            if (btnStartInventory instanceof TextView) {
                TextView btn = (TextView) btnStartInventory;
                if (started) {
                    btn.setEnabled(false);
                    btn.setText("Reading");
                } else {
                    btn.setEnabled(true);
                    btn.setText(R.string.start_inventory);
                }
            }
        });
    }
}
