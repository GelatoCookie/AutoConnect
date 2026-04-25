package com.zebra.rfid.demo.sdksample;

import android.Manifest;
import android.content.pm.PackageManager;

import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class ExampleInstrumentedTest {

	@Test
	public void usbReceiverRegistrationTracksActivityLifecycle() {
		try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
			scenario.onActivity(activity -> assertTrue(readUsbReceiverRegisteredFlag(activity)));

			scenario.moveToState(Lifecycle.State.CREATED);
			scenario.onActivity(activity -> assertFalse(readUsbReceiverRegisteredFlag(activity)));

			scenario.moveToState(Lifecycle.State.RESUMED);
			scenario.onActivity(activity -> assertTrue(readUsbReceiverRegisteredFlag(activity)));
		}
	}

	 @Test
	 public void permissionCallbackInitializesHandlerWhenBothGranted() {
		try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
		 scenario.onActivity(activity -> {
			RecordingRFIDHandler handler = new RecordingRFIDHandler();
			writeRfidHandler(activity, handler);

			activity.onRequestPermissionsResult(
				readBluetoothPermissionRequestCode(),
				new String[]{Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN},
				new int[]{PackageManager.PERMISSION_GRANTED, PackageManager.PERMISSION_GRANTED}
			);

			assertEquals(1, handler.onCreateCallCount);
		 });
		}
	 }

	 @Test
	 public void permissionCallbackInitializesHandlerWhenMissingPermissionIsAlreadyGranted() {
		try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
		 scenario.onActivity(activity -> {
			RecordingRFIDHandler handler = new RecordingRFIDHandler();
			writeRfidHandler(activity, handler);
			activity.setBluetoothPermissionOverrideForTests(true);

			activity.onRequestPermissionsResult(
				readBluetoothPermissionRequestCode(),
				new String[]{Manifest.permission.BLUETOOTH_SCAN},
				new int[]{PackageManager.PERMISSION_GRANTED}
			);

			assertEquals(1, handler.onCreateCallCount);
		 });
		}
	 }

	 @Test
	 public void permissionCallbackDoesNotInitializeHandlerWhenOneDenied() {
		try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
		 scenario.onActivity(activity -> {
			RecordingRFIDHandler handler = new RecordingRFIDHandler();
			writeRfidHandler(activity, handler);
			activity.setBluetoothPermissionOverrideForTests(false);

			activity.onRequestPermissionsResult(
				readBluetoothPermissionRequestCode(),
				new String[]{Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT},
				new int[]{PackageManager.PERMISSION_GRANTED, PackageManager.PERMISSION_DENIED}
			);

			assertEquals(0, handler.onCreateCallCount);
		 });
		}
	 }

	 @Test
	 public void initRfidSdkSkipsInitializationWhenBluetoothPermissionsMissing() {
		try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
		 scenario.onActivity(activity -> {
			RecordingRFIDHandler handler = new RecordingRFIDHandler();
			writeRfidHandler(activity, handler);
			activity.setBluetoothPermissionOverrideForTests(false);

			activity.InitRfidSDK();

			assertEquals(0, handler.onCreateCallCount);
		 });
		}
	 }

	 @Test
	 public void startupPermissionRequestReturnsPendingWithoutInitializingHandler() {
		try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
		 scenario.onActivity(activity -> {
			RecordingRFIDHandler handler = new RecordingRFIDHandler();
			writeRfidHandler(activity, handler);
			activity.setBluetoothPermissionOverrideForTests(false);

			boolean permissionReady = invokeEnsureBluetoothPermissionsForRfidInit(activity);

			assertFalse(permissionReady);
			assertEquals(0, handler.onCreateCallCount);
		 });
		}
	 }

	 @Test
	 public void onPostResumeSkipsResumeWhenBluetoothPermissionsMissing() {
		try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
		 scenario.onActivity(activity -> {
			RecordingRFIDHandler handler = new RecordingRFIDHandler();
			writeRfidHandler(activity, handler);
			activity.setBluetoothPermissionOverrideForTests(false);

			activity.onPostResume();

			assertEquals(0, handler.onResumeCallCount);
		 });
		}
	 }

	@Test
	public void zebraVendorIdMatcherOnlyAcceptsConfiguredVendor() {
		try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
			scenario.onActivity(activity -> {
				assertTrue(activity.isRfidVendorDevice(1504));
				assertFalse(activity.isRfidVendorDevice(1234));
			});
		}
	}

	@Test
	public void inventoryStartStopRunsThreeTimesWhenReaderConnected() {
		InventoryLoopRFIDHandler handler = new InventoryLoopRFIDHandler(true);

		boolean result = handler.runInventoryStartStopCycles(3);

		assertTrue(result);
		assertEquals(3, handler.performInventoryCallCount);
		assertEquals(3, handler.stopInventoryCallCount);
	}

	@Test
	public void inventoryStartStopFailsWhenReaderNotConnected() {
		InventoryLoopRFIDHandler handler = new InventoryLoopRFIDHandler(false);

		boolean result = handler.runInventoryStartStopCycles(3);

		assertFalse(result);
		assertEquals(0, handler.performInventoryCallCount);
		assertEquals(0, handler.stopInventoryCallCount);
	}

	@Test
	public void preferredReaderNameMatchesUsbReaderPrefix() {
		RFIDHandler handler = new RFIDHandler();

		assertTrue(handler.isPreferredReaderName("RFD4031-G10B700-US-ABC", com.zebra.rfid.api3.ENUM_TRANSPORT.SERVICE_USB));
		assertFalse(handler.isPreferredReaderName("RFD40+_211545201D0011", com.zebra.rfid.api3.ENUM_TRANSPORT.SERVICE_USB));
	}

	@Test
	public void preferredReaderNameMatchesBluetoothReaderPrefixes() {
		RFIDHandler handler = new RFIDHandler();

		assertTrue(handler.isPreferredReaderName("RFD40+_211545201D0011", com.zebra.rfid.api3.ENUM_TRANSPORT.BLUETOOTH));
		assertTrue(handler.isPreferredReaderName("RFD8500161755230D5038", com.zebra.rfid.api3.ENUM_TRANSPORT.BLUETOOTH));
		assertFalse(handler.isPreferredReaderName("RFD4031-G10B700-US-ABC", com.zebra.rfid.api3.ENUM_TRANSPORT.BLUETOOTH));
	}

	@Test
	public void autoConnectDisconnectRunsThreeCycles() {
		try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
			scenario.onActivity(activity -> {
				AutoConnectDisconnectRFIDHandler handler = new AutoConnectDisconnectRFIDHandler(true);
				writeRfidHandler(activity, handler);

				activity.runAutoConnectDisconnectCycles(3);

				assertEquals(3, handler.onDestroyCallCount);
				assertEquals(3, handler.onCreateCallCount);
				assertTrue(handler.isReaderConnected());
			});
		}
	}

	@Test
	public void grantAllPermissionsAndRunTenCycleStressTests() {
		try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
			scenario.onActivity(activity -> {
				PermissionAndLoopRFIDHandler handler = new PermissionAndLoopRFIDHandler(true);
				writeRfidHandler(activity, handler);

				activity.onRequestPermissionsResult(
					readBluetoothPermissionRequestCode(),
					new String[]{Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT},
					new int[]{PackageManager.PERMISSION_GRANTED, PackageManager.PERMISSION_GRANTED}
				);

				assertEquals(1, handler.onCreateCallCount);

				boolean inventoryResult = handler.runInventoryStartStopCycles(10);
				assertTrue(inventoryResult);
				assertEquals(10, handler.performInventoryCallCount);
				assertEquals(10, handler.stopInventoryCallCount);

				activity.runAutoConnectDisconnectCycles(10);
				assertEquals(10, handler.onDestroyCallCount);
				assertEquals(11, handler.onCreateCallCount);
				assertTrue(handler.isReaderConnected());
			});
		}
	}

	private boolean readUsbReceiverRegisteredFlag(MainActivity activity) {
		try {
			Field field = MainActivity.class.getDeclaredField("usbReceiverRegistered");
			field.setAccessible(true);
			return field.getBoolean(activity);
		} catch (NoSuchFieldException | IllegalAccessException e) {
			throw new AssertionError("Unable to read usbReceiverRegistered field", e);
		}
	}

	 private int readBluetoothPermissionRequestCode() {
		try {
		 Field field = MainActivity.class.getDeclaredField("BLUETOOTH_PERMISSION_REQUEST_CODE");
		 field.setAccessible(true);
		 return field.getInt(null);
		} catch (NoSuchFieldException | IllegalAccessException e) {
		 throw new AssertionError("Unable to read BLUETOOTH_PERMISSION_REQUEST_CODE field", e);
		}
	 }

	 private void writeRfidHandler(MainActivity activity, RFIDHandler handler) {
		try {
		 Field field = MainActivity.class.getDeclaredField("rfidHandler");
		 field.setAccessible(true);
		 field.set(activity, handler);
		} catch (NoSuchFieldException | IllegalAccessException e) {
		 throw new AssertionError("Unable to replace rfidHandler field", e);
		}
	 }

	 private boolean invokeEnsureBluetoothPermissionsForRfidInit(MainActivity activity) {
		try {
		 Method method = MainActivity.class.getDeclaredMethod("ensureBluetoothPermissionsForRfidInit");
		 method.setAccessible(true);
		 return (Boolean) method.invoke(activity);
		} catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
		 throw new AssertionError("Unable to invoke ensureBluetoothPermissionsForRfidInit", e);
		}
	 }

	 private static final class RecordingRFIDHandler extends RFIDHandler {
		private int onCreateCallCount = 0;
		private int onResumeCallCount = 0;

		@Override
		public void onCreate(MainActivity activity) {
		 onCreateCallCount++;
		}

		@Override
		String onResume() {
			onResumeCallCount++;
			return "";
		}
	 }

	private static final class InventoryLoopRFIDHandler extends RFIDHandler {
		private final boolean connected;
		private int performInventoryCallCount = 0;
		private int stopInventoryCallCount = 0;

		private InventoryLoopRFIDHandler(boolean connected) {
			this.connected = connected;
		}

		@Override
		public synchronized boolean isReaderConnected() {
			return connected;
		}

		@Override
		synchronized void performInventory() {
			performInventoryCallCount++;
		}

		@Override
		synchronized void stopInventory() {
			stopInventoryCallCount++;
		}
	}

	private static final class AutoConnectDisconnectRFIDHandler extends RFIDHandler {
		private boolean connected;
		private int onCreateCallCount = 0;
		private int onDestroyCallCount = 0;

		private AutoConnectDisconnectRFIDHandler(boolean initiallyConnected) {
			connected = initiallyConnected;
		}

		@Override
		public synchronized boolean isReaderConnected() {
			return connected;
		}

		@Override
		public void onCreate(MainActivity activity) {
			onCreateCallCount++;
			connected = true;
		}

		@Override
		void onDestroy() {
			onDestroyCallCount++;
			connected = false;
		}
	}

	private static final class PermissionAndLoopRFIDHandler extends RFIDHandler {
		private boolean connected;
		private int onCreateCallCount = 0;
		private int onDestroyCallCount = 0;
		private int performInventoryCallCount = 0;
		private int stopInventoryCallCount = 0;

		private PermissionAndLoopRFIDHandler(boolean initiallyConnected) {
			connected = initiallyConnected;
		}

		@Override
		public synchronized boolean isReaderConnected() {
			return connected;
		}

		@Override
		public void onCreate(MainActivity activity) {
			onCreateCallCount++;
			connected = true;
		}

		@Override
		void onDestroy() {
			onDestroyCallCount++;
			connected = false;
		}

		@Override
		synchronized void performInventory() {
			performInventoryCallCount++;
		}

		@Override
		synchronized void stopInventory() {
			stopInventoryCallCount++;
		}
	}
}
