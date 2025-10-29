package com.tnvsai.yatramate.config.models

/**
 * Data class representing notification type configuration for MCU
 */
data class NotificationTypeConfig(
    val type: String,
    val fields: List<String>
)

/**
 * Data class representing an MCU data format configuration
 */
data class MCUFormat(
    val name: String,
    val maxPayload: Int,
    val directionMapping: Map<String, String>,
    val callStateMapping: Map<String, String>,
    val notificationTypes: Map<String, NotificationTypeConfig> = emptyMap()
)

/**
 * Data class for the complete MCU formats configuration
 */
data class MCUFormatsConfig(
    val version: String,
    val activeFormat: String,
    val formats: Map<String, MCUFormat>
)
