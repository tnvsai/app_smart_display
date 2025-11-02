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
smart_display_main/
├── smart_display_main.ino         # Main firmware entry point
├── lvgl_display_driver.h/cpp      # LVGL display & touch initialization
├── ui_screens.h/cpp                # Screen management & transitions
├── ui_theme.h/cpp                  # Global UI theme & styles
├── ui_welcome_screen.h/cpp         # Welcome/boot screen
├── ui_idle_screen.h/cpp            # Idle screen (BLE connected, no nav)
├── ui_navigation_screen.h/cpp      # Navigation display
├── ui_incoming_call_screen.h/cpp   # Incoming call screen
├── ui_outgoing_call_screen.h/cpp   # Outgoing call screen
├── ui_missed_call_screen.h/cpp     # Missed call screen
├── lv_conf.h                       # LVGL configuration
└── images/                         # UI assets

Arduino Libraries:
├── Arduino_GFX_Library             # ST7789 display driver
├── LVGL                            # Graphics framework
├── BLE Libraries (ESP32)           # Bluetooth Low Energy
├── ArduinoJson                     # JSON parsing
├── esp_lcd_touch_axs5106l         # Touch input driver
└── Wire.h                          # I2C communication
```

### Display Layout

The 172x320 display uses LVGL-based screens:

**Navigation Screen** (LVGL):
- **Status badge (5px)**: Connection status & compass
- **Arrow zone (40-160px)**: Large directional arrow with animations
- **Distance display (200px)**: Next maneuver distance
- **Maneuver text (270px)**: Turn instruction with auto-wrap
- **ETA banner (300px)**: Estimated arrival time

**Call Screens** (LVGL):
- **Welcome/Idle**: Status indicators and connection info
- **Incoming/Outgoing**: Avatar, caller info, pulsing animations
- **Missed Call**: Persistent notification with dismiss button

### BLE Processing

#### onWrite Callback Flow
1. Receive JSON data from Android app
2. Parse JSON using ArduinoJson
3. Determine data type (navigation/phone)
4. Process navigation data:
   - Check for direction changes
   - Call `ui_navigation_screen_update_*()` functions
   - Animate arrow changes with LVGL transitions
5. Process phone call data:
   - Handle incoming/outgoing/ended/missed states
   - Switch to appropriate call screen via `ui_show_screen()`
   - Trigger LVGL animations (pulse, fade, etc.)
6. Non-blocking execution (no delays in callback)
7. LVGL handles rendering and animations automatically

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

### LVGL Architecture

#### Core Components
- **Display Driver** (`lvgl_display_driver`): Bridges Arduino_GFX and LVGL
  - Flush callback: Draws LVGL framebuffers to hardware
  - Touch input: I2C touch event processing
  - Buffers: Double-buffered rendering for smooth animations
  
- **Screen Manager** (`ui_screens`): Central screen switching
  - State machine for screen transitions
  - Fade animations between screens
  - Screen lifecycle management
  
- **Theme System** (`ui_theme`): Global styling
  - RGB565 color definitions (optimized for daylight)
  - Typography scales and styles
  - Animation timing constants
  - Reusable UI styles

#### Individual Screens
Each screen module (`ui_*_screen`) provides:
- `create()`: Initialize UI elements
- `update_*()`: Update screen data (navigation, call info)
- `start/stop_animations()`: Control animations
- Event callbacks for user interactions

#### Touch Handling

- **Touch zones**: Full screen (LVGL handles coordinates)
- **Actions**:
  - Dismiss missed call reminders
  - No action for navigation or active calls
- **Debouncing**: Built into LVGL input processing

### Main Loop Processing

The ESP32 main loop (`loop()`) performs the following tasks every 5ms:

1. **LVGL Timer Handler** (`lv_timer_handler()`): Critical for UI updates
   - Must be called every 5-10ms for smooth animations
   - Handles rendering, animations, and input processing
   
2. **Touch Input Processing**:
   - Read I2C touch events (if enabled)
   - Convert coordinates to LVGL format
   - Process missed call dismiss button
   
3. **Missed Call Reminders**:
   - Periodic pulsing animation (every 2 seconds)
   - Resume if user missed alert
   
4. **BLE State Management**:
   - Update screen BLE status indicators
   - Auto-transition Welcome → Idle on connection
   - Restart advertising if disconnected
   
5. **Screen Auto-Navigation**:
   - Detect when navigation data received during phone call
   - Transition to navigation screen after call ends
   
6. **Heartbeat Logging** (every 1 second):
   - BLE connection status
   - Current active screen ID
   - Debug information

### Performance Optimizations

1. **Non-blocking BLE**: No delays in onWrite callback
2. **LVGL rendering**: Automatic partial display updates (dirty regions)
3. **Smart caching**: Only update UI when data actually changes
4. **Watchdog safety**: LVGL tasks run in main loop, not callbacks
5. **Memory efficient**: Shared styles, reusable screen objects
6. **Touch debouncing**: Built into LVGL touch processing
7. **Fast loop**: 5ms delay ensures responsive UI while conserving power

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
- **JSON parsing errors**: Validate required fields before UI updates
- **BLE disconnection**: Re-advertise for reconnection, show welcome screen
- **Display errors**: LVGL handles graphics errors gracefully
- **Memory errors**: Check LVGL heap before UI creation
- **Watchdog resets**: LVGL tasks in main loop, BLE callbacks non-blocking

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
- **Memory**: ~40-60 KB RAM (LVGL + application)
- **Flash**: ~800 KB (compiled firmware + LVGL)
- **CPU**: <15% during normal operation (LVGL rendering)
- **Display refresh**: 30-60 FPS animations (LVGL optimized)
- **BLE latency**: <100ms typical (notification to display)
- **LVGL heap**: ~20 KB allocated for UI

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
- **Graphics**: LVGL 8.3+ (embedded graphics library)
- **Display**: Arduino_GFX (ST7789 172x320 TFT)
- **Touch**: AXS5106L I2C touch controller

## Development Tools

### Android Studio Features
- Compose preview
- Layout inspector
- Profiler
- Logcat viewer

### PlatformIO Features
- Serial monitor (115200 baud)
- Flash tools
- Debugging support
- LVGL memory profiling
- Heap monitoring

## Debugging

### Android Log Tags
- `NavigationService`: Service operations
- `WorkingBLEService`: BLE operations
- `NotificationListener`: Notification handling
- `PhoneCallParser`: Call parsing logic

### ESP32 Serial Output
- BLE operations: Connection/disconnection events
- LVGL: Heap usage, rendering stats (if enabled)
- JSON parsing: Responds with "OK" on successful data receive
- Error handling: Debug info for parsing errors, memory issues
- Touch events: Coordinates and touch state (DEBUG_TOUCH flag)
- Screen transitions: Current screen state changes

---

**Last Updated**: January 2025  
**Maintainer**: tnvsai
**Version**: 2.0 (LVGL-based UI)

