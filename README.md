# Zebra RFID SDK Sample - AutoConnect

This application demonstrates how to use the Zebra RFID SDK to connect to RFID readers over USB or Bluetooth.

## Features
-   **Auto-Connection**: Automatically attempts to connect to available RFID readers on startup or when a device is attached.
-   **USB & Bluetooth Support**: Supports Zebra Sled readers over USB (CDC mode) and Bluetooth (BTLE/Classic).
-   **Inventory**: Perform RFID tag inventory and view results in real-time.
-   **Visual Feedback**: Progress indicator (hourly-glass style) during inventory.
-   **Permissions**: Handles Bluetooth permissions required for discovery and connection on Android 12+.

## Getting Started
1.  Clone the repository.
2.  Open in Android Studio.
3.  Build and run on a Zebra device (e.g., TC22) or any Android device with Zebra RFID sleds.

## Version
- **Init**: Initial release with core functionality and fixes.
