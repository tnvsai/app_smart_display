package com.example.smart.notification

import com.example.smart.model.CallState
import com.example.smart.model.PhoneCallData
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
        
        Log.d(TAG, "Parsing phone notification from: $packageName")
        Log.d(TAG, "Title: $title")
        Log.d(TAG, "Text: $text")
        Log.d(TAG, "BigText: $bigText")
        
        // Deduplication - skip duplicate notifications
        val notificationKey = "$packageName:$title:$text"
        val currentTime = System.currentTimeMillis()
        val lastTime = recentNotifications[notificationKey]
        
        if (lastTime != null && (currentTime - lastTime) < DEDUP_TIMEOUT_MS) {
            Log.d(TAG, "Skipping duplicate notification (within ${currentTime - lastTime}ms)")
            return null
        }
        recentNotifications[notificationKey] = currentTime
        
        // Clean up old entries
        recentNotifications.entries.removeIf { currentTime - it.value > DEDUP_TIMEOUT_MS * 2 }
        
        // For Samsung dialer, prefer title over text
        val primaryText = if (packageName.contains("samsung")) {
            title ?: text ?: bigText
        } else {
            title ?: text ?: bigText
        }
        
        // Determine call state first
        val callState = determineCallState(primaryText ?: (text ?: ""), packageName)
        Log.d(TAG, "Detected call state: $callState")
        
        // If it's a MISSED call and primary text is "Missed call", use text instead
        val textToParse = when {
            callState == CallState.MISSED && primaryText?.equals("Missed call", ignoreCase = true) == true -> {
                Log.d(TAG, "Title is 'Missed call' - using text field instead")
                text ?: bigText ?: primaryText
            }
            else -> primaryText
        }
        
        // Clean up text - remove common notification suffixes
        val cleanedText = textToParse?.let { 
            it.replace(" Incoming call", "", ignoreCase = true)
                .replace(" is calling", "", ignoreCase = true)
                .replace(" calling", "", ignoreCase = true)
                .replace("Missed call", "", ignoreCase = true)
                .trim()
        }
        
        Log.d(TAG, "Text to parse: '$textToParse'")
        Log.d(TAG, "Cleaned text: '$cleanedText'")
        
        // Extract caller information from cleaned text
        val callerInfo = cleanedText?.let { extractCallerInfo(it, phoneNumberFromExtras) }
        Log.d(TAG, "Extracted caller info: $callerInfo")
        
        if (callerInfo != null) {
            val phoneCallData = PhoneCallData(
                callerName = callerInfo.first,
                callerNumber = callerInfo.second,
                callState = callState
            )
            Log.i(TAG, "Successfully parsed phone call: $phoneCallData")
            return phoneCallData
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
            // INCOMING CALL PATTERNS
            
            // English patterns
            text.contains("incoming call") || 
            text.contains("calling") ||
            text.contains("ringing") ||
            text.contains("call from") ||
            
            // Hindi patterns (India's national language)
            text.contains("आने वाला कॉल") ||
            text.contains("कॉलिंग") ||
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
        val text = fullText.trim()
        
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
