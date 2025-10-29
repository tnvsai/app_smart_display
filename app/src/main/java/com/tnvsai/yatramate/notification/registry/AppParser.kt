package com.tnvsai.yatramate.notification.registry

import com.tnvsai.yatramate.model.NavigationData

/**
 * Interface for app-specific notification parsers
 * Allows plugin-style addition of new navigation apps
 */
interface AppParser {
    /**
     * Check if this parser can handle a notification from the given package
     */
    fun canParse(packageName: String, title: String?, text: String?): Boolean
    
    /**
     * Parse navigation data from notification
     * @param title Notification title
     * @param text Notification text
     * @param bigText Notification big text (expanded view)
     * @return Parsed NavigationData or null if parsing fails
     */
    fun parseNavigation(title: String?, text: String?, bigText: String?): NavigationData?
    
    /**
     * Check if the notification text appears to be a navigation notification
     * @param text Notification text to check
     * @return true if this looks like navigation content
     */
    fun isNavigationNotification(text: String): Boolean
}
