package com.tnvsai.yatramate.notification

import android.app.Notification
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.tnvsai.yatramate.ble.WorkingBLEService
import com.tnvsai.yatramate.model.PhoneCallData
import com.tnvsai.yatramate.model.CallState
import com.tnvsai.yatramate.model.NavigationData
import com.tnvsai.yatramate.notification.PhoneCallParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Pattern

/**
 * Data class to store notification information for debugging
 */
data class NotificationInfo(
    val timestamp: String,
    val packageName: String,
    val title: String?,
    val text: String?,
    val bigText: String?,
    val isGoogleMaps: Boolean,
    val isNavigation: Boolean,
    val parsedData: String?
)

/**
 * Service to listen for Google Maps navigation notifications
 */
class NotificationListenerService : NotificationListenerService() {
    
    companion object {
        private const val TAG = "NotificationListener"
        private var instance: NotificationListenerService? = null
        var bleService: WorkingBLEService? = null
            private set
        
        // Store recent notifications for debugging
        private val recentNotifications = mutableListOf<NotificationInfo>()
        private const val MAX_RECENT_NOTIFICATIONS = 20
        
        // Track sent missed calls with timestamps to avoid duplicates within a time window
        private val sentMissedCallsTime = mutableMapOf<String, Long>()
        private const val MISSED_CALL_DEDUP_WINDOW_MS = 30000L // 30 seconds
        
        // Track if incoming calls were answered (to determine if ENDED means MISSED)
        private val incomingCalls = mutableMapOf<String, Boolean>()  // Map of caller number to wasAnswered
        
        // Track outgoing calls to distinguish from incoming calls
        private val outgoingCalls = mutableSetOf<String>()  // Set of caller keys for outgoing calls
        
        // Track calls that were initially INCOMING but became ONGOING (likely outgoing misclassified)
        private val correctedOutgoingCalls = mutableSetOf<String>()  // Set of caller keys for corrected outgoing calls
        
        // Phone call debug data
        data class PhoneDebugLog(
            val timestamp: String,
            val state: String,
            val title: String?,
            val text: String?,
            val callerName: String?,
            val callerNumber: String?,
            val sentToMCU: Boolean
        )
        
        private val phoneDebugLogs = mutableListOf<PhoneDebugLog>()
        private const val MAX_PHONE_DEBUG_LOGS = 20
        
        var phoneDebugLogCallback: ((PhoneDebugLog) -> Unit)? = null
            private set
        
        // Debug function to clear tracking (for testing)
        fun clearMissedCallTracking() {
            sentMissedCallsTime.clear()
            incomingCalls.clear()
            outgoingCalls.clear()
            correctedOutgoingCalls.clear()
            Log.i(TAG, "🧹 Cleared missed call tracking")
        }
        
        fun setPhoneDebugLogCallback(callback: (PhoneDebugLog) -> Unit) {
            phoneDebugLogCallback = callback
        }
        
        fun getPhoneDebugLogs(): List<PhoneDebugLog> = phoneDebugLogs.toList()
        
        fun clearPhoneDebugLogs() {
            phoneDebugLogs.clear()
        }
        
        // Debug log callback
        var debugLogCallback: ((String) -> Unit)? = null
            private set
        
        fun setBLEService(bleService: WorkingBLEService?) {
            this.bleService = bleService
        }
        
        fun setDebugLogCallback(callback: (String) -> Unit) {
            debugLogCallback = callback
        }
        
        fun getInstance(): NotificationListenerService? = instance
        
        fun getRecentNotifications(): List<NotificationInfo> = recentNotifications.toList()
        
        fun clearRecentNotifications() {
            recentNotifications.clear()
        }
        
        /**
         * Check if we have permission to access notifications (static method)
         */
        fun hasNotificationAccess(): Boolean {
            return try {
                instance?.activeNotifications != null
            } catch (e: SecurityException) {
                false
            }
        }
        
        /**
         * Get all active notifications (static method)
         */
        fun getAllActiveNotifications(): List<StatusBarNotification> {
            return try {
                instance?.activeNotifications?.toList() ?: emptyList()
            } catch (e: SecurityException) {
                emptyList()
            }
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.i(TAG, "NotificationListenerService created")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        instance = null
        Log.i(TAG, "NotificationListenerService destroyed")
    }
    
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)

        val packageName = sbn.packageName
        val notification = sbn.notification
        val title = notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        val bigText = notification.extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()

        // Log ALL notifications for debugging
        Log.i(TAG, "🔔🔔🔔 === NOTIFICATION RECEIVED === 🔔🔔🔔")
        Log.i(TAG, "📦 Package: $packageName")
        Log.i(TAG, "📝 Title: $title")
        Log.i(TAG, "💬 Text: $text")
        Log.i(TAG, "📄 BigText: $bigText")
        Log.i(TAG, "BLE Service Available: ${bleService != null}")
        
        // Enhanced debugging for phone notifications
        if (PhoneCallParser.isPhoneCallNotification(packageName, title, text)) {
            Log.i(TAG, "🔔 PHONE NOTIFICATION DETECTED!")
            Log.i(TAG, "Package matches phone app: $packageName")
        }

        // Route notification based on package name
        when (packageName) {
            "com.google.android.apps.maps" -> handleGoogleMapsNotification(sbn, title, text, bigText)
            
            // Universal phone package routing - All Indian device brands
            "com.android.server.telecom", 
            "com.android.dialer",
            "com.android.incallui",
            "com.android.phone",
            "com.android.telecom",
            
            // Samsung (India's #1 brand - 30% market share)
            "com.samsung.android.dialer",
            "com.samsung.android.incallui",
            "com.samsung.android.phone",
            
            // Xiaomi/Redmi (India's #2 brand - 20% market share)
            "com.miui.phone",
            "com.miui.incallui",
            "com.miui.dialer",
            
            // OnePlus (8% market share)
            "com.oneplus.incallui",
            "com.oneplus.dialer",
            "com.oneplus.phone",
            
            // Realme (12% market share)
            "com.coloros.incallui",
            "com.coloros.dialer",
            "com.coloros.phone",
            
            // Vivo (10% market share)
            "com.vivo.incallui",
            "com.vivo.dialer",
            "com.vivo.phone",
            
            // Oppo (8% market share)
            "com.oppo.incallui",
            "com.oppo.dialer",
            "com.oppo.phone",
            
            // Google Pixel (5% market share)
            "com.google.android.dialer",
            "com.google.android.incallui",
            
            // Motorola (3% market share)
            "com.motorola.dialer",
            "com.motorola.incallui",
            "com.motorola.phone",
            
            // Huawei/Honor
            "com.huawei.contacts",
            "com.huawei.incallui",
            "com.huawei.dialer",
            "com.hihonor.incallui",
            
            // Nokia
            "com.nokia.dialer",
            "com.nokia.incallui",
            
            // Generic fallbacks
            "com.android.contacts",
            "com.android.calllog" -> handlePhoneNotification(sbn, title, text, bigText)
            
            else -> {
                // Check if it's a phone notification by content analysis
                if (PhoneCallParser.isPhoneCallNotification(packageName, title, text)) {
                    Log.i(TAG, "Detected phone notification by content analysis: $packageName")
                    handlePhoneNotification(sbn, title, text, bigText)
                } else {
                    Log.d(TAG, "Notification from other app: $packageName")
                }
            }
        }
    }

    private fun handleGoogleMapsNotification(sbn: StatusBarNotification, title: String?, text: String?, bigText: String?) {
        // Combine all text sources
        val fullText = buildString {
            title?.let { append("$it ") }
            text?.let { append("$it ") }
            bigText?.let { append("$it ") }
        }.trim()

        val isNavigation = NotificationParser.isNavigationNotification(fullText)

        // Parse navigation data if it's a navigation notification
        var parsedData: String? = null
        if (isNavigation) {
            val navigationData = NotificationParser.parseNotification(fullText)
            parsedData = navigationData?.toString()

            if (navigationData != null) {
                Log.i(TAG, "✅ PARSED NAVIGATION DATA: $navigationData")
                
                // Don't log here - will log in WorkingBLEService after sending

                // Send to BLE service
                bleService?.let { service ->
                    Log.i(TAG, "Sending to BLE service...")
                    CoroutineScope(Dispatchers.IO).launch {
                        service.sendNavigationData(navigationData)
                    }
                } ?: Log.e(TAG, "❌ BLE SERVICE NOT AVAILABLE!")
            }
        }

        // Store notification for debugging
        storeNotificationForDebugging(sbn.packageName, title, text, bigText, true, isNavigation, parsedData)
    }

    private fun handlePhoneNotification(sbn: StatusBarNotification, title: String?, text: String?, bigText: String?) {
        Log.i(TAG, "=== PHONE NOTIFICATION RECEIVED ===")
        Log.i(TAG, "Package: ${sbn.packageName}")
        Log.i(TAG, "Title: $title")
        Log.i(TAG, "Text: $text")
        Log.i(TAG, "BigText: $bigText")
        
        // Extract phone number from notification extras
        val extras = sbn.notification.extras
        var phoneNumber: String? = null
        
        // Try to get phone number from various common keys
        extras?.let {
            phoneNumber = it.getCharSequence("android.phoneNumber")?.toString()
                ?: it.getCharSequence("android.phone_number")?.toString()
                ?: it.getCharSequence("phone_number")?.toString()
                ?: it.getCharSequence("number")?.toString()
                ?: it.getString("android.phoneNumber")
                ?: it.getString("phone_number")
                ?: it.getString("number")
            
            if (phoneNumber != null) {
                Log.i(TAG, "📞 Phone number found in extras: $phoneNumber")
            } else {
                Log.i(TAG, "🔍 No phone number in extras, dumping all key-value pairs:")
                it.keySet().forEach { key ->
                    try {
                        val value = it.get(key)
                        Log.i(TAG, "  Key='$key', Value='$value' (type: ${value?.javaClass?.simpleName})")
                    } catch (e: Exception) {
                        Log.i(TAG, "  Key='$key', Error reading value: ${e.message}")
                    }
                }
            }
        }
        
        // If phone number not found in extras, try extracting from text
        if (phoneNumber == null || phoneNumber.isEmpty()) {
            phoneNumber = extractPhoneNumber(text) ?: extractPhoneNumber(bigText)
            if (phoneNumber != null) {
                Log.i(TAG, "📞 Phone number extracted from text: $phoneNumber")
            }
        }
        
        // Check if it's Samsung dialer specifically
        if (sbn.packageName.contains("samsung")) {
            Log.i(TAG, "🔍 SAMSUNG DIALER DETECTED - Enhanced debugging")
            Log.i(TAG, "Notification ID: ${sbn.id}")
            Log.i(TAG, "Post time: ${sbn.postTime}")
            Log.i(TAG, "Full notification extras: ${sbn.notification.extras}")
            Log.i(TAG, "All extras keys: ${extras?.keySet()}")
        }
        
        // Parse phone call data
        val phoneCallData = PhoneCallParser.parsePhoneNotification(
            sbn.packageName,
            title,
            text,
            bigText,
            phoneNumber
        )
        
        if (phoneCallData != null) {
            Log.i(TAG, "✅ PARSED PHONE CALL DATA: $phoneCallData")
            
            // Add phone call debug log
            val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            val debugLog = PhoneDebugLog(
                timestamp = timestamp,
                state = phoneCallData.callState.toString(),
                title = title,
                text = text,
                callerName = phoneCallData.callerName,
                callerNumber = phoneCallData.callerNumber,
                sentToMCU = false // Will be updated when actually sent
            )
            
            // Add to phone debug logs
            phoneDebugLogs.add(0, debugLog)
            if (phoneDebugLogs.size > MAX_PHONE_DEBUG_LOGS) {
                phoneDebugLogs.removeLast()
            }
            
            // Notify callback
            phoneDebugLogCallback?.invoke(debugLog)
            
            // Track call state transitions
            val callerKey = "${phoneCallData.callerName}:${phoneCallData.callerNumber}"
            
            when (phoneCallData.callState) {
                CallState.INCOMING -> {
                    // Mark this incoming call as not yet answered
                    incomingCalls[callerKey] = false
                    outgoingCalls.remove(callerKey)  // Remove from outgoing if it was there
                    Log.i(TAG, "📞 INCOMING call from $callerKey - marking as not answered")
                    
                    // Send INCOMING state to MCU
                    sendPhoneCallData(phoneCallData)
                }
                CallState.ONGOING -> {
                    // Check if this is an outgoing call (never had INCOMING notification)
                    val wasInitiallyIncoming = incomingCalls.containsKey(callerKey)
                    val isOutgoing = !wasInitiallyIncoming
                    
                    Log.i(TAG, "📞 ONGOING call - isOutgoing: $isOutgoing")
                    Log.i(TAG, "   wasInitiallyIncoming: $wasInitiallyIncoming")
                    Log.i(TAG, "   incomingCalls.containsKey('$callerKey'): $wasInitiallyIncoming")
                    Log.i(TAG, "   incomingCalls: $incomingCalls")
                    
                    if (isOutgoing) {
                        // This is a true outgoing call from the start
                        outgoingCalls.add(callerKey)
                        Log.i(TAG, "📞 OUTGOING call to $callerKey - added to outgoingCalls")
                        Log.i(TAG, "   outgoingCalls now: $outgoingCalls")
                    } else if (wasInitiallyIncoming && !incomingCalls[callerKey]!!) {
                        // This was initially INCOMING but never answered, now ONGOING
                        // This is likely a misclassified outgoing call!
                        correctedOutgoingCalls.add(callerKey)
                        outgoingCalls.add(callerKey)
                        Log.i(TAG, "🔄 CORRECTED OUTGOING: Call was misclassified as INCOMING, now correctly identified as OUTGOING")
                        Log.i(TAG, "   added to correctedOutgoingCalls: $correctedOutgoingCalls")
                        Log.i(TAG, "   added to outgoingCalls: $outgoingCalls")
                        
                        // Clean up incoming tracking
                        incomingCalls.remove(callerKey)
                    } else {
                        // Call was answered
                        incomingCalls[callerKey] = true
                        outgoingCalls.remove(callerKey)
                        correctedOutgoingCalls.remove(callerKey)  // Remove from corrected if it was there
                        Log.i(TAG, "✅ Call answered - marking $callerKey as answered")
                        
                        // Clear any pending missed call tracking for this caller
                        val missedCallKeyToRemove = "$callerKey"
                        if (sentMissedCallsTime.containsKey(missedCallKeyToRemove)) {
                            sentMissedCallsTime.remove(missedCallKeyToRemove)
                            Log.i(TAG, "🗑️ Cleared missed call tracking for answered call: $missedCallKeyToRemove")
                        }
                    }
                    
                    // Send ONGOING state to MCU
                    sendPhoneCallData(phoneCallData)
                }
                CallState.ENDED -> {
                    // Check if this was an outgoing call
                    val wasOutgoing = outgoingCalls.contains(callerKey)
                    val wasAnswered = incomingCalls[callerKey] ?: false
                    
                    Log.i(TAG, "📴 Call ended - callerKey='$callerKey'")
                    Log.i(TAG, "   wasOutgoing: $wasOutgoing (outgoingCalls: $outgoingCalls)")
                    Log.i(TAG, "   wasAnswered: $wasAnswered (incomingCalls: $incomingCalls)")
                    
                    if (wasOutgoing) {
                        // This was an outgoing call that ended normally
                        Log.i(TAG, "📞 Outgoing call ended normally - NO ACTION NEEDED")
                        // Clean up tracking
                        outgoingCalls.remove(callerKey)
                    } else if (!wasAnswered) {
                        // This was an unanswered incoming call - send MISSED state
                        Log.i(TAG, "❌ Detected MISSED incoming call - caller never answered")
                        val missedCallData = PhoneCallData(
                            callerName = phoneCallData.callerName,
                            callerNumber = phoneCallData.callerNumber,
                            callState = CallState.MISSED,
                            duration = 0
                        )
                        processMissedCall(missedCallData)
                        // Clean up tracking
                        incomingCalls.remove(callerKey)
                    } else {
                        // This was an answered call that ended normally
                        Log.i(TAG, "✅ Answered call ended normally - NO ACTION NEEDED")
                        // Clean up tracking
                        incomingCalls.remove(callerKey)
                    }
                }
                CallState.MISSED -> {
                    // Direct MISSED notification from system
                    Log.i(TAG, "❌ Direct MISSED call notification received")
                    processMissedCall(phoneCallData)
                }
            }
            
            // Store notification for debugging
            storeNotificationForDebugging(
                sbn.packageName, 
                title, 
                text, 
                bigText, 
                false, 
                true, 
                phoneCallData.toString()
            )
        } else {
            Log.w(TAG, "Failed to parse phone call data")
            // Store notification for debugging
            storeNotificationForDebugging(sbn.packageName, title, text, bigText, false, false, null)
        }
    }

    private fun storeNotificationForDebugging(
        packageName: String,
        title: String?,
        text: String?,
        bigText: String?,
        isGoogleMaps: Boolean,
        isNavigation: Boolean,
        parsedData: String?
    ) {
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val notificationInfo = NotificationInfo(
            timestamp = timestamp,
            packageName = packageName,
            title = title,
            text = text,
            bigText = bigText,
            isGoogleMaps = isGoogleMaps,
            isNavigation = isNavigation,
            parsedData = parsedData
        )

        // Add to recent notifications list
        recentNotifications.add(0, notificationInfo) // Add to beginning
        if (recentNotifications.size > MAX_RECENT_NOTIFICATIONS) {
            recentNotifications.removeAt(recentNotifications.size - 1) // Remove oldest
        }

        Log.i(TAG, "Stored notification for debugging. Total stored: ${recentNotifications.size}")
    }
    
    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        super.onNotificationRemoved(sbn)
        Log.d(TAG, "Notification removed: ${sbn.packageName}")
        
        // Check if this is a phone notification that was removed
        if (PhoneCallParser.isPhoneCallNotification(sbn.packageName, null, null)) {
            Log.i(TAG, "🔔 Phone notification removed - checking if call was missed...")
            
            val notification = sbn.notification
            val title = notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            val text = notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            val bigText = notification.extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            
            // Extract phone number
            val extras = notification.extras
            var phoneNumber: String? = null
            extras?.let {
                phoneNumber = it.getCharSequence("android.phoneNumber")?.toString()
                    ?: it.getCharSequence("android.phone_number")?.toString()
                    ?: it.getCharSequence("phone_number")?.toString()
                    ?: it.getCharSequence("number")?.toString()
                    ?: it.getString("android.phoneNumber")
                    ?: it.getString("phone_number")
                    ?: it.getString("number")
            }
            
            if (phoneNumber == null || phoneNumber.isEmpty()) {
                phoneNumber = extractPhoneNumber(text) ?: extractPhoneNumber(bigText)
            }
            
            // Parse to get caller info
            val phoneCallData = PhoneCallParser.parsePhoneNotification(
                sbn.packageName,
                title,
                text,
                bigText,
                phoneNumber
            )
            
            Log.i(TAG, "Notification removed - title='$title', text='$text', phoneCallData=$phoneCallData")
            
            if (phoneCallData != null) {
                val callerKey = "${phoneCallData.callerName}:${phoneCallData.callerNumber}"
                
                // Check if this was an outgoing call
                val wasOutgoing = outgoingCalls.contains(callerKey)
                val wasCorrected = correctedOutgoingCalls.contains(callerKey)
                val wasAnswered = incomingCalls[callerKey] ?: false
                
                Log.i(TAG, "Call removed - wasOutgoing: $wasOutgoing, wasCorrected: $wasCorrected, wasAnswered: $wasAnswered")
                Log.i(TAG, "   outgoingCalls: $outgoingCalls")
                Log.i(TAG, "   correctedOutgoingCalls: $correctedOutgoingCalls")
                Log.i(TAG, "   incomingCalls: $incomingCalls")
                Log.i(TAG, "   callerKey: '$callerKey'")
                
                if (wasOutgoing) {
                    // This was an outgoing call that ended - send ENDED state to MCU
                    if (wasCorrected) {
                        Log.i(TAG, "📞 Corrected outgoing call ended - sending ENDED state to MCU")
                    } else {
                        Log.i(TAG, "📞 Outgoing call ended - sending ENDED state to MCU")
                    }
                    val endedCallData = PhoneCallData(
                        callerName = phoneCallData.callerName,
                        callerNumber = phoneCallData.callerNumber,
                        callState = CallState.ENDED,
                        duration = 0
                    )
                    sendPhoneCallData(endedCallData)
                    outgoingCalls.remove(callerKey)
                    correctedOutgoingCalls.remove(callerKey)  // Also remove from corrected if it was there
                } else if (!wasAnswered) {
                    // This was a missed incoming call (caller hung up before answer)
                    Log.i(TAG, "❌ Detected MISSED incoming call via notification removal - caller hung up")
                    val missedCallData = PhoneCallData(
                        callerName = phoneCallData.callerName,
                        callerNumber = phoneCallData.callerNumber,
                        callState = CallState.MISSED,
                        duration = 0
                    )
                    processMissedCall(missedCallData)
                    
                    // Clean up tracking
                    incomingCalls.remove(callerKey)
                } else {
                    // This was an answered call - clean up tracking
                    Log.i(TAG, "✅ Answered call ended normally - NO ACTION NEEDED")
                    incomingCalls.remove(callerKey)
                }
            } else {
                // Could not parse notification - try to check if we have any outgoing calls
                Log.w(TAG, "⚠️ Could not parse phone notification on removal")
                if (outgoingCalls.isNotEmpty()) {
                    Log.i(TAG, "   There are ${outgoingCalls.size} active outgoing calls")
                    Log.i(TAG, "   outgoingCalls: $outgoingCalls")
                }
            }
        }
    }
    
    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i(TAG, "Notification listener connected")
    }
    
    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.w(TAG, "Notification listener disconnected")
    }
    
    /**
     * Get all active notifications (for debugging)
     */
    fun getAllActiveNotifications(): List<StatusBarNotification> {
        return try {
            activeNotifications?.toList() ?: emptyList()
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception getting active notifications: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * Check if we have permission to access notifications
     */
    fun hasNotificationAccess(): Boolean {
        return try {
            activeNotifications != null
        } catch (e: SecurityException) {
            false
        }
    }
    
    /**
     * Check if the service is running and has notification access
     */
    fun isServiceRunning(): Boolean {
        return instance != null && hasNotificationAccess()
    }
    
    private fun processMissedCall(missedCallData: PhoneCallData) {
        val missedCallKey = "${missedCallData.callerName}:${missedCallData.callerNumber}"
        val currentTime = System.currentTimeMillis()
        
        Log.i(TAG, "🔔 Processing missed call: name='${missedCallData.callerName}', number='${missedCallData.callerNumber}'")
        Log.i(TAG, "🔑 Missed call key: '$missedCallKey'")
        
        // Check if we sent this missed call recently (within dedup window)
        val lastSentTime = sentMissedCallsTime[missedCallKey]
        if (lastSentTime != null && (currentTime - lastSentTime) < MISSED_CALL_DEDUP_WINDOW_MS) {
            val timeSinceLast = currentTime - lastSentTime
            Log.i(TAG, "⏭ MISSED call sent recently (${timeSinceLast}ms ago), skipping duplicate")
            return
        }
        
        // Update timestamp and send
        sentMissedCallsTime[missedCallKey] = currentTime
        Log.i(TAG, "✅ Sending MISSED call (new or expired): $missedCallKey")
        Log.i(TAG, "📤 Calling sendPhoneCallData...")
        
        sendPhoneCallData(missedCallData)
    }
    
    private fun sendPhoneCallData(phoneCallData: PhoneCallData) {
        Log.i(TAG, "📞 sendPhoneCallData called with: $phoneCallData")
        Log.i(TAG, "🔍 BLE service is: $bleService")
        
        // Send to BLE service
        bleService?.let { service ->
            Log.i(TAG, "✅ BLE service available, sending phone call data...")
            CoroutineScope(Dispatchers.IO).launch {
                Log.i(TAG, "🚀 Launching coroutine to call service.sendPhoneCallData...")
                service.sendPhoneCallData(phoneCallData)
                Log.i(TAG, "✅ service.sendPhoneCallData completed")
            }
        } ?: Log.e(TAG, "❌ BLE SERVICE NOT AVAILABLE!")
    }
    
    /**
     * Extract phone number from text using regex patterns
     * Supports Indian and international formats
     */
    private fun extractPhoneNumber(text: String?): String? {
        if (text.isNullOrBlank()) return null
        
        // Phone number patterns (ordered by priority)
        val patterns = listOf(
            // Indian mobile with country code
            "(\\+91[-\\s]?[6-9]\\d{9})",
            // Indian mobile without country code (10 digits starting with 6-9)
            "([6-9]\\d{9})",
            // International format
            "(\\+\\d{10,15})",
            // Generic 10-15 digit number
            "([0-9]{10,15})",
            // US/International formats
            "(\\+?\\d{1,3}[\\s-]?\\(?\\d{3}\\)?[\\s-]?\\d{3}[\\s-]?\\d{4})",
            // Standard format
            "(\\d{3}[\\s-]?\\d{3}[\\s-]?\\d{4})"
        )
        
        for (patternStr in patterns) {
            val pattern = Pattern.compile(patternStr)
            val matcher = pattern.matcher(text)
            if (matcher.find()) {
                val number = matcher.group(0)?.trim()
                if (number != null && number.length >= 10) {
                    // Clean up the number (remove spaces, dashes, parentheses)
                    val cleaned = number.replace(Regex("[\\s()-]"), "")
                    // Validate it looks like a phone number
                    if (cleaned.matches(Regex("\\d{10,15}"))) {
                        return cleaned
                    }
                }
            }
        }
        
        return null
    }
    
}


