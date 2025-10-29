package com.tnvsai.yatramate.notification.parsers

import com.tnvsai.yatramate.model.NavigationData
import com.tnvsai.yatramate.notification.NotificationParser
import com.tnvsai.yatramate.notification.registry.AppParser
import android.util.Log

/**
 * Generic parser for any navigation app
 * Uses pattern matching based on app configuration
 */
class GenericNavigationParser(
    private val appPattern: com.tnvsai.yatramate.config.models.AppPattern
) : AppParser {
    
    companion object {
        private const val TAG = "GenericNavigationParser"
    }
    
    override fun canParse(packageName: String, title: String?, text: String?): Boolean {
        // Check if package name matches
        if (packageName in appPattern.packageNames) {
            return true
        }
        
        // Check if title matches patterns
        if (title != null) {
            val matched = appPattern.titlePatterns.any { pattern ->
                title.contains(pattern, ignoreCase = true)
            }
            if (matched) return true
        }
        
        // Check if text contains detection keywords
        if (text != null) {
            val lowerText = text.lowercase()
            val matched = appPattern.detectionKeywords.any { keyword ->
                lowerText.contains(keyword.lowercase())
            }
            if (matched) return true
        }
        
        return false
    }
    
    override fun parseNavigation(title: String?, text: String?, bigText: String?): NavigationData? {
        // Combine all text sources
        val fullText = buildString {
            title?.let { append("$it ") }
            text?.let { append("$it ") }
            bigText?.let { append("$it ") }
        }.trim()
        
        if (fullText.isEmpty()) {
            Log.d(TAG, "Empty notification text for app: ${appPattern.name}")
            return null
        }
        
        Log.d(TAG, "Parsing notification from ${appPattern.name}: $fullText")
        
        // Use the existing NotificationParser to parse the text
        return NotificationParser.parseNotification(fullText)
    }
    
    override fun isNavigationNotification(text: String): Boolean {
        return NotificationParser.isNavigationNotification(text)
    }
}
