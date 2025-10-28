# YatraMate - Project Structure Documentation

## Overview

YatraMate consists of two main components:
1. **Android Application** - Monitors notifications and communicates via BLE
2. **ESP32 Firmware** - Displays navigation and call information on an embedded display

## Android Application Architecture

### Package Structure

```
com.tnvsai.yatramate
├── MainActivity.kt                   # Entry point, tabbed UI
├── ble/
│   ├── WorkingBLEService.kt         # BLE singleton service
│   └── BLEConstants.kt              # BLE UUIDs and constants
├── notification/
│   ├── NotificationListenerService.kt  # Notification interceptor
│   ├── NotificationParser.kt        # Google Maps parser
│   └── PhoneCallParser.kt           # Phone call parser
├── service/
│   └── NavigationService.kt         # Foreground service
├── model/
│   ├── NavigationData.kt            # Navigation data model
│   └── PhoneCallData.kt             # Phone call data model
├── permission/
│   └── PermissionHandler.kt         # Permission management
├── ui/
│   ├── screens/
│   │   └── HomeScreen.kt            # Home screen
│   ├── components/
│   │   ├── StatusCard.kt            # Status display component
│   │   ├── AnimatedStatusCard.kt    # Animated status card
│   │   ├── NavigationDataDisplay.kt # Navigation display component
│   │   ├── QuickActionPanel.kt      # Action button panel
│   │   └── ConnectionIndicator.kt   # BLE connection indicator
│   └── theme/
│       ├── Color.kt                 # Material3 colors
│       ├── Theme.kt                 # Material3 theme setup
│       └── Type.kt                  # Typography definitions
└── utils/
    └── ETACalculator.kt             # ETA parsing utilities
```

### Core Components

#### MainActivity.kt
- **Purpose**: Main UI controller, manages tabbed navigation
- **Responsibilities**:
  - Tab switching (Home/Developer)
  - Service lifecycle management
  - Permission requests
  - Dark mode toggle
  - BLE service initialization

#### WorkingBLEService.kt
- **Purpose**: Manages BLE communication with ESP32
- **Key Features**:
  - Singleton pattern for consistent state
  - Automatic scanning and connection
  - Data deduplication
  - StateFlow for real-time updates
  - Connection status tracking
  - Statistics logging

#### NotificationListenerService.kt
- **Purpose**: Intercepts Android notifications
- **Key Features**:
  - Listens to all system notifications
  - Routes Google Maps notifications to parser
  - Routes phone call notifications to parser
  - Maintains recent notification history
  - Call state tracking (incoming/outgoing/missed)
  - Deduplication for missed calls

#### NavigationService.kt
- **Purpose**: Foreground service for continuous operation
- **Key Features**:
  - Keeps app running in background
  - Provides persistent notification
  - Manages BLE service lifecycle
  - DAC (Disconnect, Accept Call) actions

### Data Flow

#### Navigation Data Flow
1. Google Maps sends notification
2. NotificationListenerService intercepts
3. NotificationParser extracts data (direction, distance, maneuver, ETA)
4. NavigationService receives parsed data
5. WorkingBLEService transmits via BLE
6. ESP32 receives and displays

#### Phone Call Data Flow
1. Phone app sends notification (incoming/outgoing/ended)
2. NotificationListenerService intercepts
3. PhoneCallParser determines call state and extracts caller info
4. NotificationListenerService tracks call lifecycle
5. WorkingBLEService transmits via BLE
6. ESP32 receives and displays with appropriate animation

### BLE Communication Protocol

#### BLE Configuration
- **Service UUID**: `12345678-1234-1234-1234-1234567890ab`
- **Characteristic UUID**: `abcd1234-5678-90ab-cdef-1234567890ab`
- **Connection Type**: Write without response

#### Data Format
All data is transmitted as JSON strings:

**Navigation JSON**:
```json
{
  "type": "NAVIGATION",
  "direction": "left|right|straight|uturn|...",
  "distance": 300,
  "maneuver": "Turn left at the light",
  "eta": "5 mins"
}
```

**Phone Call JSON**:
```json
{
  "type": "PHONE_CALL",
  "callState": "INCOMING|ONGOING|ENDED|MISSED",
  "callerName": "John Doe",
  "callerNumber": "+1234567890"
}
```

### State Management

#### BLE Connection States
- `Disconnected`: No connection to ESP32
- `Scanning`: Searching for ESP32
- `Connecting`: Establishing connection
- `Connected`: Ready for data transmission

#### Phone Call States
- `INCOMING`: Call received, not answered
- `ONGOING`: Call active (answered or outgoing)
- `ENDED`: Call completed normally
- `MISSED`: Call not answered

### UI Architecture

#### Material3 Theme System
- Custom color schemes (Light/Dark)
- Typography scale
- Component styling
- Dark mode support

#### Jetpack Compose
- Declarative UI
- State hoisting
- Composition over inheritance
- Reusable components

#### Key UI Components
- `HomeScreen`: Primary user interface
- `StatusCard`: Permission and connection status
- `NavigationDataDisplay`: Real-time navigation display
- `QuickActionPanel`: Service controls
- `ConnectionIndicator`: BLE status with animations

## ESP32 Firmware Architecture

### File Structure

```
smart_display_main.ino
├── Includes and Libraries
│   ├── Arduino_GFX_Library
│   ├── BLE Libraries (ESP32)
│   ├── ArduinoJson
│   └── Touch Driver (AXS5106L)
├── Configuration Constants
│   ├── BLE UUIDs
│   ├── Display pin definitions
│   ├── Touch pin definitions
│   └── Screen layout zones
├── Global Variables
│   ├── BLE state
│   ├── Display state
│   ├── Touch state
│   ├── Navigation data cache
│   └── Phone call state
├── Display Functions
│   ├── clearDisplay()
│   ├── displayNavigation()
│   ├── displayPhoneCall()
│   └── drawArrow()
├── Animation Functions
│   ├── drawCallingAnimation()
│   ├── drawMissedCallAnimation()
│   └── animateDirectionChange()
├── Touch Handlers
│   └── handlePhoneCallTouch()
├── BLE Callbacks
│   ├── MyCallbacks::onConnect()
│   ├── MyCallbacks::onDisconnect()
│   └── MyCallbacks::onWrite()
└── Main Loop
    ├── Touch processing
    ├── Missed call reminders
    └── Display refresh
```

### Display Layout

The 172x320 display is divided into zones:
- **Status bar (0-30px)**: Top status indicator
- **Speed zone (30-60px)**: Current speed (if available)
- **Arrow zone (60-160px)**: Direction arrow
- **Distance zone (160-210px)**: Distance to next maneuver
- **Maneuver zone (210-260px)**: Turn description
- **Bottom zone (260-320px)**: Additional info

### BLE Processing

#### onWrite Callback Flow
1. Receive JSON data from Android app
2. Parse JSON using ArduinoJson
3. Determine data type (navigation/phone)
4. Process navigation data:
   - Check for direction changes
   - Update display zones
   - Flash arrow on direction change
5. Process phone call data:
   - Handle incoming/outgoing/ended states
   - Display appropriate animation
   - Implement missed call reminders
6. Non-blocking execution (no delays in callback)

### Priority System

#### Display Priority
1. **Highest**: Missed call (persistent until dismissed)
2. **High**: Phone call (incoming/outgoing)
3. **Medium**: Navigation (with override logic)
4. **Low**: Idle state

#### Override Logic
- Navigation won't interrupt phone calls for 5 seconds minimum
- Direction changes can override phone calls after timeout
- Missed calls always take priority

### Touch Handling

- **Touch zones**: Full screen
- **Actions**:
  - Dismiss missed call reminders
  - No action for navigation or active calls
- **Debouncing**: 500ms minimum between touches

### Performance Optimizations

1. **Non-blocking BLE**: No delays in onWrite callback
2. **Efficient rendering**: Clear only changed zones
3. **Smart updates**: Cache data to prevent unnecessary redraws
4. **Watchdog safety**: Avoid long-running operations in callbacks

## Communication Protocol Details

### Android → ESP32

#### Navigation Data
- Transmitted when navigation updates
- Deduplicated to prevent redundant transmission
- Includes: direction, distance (meters), maneuver text, ETA

#### Phone Call Data
- Transmitted on call state changes
- Deduplicated based on caller and state
- Special handling for missed calls (single transmission)

### ESP32 → Android (Future)
- Currently one-way communication
- Potential for: button events, display feedback

## Error Handling

### Android App
- **BLE failures**: Automatic retry with exponential backoff
- **Permission denials**: User prompts with settings redirect
- **Notification parsing errors**: Graceful degradation with logging
- **Connection loss**: Auto-reconnect on service restart

### ESP32 Firmware
- **JSON parsing errors**: Validate required fields
- **BLE disconnection**: Re-advertise for reconnection
- **Display errors**: Safe fallback to text-only display
- **Watchdog resets**: Avoid long delays in callbacks

## Security and Privacy

### Data Handling
- All data processing occurs locally
- No network transmission
- No data storage on device
- BLE communication is local only

### Permissions
- Notification access: Required for parsing
- BLE permissions: Required for communication
- Location permissions: Required for BLE scanning (Android requirement)

## Performance Characteristics

### Android App
- **Memory**: ~50-80 MB
- **CPU**: <5% during normal operation
- **BLE bandwidth**: ~1 KB per navigation update
- **Background operation**: Battery efficient foreground service

### ESP32 Firmware
- **Memory**: ~20-30 KB RAM usage
- **CPU**: <10% during normal operation
- **Display refresh**: ~30 FPS animations
- **BLE latency**: <100ms typical

## Future Enhancements

### Potential Features
1. MCU-to-phone commands (answer/reject calls)
2. Speech recognition for hands-free operation
3. Integration with other navigation apps
4. Customizable display themes
5. Statistics and trip recording
6. Multiple display support

### Technical Improvements
1. OTA firmware updates
2. Encrypted BLE communication
3. Better error recovery
4. Performance profiling
5. Automated testing

## Build and Deployment

### Android App
- **Build tool**: Gradle 8.0+
- **Min SDK**: API 26 (Android 8.0)
- **Target SDK**: API 36
- **ProGuard**: Disabled for debugging

### ESP32 Firmware
- **Build tool**: PlatformIO
- **Framework**: Arduino
- **Board**: ESP32 (generic)
- **Flash size**: 4MB (SPIFFS disabled)

## Development Tools

### Android Studio Features
- Compose preview
- Layout inspector
- Profiler
- Logcat viewer

### PlatformIO Features
- Serial monitor
- Flash tools
- Debugging support

## Debugging

### Android Log Tags
- `NavigationService`: Service operations
- `WorkingBLEService`: BLE operations
- `NotificationListener`: Notification handling
- `PhoneCallParser`: Call parsing logic

### ESP32 Serial Output
- Responds with "OK" on successful data receive
- Prints debug info for errors
- Shows connection/disconnection events

---

**Last Updated**: October 2025  
**Maintainer**: tnvsai

