package com.tnvsai.yatramate.notification

import android.util.Log
import com.tnvsai.yatramate.model.CallState
import com.tnvsai.yatramate.model.NavigationData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.*

/**
 * Single source of truth for all notification history
 * Manages unified notification history across all notification types
 */
object NotificationHistoryManager {
    private const val TAG = "NotificationHistoryManager"
    private val _history = MutableStateFlow<List<UnifiedNotificationEntry>>(emptyList())
    val history: StateFlow<List<UnifiedNotificationEntry>> = _history.asStateFlow()
    
    private const val MAX_HISTORY_SIZE = 50
    
    /**
     * Add a navigation notification entry
     */
    fun addNavigationEntry(
        info: NotificationInfo,
        navigationData: NavigationData? = null,
        sentToMCU: Boolean = false,
        debugInfo: DebugInfo = DebugInfo(),
        parsingMethod: String? = null,
        parserName: String? = null
    ) {
        val parsedData = navigationData ?: extractNavigationDataFromString(info.parsedData)
        
        val entry = NavigationNotificationEntry(
            id = UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis(),
            timestampDisplay = info.timestamp,
            packageName = info.packageName,
            sentToMCU = sentToMCU,
            debugInfo = debugInfo,
            title = info.title,
            text = info.text,
            bigText = info.bigText,
            isNavigation = info.isNavigation,
            isGoogleMaps = info.isGoogleMaps,
            parsedData = info.parsedData,
            direction = parsedData?.direction?.name,
            distance = parsedData?.distance,
            maneuver = parsedData?.maneuver,
            eta = parsedData?.eta,
            parsingMethod = parsingMethod,
            parserName = parserName
        )
        addEntry(entry)
    }
    
    /**
     * Add a phone call notification entry
     */
    fun addPhoneCallEntry(
        log: NotificationListenerService.Companion.PhoneDebugLog,
        phoneCallData: com.tnvsai.yatramate.model.PhoneCallData? = null,
        sentToMCU: Boolean = false,
        debugInfo: DebugInfo = DebugInfo(),
        deviceProfile: String? = null,
        detectionPattern: String? = null,
        wasOutgoing: Boolean = false,
        wasAnswered: Boolean = false,
        wasMissed: Boolean = false,
        phoneNumberFromExtras: String? = null
    ) {
        val callState = phoneCallData?.callState ?: parseCallState(log.state)
        
        val entry = PhoneCallNotificationEntry(
            id = UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis(),
            timestampDisplay = log.timestamp,
            packageName = getPackageNameForCallState(log.state),
            sentToMCU = sentToMCU || log.sentToMCU,
            debugInfo = debugInfo,
            title = log.title,
            text = log.text,
            bigText = null,
            callState = callState,
            callerName = phoneCallData?.callerName ?: log.callerName,
            callerNumber = phoneCallData?.callerNumber ?: log.callerNumber,
            duration = phoneCallData?.duration ?: 0,
            deviceProfile = deviceProfile,
            detectionPattern = detectionPattern,
            wasOutgoing = wasOutgoing,
            wasAnswered = wasAnswered,
            wasMissed = wasMissed,
            phoneNumberFromExtras = phoneNumberFromExtras
        )
        addEntry(entry)
    }
    
    /**
     * Add a message notification entry (WhatsApp, SMS, etc.)
     */
    fun addMessageEntry(
        type: String,
        packageName: String,
        title: String?,
        text: String?,
        bigText: String?,
        sender: String?,
        message: String?,
        appName: String,
        sentToMCU: Boolean = false,
        debugInfo: DebugInfo = DebugInfo(),
        messageType: String? = null,
        hasAttachment: Boolean = false,
        groupName: String? = null
    ) {
        val entry = MessageNotificationEntry(
            id = UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis(),
            timestampDisplay = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date()),
            packageName = packageName,
            sentToMCU = sentToMCU,
            debugInfo = debugInfo,
            title = title,
            text = text,
            bigText = bigText,
            sender = sender,
            message = message,
            appName = appName,
            messageType = messageType,
            hasAttachment = hasAttachment,
            groupName = groupName
        )
        addEntry(entry)
    }
    
    /**
     * Add a generic notification entry
     */
    fun addGenericEntry(
        type: String,
        packageName: String,
        title: String?,
        text: String?,
        bigText: String?,
        extractedData: Map<String, Any>,
        sentToMCU: Boolean = false,
        debugInfo: DebugInfo = DebugInfo(),
        mcuType: String? = null,
        classificationKeywordsMatched: List<String> = emptyList(),
        classificationTitlePatternsMatched: List<String> = emptyList(),
        classificationAppMatched: Boolean = false,
        classificationMethod: String = "unknown"
    ) {
        val entry = GenericNotificationEntry(
            id = UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis(),
            timestampDisplay = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date()),
            packageName = packageName,
            sentToMCU = sentToMCU,
            debugInfo = debugInfo,
            title = title,
            text = text,
            bigText = bigText,
            notificationType = type,
            mcuType = mcuType,
            extractedData = extractedData,
            classificationKeywordsMatched = classificationKeywordsMatched,
            classificationTitlePatternsMatched = classificationTitlePatternsMatched,
            classificationAppMatched = classificationAppMatched,
            classificationMethod = classificationMethod
        )
        addEntry(entry)
    }
    
    private fun addEntry(entry: UnifiedNotificationEntry) {
        val current = _history.value.toMutableList()
        current.add(0, entry) // Add to beginning
        _history.value = current.take(MAX_HISTORY_SIZE)
        Log.d(TAG, "Added ${entry.javaClass.simpleName} to history. Total: ${_history.value.size}")
    }
    
    fun clearHistory() {
        _history.value = emptyList()
        Log.d(TAG, "History cleared")
    }
    
    fun getHistoryByType(type: String): List<UnifiedNotificationEntry> {
        return when (type.lowercase()) {
            "navigation", "nav" -> _history.value.filterIsInstance<NavigationNotificationEntry>()
            "phone_call", "call", "calls" -> _history.value.filterIsInstance<PhoneCallNotificationEntry>()
            "message", "messages", "whatsapp" -> _history.value.filterIsInstance<MessageNotificationEntry>()
            "generic", "other" -> _history.value.filterIsInstance<GenericNotificationEntry>()
            else -> _history.value
        }
    }
    
    // Helper functions
    
    private fun extractNavigationDataFromString(parsedDataString: String?): NavigationData? {
        if (parsedDataString == null) return null
        
        // Try to parse from string format like "NavigationData(direction=LEFT, distance=100 m, ...)"
        try {
            // Simple parsing - could be improved
            val directionMatch = Regex("direction=([A-Z_]+)").find(parsedDataString)
            val distanceMatch = Regex("distance=([^,}]+)").find(parsedDataString)
            val maneuverMatch = Regex("maneuver=([^,}]+)").find(parsedDataString)
            val etaMatch = Regex("eta=([^,}]+)").find(parsedDataString)
            
            return NavigationData(
                direction = directionMatch?.groupValues?.get(1)?.let { 
                    try { 
                        com.tnvsai.yatramate.model.Direction.valueOf(it) 
                    } catch (e: Exception) { null }
                },
                distance = distanceMatch?.groupValues?.get(1)?.trim(),
                maneuver = maneuverMatch?.groupValues?.get(1)?.trim(),
                eta = etaMatch?.groupValues?.get(1)?.trim()
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse NavigationData from string: $parsedDataString", e)
            return null
        }
    }
    
    private fun parseCallState(stateString: String): CallState {
        return try {
            CallState.valueOf(stateString)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse call state: $stateString", e)
            CallState.INCOMING  // Default fallback
        }
    }
    
    private fun getPackageNameForCallState(state: String): String {
        // Most calls come from dialer apps
        return when {
            state.contains("INCOMING", ignoreCase = true) -> "com.android.dialer"
            state.contains("MISSED", ignoreCase = true) -> "com.android.dialer"
            else -> "com.android.dialer"
        }
    }
}

