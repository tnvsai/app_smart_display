package com.tnvsai.yatramate.model

/**
 * Data class representing navigation information extracted from Google Maps notifications
 */
data class NavigationData(
    val type: EventType = EventType.NAVIGATION,
    val direction: Direction? = null,
    val distance: String? = null,
    val maneuver: String? = null,
    val icon: String? = null,  // Icon identifier for ESP32
    val eta: String? = null,   // Estimated time of arrival
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Enum representing possible navigation directions
 */
enum class Direction(val displayName: String) {
    LEFT("LEFT"),
    RIGHT("RIGHT"),
    STRAIGHT("STRAIGHT"),
    U_TURN("U_TURN"),
    SHARP_LEFT("SHARP_LEFT"),
    SHARP_RIGHT("SHARP_RIGHT"),
    SLIGHT_LEFT("SLIGHT_LEFT"),
    SLIGHT_RIGHT("SLIGHT_RIGHT"),
    ROUNDABOUT_LEFT("ROUNDABOUT_LEFT"),
    ROUNDABOUT_RIGHT("ROUNDABOUT_RIGHT"),
    ROUNDABOUT_STRAIGHT("ROUNDABOUT_STRAIGHT"),
    MERGE_LEFT("MERGE_LEFT"),
    MERGE_RIGHT("MERGE_RIGHT"),
    KEEP_LEFT("KEEP_LEFT"),
    KEEP_RIGHT("KEEP_RIGHT"),
    DESTINATION_REACHED("DESTINATION_REACHED"),
    WAYPOINT_REACHED("WAYPOINT_REACHED"),
    UNKNOWN("UNKNOWN")
}

/**
 * Enum representing different event types
 */
enum class EventType(val displayName: String) {
    NAVIGATION("NAVIGATION"),      // Regular navigation instructions
    ALERT("ALERT"),               // Speed cameras, accidents, road closures
    WAYPOINT("WAYPOINT"),         // Waypoint/destination events
    INFO("INFO")                  // General information (speed limit, etc.)
}

/**
 * Data class for BLE connection status
 */
data class BLEConnectionStatus(
    val isConnected: Boolean = false,
    val deviceName: String? = null,
    val deviceAddress: String? = null,
    val queuedMessages: Int = 0
)

/**
 * Data class for message queuing
 */
data class QueuedMessage(
    val navigationData: NavigationData,
    val timestamp: Long = System.currentTimeMillis()
)


