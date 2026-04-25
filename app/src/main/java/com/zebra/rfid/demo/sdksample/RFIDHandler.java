package com.zebra.rfid.demo.sdksample;

import android.media.AudioManager;
import android.media.ToneGenerator;
import android.util.Log;
import android.widget.TextView;

import com.zebra.rfid.api3.Antennas;
import com.zebra.rfid.api3.ENUM_TRANSPORT;
import com.zebra.rfid.api3.ENUM_TRIGGER_MODE;
import com.zebra.rfid.api3.FILTER_ACTION;
import com.zebra.rfid.api3.HANDHELD_TRIGGER_EVENT_TYPE;
import com.zebra.rfid.api3.INVENTORY_STATE;
import com.zebra.rfid.api3.InvalidUsageException;
import com.zebra.rfid.api3.MEMORY_BANK;
import com.zebra.rfid.api3.OperationFailureException;
import com.zebra.rfid.api3.PreFilters;
import com.zebra.rfid.api3.RFIDReader;
import com.zebra.rfid.api3.ReaderDevice;
import com.zebra.rfid.api3.Readers;
import com.zebra.rfid.api3.RfidEventsListener;
import com.zebra.rfid.api3.RfidReadEvents;
import com.zebra.rfid.api3.RfidStatusEvents;
import com.zebra.rfid.api3.SESSION;
import com.zebra.rfid.api3.SL_FLAG;
import com.zebra.rfid.api3.START_TRIGGER_TYPE;
import com.zebra.rfid.api3.STATE_AWARE_ACTION;
import com.zebra.rfid.api3.STATUS_EVENT_TYPE;
import com.zebra.rfid.api3.STOP_TRIGGER_TYPE;
import com.zebra.rfid.api3.TARGET;
import com.zebra.rfid.api3.TagData;
import com.zebra.rfid.api3.TriggerInfo;
import com.zebra.scannercontrol.DCSSDKDefs;
import com.zebra.scannercontrol.DCSScannerInfo;
import com.zebra.scannercontrol.FirmwareUpdateEvent;
import com.zebra.scannercontrol.IDcsSdkApiDelegate;
import com.zebra.scannercontrol.SDKHandler;

import java.util.ArrayList;

/**
 * @noinspection ALL
 */
class RFIDHandler implements IDcsSdkApiDelegate, Readers.RFIDReaderEventHandler {

    static final String TAG = "RFID_SAMPLE";
    private static final String NOT_CONNECTED_MESSAGE = "Not connected";
    private Readers readers;
    private ArrayList<ReaderDevice> availableRFIDReaderList;
    private ReaderDevice readerDevice;
    private RFIDReader reader;
    TextView textView;
    private EventHandler eventHandler;
    private MainActivity context;
    private SDKHandler sdkHandler;
    private ArrayList<DCSScannerInfo> scannerList;
    private int scannerID;
    private int maxPower = 270;
    private volatile boolean bIsReading = false;

    // In case of RFD8500 change reader name with intended device below from list of paired RFD8500
    // If barcode scan is available in RFD8500, for barcode scanning change mode using mode button on RFD8500 device. By default it is set to RFID mode
    String readerNamebt = "RFD40+_211545201D0011";
    String readerName = "RFD4031-G10B700-US";
    String readerNameRfd8500 = "RFD8500161755230D5038";
    private ENUM_TRANSPORT currentTransport = ENUM_TRANSPORT.SERVICE_USB;
    private static volatile boolean bIsBTReader = false;

    ToneGenerator toneG = new ToneGenerator(AudioManager.STREAM_ALARM, 100);

    private void updateTransportFlagsFromReader() {
        bIsBTReader = reader != null && reader.getHostName() != null && reader.getHostName().contains("+");
    }

    public void onCreate(MainActivity activity) {
        context = activity;
        textView = activity.getStatusTextViewRFID();
        scannerList = new ArrayList<>();
        Log.d(TAG, "RFIDHandler onCreate");
        InitSDK();
    }

    @Override
    public void dcssdkEventScannerAppeared(DCSScannerInfo dcsScannerInfo) {
        // No-op: scanner appearance is not used by this sample flow.
    }

    @Override
    public void dcssdkEventScannerDisappeared(int i) {
        // No-op: scanner disappearance is not used by this sample flow.
    }

    @Override
    public void dcssdkEventCommunicationSessionEstablished(DCSScannerInfo dcsScannerInfo) {
        // No-op: scanner session callbacks are not used by this sample flow.
    }

    @Override
    public void dcssdkEventCommunicationSessionTerminated(int i) {
        // No-op: scanner session callbacks are not used by this sample flow.
    }

    @Override
    public void dcssdkEventBarcode(byte[] barcodeData, int barcodeType, int fromScannerID) {
        String s = new String(barcodeData);
        context.barcodeData(s);
        Log.d(TAG,"barcaode ="+ s);
    }

    @Override
    public void dcssdkEventImage(byte[] bytes, int i) {
        // No-op: image callbacks are not used by this sample flow.
    }

    @Override
    public void dcssdkEventVideo(byte[] bytes, int i) {
        // No-op: video callbacks are not used by this sample flow.
    }

    @Override
    public void dcssdkEventBinaryData(byte[] bytes, int i) {
        // No-op: binary data callbacks are not used by this sample flow.
    }

    @Override
    public void dcssdkEventFirmwareUpdate(FirmwareUpdateEvent firmwareUpdateEvent) {
        // No-op: firmware update callbacks are not used by this sample flow.
    }

    @Override
    public void dcssdkEventAuxScannerAppeared(DCSScannerInfo dcsScannerInfo, DCSScannerInfo dcsScannerInfo1) {
        // No-op: auxiliary scanner callbacks are not used by this sample flow.
    }
    
    public synchronized boolean isReaderConnected() {
        if (reader != null && reader.isConnected()) {
            Log.d(TAG, "reader is connected, Name=" + reader.getHostName());
            return true;
        }
        else {
            Log.d(TAG, "reader is not connected");
            return false;
        }
    }

    //
    //  Activity life cycle behavior
    //

    String onResume() {
        return connectMethod();
    }

    void onDestroy() {
        dispose();
    }

    //
    // RFID SDK
    //
    public synchronized void InitSDK() {
        Log.d(TAG, "\r\n\r\nInitSDK");
        if (readers == null) {
            Log.d(TAG, "reader is null");
            initializeReadersAsync();
        } else {
            Log.d(TAG, "InitSDK: reader is not null connect to reader");
            connectReader();
        }
    }

    private boolean hasAvailableReaders() {
        return availableRFIDReaderList != null && !availableRFIDReaderList.isEmpty();
    }

    private void initializeReadersAsync() {
        runAsyncTask(() -> {
            initializeReaders();
            postToUiThread(() -> {
                Log.d(TAG, "CreateInstanceTask onPostExecute connectReader");
                connectReader();
            });
        });
    }

    private void initializeReaders() {
        Log.d(TAG, "CreateInstanceTask");

        InvalidUsageException invalidUsageException = null;
        readers = new Readers(context, ENUM_TRANSPORT.SERVICE_USB);
        currentTransport = ENUM_TRANSPORT.SERVICE_USB;
        try {
            availableRFIDReaderList = readers.GetAvailableRFIDReaderList();
            Log.d(TAG, "availableRFIDReaderList USB Size=" + (availableRFIDReaderList == null ? 0 : availableRFIDReaderList.size()));
        } catch (InvalidUsageException e) {
            invalidUsageException = e;
            e.printStackTrace();
        }

        if (invalidUsageException != null || !hasAvailableReaders()) {
            Log.d(TAG, "ERR: Dispose readers");
            dispose();
            if (readers == null) {
                readers = new Readers(context, ENUM_TRANSPORT.BLUETOOTH);
                currentTransport = ENUM_TRANSPORT.BLUETOOTH;

                try {
                    availableRFIDReaderList = readers.GetAvailableRFIDReaderList();
                } catch (InvalidUsageException e) {
                    e.printStackTrace();
                }
                Log.d(TAG, "availableRFIDReaderList BT Size=" + (availableRFIDReaderList == null ? 0 : availableRFIDReaderList.size()));
            }
        }
    }

    private synchronized void connectReader(){
        if(!isReaderConnected() ) {
            Log.d(TAG, "NO Connection: ConnectionTask...");
            connectReaderAsync();
        }
        else
            Log.d(TAG, "Connected; skip connectReader() method");
    }

    private void connectReaderAsync() {
        runAsyncTask(() -> {
            Log.d(TAG, "ConnectionTask");
            getAvailableReader();
            if(!hasAvailableReaders()) {
                Log.d(TAG, "TO DO: Wait for USB to be ready...");
            } else {
                if (reader != null) {
                    Log.d(TAG, "Ready to Connect to " + reader.getHostName());
                } else {
                    Log.d(TAG, "Reader list present but no matching target selected");
                }
            }

            String result = reader != null ? connectMethod() : "Failed to find or connect reader";
            postToUiThread(() -> textView.setText(result));
        });
    }

    private synchronized void getAvailableReader() {
        Log.d(TAG, "GetAvailableReader");
        if (readers == null) {
            return;
        }
        readers.attach(this);
        refreshAvailableReaders();
        if (!hasAvailableReaders()) {
            return;
        }
        selectCurrentReader();
    }

    private void refreshAvailableReaders() {
        try {
            availableRFIDReaderList = readers.GetAvailableRFIDReaderList();
        } catch (InvalidUsageException ie) {
            ie.printStackTrace();
            availableRFIDReaderList = null;
        }
    }

    private void selectCurrentReader() {
        if (availableRFIDReaderList.size() == 1) {
            setSelectedReader(availableRFIDReaderList.get(0));
            return;
        }

        ReaderDevice preferredDevice = findPreferredReaderDevice();
        if (preferredDevice != null) {
            setSelectedReader(preferredDevice);
            return;
        }

        setSelectedReader(availableRFIDReaderList.get(0));
    }

    private void setSelectedReader(ReaderDevice device) {
        readerDevice = device;
        reader = readerDevice.getRFIDReader();
        updateTransportFlagsFromReader();
    }

    private ReaderDevice findPreferredReaderDevice() {
        for (ReaderDevice device : availableRFIDReaderList) {
            String deviceName = device.getName();
            Log.d(TAG, "device: " + deviceName + " transport=" + currentTransport);
            if (isPreferredReaderName(deviceName)) {
                return device;
            }
        }
        return null;
    }

    boolean isPreferredReaderName(String deviceName) {
        return isPreferredReaderName(deviceName, currentTransport);
    }

    boolean isPreferredReaderName(String deviceName, ENUM_TRANSPORT transport) {
        if (deviceName == null) {
            return false;
        }
        if (transport == ENUM_TRANSPORT.BLUETOOTH) {
            return deviceName.startsWith(readerNamebt) || deviceName.startsWith(readerNameRfd8500);
        }
        return deviceName.startsWith(readerName);
    }

    // handler for receiving reader appearance events
    @Override
    public void RFIDReaderAppeared(ReaderDevice readerDevice) {
        Log.d(TAG, "RFIDReaderAppeared " + readerDevice.getName());
        if (reader != null && reader.isConnected()){
            context.dismissConnectionDialog();
            context.updateStatus("RFIDReaderAppeared Reader Connected:\n" + reader.getHostName());
        }
        else{
            context.updateStatus("Reader Disconnected\r\nReconnect Battery or Re-Attach RFD40\r\nClick Setting Dots Upper Right Corner and Connect");
        }
    }

    @Override
    public void RFIDReaderDisappeared(ReaderDevice readerDevice) {
        Log.d(TAG, "RFIDReaderDisappeared " + readerDevice.getName());
        context.sendToast("RFIDReaderDisappeared");

        String interfaceType = (currentTransport == ENUM_TRANSPORT.SERVICE_USB) ? "USB" : "Bluetooth";
        context.sendStatusText(interfaceType + " Reader Disconnected\r\nReconnect Battery or Re-Attach RFD40\r\nClick Setting Dots Upper Right Corner and Connect");

        disconnect();

        onDestroy();

        Log.d(TAG, "RFIDReaderDisappeared " + readerDevice.getName() + " " + interfaceType + " to BT");
        context.sendToast("Intent from " + interfaceType + " to BT if paired");

        context.bt_usb_connect();

    }


    private synchronized String connectMethod() {
        if (reader != null) {
            Log.d(TAG, "connectMethod connecting " + reader.getHostName());
            try {
                if (!reader.isConnected()) {
                    // Establish connection to the RFID Reader
                    long connectStart = System.currentTimeMillis();
                    reader.connect();
                    updateTransportFlagsFromReader();

                    long connectTimeMillis = System.currentTimeMillis() - connectStart;
                    Log.d(TAG, "&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&");
                    Log.d(TAG, "& Connect API Total Time=" + connectTimeMillis + " ms");
                    Log.d(TAG, "&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&");

                    configureReader(connectTimeMillis);

                    if(reader.isConnected()){
                        context.dismissConnectionDialog();
                        context.sendToast("Connected, Time=" + connectTimeMillis + " ms");
                        return "Connected: " + reader.getHostName() + ". Time=" + connectTimeMillis + " ms";
                    }
                }
                else{
                    Log.d(TAG, "Already Connected");
                    updateTransportFlagsFromReader();
                    context.dismissConnectionDialog();
                    context.sendToast("Connected: " + reader.getHostName());
                    return "Connected: " + reader.getHostName();
                }

            } catch (InvalidUsageException e) {
                e.printStackTrace();
            } catch (OperationFailureException e) {
                e.printStackTrace();
                Log.d(TAG, "OperationFailureException " + e.getVendorMessage());
                String des = e.getResults().toString();
                return "Connection failed" + e.getVendorMessage() + " " + des;
            } catch (SecurityException e) {
                Log.e(TAG, "SecurityException while connecting reader", e);
                return "Connection failed: Bluetooth permissions missing";
            }
        }
        return "";
    }

    private void configureReader(long connectTimeMillis) {
        Log.d(TAG, "ConfigureReader " + reader.getHostName());
        if (reader.isConnected()) {
            try {
                // receive events from reader
                if (eventHandler == null)
                    eventHandler = new EventHandler();
                reader.Events.addEventsListener(eventHandler);
                // HH event
                reader.Events.setHandheldEvent(true);
                // tag event with tag data
                reader.Events.setTagReadEvent(true);
                reader.Events.setAttachTagDataWithReadEvent(false);

                //ECRT
                reader.Events.setReaderDisconnectEvent(true);

                reader.Events.setInventoryStartEvent(true);
                reader.Events.setInventoryStopEvent(true);
                bIsReading = false;

                playConnectTone();
                context.sendStatusText("Congiure Reader " + reader.getHostName() + "\nConnect Time=" + connectTimeMillis + " ms");

            } catch (InvalidUsageException | OperationFailureException e) {
                e.printStackTrace();
            }
        }
    }

    private boolean sleepAfterTone(long millis) {
        try {
            Thread.sleep(millis);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Log.w(TAG, "Tone sleep interrupted", e);
            return false;
        }
    }

    private void playConnectTone() {
        new Thread(() -> {
            for (int i = 0; i < 2; i++) {
                toneG.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 200);
                if (!sleepAfterTone(200)) {
                    return;
                }
            }
        }).start();
    }

    private void runAsyncTask(Runnable action) {
        new Thread(action).start();
    }

    private void postToUiThread(Runnable action) {
        context.runOnUiThread(action);
    }

    public void setPrefilterForSearchAndFind(int characterLength){
        PreFilters filters = new PreFilters();
        PreFilters.PreFilter filter = filters.new PreFilter();
        filter.setAntennaID((short) 1);// Set this filter for Antenna ID 1
        filter.setTagPatternBitCount(characterLength);
        filter.setBitOffset(32); // skip PC bits (always it should be in bit length)
        filter.setTagPattern("111122223333444455556666");
        filter.setMemoryBank(MEMORY_BANK.MEMORY_BANK_EPC);
        filter.setFilterAction(FILTER_ACTION.FILTER_ACTION_STATE_AWARE); // use state aware singulation
        filter.StateAwareAction.setTarget(TARGET.TARGET_INVENTORIED_STATE_S1); // inventoried flag of session S1 of matching tags to B
        filter.StateAwareAction.setStateAwareAction(STATE_AWARE_ACTION.STATE_AWARE_ACTION_INV_B);
        // not to select tags that match the criteria
        try {
            reader.Actions.PreFilters.add(filter);
        } catch (InvalidUsageException e) {
            e.printStackTrace();
        } catch (OperationFailureException e) {
            e.printStackTrace();
        }
        setSingulationForFilter();

    }

    private void setSingulationForFilter() {
        try {
            Antennas.SingulationControl s1_singulationControl = reader.Config.Antennas.getSingulationControl(1);
            s1_singulationControl.setSession(SESSION.SESSION_S1);
            s1_singulationControl.Action.setInventoryState(INVENTORY_STATE.INVENTORY_STATE_B);
            s1_singulationControl.Action.setPerformStateAwareSingulationAction(true);
            reader.Config.Antennas.setSingulationControl(1, s1_singulationControl);
        } catch (InvalidUsageException e) {
            e.printStackTrace();
        } catch (OperationFailureException e) {
            e.printStackTrace();
        }
    }

    private void initializeScannerSdkIfNeeded() {
        if (sdkHandler != null) {
            return;
        }
        sdkHandler = new SDKHandler(context);
        sdkHandler.dcssdkSetOperationalMode(DCSSDKDefs.DCSSDK_MODE.DCSSDK_OPMODE_USB_CDC);
        DCSSDKDefs.DCSSDK_RESULT btResult = sdkHandler.dcssdkSetOperationalMode(DCSSDKDefs.DCSSDK_MODE.DCSSDK_OPMODE_BT_LE);
        DCSSDKDefs.DCSSDK_RESULT btNormalResult = sdkHandler.dcssdkSetOperationalMode(DCSSDKDefs.DCSSDK_MODE.DCSSDK_OPMODE_BT_NORMAL);
        Log.d(TAG, btNormalResult + " results " + btResult);
        sdkHandler.dcssdkSetDelegate(this);
        sdkHandler.dcssdkSubsribeForEvents(buildScannerNotificationMask());
    }

    private int buildScannerNotificationMask() {
        int notificationsMask = 0;
        notificationsMask |= DCSSDKDefs.DCSSDK_EVENT.DCSSDK_EVENT_SCANNER_APPEARANCE.value;
        notificationsMask |= DCSSDKDefs.DCSSDK_EVENT.DCSSDK_EVENT_SCANNER_DISAPPEARANCE.value;
        notificationsMask |= DCSSDKDefs.DCSSDK_EVENT.DCSSDK_EVENT_BARCODE.value;
        notificationsMask |= DCSSDKDefs.DCSSDK_EVENT.DCSSDK_EVENT_SESSION_ESTABLISHMENT.value;
        notificationsMask |= DCSSDKDefs.DCSSDK_EVENT.DCSSDK_EVENT_SESSION_TERMINATION.value;
        return notificationsMask;
    }

    private void refreshAvailableScanners() {
        if (sdkHandler == null) {
            return;
        }

        ArrayList<DCSScannerInfo> availableScanners = (ArrayList<DCSScannerInfo>) sdkHandler.dcssdkGetAvailableScannersList();
        scannerList.clear();
        if (availableScanners == null) {
            Log.d(TAG, "Available scanners null");
            return;
        }

        scannerList.addAll(availableScanners);
    }

    private void establishScannerSessionForReader() {
        if (reader == null) {
            return;
        }

        for (DCSScannerInfo device : scannerList) {
            if (!device.getScannerName().contains(reader.getHostName())) {
                continue;
            }
            try {
                sdkHandler.dcssdkEstablishCommunicationSession(device.getScannerID());
                scannerID = device.getScannerID();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void setupScannerSDK(){
        initializeScannerSdkIfNeeded();
        refreshAvailableScanners();
        establishScannerSessionForReader();
    }

    private synchronized void disconnect() {
        Log.d(TAG, "Disconnect");
        try {
            if (reader != null) {
                if (eventHandler != null) {
                    reader.Events.removeEventsListener(eventHandler);
                    eventHandler = null;
                }
                if(isReaderConnected()) {
                    reader.disconnect();
                    bIsBTReader = false;
                    context.sendToast("Disconnected!!!");
                    new Thread(() -> toneG.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 200)).start();

                }
            }
        } catch (InvalidUsageException e) {
            e.printStackTrace();
        } catch (OperationFailureException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private synchronized void dispose() {
        disconnect();
        try {
            if (reader != null) {
                if (eventHandler != null)
                    reader.Events.removeEventsListener(eventHandler);

                reader = null;
                bIsBTReader = false;
            }

            Log.d(TAG, "Dispose readers");
            if (readers != null) {
                readers.deattach(this);
                readers.Dispose();

                readers = null;
            }

            availableRFIDReaderList = null;
            readerDevice = null;
            bIsBTReader = false;


        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    synchronized void performInventory() {
        try {
            if(reader!=null && isReaderConnected() && bIsReading==false)
                reader.Actions.Inventory.perform();
        } catch (InvalidUsageException e) {
            e.printStackTrace();
        } catch (OperationFailureException e) {
            e.printStackTrace();
        }
    }

    synchronized void stopInventory() {
        try {
            if(reader!=null && isReaderConnected())
                 reader.Actions.Inventory.stop();
        } catch (InvalidUsageException e) {
            e.printStackTrace();
        } catch (OperationFailureException e) {
            e.printStackTrace();
        }
    }

    synchronized boolean runInventoryStartStopCycles(int repeatCount) {
        if (repeatCount <= 0 || !isReaderConnected()) {
            return false;
        }

        for (int i = 0; i < repeatCount; i++) {
            if (!isReaderConnected()) {
                return false;
            }
            performInventory();
            stopInventory();
        }
        return true;
    }

    public void scanCode(){
        String inXml = "<inArgs><scannerID>" + scannerID + "</scannerID></inArgs>";
        runAsyncTask(() -> executeCommand(DCSSDKDefs.DCSSDK_COMMAND_OPCODE.DCSSDK_DEVICE_PULL_TRIGGER, inXml, null, scannerID));
    }
    public boolean executeCommand(DCSSDKDefs.DCSSDK_COMMAND_OPCODE opCode, String inXML, StringBuilder outXML, int scannerID) {
        if (sdkHandler != null)
        {
            if(outXML == null){
                outXML = new StringBuilder();
            }
            DCSSDKDefs.DCSSDK_RESULT result=sdkHandler.dcssdkExecuteCommandOpCodeInXMLForScanner(opCode,inXML,outXML,scannerID);
            Log.d(TAG, "execute command returned " + result.toString() );
            if(result== DCSSDKDefs.DCSSDK_RESULT.DCSSDK_RESULT_SUCCESS)
                return true;
            else if(result==DCSSDKDefs.DCSSDK_RESULT.DCSSDK_RESULT_FAILURE)
                return false;
        }
        return false;
    }
    // Read/Status Notify handler
    // Implement the RfidEventsLister class to receive event notifications
    public class EventHandler implements RfidEventsListener {
        // Read Event Notification
        public void eventReadNotify(RfidReadEvents e) {
            TagData[] myTags = reader.Actions.getReadTags(100);
            if (myTags != null) {
                for (int index = 0; index < myTags.length; index++) {
                    Log.d(TAG, "Tag ID" + myTags[index].getTagID() +"RSSI value "+ myTags[index].getPeakRSSI());
                    Log.d(TAG, "RSSI value "+ myTags[index].getPeakRSSI());
                }
                runAsyncTask(() -> context.handleTagdata(myTags));
            }
        }

        // Status Event Notification
        public void eventStatusNotify(RfidStatusEvents rfidStatusEvents) {
            STATUS_EVENT_TYPE statusEventType = rfidStatusEvents.StatusEventData.getStatusEventType();
            Log.d(TAG, "Status Notification: " + statusEventType);

            if (statusEventType == STATUS_EVENT_TYPE.INVENTORY_START_EVENT) {
                bIsReading = true;
                context.inventoryStartEvent(true);
            }
            if (statusEventType == STATUS_EVENT_TYPE.INVENTORY_STOP_EVENT) {
                bIsReading = false;
                context.inventoryStartEvent(false);
            }

            if (statusEventType == STATUS_EVENT_TYPE.HANDHELD_TRIGGER_EVENT) {
                handleTriggerEvent(rfidStatusEvents);
            }

            if (statusEventType == STATUS_EVENT_TYPE.DISCONNECTION_EVENT && bIsBTReader) {
                handleBluetoothDisconnection();
            }

        }

        private void handleTriggerEvent(RfidStatusEvents rfidStatusEvents) {
            HANDHELD_TRIGGER_EVENT_TYPE triggerEventType = rfidStatusEvents.StatusEventData.HandheldTriggerEventData.getHandheldEvent();
            if (triggerEventType == HANDHELD_TRIGGER_EVENT_TYPE.HANDHELD_TRIGGER_PRESSED) {
                runAsyncTask(() -> {
                    Log.d(TAG, "HANDHELD_TRIGGER_PRESSED");
                    context.handleTriggerPress(true);
                });
                return;
            }

            if (triggerEventType == HANDHELD_TRIGGER_EVENT_TYPE.HANDHELD_TRIGGER_RELEASED) {
                runAsyncTask(() -> {
                    context.handleTriggerPress(false);
                    Log.d(TAG, "HANDHELD_TRIGGER_RELEASED");
                });
            }
        }

        private void handleBluetoothDisconnection() {
            runAsyncTask(() -> {
                String disappearedName = readerDevice != null ? readerDevice.getName() : "unknown";
                Log.d(TAG, "BLUETOOTH DISCONNECTION_EVENT " + disappearedName);
                context.sendToast("BLUETOOTH DISCONNECTION_EVENT");
                onDestroy();
                context.sendStatusText("Bluetooth Reader Disconnected\r\nReconnect Battery or Re-Attach RFD40\r\nClick Setting Dots Upper Right Corner and Connect");
                context.bt_usb_connect();
            });
        }
    }

    interface ResponseHandlerInterface {
        void handleTagdata(TagData[] tagData);

        void handleTriggerPress(boolean pressed);

        void barcodeData(String val);
        void sendToast(String val);

        void inventoryStartEvent(boolean started);
    }

}
