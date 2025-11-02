# YatraMate Configuration Guide

## Overview

YatraMate supports a **generic notification classification system** that allows you to easily add support for any app notification by simply updating configuration files. No code changes required!

**Version**: 2.0 (Updated for YatraMate 2.0 with LVGL UI)

## How It Works

1. **Notification Detected** → App receives any notification
2. **Pattern Matching** → System compares notification against configured keywords, apps, and patterns
3. **Type Classification** → Notification is classified into a type (navigation, phone_call, message, etc.)
4. **Data Extraction** → Relevant data is extracted based on the notification type
5. **MCU Communication** → Data is transformed and sent to your ESP32/MCU
6. **MCU Logic** → Your MCU handles the notification based on its type

## Configuration Files

### 1. Notification Types (`notification_types.json`)

This file defines all supported notification types and their detection patterns.

**Location:** `app/src/main/assets/config/notification_types.json`

**Structure:**
```json
{
  "version": "1.1",
  "notification_types": [
    {
      "id": "navigation",
      "name": "Navigation",
      "priority": "high",
      "enabled": true,
      "keywords": ["turn left in", "turn right in", "head", "keep", "merge"],
      "apps": ["com.google.android.apps.maps", "com.waze"],
      "enabledApps": [],
      "disabledApps": [],
      "title_patterns": ["Google Maps", "Waze", "Navigation"],
      "mcu_type": "nav"
    }
  ],
  "user_types": [],
  "settings": {
    "enableAllByDefault": true,
    "sendToMCUWhenDisabled": false
  }
}
```

**Fields:**
- `id`: Unique identifier for the notification type
- `name`: Human-readable name
- `priority`: Processing priority ("high", "medium", "low")
- `enabled`: Enable/disable this notification type
- `keywords`: List of keywords to match in notification text (phrases work better than single words)
- `apps`: List of package names that generate this type
- `enabledApps`: Specific apps to enable (empty = all enabled)
- `disabledApps`: Specific apps to disable
- `title_patterns`: Patterns to match in notification titles
- `mcu_type`: Type identifier sent to MCU

### 2. MCU Formats (`mcu_formats.json`)

This file defines how data is formatted for your MCU.

**Location:** `app/src/main/assets/config/mcu_formats.json`

**Structure:**
```json
{
  "version": "1.0",
  "active_format": "esp32_json",
  "formats": {
    "esp32_json": {
      "name": "ESP32 JSON Format",
      "max_payload": 512,
      "notification_types": {
        "navigation": {
          "type": "nav",
          "fields": ["direction", "distance", "maneuver"]
        }
      }
    }
  }
}
```

## Adding New Notification Support

### Example: Adding Weather Notifications

**Step 1:** Add to `notification_types.json`:

```json
{
  "id": "weather",
  "name": "Weather",
  "priority": "low",
  "enabled": true,
  "keywords": ["weather", "temperature", "rain", "sunny", "cloudy", "°C", "°F"],
  "apps": ["com.weather.app", "com.accuweather.android"],
  "enabledApps": [],
  "disabledApps": [],
  "title_patterns": ["Weather", "AccuWeather"],
  "mcu_type": "weather"
}
```

**Step 2:** Add to `mcu_formats.json`:

```json
"weather": {
  "type": "weather",
  "fields": ["temperature", "condition", "location"]
}
```

**Step 3:** That's it! No code changes needed.

### Example: Adding Social Media Notifications

```json
{
  "id": "social",
  "name": "Social Media",
  "priority": "medium",
  "enabled": true,
  "keywords": ["like", "comment", "follow", "message", "notification"],
  "apps": ["com.facebook.katana", "com.instagram.android", "com.twitter.android"],
  "enabledApps": [],
  "disabledApps": [],
  "title_patterns": ["Facebook", "Instagram", "Twitter"],
  "mcu_type": "social"
}
```

### Example: Adding Email Notifications

```json
{
  "id": "email",
  "name": "Email",
  "priority": "medium",
  "enabled": true,
  "keywords": ["new email", "unread", "inbox", "gmail"],
  "apps": ["com.google.android.gm", "com.microsoft.office.outlook"],
  "enabledApps": [],
  "disabledApps": [],
  "title_patterns": ["Gmail", "Outlook", "Email"],
  "mcu_type": "email"
}
```

## Built-in Notification Types

### 1. Navigation (`navigation`)
- **Apps:** Google Maps, Waze
- **Keywords:** turn, left, right, miles, meters, exit, ramp
- **MCU Fields:** direction, distance, maneuver
- **MCU Type:** `nav`

### 2. Phone Call (`phone_call`)
- **Apps:** All dialer apps (Samsung, Xiaomi, OnePlus, etc.)
- **Keywords:** incoming call, calling, missed call
- **MCU Fields:** caller, state
- **MCU Type:** `call`

### 3. Messages (`message`)
- **Apps:** WhatsApp, Telegram, SMS
- **Keywords:** new message, whatsapp, telegram, sms
- **MCU Fields:** sender, preview
- **MCU Type:** `msg`

### 4. Battery (`battery`)
- **Apps:** System UI
- **Keywords:** battery, charging, low battery
- **MCU Fields:** level, status
- **MCU Type:** `battery`

### 5. Weather (`weather`)
- **Apps:** Weather apps
- **Keywords:** weather, temperature, rain, sunny
- **MCU Fields:** temperature, condition, location
- **MCU Type:** `weather`

### 6. Music (`music`)
- **Apps:** Spotify, YouTube Music, Apple Music, Amazon Music
- **Keywords:** now playing, playing, pause, resume, music player
- **MCU Fields:** title, artist, song
- **MCU Type:** `music`

## MCU Implementation

Your ESP32/MCU receives JSON data with the notification type via BLE:

**Note:** YatraMate currently uses LVGL-based screens for navigation and calls. You can extend this system to handle additional notification types.

### Basic Implementation (Non-LVGL)

```cpp
void handleNotification(JsonObject& notification) {
    String type = notification["type"];
    
    if (type == "nav") {
        // Navigation notification
        String direction = notification["direction"];
        String distance = notification["distance"];
        showNavigationArrow(direction, distance);
    }
    else if (type == "call") {
        // Phone call notification
        String caller = notification["caller"];
        String state = notification["state"];
        flashCallLED(caller, state);
    }
    else if (type == "msg") {
        // Message notification
        String sender = notification["sender"];
        briefNotificationLED();
    }
    else if (type == "weather") {
        // Weather notification
        String temperature = notification["temperature"];
        String condition = notification["condition"];
        showWeatherIcon(temperature, condition);
    }
    else if (type == "battery") {
        // Battery notification
        int level = notification["level"];
        String status = notification["status"];
        showBatteryStatus(level, status);
    }
}
```

### Adding LVGL Screens for New Types

To add LVGL screens for new notification types:

1. Create new screen files (e.g., `ui_weather_screen.cpp/h`)
2. Add screen to `ui_screens.h` enum
3. Update BLE callback to handle new type
4. Transition to new screen when notification received

See `PROJECT_STRUCTURE.md` for detailed LVGL architecture information.

## Configuration Tips

### 1. Keyword Selection
- Use specific keywords that uniquely identify the notification type
- Avoid generic words that might match multiple types
- Test with actual notifications to refine keywords

### 2. App Package Names
- Find package names using: `adb shell pm list packages | grep appname`
- Include all variants (free/paid versions, different regions)
- Test with different device brands

### 3. Priority Levels
- `high`: Critical notifications (calls, navigation)
- `medium`: Important notifications (messages)
- `low`: Informational notifications (weather, battery)

### 4. MCU Field Mapping
- Only include fields your MCU actually uses
- Keep field names short to save payload space
- Use consistent naming across notification types

## Testing Your Configuration

### 1. Enable Debug Logging
Check Android logs for classification results:
```bash
adb logcat | grep NotificationClassifier
```

### 2. Test with Real Notifications
- Send yourself test notifications
- Check if they're classified correctly
- Adjust keywords if needed

### 3. Verify MCU Reception
- Check ESP32 serial output
- Verify JSON format is correct
- Test MCU handling logic

## Advanced Configuration

### 1. User-Defined Types
Add custom notification types via the app UI:
```kotlin
val customType = NotificationType(
    id = "custom_app",
    name = "My Custom App",
    priority = "medium",
    keywords = listOf("custom", "keyword"),
    apps = listOf("com.myapp"),
    titlePatterns = listOf("My App"),
    mcuType = "custom"
)
ConfigManager.addUserNotificationType(customType)
```

### 2. Dynamic Configuration
Reload configurations without restarting:
```kotlin
ConfigManager.reloadConfigurations()
```

### 3. Import/Export
Share configurations between devices:
- Export: Creates ZIP with all configs
- Import: Validates and applies new configs

## Troubleshooting

### 1. Notifications Not Detected
- Check if package name is correct
- Verify keywords match notification text
- Enable debug logging

### 2. Wrong Classification
- Adjust keyword specificity
- Check priority levels
- Review title patterns

### 3. MCU Not Receiving Data
- Verify BLE connection
- Check payload size limits
- Validate JSON format

### 4. Performance Issues
- Reduce keyword lists
- Optimize priority levels
- Limit active notification types

## Best Practices

1. **Start Simple**: Begin with basic notification types
2. **Test Thoroughly**: Use real notifications for testing
3. **Iterate**: Refine keywords based on actual usage
4. **Document**: Keep track of custom configurations
5. **Backup**: Export configurations before major changes

## Example Configurations

### Complete Weather App Support
```json
{
  "id": "weather_detailed",
  "name": "Detailed Weather",
  "priority": "low",
  "enabled": true,
  "keywords": [
    "weather", "temperature", "rain", "sunny", "cloudy", 
    "storm", "snow", "wind", "humidity", "°C", "°F",
    "forecast", "alert", "warning"
  ],
  "apps": [
    "com.weather.app",
    "com.accuweather.android",
    "com.google.android.apps.weather",
    "com.weather.Weather"
  ],
  "enabledApps": [],
  "disabledApps": [],
  "title_patterns": [
    "Weather", "AccuWeather", "Weather Alert", "Forecast"
  ],
  "mcu_type": "weather"
}
```

### Complete Social Media Support
```json
{
  "id": "social_comprehensive",
  "name": "Social Media",
  "priority": "medium",
  "enabled": true,
  "keywords": [
    "like", "comment", "follow", "message", "notification",
    "friend request", "tagged", "mentioned", "shared",
    "new post", "story", "live", "reaction"
  ],
  "apps": [
    "com.facebook.katana",
    "com.instagram.android",
    "com.twitter.android",
    "com.linkedin.android",
    "com.snapchat.android",
    "com.whatsapp"
  ],
  "enabledApps": [],
  "disabledApps": [],
  "title_patterns": [
    "Facebook", "Instagram", "Twitter", "LinkedIn", 
    "Snapchat", "WhatsApp", "Social"
  ],
  "mcu_type": "social"
}
```

This configuration system makes YatraMate incredibly flexible - you can support any notification type by just updating JSON files!
