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
        
        // Ensure ConfigManager is initialized (defensive check)
        try {
                // Try to get types - if it fails, initialize
                val testTypes = try {
                    val types = com.tnvsai.yatramate.config.ConfigManager.getAllNotificationTypes()
                    if (types.isEmpty()) {
                        Log.w(TAG, "⚠️ ConfigManager returned 0 types, initializing...")
                        com.tnvsai.yatramate.config.ConfigManager.initialize(applicationContext)
                        com.tnvsai.yatramate.config.ConfigPersistence.initialize(applicationContext)
                        com.tnvsai.yatramate.config.NotificationConfigManager.initialize(applicationContext)
                        com.tnvsai.yatramate.config.ConfigManager.getAllNotificationTypes()
                    } else {
                        types
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ ConfigManager.getAllNotificationTypes() failed, initializing... ${e.message}", e)
                    com.tnvsai.yatramate.config.ConfigManager.initialize(applicationContext)
                    com.tnvsai.yatramate.config.ConfigPersistence.initialize(applicationContext)
                    com.tnvsai.yatramate.config.NotificationConfigManager.initialize(applicationContext)
                    try {
                        com.tnvsai.yatramate.config.ConfigManager.getAllNotificationTypes()
                    } catch (e2: Exception) {
                        Log.e(TAG, "❌ Failed to get types even after initialization: ${e2.message}", e2)
                        emptyList()
                    }
                }
                
                if (testTypes.isEmpty()) {
                    Log.e(TAG, "❌❌❌ CRITICAL: Still 0 types after initialization! Check notification_types.json!")
                } else {
                    Log.i(TAG, "✅ ConfigManager initialized in NotificationListenerService: ${testTypes.size} types loaded")
                    testTypes.take(3).forEach { type ->
                        Log.d(TAG, "  - ${type.id}: ${type.apps.size} apps, ${type.keywords.size} keywords")
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to initialize ConfigManager in NotificationListenerService: ${e.message}", e)
        }
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
        
        // Use generic classifier for ALL notifications (configuration-driven)
        Log.i(TAG, "🔍 Attempting to classify notification from: $packageName")
        Log.i(TAG, "🔍 Package: $packageName")
        Log.i(TAG, "🔍 Title: $title")
        Log.i(TAG, "🔍 Text: $text")
        Log.i(TAG, "🔍 BigText: $bigText")
        
        // CRITICAL: Verify ConfigManager is initialized BEFORE classification
        try {
            var testTypes = com.tnvsai.yatramate.config.ConfigManager.getAllNotificationTypes()
            Log.i(TAG, "✅ ConfigManager check: ${testTypes.size} types available")
            
            // If no types, force initialization
            if (testTypes.isEmpty()) {
                Log.w(TAG, "⚠️ ConfigManager has 0 types! Force initializing...")
                com.tnvsai.yatramate.config.ConfigManager.initialize(applicationContext)
                com.tnvsai.yatramate.config.ConfigPersistence.initialize(applicationContext)
                com.tnvsai.yatramate.config.NotificationConfigManager.initialize(applicationContext)
                
                // Retry getting types
                testTypes = com.tnvsai.yatramate.config.ConfigManager.getAllNotificationTypes()
                Log.i(TAG, "✅ After force init: ${testTypes.size} types available")
                
                if (testTypes.isEmpty()) {
                    Log.e(TAG, "❌❌❌ STILL 0 TYPES AFTER FORCE INIT! This is a CRITICAL ERROR! ❌❌❌")
                    Log.e(TAG, "   Check notification_types.json file in assets/config/")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ ConfigManager check failed! Initializing... ${e.message}", e)
            try {
                com.tnvsai.yatramate.config.ConfigManager.initialize(applicationContext)
                com.tnvsai.yatramate.config.ConfigPersistence.initialize(applicationContext)
                com.tnvsai.yatramate.config.NotificationConfigManager.initialize(applicationContext)
                Log.i(TAG, "✅ ConfigManager initialized after exception")
            } catch (e2: Exception) {
                Log.e(TAG, "❌ Failed to initialize ConfigManager: ${e2.message}", e2)
            }
        }
        
        // Classify notification - NOW ALWAYS returns a result (never null)
        val classified = NotificationClassifier.classifyNotification(packageName, title, text, bigText)
        
        // classified is never null now (uses fallback if config fails)
        if (classified != null) {
            Log.i(TAG, "✅✅✅ CLASSIFIED as ${classified.type.name} (method: ${classified.classificationMethod}, confidence: ${classified.confidence}) ✅✅✅")
            
            // Check if type is enabled (only for config-based classifications)
            val shouldSendToMCU = if (classified.classificationMethod == "config") {
                val typeEnabled = com.tnvsai.yatramate.config.NotificationConfigManager.isTypeEnabled(classified.type.id)
                val appEnabled = com.tnvsai.yatramate.config.NotificationConfigManager.isAppEnabledForType(
                    classified.type.id, packageName
                )
                typeEnabled && appEnabled
            } else {
                // Fallback classifications are always enabled (they work even without config)
                true
            }
            
            Log.i(TAG, "   shouldSendToMCU: $shouldSendToMCU (method: ${classified.classificationMethod})")
            
            // Store in unified history (always store, even if not sent to MCU)
            // Note: Phone calls and navigation use specialized handlers, don't send via generic sendToMCU
            when (classified.type.id) {
                "navigation" -> {
                    Log.i(TAG, "🗺️ Processing navigation notification")
                    Log.i(TAG, "🗺️ Title: $title, Text: $text, BigText: $bigText")
                    Log.i(TAG, "🗺️ Extracted data from classifier: ${classified.extractedData}")
                    Log.i(TAG, "🗺️ shouldSendToMCU: $shouldSendToMCU")
                    
                    // Use NotificationParser for better navigation data extraction
                    val isNavigation = NotificationParser.isNavigationNotification(text ?: "")
                    Log.d(TAG, "🗺️ isNavigationNotification check: $isNavigation")
                    var navData = if (isNavigation) {
                        NotificationParser.parseNotification(text ?: "")
                    } else {
                        Log.d(TAG, "🗺️ NotificationParser returned null or isNavigation=false")
                        null
                    }
                    
                    // If parsing failed but classifier identified it as navigation, use extracted data
                    if (navData == null && classified.extractedData.containsKey("direction")) {
                        val directionStr = (classified.extractedData["direction"] as? String ?: "STRAIGHT").uppercase()
                        val direction = try {
                            com.tnvsai.yatramate.model.Direction.valueOf(directionStr)
                        } catch (e: IllegalArgumentException) {
                            Log.w(TAG, "Invalid direction enum value: $directionStr, defaulting to STRAIGHT")
                            com.tnvsai.yatramate.model.Direction.STRAIGHT
                        }
                        navData = com.tnvsai.yatramate.model.NavigationData(
                            direction = direction,
                            distance = classified.extractedData["distance"] as? String ?: "",
                            maneuver = classified.extractedData["maneuver"] as? String ?: "",
                            eta = null
                        )
                        Log.d(TAG, "Using navigation data from classifier: $navData")
                    }
                    
                    // Navigation uses specialized handler via bleService.sendNavigationData
                    // This ensures proper formatting and deduplication logic
                    if (navData != null && shouldSendToMCU) {
                        Log.i(TAG, "✅ Sending navigation data to MCU: $navData")
                        bleService?.let { service ->
                            CoroutineScope(Dispatchers.IO).launch {
                                service.sendNavigationData(navData!!)
                            }
                        }
                    } else {
                        if (navData == null) {
                            Log.w(TAG, "⚠️ Navigation classified but could not parse data, not sending to MCU")
                        } else if (!shouldSendToMCU) {
                            Log.d(TAG, "⚠️ Navigation data available but not sending to MCU (disabled)")
                        }
                    }
                    
                    storeNotificationForDebugging(
                        packageName, title, text, bigText, 
                        isGoogleMaps = packageName == "com.google.android.apps.maps",
                        isNavigation = navData != null,
                        parsedData = navData?.toString(),
                        navigationData = navData,
                        sentToMCU = shouldSendToMCU && navData != null,
                        parsingMethod = "NotificationClassifier",
                        parserName = "Generic"
                    )
                }
                "phone_call" -> {
                    // Use PhoneCallParser for better phone call data extraction
                    val phoneCallData = PhoneCallParser.parsePhoneNotification(
                        packageName, title, text, bigText,
                        extractPhoneNumberFromExtras(sbn)
                    )
                    
                    if (phoneCallData != null) {
                        // Phone calls use specialized handler that calls sendPhoneCallData directly
                        // This ensures proper formatting and deduplication logic
                        handlePhoneNotification(sbn, title, text, bigText, phoneCallData, shouldSendToMCU)
                    } else {
                        // Fallback: store as generic
                        NotificationHistoryManager.addGenericEntry(
                            type = "phone_call",
                            packageName = packageName,
                            title = title,
                            text = text,
                            bigText = bigText,
                            extractedData = classified.extractedData,
                            sentToMCU = shouldSendToMCU,
                            mcuType = classified.type.mcuType,
                            classificationKeywordsMatched = getMatchedKeywords(classified, title, text),
                            classificationTitlePatternsMatched = getMatchedTitlePatterns(classified, title),
                            classificationAppMatched = classified.type.apps.contains(packageName)
                        )
                    }
                }
                "message" -> {
                    // Send to MCU if enabled (messages use generic sendToMCU)
                    if (shouldSendToMCU) {
                        sendToMCU(classified.type, classified.extractedData)
                    }
                    // Store as message entry
                    NotificationHistoryManager.addMessageEntry(
                                type = "message",
                                packageName = packageName,
                                title = title,
                                text = text,
                                bigText = bigText,
                                sender = title,
                                message = text,
                                appName = getAppNameFromPackage(packageName),
                                messageType = getMessageTypeFromPackage(packageName),
                                sentToMCU = shouldSendToMCU
                            )
                        }
                        "battery" -> {
                            // Send to MCU if enabled (battery uses generic sendToMCU)
                            if (shouldSendToMCU) {
                                sendToMCU(classified.type, classified.extractedData)
                            }
                            // Store as generic entry with battery type
                            NotificationHistoryManager.addGenericEntry(
                                type = "battery",
                                packageName = packageName,
                                title = title,
                                text = text,
                                bigText = bigText,
                                extractedData = classified.extractedData,
                                sentToMCU = shouldSendToMCU,
                                mcuType = classified.type.mcuType,
                                classificationKeywordsMatched = getMatchedKeywords(classified, title, text),
                                classificationTitlePatternsMatched = getMatchedTitlePatterns(classified, title),
                                classificationAppMatched = classified.type.apps.contains(packageName)
                            )
                        }
                        "music" -> {
                            // Send to MCU if enabled (music uses generic sendToMCU)
                            if (shouldSendToMCU) {
                                sendToMCU(classified.type, classified.extractedData)
                            }
                            // Store as generic entry with music type
                            NotificationHistoryManager.addGenericEntry(
                                type = "music",
                                packageName = packageName,
                                title = title,
                                text = text,
                                bigText = bigText,
                                extractedData = classified.extractedData,
                                sentToMCU = shouldSendToMCU,
                                mcuType = classified.type.mcuType,
                                classificationKeywordsMatched = getMatchedKeywords(classified, title, text),
                                classificationTitlePatternsMatched = getMatchedTitlePatterns(classified, title),
                                classificationAppMatched = classified.type.apps.contains(packageName)
                            )
                        }
                        else -> {
                            // Send to MCU if enabled (generic notification types)
                            if (shouldSendToMCU) {
                                sendToMCU(classified.type, classified.extractedData)
                            }
                            // Store as generic notification entry
                            NotificationHistoryManager.addGenericEntry(
                                type = classified.type.id,
                                packageName = packageName,
                                title = title,
                                text = text,
                                bigText = bigText,
                                extractedData = classified.extractedData,
                                sentToMCU = shouldSendToMCU,
                                mcuType = classified.type.mcuType,
                                classificationKeywordsMatched = getMatchedKeywords(classified, title, text),
                                classificationTitlePatternsMatched = getMatchedTitlePatterns(classified, title),
                                classificationAppMatched = classified.type.apps.contains(packageName),
                                classificationMethod = classified.classificationMethod
                            )
                        }
                    }
        } else {
            // This should never happen now (classifier always returns a result)
            // But keep as safety fallback
            Log.e(TAG, "❌❌❌ CRITICAL: NotificationClassifier returned null! This shouldn't happen! ❌❌❌")
            Log.e(TAG, "❌ Package: $packageName")
            Log.e(TAG, "❌ Title: $title")
            Log.e(TAG, "❌ Text: $text")
            NotificationHistoryManager.addGenericEntry(
                type = "unknown",
                packageName = packageName,
                title = title,
                text = text,
                bigText = bigText,
                extractedData = mapOf("raw_text" to (text ?: "")),
                sentToMCU = false,
                mcuType = null,
                classificationKeywordsMatched = emptyList(),
                classificationTitlePatternsMatched = emptyList(),
                classificationAppMatched = false
            )
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
        var navigationData: NavigationData? = null
        if (isNavigation) {
            navigationData = NotificationParser.parseNotification(fullText)
            parsedData = navigationData?.toString()

            if (navigationData != null) {
                Log.i(TAG, "✅ PARSED NAVIGATION DATA: $navigationData")
                
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
        val finalNavigationData = navigationData
        storeNotificationForDebugging(
            packageName = sbn.packageName,
            title = title,
            text = text,
            bigText = bigText,
            isGoogleMaps = true,
            isNavigation = isNavigation,
            parsedData = parsedData,
            navigationData = finalNavigationData,
            sentToMCU = finalNavigationData != null && bleService != null,
            parsingMethod = "NotificationParser",
            parserName = "GoogleMapsParser"
        )
    }

    private fun handlePhoneNotification(
        sbn: StatusBarNotification, 
        title: String?, 
        text: String?, 
        bigText: String?,
        phoneCallData: com.tnvsai.yatramate.model.PhoneCallData,
        shouldSendToMCU: Boolean = true
    ) {
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
            }
        }
        
        // If phone number not found in extras, try extracting from text
        if (phoneNumber == null || phoneNumber.isEmpty()) {
            phoneNumber = extractPhoneNumber(text) ?: extractPhoneNumber(bigText)
            if (phoneNumber != null) {
                Log.i(TAG, "📞 Phone number extracted from text: $phoneNumber")
            }
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
            
            // Keep legacy list for backward compatibility
            phoneDebugLogs.add(0, debugLog)
            if (phoneDebugLogs.size > MAX_PHONE_DEBUG_LOGS) {
                phoneDebugLogs.removeLast()
            }
            
            // Notify callback
            phoneDebugLogCallback?.invoke(debugLog)
            
            // Track call state transitions
            val callerKey = "${phoneCallData.callerName}:${phoneCallData.callerNumber}"
            
            // Add to unified history manager (after tracking setup)
            val wasOutgoing = outgoingCalls.contains(callerKey)
            val wasAnswered = incomingCalls[callerKey] ?: false
            val wasMissed = phoneCallData.callState == CallState.MISSED
            
            NotificationHistoryManager.addPhoneCallEntry(
                log = debugLog,
                phoneCallData = phoneCallData,
                sentToMCU = false,  // Will be updated when actually sent
                deviceProfile = try {
                    com.tnvsai.yatramate.config.ConfigManager.getActiveDeviceProfile()?.name
                } catch (e: Exception) {
                    null
                },
                detectionPattern = null,  // Can be extracted from parser if needed
                wasOutgoing = wasOutgoing,
                wasAnswered = wasAnswered,
                wasMissed = wasMissed,
                phoneNumberFromExtras = extractPhoneNumberFromExtras(sbn)
            )
            
            when (phoneCallData.callState) {
                CallState.INCOMING -> {
                    // Mark this incoming call as not yet answered
                    incomingCalls[callerKey] = false
                    outgoingCalls.remove(callerKey)
                    Log.i(TAG, "📞 INCOMING call from $callerKey - marking as not answered")
                    Log.i(TAG, "📞 INCOMING call - shouldSendToMCU: $shouldSendToMCU")
                    Log.i(TAG, "📞 INCOMING call - phoneCallData: $phoneCallData")
                    
                    // Send INCOMING state to MCU if enabled
                    if (shouldSendToMCU) {
                        Log.i(TAG, "✅ Sending INCOMING call to MCU")
                        sendPhoneCallData(phoneCallData)
                    } else {
                        Log.w(TAG, "⚠️ INCOMING call NOT sent to MCU (shouldSendToMCU=false)")
                    }
                }
                CallState.ONGOING -> {
                    // Check if this is an outgoing call
                    val wasInitiallyIncoming = incomingCalls.containsKey(callerKey)
                    val isOutgoing = !wasInitiallyIncoming
                    
                    Log.i(TAG, "📞 ONGOING call - isOutgoing: $isOutgoing")
                    
                    if (isOutgoing) {
                        // This is a true outgoing call
                        outgoingCalls.add(callerKey)
                        Log.i(TAG, "📞 OUTGOING call to $callerKey - SENDING TO MCU")
                        
                        // Send ONGOING state to MCU immediately for outgoing calls
                        if (shouldSendToMCU) {
                            Log.i(TAG, "✅ Sending OUTGOING call ONGOING state to MCU")
                            sendPhoneCallData(phoneCallData)
                        } else {
                            Log.w(TAG, "⚠️ OUTGOING call not sent to MCU (shouldSendToMCU=false)")
                        }
                    } else {
                        // Call was answered
                        incomingCalls[callerKey] = true
                        outgoingCalls.remove(callerKey)
                        Log.i(TAG, "✅ Call answered - marking $callerKey as answered")
                        
                        // Send ONGOING state to MCU for answered incoming calls
                        if (shouldSendToMCU) {
                            sendPhoneCallData(phoneCallData)
                        }
                    }
                }
                CallState.ENDED -> {
                    // Check if this was an outgoing call
                    val wasOutgoing = outgoingCalls.contains(callerKey)
                    val wasAnswered = incomingCalls[callerKey] ?: false
                    
                    if (wasOutgoing) {
                        // This was an outgoing call that ended - send ENDED to MCU if enabled
                        Log.i(TAG, "📞 Outgoing call ended - sending ENDED state to MCU")
                        if (shouldSendToMCU) {
                            sendPhoneCallData(phoneCallData)
                        }
                        outgoingCalls.remove(callerKey)
                    } else if (!wasAnswered) {
                        // This was an unanswered incoming call
                        Log.i(TAG, "❌ Detected MISSED incoming call")
                        val missedCallData = PhoneCallData(
                            callerName = phoneCallData.callerName,
                            callerNumber = phoneCallData.callerNumber,
                            callState = CallState.MISSED,
                            duration = 0
                        )
                        processMissedCall(missedCallData)
                        incomingCalls.remove(callerKey)
                    } else {
                        // This was an answered call - send ENDED to MCU if enabled
                        Log.i(TAG, "✅ Answered call ended - sending ENDED state to MCU")
                        if (shouldSendToMCU) {
                            sendPhoneCallData(phoneCallData)
                        }
                        incomingCalls.remove(callerKey)
                    }
                }
                CallState.MISSED -> {
                    // Direct MISSED notification
                    Log.i(TAG, "❌ Direct MISSED call notification received")
                    if (shouldSendToMCU) {
                        processMissedCall(phoneCallData)
                    } else {
                        // Still process for history but don't send to MCU
                        processMissedCall(phoneCallData, sendToMCU = false)
                    }
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
            storeNotificationForDebugging(sbn.packageName, title, text, bigText, false, false, null)
        }
    }
    
    private fun sendToMCU(type: com.tnvsai.yatramate.config.models.NotificationType, data: Map<String, Any>) {
        try {
            val transformer = com.tnvsai.yatramate.config.ConfigManager.getActiveTransformer()
            // Use type.id as the key to look up notification type config, not mcuType
            val payload = transformer.transformNotification(type.id, data)
            
            Log.d(TAG, "Sending to MCU: $payload")
            
            // Send via BLE
            bleService?.let { service ->
                CoroutineScope(Dispatchers.IO).launch {
                    // For now, we'll use the existing BLE service methods
                    // In the future, we can add a generic sendData method
                    when (type.id) {
                        "navigation" -> {
                            // Convert back to NavigationData for compatibility
                            val directionStr = (data["direction"] as? String ?: "STRAIGHT").uppercase()
                            val direction = try {
                                com.tnvsai.yatramate.model.Direction.valueOf(directionStr)
                            } catch (e: IllegalArgumentException) {
                                Log.w(TAG, "Invalid direction enum value: $directionStr, defaulting to STRAIGHT")
                                com.tnvsai.yatramate.model.Direction.STRAIGHT
                            }
                            val navigationData = com.tnvsai.yatramate.model.NavigationData(
                                direction = direction,
                                distance = data["distance"] as? String ?: "",
                                maneuver = data["maneuver"] as? String ?: "",
                                eta = null
                            )
                            service.sendNavigationData(navigationData)
                        }
                        "phone_call" -> {
                            // Convert back to PhoneCallData for compatibility
                            val stateStr = (data["state"] as? String ?: "INCOMING").uppercase()
                            val callState = try {
                                com.tnvsai.yatramate.model.CallState.valueOf(stateStr)
                            } catch (e: IllegalArgumentException) {
                                Log.w(TAG, "Invalid call state enum value: $stateStr, defaulting to INCOMING")
                                com.tnvsai.yatramate.model.CallState.INCOMING
                            }
                            val phoneCallData = com.tnvsai.yatramate.model.PhoneCallData(
                                callerName = data["caller"] as? String,
                                callerNumber = data["caller"] as? String ?: "",
                                callState = callState,
                                duration = 0
                            )
                            service.sendPhoneCallData(phoneCallData)
                        }
                        else -> {
                            // For other notification types, send raw JSON via BLE
                            Log.i(TAG, "Sending generic notification type ${type.id} to MCU")
                            service.sendRawData(payload)
                        }
                    }
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error sending to MCU: ${e.message}")
        }
    }

    private fun storeNotificationForDebugging(
        packageName: String,
        title: String?,
        text: String?,
        bigText: String?,
        isGoogleMaps: Boolean,
        isNavigation: Boolean,
        parsedData: String?,
        navigationData: NavigationData? = null,
        sentToMCU: Boolean = false,
        debugInfo: DebugInfo = DebugInfo(),
        parsingMethod: String? = null,
        parserName: String? = null
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

        // Only store as navigation entry if it's actually a navigation notification
        if (isNavigation && navigationData != null) {
            // Add to unified history manager as navigation entry
            NotificationHistoryManager.addNavigationEntry(
                info = notificationInfo,
                navigationData = navigationData,
                sentToMCU = sentToMCU,
                debugInfo = debugInfo,
                parsingMethod = parsingMethod,
                parserName = parserName
            )
        } else {
            // Not a valid navigation notification - store as generic/unknown
            Log.d(TAG, "Notification from $packageName marked as NOT navigation - storing as generic")
            NotificationHistoryManager.addGenericEntry(
                type = "unknown",
                packageName = packageName,
                title = title,
                text = text,
                bigText = bigText,
                extractedData = mapOf("raw_text" to (text ?: ""), "isGoogleMaps" to isGoogleMaps, "isNavigation" to isNavigation),
                sentToMCU = sentToMCU,
                mcuType = null,
                classificationKeywordsMatched = emptyList(),
                classificationTitlePatternsMatched = emptyList(),
                classificationAppMatched = false
            )
        }

        // Keep legacy list for backward compatibility
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
                    // This was an answered call that ended - send ENDED state to MCU
                    Log.i(TAG, "✅ Answered call ended - sending ENDED state to MCU")
                    val endedCallData = PhoneCallData(
                        callerName = phoneCallData.callerName,
                        callerNumber = phoneCallData.callerNumber,
                        callState = CallState.ENDED,
                        duration = 0
                    )
                    sendPhoneCallData(endedCallData)
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
    
    private fun processMissedCall(missedCallData: PhoneCallData, sendToMCU: Boolean = true) {
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
        
        // Update timestamp and send (if enabled)
        if (sendToMCU) {
            sentMissedCallsTime[missedCallKey] = currentTime
            Log.i(TAG, "✅ Sending MISSED call (new or expired): $missedCallKey")
            Log.i(TAG, "📤 Calling sendPhoneCallData...")
            sendPhoneCallData(missedCallData)
        } else {
            Log.d(TAG, "⚠️ MISSED call not sent to MCU (disabled)")
        }
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
    private fun extractPhoneNumberFromExtras(sbn: StatusBarNotification): String? {
        val extras = sbn.notification.extras
        extras?.let {
            return it.getCharSequence("android.phoneNumber")?.toString()
                ?: it.getCharSequence("android.phone_number")?.toString()
                ?: it.getCharSequence("phone_number")?.toString()
                ?: it.getCharSequence("number")?.toString()
                ?: it.getString("android.phoneNumber")
                ?: it.getString("phone_number")
                ?: it.getString("number")
        }
        return null
    }
    
    private fun getCurrentDeviceProfile(): String? {
        return try {
            com.tnvsai.yatramate.config.ConfigManager.getActiveDeviceProfile()?.name
        } catch (e: Exception) {
            null
        }
    }
    
    private fun getMatchedKeywords(
        classified: ClassifiedNotification,
        title: String?,
        text: String?
    ): List<String> {
        val allText = "${title ?: ""} ${text ?: ""}".lowercase()
        return classified.type.keywords.filter { keyword: String ->
            allText.contains(keyword.lowercase())
        }
    }
    
    private fun getMatchedTitlePatterns(
        classified: ClassifiedNotification,
        title: String?
    ): List<String> {
        if (title == null) return emptyList()
        return classified.type.titlePatterns.filter { pattern: String ->
            title.contains(pattern, ignoreCase = true)
        }
    }
    
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
    
    private fun getAppNameFromPackage(packageName: String): String {
        return when {
            packageName.contains("whatsapp", ignoreCase = true) -> "WhatsApp"
            packageName.contains("telegram", ignoreCase = true) -> "Telegram"
            packageName.contains("messaging", ignoreCase = true) -> "Messages"
            packageName.contains("mms", ignoreCase = true) -> "SMS"
            else -> packageName.substringAfterLast(".")
        }
    }
    
    private fun getMessageTypeFromPackage(packageName: String): String? {
        return when {
            packageName.contains("whatsapp", ignoreCase = true) -> "whatsapp"
            packageName.contains("telegram", ignoreCase = true) -> "telegram"
            packageName.contains("messaging", ignoreCase = true) -> "sms"
            packageName.contains("mms", ignoreCase = true) -> "sms"
            else -> null
        }
    }
}


