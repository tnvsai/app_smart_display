# Navigation Monitor Android App

A complete Android app that reads live Google Maps navigation notifications and sends the relevant navigation data (direction, distance, maneuver) via BLE to an ESP32 or similar microcontroller.

## Features

- **Real-time Notification Monitoring**: Captures Google Maps navigation notifications using NotificationListenerService
- **Smart Data Parsing**: Extracts direction, distance, and maneuver information from notification text
- **BLE Communication**: Connects to ESP32 devices and sends navigation data via Bluetooth Low Energy
- **Message Queuing**: Queues messages when BLE device is disconnected and flushes when reconnected
- **Manual Input**: Allows manual entry of navigation data for testing
- **Permission Management**: Handles all Android 12+ runtime permissions
- **Foreground Service**: Keeps the app running in background for continuous monitoring
- **Modern UI**: Clean Material Design 3 interface with real-time status updates

## Requirements

- Android 11+ (API 30+)
- Kotlin
- BLE-capable device
- ESP32 or similar microcontroller with BLE support

## Permissions

The app requires the following permissions:

- `BLUETOOTH_SCAN` - For scanning BLE devices
- `BLUETOOTH_CONNECT` - For connecting to BLE devices
- `BLUETOOTH_ADVERTISE` - For BLE advertising
- `ACCESS_FINE_LOCATION` - Required for BLE scanning
- `ACCESS_COARSE_LOCATION` - Required for BLE scanning
- `POST_NOTIFICATIONS` - For displaying app notifications
- `BIND_NOTIFICATION_LISTENER_SERVICE` - For accessing system notifications
- `FOREGROUND_SERVICE` - For background service
- `WAKE_LOCK` - For background processing

## ESP32 Setup

To use this app with an ESP32, you need to:

1. **Set up BLE service** with the following UUIDs:
   - Service UUID: `12345678-1234-1234-1234-1234567890ab`
   - Characteristic UUID: `abcd1234-5678-90ab-cdef-1234567890ab`

2. **Configure device name** as "ESP32_BLE"

3. **Handle incoming data** in pipe-separated format:
   ```
   direction|distance|maneuver
   ```
   Where:
   - `direction`: "left", "right", "straight", or "uturn"
   - `distance`: integer value in meters
   - `maneuver`: text description (e.g., "roundabout", "exit")

## Installation

1. Clone the repository
2. Open in Android Studio
3. Build and install on your Android device
4. Grant all required permissions when prompted
5. Enable notification access in system settings

## Usage

1. **Start the app** and grant all permissions
2. **Connect ESP32** - The app will automatically scan for "ESP32_BLE" devices
3. **Start navigation** in Google Maps
4. **Monitor status** - The app will show connection status and queued messages
5. **Manual testing** - Use the manual input section to test BLE communication

## Architecture

### Core Components

- **MainActivity**: Main UI with permission handling and service control
- **NavigationService**: Foreground service for background monitoring
- **NotificationListenerService**: Captures Google Maps notifications
- **NotificationParser**: Extracts navigation data from notification text
- **BLEService**: Handles BLE scanning, connection, and communication
- **PermissionHandler**: Manages Android 12+ runtime permissions

### Data Flow

1. Google Maps sends navigation notification
2. NotificationListenerService captures the notification
3. NotificationParser extracts navigation data
4. BLEService formats data as "direction|distance|maneuver" and sends to ESP32 via BLE
5. ESP32 displays the navigation information on the LCD screen
6. If disconnected, messages are queued and sent when reconnected

## Customization

### BLE Configuration

Modify `BLEConstants.kt` to change:
- Device name to scan for
- Service and characteristic UUIDs
- Timeout values

### Notification Parsing

Update `NotificationParser.kt` to:
- Add new direction patterns
- Modify distance extraction regex
- Add new maneuver types

### UI Customization

The UI is built with Jetpack Compose and Material Design 3. Modify the composables in `MainActivity.kt` to customize the interface.

## Troubleshooting

### Common Issues

1. **BLE not connecting**: Ensure ESP32 is advertising with correct name and service UUIDs
2. **Notifications not captured**: Check notification access permission in system settings
3. **App stops in background**: Ensure battery optimization is disabled for the app
4. **Permissions denied**: Check all permissions are granted in app settings

### Debug Logs

Enable debug logging to see detailed information:
- BLE connection status
- Notification parsing results
- Message queuing status
- Permission status

## License

This project is open source and available under the MIT License.

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.
