package com.example.smart.model

/**
 * Data class representing navigation information extracted from Google Maps notifications
 */
data class NavigationData(
    val direction: Direction? = null,
    val distance: String? = null,
    val maneuver: String? = null,
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
    UNKNOWN("UNKNOWN")
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
