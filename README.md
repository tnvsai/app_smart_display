# YatraMate

**YatraMate** is a comprehensive Android application that integrates with an ESP32-based smart display to provide real-time navigation guidance and phone call notifications while driving. The app monitors Google Maps navigation and phone calls through Android notifications and transmits this data via Bluetooth Low Energy (BLE) to a companion ESP32 display unit.

**By tnvsai**

## Features

### 🗺️ Navigation Monitoring
- Real-time parsing of Google Maps navigation notifications
- Displays turn-by-turn directions with arrows
- Shows distance to next maneuver and ETA
- Updates live as navigation progresses

### 📞 Phone Call Management
- Detects incoming, outgoing, missed, and ongoing calls
- Displays caller information (name and number)
- Missed call reminders with periodic notifications
- Smart call state detection with deduplication
- Call animations on the ESP32 display

### 🔵 Bluetooth Low Energy (BLE)
- Stable BLE communication between Android device and ESP32
- Automatic connection management and reconnection
- Real-time data transmission with efficient deduplication
- Connection status monitoring

### 🎨 Modern Material3 UI
- Clean, modern interface following Google's Material Design 3
- Dark mode support
- Tabbed navigation (Home and Developer tabs)
- Real-time connection status indicators
- Recent activity log

### 🧑‍💻 Developer Tools
- Manual JSON transmission for testing
- Key-value pair builder with smart type conversion
- Comprehensive debug logs (Navigation History, Call History, System Log)
- Clear all functionality for logs

## Hardware Requirements

### ESP32 Display Unit
- ESP32 development board
- Display: 172x320 ST7789 TFT LCD
- Touch screen (AXS5106L) with I2C interface
- Required connections:
  - Display: SPI bus (DC: GPIO15, CS: GPIO14, SCK: GPIO1, MOSI: GPIO2, RST: GPIO22, BL: GPIO23)
  - Touch: I2C bus (SDA: GPIO18, SCL: GPIO19, RST: GPIO20, INT: GPIO21)

### Android Device
- Android 8.0 (API 26) or higher
- Bluetooth 4.0+ support
- Google Maps installed for navigation features

## Software Setup

### Android App Installation

1. Clone the repository:
   ```bash
   git clone <repository-url>
   cd YatraMate
   ```

2. Open the project in Android Studio (latest version recommended)

3. Ensure you have the following installed:
   - Android SDK Build-Tools
   - Android SDK Platform (API 36)
   - Gradle 8.0+

4. Build and run:
   ```bash
   ./gradlew assembleDebug
   ```

5. Install on your Android device:
   ```bash
   adb install app/build/outputs/apk/debug/app-debug.apk
   ```

### ESP32 Firmware Installation

1. Install PlatformIO:
   - Install PlatformIO IDE or PlatformIO Core
   - Reference: https://platformio.org/install

2. Open the firmware project:
   ```bash
   cd ardunio_files
   ```

3. Build and upload:
   ```bash
   pio run -t upload
   ```

4. Monitor serial output:
   ```bash
   pio device monitor
   ```

## Initial Setup

### Android Permissions

After installing the app, grant the following permissions:

1. **Bluetooth Permissions** (automatically requested):
   - Bluetooth, Bluetooth Admin, Bluetooth Scan, Bluetooth Connect, Bluetooth Advertise

2. **Location Permissions** (automatically requested):
   - Fine Location, Coarse Location

3. **Notification Access** (manual setup required):
   - Go to: Settings → Apps → YatraMate → Special Access → Notification Access
   - Enable notification access for YatraMate

4. **Battery Optimization** (recommended):
   - Go to: Settings → Apps → YatraMate → Battery
   - Set to "Unrestricted" to ensure continuous background operation

### ESP32 Connection

1. Power on the ESP32 display
2. Note the BLE device name (displayed on the ESP32 serial monitor)
3. Open YatraMate app on your Android device
4. Click "Start Service" on the Home tab
5. The app will automatically scan and connect to the ESP32 device
6. Connection status will show "Connected" once paired

## Usage

### Navigation Display

1. Start Google Maps navigation on your Android device
2. YatraMate will automatically detect navigation notifications
3. The ESP32 display will show:
   - Direction arrows (Left, Right, Straight, U-turn, etc.)
   - Distance to next maneuver
   - Turn direction text
   - Estimated time of arrival (ETA)

### Phone Call Display

1. When a call is received or placed, YatraMate intercepts the notification
2. The ESP32 display shows:
   - Incoming calls: "INCOMING CALL" with caller info
   - Outgoing calls: "CALLING..." with recipient info
   - Missed calls: "MISSED CALL" with periodic reminders
3. Navigation data will not interrupt call displays for at least 5 seconds
4. Tap the display to dismiss missed call reminders

### Developer Features

1. **Manual Send**: Test BLE communication by sending custom JSON data
2. **Navigation History**: View all parsed Google Maps notifications
3. **Call History**: View all detected phone call events with parsed data
4. **System Log**: Monitor all BLE operations and data transmissions

## Project Structure

```
YatraMate/
├── app/
│   ├── src/main/java/com/tnvsai/yatramate/
│   │   ├── MainActivity.kt              # Main UI and user interface
│   │   ├── ble/
│   │   │   ├── WorkingBLEService.kt     # BLE connection management
│   │   │   └── BLEConstants.kt          # BLE configuration constants
│   │   ├── notification/
│   │   │   ├── NotificationListenerService.kt  # Notification interception
│   │   │   ├── NotificationParser.kt    # Google Maps parsing
│   │   │   └── PhoneCallParser.kt       # Phone call parsing
│   │   ├── service/
│   │   │   └── NavigationService.kt     # Foreground service
│   │   ├── model/
│   │   │   ├── NavigationData.kt        # Navigation data model
│   │   │   └── PhoneCallData.kt         # Phone call data model
│   │   ├── permission/
│   │   │   └── PermissionHandler.kt     # Permission management
│   │   ├── ui/
│   │   │   ├── screens/
│ Sage  │   │   │   └── HomeScreen.kt      # Home screen composable
│   │   │   ├── components/
│   │   │   │   ├── StatusCard.kt         # Status display card
│   │   │   │   ├── NavigationDataDisplay.kt  # Navigation display
│   │   │   │   ├── QuickActionPanel.kt   # Action buttons
│   │   │   │   └── ConnectionIndicator.kt  # Connection status
│   │   │   └── theme/
│   │   │       ├── Color.kt              # Color definitions
│   │   │       ├── Theme.kt              # Material3 theme
│   │   │       └── Type.kt               # Typography
│   │   └── utils/
│   │       └── ETACalculator.kt          # ETA calculations
│   ├── build.gradle.kts                 # App build configuration
│   └── proguard-rules.pro              # ProGuard rules
├── ardunio_files/
│   └── src/smart_display_main/
│       ├── smart_display_main.ino       # ESP32 firmware
│       └── lv_conf.h                    # LVGL configuration
├── build.gradle.kts                     # Root build configuration
├── settings.gradle.kts                  # Project settings
├── platformio.ini                       # PlatformIO configuration
├── README.md                            # This file
└── PROJECT_STRUCTURE.md                 # Detailed architecture documentation
```

## Data Formats

### Navigation JSON
```json
{
  "type": "NAVIGATION",
  "direction": "left",
  "distance": 300,
  "maneuver": "Turn left at the light",
  "eta": "5 mins"
}
```

### Phone Call JSON
```json
{
  "type": "PHONE_CALL",
  "callState": "INCOMING",
  "callerName": "John Doe",
  "callerNumber": "+1234567890"
}
```

### Call States
- `INCOMING`: Incoming call detected
- `ONGOING`: Call in progress (incoming answered or outgoing)
- `ENDED`: Call ended normally
- `MISSED`: Missed call (not answered)

## Dependencies

### Android App
- AndroidX Core KTX
- Jetpack Compose (Material3)
- Lifecycle Runtime KTX
- Work Manager KTX
- Gson for JSON parsing

### ESP32 Firmware
- Arduino GFX Library
- BLE Libraries (ESP32)
- ArduinoJson
- Touch driver (AXS5106L)

## Troubleshooting

### Connection Issues
- **App shows "Disconnected"**: Check Bluetooth is enabled, ESP32 is powered on, and try restarting the app
- **Cannot find ESP32 device**: Ensure the ESP32 is in pairing mode (check serial monitor)
- **Intermittent disconnections**: Move Android device closer to ESP32 or check for interference

### Navigation Not Displaying
- **No navigation data**: Ensure Google Maps is actively navigating and notification access is enabled
- **Wrong directions**: Check that notification parsing is working in Developer tab → Navigation History
- **Stale data**: Tap "Clear All" in Navigation History and restart Google Maps navigation

### Phone Calls Not Detected
- **No call notifications**: Enable notification access in Android settings
- **Wrong call state**: Check Developer tab → Call History for parsed data
- **Samsung device issues**: YatraMate includes special handling for Samsung's dialer notifications

### ESP32 Issues
- **Display blank**: Check GPIO connections and power supply
- **Touch not working**: Verify I2C connections and reset touch controller
- **Watchdog resets**: Check serial monitor for error messages

## Development

### Building from Source

1. **Android App**:
   ```bash
   cd YatraMate
   ./gradlew clean build
   ```

2. **ESP32 Firmware**:
   ```bash
   cd ardunio_files
   pio run -t upload
   ```

### Contributing

This is a personal project by tnvsai. Contributions are welcome! Please:
1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Submit a pull request

### License

Private project - All rights reserved by tnvsai

## Credits

**Developer**: tnvsai  
**Project**: YatraMate  
**Version**: 1.0

## Support

For issues, questions, or feature requests, please refer to the Developer tab in the app for detailed logs and debugging information.

---

*Built with ❤️ for safer and smarter driving*
