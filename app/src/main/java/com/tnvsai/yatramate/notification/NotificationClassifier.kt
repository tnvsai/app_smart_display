package com.tnvsai.yatramate.notification

import android.util.Log
import com.tnvsai.yatramate.config.models.NotificationType
import com.tnvsai.yatramate.config.ConfigManager
import com.tnvsai.yatramate.config.NotificationConfigManager
import com.tnvsai.yatramate.model.NavigationData
import com.tnvsai.yatramate.model.PhoneCallData
import java.util.HashMap

/**
 * Classification result with confidence score
 */
data class ClassifiedNotification(
    val type: NotificationType,
    val confidence: Int,
    val extractedData: Map<String, Any>,
    val classificationMethod: String = "config"  // "config", "fallback_pattern", "fallback_keyword"
)

/**
 * Simplified Notification Classifier
 * 
 * Strategy:
 * 1. Try to load from config (if available)
 * 2. Fallback to hardcoded patterns if config fails
 * 3. Always return a classification (never null)
 * 4. Provide easy debugging information
 */
object NotificationClassifier {
    private const val TAG = "NotificationClassifier"
    
    // FALLBACK PATTERNS - Work even if config doesn't load
    private val FALLBACK_PATTERNS = mapOf(
        "phone_call" to FallbackPattern(
            packages = listOf(
                "com.android.dialer",
                "com.samsung.android.dialer",
                "com.google.android.dialer",
                "com.samsung.android.incallui"
            ),
            keywords = listOf("incoming call", "missed call", "call ended", "calling", "phone"),
            titlePatterns = listOf("Phone", "Call", "Dialer")
        ),
        "navigation" to FallbackPattern(
            packages = listOf(
                "com.google.android.apps.maps",
                "com.waze"
            ),
            keywords = listOf("head", "turn", "exit", "destination", "arrived", "continue", "keep", "merge"),
            titlePatterns = listOf("Google Maps", "Waze", "Navigation")
        ),
        "message" to FallbackPattern(
            packages = listOf(
                "com.whatsapp",
                "com.samsung.android.messaging",
                "org.telegram.messenger"
            ),
            keywords = listOf("new message", "sent you", "message from"),
            titlePatterns = listOf("WhatsApp", "Telegram", "Messages")
        ),
        "battery" to FallbackPattern(
            packages = listOf("com.android.systemui"),
            keywords = listOf("charging", "battery", "plugged in", "fast charging"),
            titlePatterns = listOf("Charging", "Battery")
        )
    )
    
    private data class FallbackPattern(
        val packages: List<String>,
        val keywords: List<String>,
        val titlePatterns: List<String>
    )
    
    /**
     * Classify notification - ALWAYS returns a classification (never null)
     * Uses fallback if config isn't available
     */
    fun classifyNotification(
        packageName: String,
        title: String?,
        text: String?,
        bigText: String?
    ): ClassifiedNotification? {
        val allText = "${title ?: ""} ${text ?: ""} ${bigText ?: ""}".trim().lowercase()

        Log.i(TAG, "=== CLASSIFYING NOTIFICATION ===")
        Log.i(TAG, "Package: $packageName")
        Log.i(TAG, "Title: $title")
        Log.i(TAG, "Text: $text")
        Log.i(TAG, "AllText: $allText")
        
        // STEP 1: Try config-based classification (if available)
        val configResult = try {
            classifyWithConfig(packageName, title, text, bigText, allText)
        } catch (e: Exception) {
            Log.w(TAG, "Config classification failed: ${e.message}", e)
            null
        }
        
        if (configResult != null) {
            Log.i(TAG, "✅ Classified using CONFIG: ${configResult.type.id} (confidence: ${configResult.confidence})")
            return configResult
        }
        
        // STEP 2: Fallback to hardcoded patterns
        Log.w(TAG, "⚠️ Config not available, using FALLBACK patterns")
        val fallbackResult = classifyWithFallback(packageName, title, text, bigText, allText)
        
        if (fallbackResult != null) {
            Log.i(TAG, "✅ Classified using FALLBACK: ${fallbackResult.type.id} (confidence: ${fallbackResult.confidence})")
            return fallbackResult
        }
        
        // STEP 3: Last resort - return generic "unknown" type
        Log.w(TAG, "⚠️ No match found, classifying as UNKNOWN")
        return createUnknownType(packageName, title, text, bigText)
    }
    
    /**
     * Classify using config file (original method)
     */
    private fun classifyWithConfig(
        packageName: String,
        title: String?,
        text: String?,
        bigText: String?,
        allText: String
    ): ClassifiedNotification? {
        val allTypes = ConfigManager.getAllNotificationTypes()
        
        if (allTypes.isEmpty()) {
            Log.d(TAG, "Config has 0 types, skipping config classification")
            return null
        }
        
        val enabledTypes = allTypes.filter { it.enabled }
        if (enabledTypes.isEmpty()) {
            Log.d(TAG, "Config has 0 enabled types, skipping config classification")
            return null
        }
        
        // Score each type
        val scoredTypes = enabledTypes.map { type ->
            var score = 0
            
            // Package match (highest priority)
            if (packageName in type.apps) {
                score += 1000
            }
            
            // Title pattern match
            val titleMatches = title?.let { titleText ->
                type.titlePatterns.count { pattern ->
                    titleText.contains(pattern, ignoreCase = true)
                }
            } ?: 0
            score += titleMatches * 50
            
            // Keyword matches
            val keywordMatches = type.keywords.count { keyword ->
                allText.contains(keyword.lowercase())
            }
            score += keywordMatches * 10
            
            Pair(type, score)
        }
        
        val bestMatch = scoredTypes.maxByOrNull { it.second }
        
        if (bestMatch != null && bestMatch.second > 0) {
            val (type, score) = bestMatch
            
            // Check if app is enabled
            try {
                if (!NotificationConfigManager.isAppEnabledForType(type.id, packageName)) {
                    Log.d(TAG, "App $packageName is disabled for type ${type.id}, but returning classification anyway")
                }
            } catch (e: Exception) {
                Log.d(TAG, "Could not check app enabled status: ${e.message}")
            }
            
            val extractedData = extractNotificationData(type, title, text, bigText)
            return ClassifiedNotification(type, score, extractedData, "config")
        }
        
        return null
    }
    
    /**
     * Classify using fallback hardcoded patterns
     */
    private fun classifyWithFallback(
        packageName: String,
        title: String?,
        text: String?,
        bigText: String?,
        allText: String
    ): ClassifiedNotification? {
        // Use data class to hold scored pattern (clearer than Triple)
        data class ScoredPattern(
            val typeId: String,
            val pattern: FallbackPattern,
            val score: Int
        )
        
        val scoredPatterns = FALLBACK_PATTERNS.map { (typeId, pattern) ->
            var score = 0
            
            // Package match
            if (packageName in pattern.packages) {
                score += 1000
            }
            
            // Title pattern match
            val titleMatches = title?.let { titleText ->
                pattern.titlePatterns.count { p ->
                    titleText.contains(p, ignoreCase = true)
                }
            } ?: 0
            score += titleMatches * 50
            
            // Keyword matches
            val keywordMatches = pattern.keywords.count { keyword ->
                allText.contains(keyword.lowercase())
            }
            score += keywordMatches * 10
            
            ScoredPattern(typeId, pattern, score)
        }
        
        val bestMatch = scoredPatterns.maxByOrNull { it.score }
        
        if (bestMatch != null && bestMatch.score > 0) {
            val typeId = bestMatch.typeId
            val pattern = bestMatch.pattern
            val score = bestMatch.score
            
            // Create a synthetic NotificationType for fallback
            val fallbackType = NotificationType(
                id = typeId,
                name = typeId.replace("_", " ").replaceFirstChar { it.uppercase() },
                priority = "medium",
                enabled = true,
                keywords = pattern.keywords,
                apps = pattern.packages,
                enabledApps = emptyList(),
                disabledApps = emptyList(),
                titlePatterns = pattern.titlePatterns,
                mcuType = typeId
            )
            
            val extractedData = extractNotificationData(fallbackType, title, text, bigText)
            return ClassifiedNotification(fallbackType, score, extractedData, "fallback_pattern")
        }
        
        return null
    }
    
    /**
     * Create unknown type classification (never returns null)
     */
    private fun createUnknownType(
        packageName: String,
        title: String?,
        text: String?,
        bigText: String?
    ): ClassifiedNotification {
        val unknownType = NotificationType(
            id = "unknown",
            name = "Unknown",
            priority = "low",
            enabled = false,
            keywords = emptyList(),
            apps = listOf(packageName),
            enabledApps = emptyList(),
            disabledApps = emptyList(),
            titlePatterns = emptyList(),
            mcuType = "unknown"
        )
        
        val extractedData = HashMap<String, Any>().apply {
            put("raw_text", text ?: "")
            put("package", packageName)
            put("title", title ?: "")
        }
        
        return ClassifiedNotification(unknownType, 0, extractedData, "fallback_unknown")
    }
    
    private fun extractNotificationData(
        type: NotificationType,
        title: String?,
        text: String?,
        bigText: String?
    ): Map<String, Any> {
        return when (type.id) {
            "navigation" -> extractNavigationData(title, text, bigText)
            "phone_call" -> extractPhoneCallData(title, text, bigText)
            "message" -> extractMessageData(title, text, bigText)
            "battery" -> extractBatteryData(title, text, bigText)
            "weather" -> extractWeatherData(title, text, bigText)
            else -> {
                val map = HashMap<String, Any>()
                map["raw_text"] = text ?: ""
                map["title"] = title ?: ""
                map
            }
        }
    }
    
    private fun extractNavigationData(title: String?, text: String?, bigText: String?): Map<String, Any> {
        val map = HashMap<String, Any>()
        val fullText = "${text ?: ""} ${bigText ?: ""}".trim()
        
        // Try to extract direction
        val direction = when {
            fullText.contains("turn left", ignoreCase = true) -> "LEFT"
            fullText.contains("turn right", ignoreCase = true) -> "RIGHT"
            fullText.contains("head north", ignoreCase = true) -> "STRAIGHT"
            fullText.contains("go straight", ignoreCase = true) -> "STRAIGHT"
            else -> "STRAIGHT"
        }
        map["direction"] = direction
        
        // Extract distance
        val distanceRegex = Regex("(\\d+)\\s*(m|meters?|km|kilometers?)")
        val distanceMatch = distanceRegex.find(fullText)
        if (distanceMatch != null) {
            map["distance"] = distanceMatch.value
        } else {
            map["distance"] = ""
        }
        
        map["raw_text"] = fullText
        return map
    }
    
    private fun extractPhoneCallData(title: String?, text: String?, bigText: String?): Map<String, Any> {
        val map = HashMap<String, Any>()
        val fullText = "${title ?: ""} ${text ?: ""}".trim()
        
        // Extract caller name
        if (title != null && !title.contains("call", ignoreCase = true)) {
            map["caller"] = title
        } else {
            map["caller"] = text ?: "Unknown"
        }
        
        // Determine call state
        val state = when {
            fullText.contains("missed", ignoreCase = true) -> "MISSED"
            fullText.contains("incoming", ignoreCase = true) -> "INCOMING"
            fullText.contains("ended", ignoreCase = true) -> "ENDED"
            else -> "INCOMING"
        }
        map["state"] = state
        map["raw_text"] = fullText
        
        return map
    }
    
    private fun extractMessageData(title: String?, text: String?, bigText: String?): Map<String, Any> {
        val map = HashMap<String, Any>()
        map["sender"] = title ?: ""
        map["message"] = text ?: bigText ?: ""
        map["raw_text"] = "${title ?: ""} ${text ?: ""}".trim()
        return map
    }
    
    private fun extractBatteryData(title: String?, text: String?, bigText: String?): Map<String, Any> {
        val map = HashMap<String, Any>()
        val fullText = "${title ?: ""} ${text ?: ""}".trim()
        
        // Extract percentage
        val percentageRegex = Regex("(\\d+)%")
        val percentageMatch = percentageRegex.find(fullText)
        if (percentageMatch != null) {
            map["percentage"] = percentageMatch.groupValues[1]
        }
        
        map["is_charging"] = fullText.contains("charging", ignoreCase = true)
        map["raw_text"] = fullText
        
        return map
    }
    
    private fun extractWeatherData(title: String?, text: String?, bigText: String?): Map<String, Any> {
        val map = HashMap<String, Any>()
        map["temperature"] = text ?: ""
        map["raw_text"] = "${title ?: ""} ${text ?: ""}".trim()
        return map
    }
}
