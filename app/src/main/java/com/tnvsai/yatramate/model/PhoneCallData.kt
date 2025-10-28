package com.tnvsai.yatramate.model

/**
 * Data class representing phone call information for display on MCU
 */
data class PhoneCallData(
    val callerName: String?,
    val callerNumber: String,
    val callState: CallState,
    val timestamp: Long = System.currentTimeMillis(),
    val duration: Int = 0 // Duration in seconds for ongoing calls
)

/**
 * Enum representing possible call states
 */
enum class CallState(val displayName: String) {
    INCOMING("INCOMING"),    // Ringing
    ONGOING("ONGOING"),      // Active call
    MISSED("MISSED"),        // Missed call
    ENDED("ENDED")           // Call ended
}


