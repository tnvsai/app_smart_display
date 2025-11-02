# Example: Adding Instagram Notifications

## Step 1: Add to notification_types.json

Add this to the `notification_types` array:

```json
{
  "id": "instagram",
  "name": "Instagram",
  "priority": "medium",
  "enabled": true,
  "keywords": [
    "like", "comment", "follow", "story", "mention", 
    "tagged", "new post", "live", "reaction"
  ],
  "apps": [
    "com.instagram.android",
    "com.instagram.lite"
  ],
  "enabledApps": [],
  "disabledApps": [],
  "title_patterns": [
    "Instagram", "IG", "Insta"
  ],
  "mcu_type": "instagram"
}
```

## Step 2: Add to mcu_formats.json

Add this to the `notification_types` section:

```json
"instagram": {
  "type": "instagram",
  "fields": ["sender", "action", "preview"]
}
```

## Step 3: MCU Implementation

Your ESP32 can now handle Instagram notifications:

```cpp
else if (type == "instagram") {
    String sender = notification["sender"];
    String action = notification["action"];
    String preview = notification["preview"];
    
    // Show Instagram notification on display
    showInstagramNotification(sender, action, preview);
    
    // Flash LED for Instagram
    flashInstagramLED();
}
```

## Result

Now when someone likes your Instagram post, comments on your photo, or mentions you in a story, your ESP32 will receive:

```json
{
  "type": "instagram",
  "sender": "John Doe",
  "action": "liked your post",
  "preview": "Amazing sunset! 🌅"
}
```

**That's it!** No code changes needed - just configuration updates.
