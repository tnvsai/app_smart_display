package com.tnvsai.yatramate.notification

import com.tnvsai.yatramate.model.CallState
import com.tnvsai.yatramate.model.PhoneCallData
import android.util.Log
import java.util.regex.Pattern

/**
 * Parser for extracting phone call data from Android notifications
 */
object PhoneCallParser {
    
    private const val TAG = "PhoneCallParser"
    
    // Deduplication - track recent notifications
    private val recentNotifications = mutableMapOf<String, Long>()
    private const val DEDUP_TIMEOUT_MS = 5000L // 5 seconds
    
    // Track recent notification patterns for context-aware detection
    private data class RecentNotification(val title: String?, val text: String?, val timestamp: Long)
    private val recentNotificationHistory = mutableListOf<RecentNotification>()
    private const val MAX_HISTORY_SIZE = 10
    private const val HISTORY_TIMEOUT_MS = 10000L // 10 seconds
    
    // Track if we recently saw "Calling..." for Samsung dual-notification pattern
    private var lastCallingNotificationTime: Long = 0
    private const val CALLING_WINDOW_MS = 5000L // 5 seconds
    
    /**
     * Add notification to history for context-aware detection
     */
    private fun addToHistory(title: String?, text: String?) {
        val now = System.currentTimeMillis()
        recentNotificationHistory.add(RecentNotification(title, text, now))
        
        // Keep only recent notifications
        recentNotificationHistory.removeAll { now - it.timestamp > HISTORY_TIMEOUT_MS }
        if (recentNotificationHistory.size > MAX_HISTORY_SIZE) {
            recentNotificationHistory.removeAt(0)
        }
        
        // Track "Calling..." separately for fast lookup
        if (text?.trim()?.lowercase() == "calling...") {
            lastCallingNotificationTime = now
            Log.d(TAG, "📝 Tracked 'Calling...' notification at $now")
        }
    }
    
    /**
     * Check if recent history shows outgoing call pattern
     * Samsung sends: 1) "Calling..." -> 2) title only (name)
     */
    private fun isLikelyOutgoingFromHistory(): Boolean {
        val now = System.currentTimeMillis()
        val timeSinceCalling = now - lastCallingNotificationTime
        val result = timeSinceCalling < CALLING_WINDOW_MS
        
        Log.d(TAG, "🔍 History check: timeSinceCalling=${timeSinceCalling}ms, result=$result")
        return result
    }
    
    /**
     * Parse phone call notification to extract caller information
     * @param packageName Package name of the notification source
     * @param title Notification title
     * @param text Notification text
     * @param bigText Notification big text
     * @return PhoneCallData if successful, null otherwise
     */
    fun parsePhoneNotification(
        packageName: String,
        title: String?,
        text: String?,
        bigText: String?,
        phoneNumberFromExtras: String? = null
    ): PhoneCallData? {
        
        Log.i(TAG, "═══ RAW NOTIFICATION DATA ═══")
        Log.i(TAG, "Package: $packageName")
        Log.i(TAG, "Title: '$title'")
        Log.i(TAG, "Text: '$text'")
        Log.i(TAG, "BigText: '$bigText'")
        Log.i(TAG, "═══════════════════════════")
        
        // CRITICAL: Skip deduplication for now to debug missed calls
        // The deduplication might be blocking important state transitions
        // val notificationKey = "$packageName:$title:$text"
        // val currentTime = System.currentTimeMillis()
        // val lastTime = recentNotifications[notificationKey]
        // if (lastTime != null && (currentTime - lastTime) < DEDUP_TIMEOUT_MS) {
        //     Log.d(TAG, "Skipping duplicate notification (within ${currentTime - lastTime}ms)")
        //     return null
        // }
        // recentNotifications[notificationKey] = currentTime
        
        // Note: Deduplication cleanup temporarily disabled since deduplication is disabled
        
        // For Samsung dialer, combine all fields to find caller name
        // Filter out null values to avoid "null null" strings
        val combinedParts = listOfNotNull(title, text, bigText).filter { it.isNotEmpty() }
        val combinedText = combinedParts.joinToString(" ").trim()
        
                                   // Check history BEFORE adding current notification (critical!)
        val historyShowsOutgoing = isLikelyOutgoingFromHistory()
        
        // NOW add current notification to history for next notification
        addToHistory(title, text)
        
        // DETERMINE CALL STATE - BASED ON ACTUAL NOTIFICATION DATA
        Log.d(TAG, "=== CALL STATE DETECTION ===")
        Log.d(TAG, "Title: '$title', Text: '$text'")
        Log.d(TAG, "Package: $packageName")
        
                  // CRITICAL: Check for "Calling..." FIRST - if present, immediately set to OUTGOING and EXIT
        val callState: CallState
        if (!text.isNullOrEmpty() && text.contains("Calling", ignoreCase = true)) {
            Log.i(TAG, "✅ CRITICAL: 'Calling...' detected - IMMEDIATELY setting to OUTGOING (no other checks)")
            callState = CallState.ONGOING
        } else {
                        // DETECT CALL STATE FROM NOTIFICATION CONTENT (only if no "Calling...")
            callState = when {
                           // PATTERN 1: Text contains other outgoing keywords -> OUTGOING
             !text.isNullOrEmpty() && (
                 text.contains("dialing", ignoreCase = true) ||
                 text.contains("connecting", ignoreCase = true) ||
                 text.contains("outgoing", ignoreCase = true)
             ) -> {
                 Log.i(TAG, "✅ PATTERN 1: OUTGOING (text contains other outgoing keyword)")
                 CallState.ONGOING
             }
             
             // PATTERN 2: Text contains incoming keywords -> INCOMING
             !text.isNullOrEmpty() && (
                 text.contains("incoming", ignoreCase = true) ||
                 text.contains("ringing", ignoreCase = true) ||
                 text.contains("call from", ignoreCase = true)
             ) -> {
                 Log.i(TAG, "✅ PATTERN 2: INCOMING (text contains incoming keyword)")
                 CallState.INCOMING
             }
             
             // PATTERN 3: Text contains missed keywords -> MISSED
             !text.isNullOrEmpty() && text.contains("missed", ignoreCase = true) -> {
                 Log.i(TAG, "✅ PATTERN 3: MISSED (text contains missed keyword)")
                 CallState.MISSED
             }
             
             // PATTERN 4: Text is empty/random, use history context
             // Only if there's NO "Calling..." in the text
             (text.isNullOrEmpty() || text.trim().isEmpty() || text.length < 5) && title != null -> {
                 if (historyShowsOutgoing) {
                     Log.i(TAG, "✅ PATTERN 4a: OUTGOING (empty text, history shows 'Calling...' notification)")
                     CallState.ONGOING
                 } else {
                     Log.i(TAG, "✅ PATTERN 4b: INCOMING (empty text, no 'Calling...' history)")
                     CallState.INCOMING
                 }
             }
             
             // PATTERN 5: Fallback to comprehensive pattern matching
             // This should rarely trigger now that we check for "Calling..." first
             else -> {
                 Log.d(TAG, "PATTERN 5: Using comprehensive pattern matching (no explicit indicators)")
                 val detectedState = determineCallState(combinedText, packageName)
                 Log.i(TAG, "✅ PATTERN 5 result: $detectedState")
                 detectedState
             }
         }
        }
        
        Log.i(TAG, "=== FINAL DETECTED STATE: $callState ===")
        Log.i(TAG, "")
        
        // Text to parse for extracting caller info
        // SPECIAL CASE: If text is "Calling..." OR text is empty with title, use title directly
        var cleanedText: String? = null
        val isEmptyOrCalling = text?.trim().let { it.isNullOrEmpty() || it.lowercase() == "calling..." }
        if (isEmptyOrCalling && title != null && title.isNotEmpty()) {
            Log.d(TAG, "Special case: text is '${text}', using title as name: '$title'")
            cleanedText = title  // Use title directly as it contains the caller name (no cleaning needed)
        } else {
            // Clean up text - remove common notification keywords but preserve caller name
            cleanedText = combinedText?.let { text ->
                // Remove app names (Call, Phone, Dialer) from start
                var cleaned = text.replace("^(Call|Phone|Dialer)\\s*".toRegex(RegexOption.IGNORE_CASE), "")
                
                // Remove common suffixes - be careful with "Calling..." as it might be at the end
                cleaned = cleaned.replace(" Incoming call", "", ignoreCase = true)
                    .replace(" is calling", "", ignoreCase = true)
                    .replace("\\s*calling", "", ignoreCase = true)  // Match "Calling..." with optional leading space
                    .replace("\\s*calling\\.\\.\\..*", "", ignoreCase = true)  // Match "Calling..." exactly
                    .replace("Missed call", "", ignoreCase = true)
                    .trim()
                
                // Remove any "null" strings that may have gotten into the text
                cleaned = cleaned.replace("\\s*null\\s*".toRegex(RegexOption.IGNORE_CASE), " ")
                    .replace("\\s+".toRegex(), " ")  // Collapse multiple spaces
                    .trim()
                
                // Remove trailing dots (leftover from "Calling..." cleanup)
                cleaned = cleaned.replace("\\.+$".toRegex(), "").trim()
                
                cleaned
            }
        }
        
        Log.d(TAG, "Cleaned text: '$cleanedText'")
        
        // Extract caller information from cleaned text
        val callerInfo = cleanedText?.let { extractCallerInfo(it, phoneNumberFromExtras) }
        Log.d(TAG, "Extracted caller info: $callerInfo")
        
        if (callerInfo != null) {
            val (name, number) = callerInfo
            
            // Validate name and number - reject "null" or empty values
            val validName = when {
                name.isNullOrBlank() -> null
                name.equals("null", ignoreCase = true) -> null
                else -> name.trim()
            }
            
            val validNumber = when {
                number.isNullOrBlank() -> "Unknown"
                number.equals("null", ignoreCase = true) -> "Unknown"
                else -> number.trim()
            }
            
            // Filter out notifications with all "Unknown" data
            val nameIsUnknown = validName == null || validName.equals("Unknown", ignoreCase = true)
            val numberIsUnknown = validNumber == "Unknown"
            
            if (nameIsUnknown && numberIsUnknown) {
                Log.w(TAG, "⚠️ Skipping notification - all data fields are 'Unknown'")
                return null
            }
            
            // Only return if we have valid data
            if (validName != null || validNumber != "Unknown") {
                val phoneCallData = PhoneCallData(
                    callerName = validName ?: validNumber, // Use number as fallback for name
                    callerNumber = validNumber,
                    callState = callState
                )
                Log.i(TAG, "Successfully parsed phone call: $phoneCallData")
                return phoneCallData
            }
        }
        
        Log.w(TAG, "Failed to extract caller information")
        return null
    }
    
    /**
     * Determine call state based on notification content
     * Supports multiple Indian languages
     */
    private fun determineCallState(fullText: String, packageName: String): CallState {
        val text = fullText.lowercase()
        
        return when {
            // OUTGOING CALL PATTERNS (check first before incoming)
            // CRITICAL: Check for exact "calling..." pattern which is outgoing
            text.matches(Regex(".*calling\\.\\.\\.*", RegexOption.IGNORE_CASE)) ||
            // Or "calling" at the end of text (without incoming before it)
            (text.contains("calling", ignoreCase = true) && !text.contains("incoming", ignoreCase = true) && text.length < 30) ||
            text.contains("outgoing", ignoreCase = true) ||
            text.contains("dialing", ignoreCase = true) ||
            text.contains("connecting", ignoreCase = true) -> CallState.ONGOING  // Treat outgoing as ONGOING state
            
            // INCOMING CALL PATTERNS
            
            // English patterns
            text.contains("incoming call") || 
            (text.contains("incoming") && !text.contains("outgoing")) ||
            text.contains("ringing") ||
            text.contains("call from") ||
            
            // Hindi patterns (India's national language)
            text.contains("आने वाला कॉल") ||
            (text.contains("कॉलिंग") && !text.contains("बाहर")) ||
            text.contains("रिंगिंग") ||
            text.contains("कॉल आ रही") ||
            
            // Bengali patterns (West Bengal)
            text.contains("আসন্ন কল") ||
            text.contains("কলিং") ||
            text.contains("রিং") ||
            
            // Tamil patterns (Tamil Nadu)
            text.contains("வரும் அழைப்பு") ||
            text.contains("அழைப்பு") ||
            text.contains("ரிங்") ||
            
            // Telugu patterns (Andhra Pradesh, Telangana)
            text.contains("వచ్చే కాల్") ||
            text.contains("కాల్") ||
            text.contains("రింగ్") ||
            
            // Gujarati patterns (Gujarat)
            text.contains("આવતી કોલ") ||
            text.contains("કોલિંગ") ||
            
            // Marathi patterns (Maharashtra)
            text.contains("येणारी कॉल") ||
            text.contains("कॉलिंग") ||
            
            // Punjabi patterns (Punjab)
            text.contains("ਆਉਣ ਵਾਲੀ ਕਾਲ") ||
            text.contains("ਕਾਲਿੰਗ") ||
            
            // Generic patterns
            text.contains("call") && text.contains("from") -> CallState.INCOMING
            
            // ONGOING CALL PATTERNS
            
            // English patterns
            text.contains("in call") ||
            text.contains("call in progress") ||
            text.contains("ongoing call") ||
            text.contains("call duration") ||
            
            // Hindi patterns
            text.contains("कॉल चल रही") ||
            text.contains("कॉल प्रगति में") ||
            text.contains("चल रही कॉल") ||
            
            // Bengali patterns
            text.contains("কল চলছে") ||
            text.contains("চলমান কল") ||
            
            // Tamil patterns
            text.contains("அழைப்பு நடைபெறுகிறது") ||
            text.contains("நடைபெறும் அழைப்பு") ||
            
            // Telugu patterns
            text.contains("కాల్ జరుగుతోంది") ||
            text.contains("నడుస్తున్న కాల్") -> CallState.ONGOING
            
            // MISSED CALL PATTERNS
            
            // English patterns
            text.contains("missed call") ||
            text.contains("missed") ||
            text.contains("call missed") ||
            
            // Hindi patterns
            text.contains("मिस्ड कॉल") ||
            text.contains("चूकी कॉल") ||
            text.contains("मिस्ड") ||
            
            // Bengali patterns
            text.contains("মিসড কল") ||
            text.contains("হারানো কল") ||
            
            // Tamil patterns
            text.contains("தவறிய அழைப்பு") ||
            text.contains("மிஸ் அழைப்பு") ||
            
            // Telugu patterns
            text.contains("మిస్డ్ కాల్") ||
            text.contains("తప్పిన కాల్") ||
            
            // Gujarati patterns
            text.contains("મિસ્ડ કોલ") ||
            text.contains("ચૂકેલી કોલ") ||
            
            // Marathi patterns
            text.contains("मिस्ड कॉल") ||
            text.contains("चुकलेली कॉल") ||
            
            // Punjabi patterns
            text.contains("ਮਿਸਡ ਕਾਲ") ||
            text.contains("ਚੁਕੀ ਕਾਲ") -> CallState.MISSED
            
            // CALL ENDED PATTERNS
            
            // English patterns
            text.contains("call ended") ||
            text.contains("call finished") ||
            text.contains("call completed") ||
            
            // Hindi patterns
            text.contains("कॉल समाप्त") ||
            text.contains("कॉल खत्म") ||
            text.contains("कॉल पूरी") ||
            
            // Bengali patterns
            text.contains("কল শেষ") ||
            text.contains("কল সম্পূর্ণ") ||
            
            // Tamil patterns
            text.contains("அழைப்பு முடிந்தது") ||
            text.contains("அழைப்பு முடிவு") ||
            
            // Telugu patterns
            text.contains("కాల్ ముగిసింది") ||
            text.contains("కాల్ పూర్తి") -> CallState.ENDED
            
            // DEFAULT BASED ON PACKAGE
            else -> when (packageName) {
                // Samsung devices - enhanced detection
                "com.samsung.android.dialer",
                "com.samsung.android.incallui" -> {
                    // Samsung specific patterns
                    when {
                        text.contains("incoming call", ignoreCase = true) ||
                        text.contains("calling", ignoreCase = true) ||
                        text.contains("ringing", ignoreCase = true) -> CallState.INCOMING
                        
                        text.contains("in call", ignoreCase = true) ||
                        text.contains("call in progress", ignoreCase = true) -> CallState.ONGOING
                        
                        text.contains("missed call", ignoreCase = true) ||
                        text.contains("missed", ignoreCase = true) -> CallState.MISSED
                        
                        text.contains("call ended", ignoreCase = true) ||
                        text.contains("call finished", ignoreCase = true) -> CallState.ENDED
                        
                        else -> CallState.INCOMING // Default for Samsung
                    }
                }
                
                // Xiaomi/Redmi devices
                "com.miui.phone",
                "com.miui.incallui" -> CallState.INCOMING
                
                // OnePlus devices
                "com.oneplus.incallui",
                "com.oneplus.dialer" -> CallState.INCOMING
                
                // Realme/Oppo/Vivo devices (ColorOS)
                "com.coloros.incallui",
                "com.coloros.dialer",
                "com.vivo.incallui",
                "com.vivo.dialer" -> CallState.INCOMING
                
                // Google devices
                "com.google.android.dialer",
                "com.google.android.incallui" -> CallState.INCOMING
                
                // Motorola devices
                "com.motorola.dialer",
                "com.motorola.incallui" -> CallState.INCOMING
                
                // Huawei devices
                "com.huawei.contacts",
                "com.huawei.incallui" -> CallState.INCOMING
                
                // Default fallback
                else -> CallState.INCOMING
            }
        }
    }
    
    /**
     * Extract caller name and number from notification text
     * Enhanced for Indian phone numbers and multi-language names
     * Special handling for Samsung dialer notifications
     */
    private fun extractCallerInfo(fullText: String, phoneNumberFromExtras: String? = null): Pair<String?, String>? {
        var text = fullText.trim()
        
        // For multi-line text (like Samsung: "Call\nNanna garu\nIncoming call"),
        // extract just the name line and number
        val lines = text.split("\n").map { it.trim() }
            .filter { 
                it.isNotEmpty() && 
                !it.equals("null", ignoreCase = true) &&
                !it.equals("call", ignoreCase = true) && 
                !it.contains("incoming", ignoreCase = true) && 
                !it.contains("missed", ignoreCase = true) &&
                !it.matches(Regex("\\d{1,2}:\\d{2}\\s?(am|pm)", RegexOption.IGNORE_CASE))
            }
        
        // If we have multiple lines, use the first non-empty line as the caller name
        if (lines.isNotEmpty()) {
            text = lines.joinToString(" ").trim()
        }
        
        Log.d(TAG, "Extracting caller info from: '$text'")
        
        // Samsung dialer specific patterns (most common in India)
        val samsungPatterns = listOf(
            // Pattern 1: "John Doe" or "John Doe +91 9876543210"
            Pattern.compile("^([A-Za-z\\s\u0900-\u097F\u0980-\u09FF\u0A00-\u0A7F\u0A80-\u0AFF\u0B00-\u0B7F\u0B80-\u0BFF\u0C00-\u0C7F\u0C80-\u0CFF\u0D00-\u0D7F\u0D80-\u0DFF\u0E00-\u0E7F\u0E80-\u0EFF\u0F00-\u0FFF]+?)(?:\\s*\\+?\\d{10,})?$"),
            
            // Pattern 2: "Incoming call from John Doe"
            Pattern.compile("(?:incoming call from|call from|calling from)\\s*([A-Za-z\\s\u0900-\u097F\u0980-\u09FF\u0A00-\u0A7F\u0A80-\u0AFF\u0B00-\u0B7F\u0B80-\u0BFF\u0C00-\u0C7F\u0C80-\u0CFF\u0D00-\u0D7F\u0D80-\u0DFF\u0E00-\u0E7F\u0E80-\u0EFF\u0F00-\u0FFF]+)", Pattern.CASE_INSENSITIVE),
            
            // Pattern 3: "John Doe is calling"
            Pattern.compile("([A-Za-z\\s\u0900-\u097F\u0980-\u09FF\u0A00-\u0A7F\u0A80-\u0AFF\u0B00-\u0B7F\u0B80-\u0BFF\u0C00-\u0C7F\u0C80-\u0CFF\u0D00-\u0D7F\u0D80-\u0DFF\u0E00-\u0E7F\u0E80-\u0EFF\u0F00-\u0FFF]+)\\s+(?:is calling|calling)", Pattern.CASE_INSENSITIVE)
        )
        
        // Try Samsung patterns first
        for (pattern in samsungPatterns) {
            val matcher = pattern.matcher(text)
            if (matcher.find()) {
                val potentialName = matcher.group(1)?.trim()
                val number = phoneNumberFromExtras ?: extractNumberFromText(text)
                
                Log.d(TAG, "Samsung pattern matched: name='$potentialName', number='$number'")
                
                if (potentialName != null && potentialName.isNotEmpty() && 
                    !potentialName.matches(Regex("\\d+")) &&
                    !potentialName.matches(Regex("[^A-Za-z\u0900-\u097F\u0980-\u09FF\u0A00-\u0A7F\u0A80-\u0AFF\u0B00-\u0B7F\u0B80-\u0BFF\u0C00-\u0C7F\u0C80-\u0CFF\u0D00-\u0D7F\u0D80-\u0DFF\u0E00-\u0E7F\u0E80-\u0EFF\u0F00-\u0FFF]+"))) {
                    return Pair(potentialName, number ?: "Unknown")
                }
            }
        }
        
        // Indian phone number patterns (most common in India)
        val indianMobilePattern = Pattern.compile(
            "(\\+?91[-\\s]?)?([6-9]\\d{9})"  // Indian mobile: +91-9876543210 or 9876543210
        )
        
        val indianLandlinePattern = Pattern.compile(
            "(\\+?91[-\\s]?)?([0-9]{2,4}[-\\s]?[0-9]{6,8})"  // Indian landline: +91-11-12345678
        )
        
        // International patterns (fallback)
        val internationalPattern = Pattern.compile(
            "(\\+?\\d{1,4}[-\\s]?\\d{1,4}[-\\s]?\\d{1,4}[-\\s]?\\d{1,4})"
        )
        
        var callerName: String? = null
        var callerNumber: String = "Unknown"
        
        // Try Indian mobile patterns first (most common)
        val indianMobileMatcher = indianMobilePattern.matcher(text)
        if (indianMobileMatcher.find()) {
            callerNumber = indianMobileMatcher.group(0)?.trim() ?: "Unknown"
        } else {
            // Try Indian landline patterns
            val indianLandlineMatcher = indianLandlinePattern.matcher(text)
            if (indianLandlineMatcher.find()) {
                callerNumber = indianLandlineMatcher.group(0)?.trim() ?: "Unknown"
            } else {
                // Fallback to international patterns
                val intlMatcher = internationalPattern.matcher(text)
                if (intlMatcher.find()) {
                    callerNumber = intlMatcher.group(1)?.trim() ?: "Unknown"
                }
            }
        }
        
        // Extract name - support Indian languages and English
        // Pattern includes Unicode ranges for Indian scripts
        val namePattern = Pattern.compile(
            "^([A-Za-z\\s\u0900-\u097F\u0980-\u09FF\u0A00-\u0A7F\u0A80-\u0AFF\u0B00-\u0B7F\u0B80-\u0BFF\u0C00-\u0C7F\u0C80-\u0CFF\u0D00-\u0D7F\u0D80-\u0DFF\u0E00-\u0E7F\u0E80-\u0EFF\u0F00-\u0FFF]+?)(?:\\s*\\+?\\d{10,})?$"
        )
        val nameMatcher = namePattern.matcher(text)
        
        if (nameMatcher.find()) {
            val potentialName = nameMatcher.group(1)?.trim()
            // Check if it looks like a name (not just numbers or special chars)
            if (potentialName != null && potentialName.length > 1 && 
                !potentialName.matches(Regex("\\d+")) &&
                !potentialName.matches(Regex("[^A-Za-z\u0900-\u097F\u0980-\u09FF\u0A00-\u0A7F\u0A80-\u0AFF\u0B00-\u0B7F\u0B80-\u0BFF\u0C00-\u0C7F\u0C80-\u0CFF\u0D00-\u0D7F\u0D80-\u0DFF\u0E00-\u0E7F\u0E80-\u0EFF\u0F00-\u0FFF]+"))) {
                callerName = potentialName
            }
        }
        
        // If no name found, try common Indian name patterns
        if (callerName == null) {
            callerName = extractNameFromIndianPatterns(text)
        }
        
        // If still no name found, use number as display name
        if (callerName == null || callerName.isEmpty()) {
            callerName = callerNumber
        }
        
        // If we have phone number from extras, use that instead
        if (phoneNumberFromExtras != null && phoneNumberFromExtras.isNotEmpty()) {
            callerNumber = phoneNumberFromExtras
            Log.d(TAG, "Using phone number from notification extras: $callerNumber")
        }
        
        // If we still don't have a number, and we have a name, that's okay
        // Samsung and some OEMs don't include numbers in notifications for privacy
        if (callerNumber == "Unknown" && callerName != null && callerName.isNotEmpty()) {
            Log.i(TAG, "⚠️ Phone number not available in notification (privacy/security restriction)")
            Log.i(TAG, "✅ Caller name is available: $callerName")
        }
        
        Log.d(TAG, "Final extraction result: name='$callerName', number='$callerNumber'")
        return Pair(callerName, callerNumber)
    }
    
    /**
     * Extract phone number from text using multiple patterns
     */
    private fun extractNumberFromText(text: String): String? {
        val patterns = listOf(
            // Pattern 1: Indian mobile with country code
            Pattern.compile("(\\+91[-\\s]?[6-9]\\d{9})"),
            // Pattern 2: Indian mobile without country code
            Pattern.compile("([6-9]\\d{9})"),
            // Pattern 3: Indian landline with country code
            Pattern.compile("(\\+91[-\\s]?[0-9]{2,4}[-\\s]?[0-9]{6,8})"),
            // Pattern 4: Indian landline without country code
            Pattern.compile("([0-9]{2,4}[-\\s]?[0-9]{6,8})"),
            // Pattern 5: International format
            Pattern.compile("(\\+\\d{10,15})"),
            // Pattern 6: Generic phone number
            Pattern.compile("([0-9]{10,15})")
        )
        
        for (pattern in patterns) {
            val matcher = pattern.matcher(text)
            if (matcher.find()) {
                val number = matcher.group(0)?.trim()
                if (number != null && number.length >= 10) {
                    return number
                }
            }
        }
        return null
    }
    
    /**
     * Extract name from common Indian notification patterns
     */
    private fun extractNameFromIndianPatterns(text: String): String? {
        val patterns = listOf(
            // English patterns
            Pattern.compile("call from ([A-Za-z\\s]+)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("([A-Za-z\\s]+) is calling", Pattern.CASE_INSENSITIVE),
            Pattern.compile("incoming call:?\\s*([A-Za-z\\s]+)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("missed call from ([A-Za-z\\s]+)", Pattern.CASE_INSENSITIVE),
            
            // Hindi patterns
            Pattern.compile("कॉल फ्रॉम ([\\u0900-\\u097F\\s]+)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("([\\u0900-\\u097F\\s]+) कॉलिंग", Pattern.CASE_INSENSITIVE),
            Pattern.compile("आने वाला कॉल:?\\s*([\\u0900-\\u097F\\s]+)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("मिस्ड कॉल फ्रॉम ([\\u0900-\\u097F\\s]+)", Pattern.CASE_INSENSITIVE),
            
            // Bengali patterns
            Pattern.compile("কল ফ্রম ([\\u0980-\\u09FF\\s]+)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("([\\u0980-\\u09FF\\s]+) কলিং", Pattern.CASE_INSENSITIVE),
            Pattern.compile("আসন্ন কল:?\\s*([\\u0980-\\u09FF\\s]+)", Pattern.CASE_INSENSITIVE),
            
            // Tamil patterns
            Pattern.compile("அழைப்பு இருந்து ([\\u0B80-\\u0BFF\\s]+)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("([\\u0B80-\\u0BFF\\s]+) அழைக்கிறது", Pattern.CASE_INSENSITIVE),
            Pattern.compile("வரும் அழைப்பு:?\\s*([\\u0B80-\\u0BFF\\s]+)", Pattern.CASE_INSENSITIVE),
            
            // Telugu patterns
            Pattern.compile("కాల్ ఫ్రమ్ ([\\u0C00-\\u0C7F\\s]+)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("([\\u0C00-\\u0C7F\\s]+) కాల్ చేస్తోంది", Pattern.CASE_INSENSITIVE),
            Pattern.compile("వచ్చే కాల్:?\\s*([\\u0C00-\\u0C7F\\s]+)", Pattern.CASE_INSENSITIVE)
        )
        
        for (pattern in patterns) {
            val matcher = pattern.matcher(text)
            if (matcher.find()) {
                val name = matcher.group(1)?.trim()
                if (name != null && name.isNotEmpty() && name.length > 1) {
                    return name
                }
            }
        }
        
        return null
    }
    
    /**
     * Check if notification is a phone call notification
     * Supports all major Indian device brands and languages
     */
    fun isPhoneCallNotification(packageName: String, title: String?, text: String?): Boolean {
        val phonePackages = listOf(
            // Android Standard
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
            "com.android.incallui",
            
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
            "com.android.calllog"
        )
        
        if (packageName in phonePackages) {
            return true
        }
        
        // Additional keyword detection for unknown packages
        val fullText = "$title $text".lowercase()
        val phoneKeywords = listOf(
            // English keywords
            "calling", "incoming", "missed", "call", "phone", "ringing",
            
            // Hindi keywords (India's national language)
            "आने वाला", "मिस्ड कॉल", "कॉल", "फोन", "रिंगिंग", "कॉलिंग",
            
            // Bengali keywords (West Bengal)
            "কল", "মিসড কল", "কলিং", "ফোন", "আসন্ন কল",
            
            // Tamil keywords (Tamil Nadu)
            "அழைப்பு", "தவறிய அழைப்பு", "வரும் அழைப்பு", "தொலைபேசி",
            
            // Telugu keywords (Andhra Pradesh, Telangana)
            "కాల్", "మిస్డ్ కాల్", "వచ్చే కాల్", "ఫోన్",
            
            // Gujarati keywords (Gujarat)
            "કોલ", "મિસ્ડ કોલ", "ફોન",
            
            // Marathi keywords (Maharashtra)
            "कॉल", "मिस्ड कॉल", "फोन",
            
            // Punjabi keywords (Punjab)
            "ਕਾਲ", "ਮਿਸਡ ਕਾਲ", "ਫੋਨ"
        )
        
        return phoneKeywords.any { keyword -> fullText.contains(keyword) }
    }
}


