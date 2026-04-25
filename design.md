# Design Document: RFID Reader USB/BT Switching

This document describes the implemented architecture and runtime behavior for USB-first and Bluetooth-fallback RFID connection handling in this app.

## 1. Goals
- Prefer USB RFID transport when available.
- Fall back to Bluetooth when USB discovery fails.
- Recover from detach/disconnect without restarting the app.
- Keep reconnect and inventory UI paths thread-safe.

## 2. Runtime Architecture
- `MainActivity`
    - Owns UI state, the reconnect dialog, the USB broadcast receiver, and the inventory progress indicator.
    - Registers the receiver in `onStart` and unregisters it in `onStop` and `onDestroy`.
    - Validates Android 12+ Bluetooth runtime permissions before RFID init continues.
    - Requests USB permission for Zebra vendor ID `1504` when a matching device is already attached.
    - Triggers SDK re-init (`InitRfidSDK`) on approved connection transitions.
    - Manages `inventoryProgress` (ProgressBar) visibility based on SDK inventory events.
- `RFIDHandler`
  - Owns Zebra `Readers`, selected `ReaderDevice`, active `RFIDReader`, and event lifecycle.
  - Performs async discovery, connect, and event-side work with explicit background threads plus UI handoff where needed.
  - Emits status and data back to `MainActivity` through callback methods.
  - Keeps transport flags synchronized across connect and teardown paths.

## 3. Connection Strategy

### 3.1 Initialization Path
1. `MainActivity` checks Bluetooth runtime permissions.
2. If a Zebra USB device is already present, `MainActivity` requests USB permission.
3. `RFIDHandler` builds `Readers(context, SERVICE_USB)`.
4. If USB discovery succeeds, it selects a preferred USB reader.
5. If USB discovery fails or throws `InvalidUsageException`, it disposes and retries with `Readers(context, BLUETOOTH)`.
6. On Bluetooth fallback, it selects a preferred Bluetooth reader.
7. If no preferred reader matches, it uses the first reader returned by the SDK.
8. It connects the selected reader and configures events.

Preferred reader matching is transport-aware:
- USB transport prefers `readerName`
- Bluetooth transport prefers `readerNamebt` or `readerNameRfd8500`

On Android 12+:
1. Request `BLUETOOTH_SCAN` and `BLUETOOTH_CONNECT` when missing.
2. Continue RFID init only when both are granted.

### 3.2 Event Configuration
After connection, the following are enabled:
- Handheld trigger events
- Tag read events
- Reader disconnect events
- Inventory start/stop status events: These events trigger the `inventoryProgress` ProgressBar visibility in `MainActivity`.

### 3.3 Disconnect Recovery
- On reader disappeared or disconnect events, the handler disconnects/disposes and prompts the user with a Bluetooth-or-USB decision dialog.
- Choosing Bluetooth schedules SDK re-init after 5 seconds on the main looper.
- Choosing USB leaves the app waiting for the next USB attach broadcast.
- On Zebra USB attach broadcast (`VID=1504`), the activity dismisses any visible dialog and rebuilds the RFID stack.
- USB permission and attach payloads use guarded `UsbManager.EXTRA_DEVICE` extraction.

## 4. Threading Model
- SDK discovery, connect, trigger handling, barcode commands, and disconnect recovery run on background threads created inside `RFIDHandler`.
- UI updates (`Toast`, status text, dialog show/dismiss) are posted on the UI thread.
- Delayed Bluetooth reconnect uses `Handler(Looper.getMainLooper())`.
- Tone delays and stress-loop sleeps restore interruption state instead of throwing generic runtime exceptions.
- Inventory callbacks publish tag data asynchronously to the UI.

## 5. State Model
- `reader`, `readerDevice`, and `readers` represent the current transport/session objects.
- `bIsReading` gates inventory calls.
- `bIsBTReader` tracks Bluetooth-connected state and controls the Bluetooth disconnect recovery path.
- `bIsBTReader` is updated from the selected or connected reader and reset on disconnect/dispose.

## 6. Dependency Strategy
- Build includes one Zebra API3 AAR to avoid duplicate-class collisions.
- Active dependency:
  - `app/libs/API3_LIB-release-9.13.2.116.aar`

## 7. Verification Plan
1. Build validation:
   - `./gradlew :app:assembleDebug`
2. Full local flow:
   - `./auto_build_run.sh`
3. Connected instrumentation tests:
   - `./android_tasks.sh connected-test`
4. Device-backed checks currently cover:
   - USB receiver lifecycle registration
   - Bluetooth permission callback symmetry
   - Zebra vendor-id helper behavior
   - USB-vs-Bluetooth preferred reader-name selection
   - Inventory loop behavior
   - Auto connect/disconnect loop behavior

## 8. Current Review Summary
- Correctness issues addressed during review:
  - blocking UI-thread sleep in the reconnect dialog
  - background-thread UI writes in the stress loop
  - incomplete USB permission request path
  - startup permission grant path not resuming RFID initialization
  - premature Bluetooth permission warning shown during startup request
  - launcher script package mismatch
  - transport-blind preferred reader selection
- Remaining work is mostly structural rather than correctness-critical:
  - reduce method complexity in `MainActivity` and `RFIDHandler`
  - externalize preferred reader names into configuration

## 9. Release Readiness
- BuildConfig generation is configured in `app/build.gradle` instead of the deprecated global Gradle property.
- Bluetooth and USB permission status messages are backed by string resources for check-in consistency.
- Connected instrumentation coverage currently includes 14 device-backed tests.

## 10. Check-In Plan
1. Include source and documentation changes only.
2. Exclude generated artifacts such as `app/build/**` from version control.
3. Run smoke tests for USB-first, BT fallback, detach, and re-attach scenarios.
4. Run connected instrumentation tests before final check-in.
