# Zebra RFID USB/BT Auto Switching And Connect

Android sample for Zebra RFID readers that prefers USB, falls back to Bluetooth, and lets the user recover from detach or disconnect events without restarting the app.

## What This App Does
- Starts RFID initialization from `MainActivity`.
- Requests Bluetooth runtime permissions on Android 12+ before RFID init continues.
- Requests USB permission for Zebra devices with vendor ID `1504` when one is already attached.
- Tries `SERVICE_USB` first and falls back to `BLUETOOTH` when USB discovery fails.
- Uses transport-aware preferred reader matching when multiple readers are available.
- Shows a reconnect dialog on detach/disconnect with these choices:
   - Connect Bluetooth
   - Wait for USB re-attach
- Schedules Bluetooth reconnect after 5 seconds without blocking the UI thread.
- Rebuilds the RFID stack when a Zebra USB attach broadcast is received.
- Keeps `bIsBTReader` synchronized through connect, disconnect, and dispose paths.

## Main Components
- `app/src/main/java/com/zebra/rfid/demo/sdksample/MainActivity.java`
   - Bluetooth runtime permission checks
   - USB permission request flow and broadcast receiver
   - Reconnect dialog and delayed Bluetooth retry
   - Inventory start/stop UI actions
- `app/src/main/java/com/zebra/rfid/demo/sdksample/RFIDHandler.java`
   - USB-first and Bluetooth-fallback reader discovery
   - Transport-aware preferred reader selection
   - Reader event wiring
   - Trigger handling and inventory control

## Configuration Notes
- Zebra USB vendor ID is `1504`.
- Preferred reader names are still hardcoded in `RFIDHandler`:
   - `readerName`
   - `readerNamebt`
   - `readerNameRfd8500`
- Build includes one Zebra API3 AAR to avoid duplicate-class failures:
   - `app/libs/API3_LIB-release-9.13.2.116.aar`

## Review Summary
- Correctness issues fixed during review:
   - UI-thread sleep removed from the reconnect dialog path
   - Background-thread UI updates removed from the stress loop
   - USB permission flow wired through `UsbManager.requestPermission(...)`
   - Startup permission callback now resumes RFID init correctly after grant
   - Startup no longer shows a false Bluetooth permission warning before the user responds
   - Helper script app id corrected to match Gradle config
   - Reader selection made transport-aware for USB vs Bluetooth fallback
- Remaining technical debt:
   - Some UI/controller code in `MainActivity` is still larger than ideal
   - Reader identity should eventually move to configuration instead of hardcoded prefixes

## Runtime Hardening Completed
- Receiver lifecycle now matches the activity lifecycle.
- Android 12+ permission callback validates both requested Bluetooth permissions.
- USB permission and attach payload extraction is safe for current Android APIs.
- Bluetooth reconnect is delayed without freezing the UI.
- Legacy `AsyncTask` usage has been removed from RFID init/connect and event-side background work.
- BT/USB transport flag drift is reduced through centralized updates.
- Reader-list null assumptions were removed from the discovery path.
- Remaining USB/Bluetooth permission messages now come from string resources instead of hardcoded literals.
- BuildConfig generation is now enabled at the module level instead of the deprecated global Gradle property.

## Build And Run
Use Android Studio or Gradle wrapper:

```bash
./gradlew :app:assembleDebug
```

Fast local workflow:

```bash
./auto_build_run.sh
```

## Scripted Tasks
Use helper script commands:

```bash
./android_tasks.sh clean
./android_tasks.sh build
./android_tasks.sh deploy
./android_tasks.sh run
./android_tasks.sh connected-test
./android_tasks.sh all
```

Run a single instrumentation test method:

```bash
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.zebra.rfid.demo.sdksample.ExampleInstrumentedTest#autoConnectDisconnectRunsThreeCycles
```

VS Code task support is available in `.vscode/tasks.json`, including `Android: Connected Tests`.

## Validation Checklist
1. Confirm only source and docs are included in commit and exclude generated `app/build` outputs.
2. Verify ignore rules are present.
3. Validate the runtime matrix:
    - App start with USB attached
    - App start with only a paired Bluetooth reader
    - USB detach while inventory is active
    - USB re-attach after the Bluetooth fallback dialog appears
4. Validate instrumentation tests on device:
    - Receiver lifecycle registration test
    - Android 12+ permission-result symmetry tests
    - Zebra vendor-id helper test
    - USB-vs-Bluetooth preferred reader-name matching tests
    - Inventory start/stop loop test
    - Auto connect/disconnect loop test
    - Grant-all-permissions stress test

## Last Verified State
- `Android: Build Debug` passes.
- `Android: Connected Tests` passes with 14/14 tests on the connected device.
