package com.tnvsai.yatramate.notification

import com.tnvsai.yatramate.model.CallState

/**
 * Debug information included in all notification entries for comprehensive debugging
 */
data class DebugInfo(
    val classificationConfidence: Int? = null,  // Confidence score from classifier
    val classificationMethod: String? = null,  // "pattern_match", "app_match", "keyword_match"
    val parsingErrors: List<String> = emptyList(),  // Any parsing errors encountered
    val rawNotificationExtras: Map<String, String> = emptyMap(),  // Raw notification extras
    val processingTimeMs: Long = 0,  // Time taken to process notification
    val filterReason: String? = null,  // Why notification was filtered (if applicable)
    val bleTransmissionAttempts: Int = 0,  // Number of BLE send attempts
    val bleTransmissionSuccess: Boolean = false,  // Whether BLE transmission succeeded
    val bleTransmissionErrors: List<String> = emptyList(),  // BLE transmission errors
    val transformerOutput: String? = null,  // Raw JSON sent to MCU
    val mcuReceived: Boolean = false,  // Whether MCU acknowledged receipt
    val stackTrace: String? = null  // Stack trace if error occurred
)

/**
 * Base sealed class for all notification history entries
 */
sealed class UnifiedNotificationEntry {
    abstract val id: String
    abstract val timestamp: Long
    abstract val timestampDisplay: String
    abstract val packageName: String
    abstract val sentToMCU: Boolean
    abstract val debugInfo: DebugInfo  // Debug information for all entries
    abstract val title: String?
    abstract val text: String?
    abstract val bigText: String?
}

/**
 * Navigation notification entry
 */
data class NavigationNotificationEntry(
    override val id: String,
    override val timestamp: Long,
    override val timestampDisplay: String,
    override val packageName: String,
    override val sentToMCU: Boolean,
    override val debugInfo: DebugInfo,
    override val title: String?,
    override val text: String?,
    override val bigText: String?,
    val isNavigation: Boolean,
    val isGoogleMaps: Boolean,
    val parsedData: String?,
    val direction: String?,
    val distance: String?,
    val maneuver: String?,
    val eta: String?,
    val parsingMethod: String? = null,  // "NotificationParser", "GenericParser", etc.
    val parserName: String? = null  // Which parser was used
) : UnifiedNotificationEntry()

/**
 * Phone call notification entry
 */
data class PhoneCallNotificationEntry(
    override val id: String,
    override val timestamp: Long,
    override val timestampDisplay: String,
    override val packageName: String,
    override val sentToMCU: Boolean,
    override val debugInfo: DebugInfo,
    override val title: String?,
    override val text: String?,
    override val bigText: String?,
    val callState: CallState,
    val callerName: String?,
    val callerNumber: String?,
    val duration: Int,
    val deviceProfile: String? = null,  // Which device profile was used
    val detectionPattern: String? = null,  // Which pattern matched (e.g., "PATTERN 4b")
    val wasOutgoing: Boolean = false,
    val wasAnswered: Boolean = false,
    val wasMissed: Boolean = false,
    val phoneNumberFromExtras: String? = null  // Phone number extracted from extras
) : UnifiedNotificationEntry()

/**
 * Message notification entry (WhatsApp, SMS, Telegram, etc.)
 */
data class MessageNotificationEntry(
    override val id: String,
    override val timestamp: Long,
    override val timestampDisplay: String,
    override val packageName: String,
    override val sentToMCU: Boolean,
    override val debugInfo: DebugInfo,
    override val title: String?,
    override val text: String?,
    override val bigText: String?,
    val sender: String?,
    val message: String?,
    val appName: String,
    val messageType: String? = null,  // "sms", "whatsapp", "telegram", etc.
    val hasAttachment: Boolean = false,
    val groupName: String? = null
) : UnifiedNotificationEntry()

/**
 * Generic notification entry for all other notification types
 */
data class GenericNotificationEntry(
    override val id: String,
    override val timestamp: Long,
    override val timestampDisplay: String,
    override val packageName: String,
    override val sentToMCU: Boolean,
    override val debugInfo: DebugInfo,
    override val title: String?,
    override val text: String?,
    override val bigText: String?,
    val notificationType: String,  // Classified type (e.g., "weather", "battery")
    val mcuType: String? = null,  // MCU type identifier
    val extractedData: Map<String, Any>,
    val classificationKeywordsMatched: List<String> = emptyList(),  // Which keywords matched
    val classificationTitlePatternsMatched: List<String> = emptyList(),  // Which title patterns matched
    val classificationAppMatched: Boolean = false,  // Whether package matched
    val classificationMethod: String = "unknown"  // How it was classified: "config", "fallback_pattern", "fallback_unknown"
) : UnifiedNotificationEntry()

