package com.tnvsai.yatramate.config.models

/**
 * Data class representing a device profile for phone call notification detection
 */
data class DeviceProfile(
    val id: String,
    val name: String,
    val manufacturers: List<String>,
    val packages: List<String>,
    val patterns: CallPatterns,
    val multiNotification: Boolean
)

/**
 * Call state patterns for a device profile
 */
data class CallPatterns(
    val incoming: List<String>,
    val outgoing: List<String>,
    val missed: List<String>,
    val ended: List<String>
)

/**
 * Data class for the complete device profiles configuration
 */
data class DeviceProfilesConfig(
    val version: String,
    val profiles: List<DeviceProfile>,
    val activeProfile: String
)
