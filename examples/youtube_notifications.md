# Example: Adding YouTube Notifications

## Step 1: Add to notification_types.json

Add this to the `notification_types` array:

```json
{
  "id": "youtube",
  "name": "YouTube",
  "priority": "medium",
  "enabled": true,
  "keywords": [
    "new video", "uploaded", "subscribed", "live", "premier",
    "comment", "like", "subscriber", "notification"
  ],
  "apps": [
    "com.google.android.youtube",
    "com.google.android.youtube.tv"
  ],
  "enabledApps": [],
  "disabledApps": [],
  "title_patterns": [
    "YouTube", "YT", "New video"
  ],
  "mcu_type": "youtube"
}
```

## Step 2: Add to mcu_formats.json

Add this to the `notification_types` section:

```json
"youtube": {
  "type": "youtube",
  "fields": ["channel", "action", "video_title"]
}
```

## Step 3: MCU Implementation

Your ESP32 can now handle YouTube notifications:

```cpp
else if (type == "youtube") {
    String channel = notification["channel"];
    String action = notification["action"];
    String videoTitle = notification["video_title"];
    
    // Show YouTube notification on display
    showYouTubeNotification(channel, action, videoTitle);
    
    // Play notification sound
    playYouTubeSound();
}
```

## Result

Now when your subscribed channels upload new videos, your ESP32 will receive:

```json
{
  "type": "youtube",
  "channel": "TechReview Channel",
  "action": "uploaded a new video",
  "video_title": "Best Smartphones 2024"
}
```

**Perfect for content creators!** Get instant notifications about your channel activity.
