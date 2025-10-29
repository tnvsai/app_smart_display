package com.tnvsai.yatramate.notification.parsers

import com.tnvsai.yatramate.model.NavigationData
import com.tnvsai.yatramate.notification.NotificationParser
import com.tnvsai.yatramate.notification.registry.AppParser
import android.util.Log

/**
 * Parser for Google Maps navigation notifications
 * Uses the existing NotificationParser for actual parsing logic
 */
class GoogleMapsParser : AppParser {
    
    companion object {
        private const val TAG = "GoogleMapsParser"
    }
    
    override fun canParse(packageName: String, title: String?, text: String?): Boolean {
        return packageName == "com.google.android.apps.maps" ||
               NotificationParser.isGoogleMapsNotification(packageName, title)
    }
    
    override fun parseNavigation(title: String?, text: String?, bigText: String?): NavigationData? {
        // Combine all text sources
        val fullText = buildString {
            title?.let { append("$it ") }
            text?.let { append("$it ") }
            bigText?.let { append("$it ") }
        }.trim()
        
        if (fullText.isEmpty()) {
            Log.d(TAG, "Empty notification text")
            return null
        }
        
        // Use the existing NotificationParser to parse the text
        return NotificationParser.parseNotification(fullText)
    }
    
    override fun isNavigationNotification(text: String): Boolean {
        return NotificationParser.isNavigationNotification(text)
    }
}
