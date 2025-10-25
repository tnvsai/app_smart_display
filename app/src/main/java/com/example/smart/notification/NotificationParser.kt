package com.example.smart.notification

import com.example.smart.model.Direction
import com.example.smart.model.NavigationData
import android.util.Log
import java.util.regex.Pattern

/**
 * Parser for extracting navigation data from Google Maps notifications
 */
object NotificationParser {
    
    private const val TAG = "NotificationParser"
    
    // Patterns for extracting navigation information
    private val directionPatterns = mapOf(
        Direction.LEFT to listOf(
            "turn left", "left turn", "go left", "veer left", "bear left",
            "take left", "head left", "exit left", "merge left", "left at",
            "turn left at", "left onto", "left on", "left in", "left for"
        ),
        Direction.RIGHT to listOf(
            "turn right", "right turn", "go right", "veer right", "bear right",
            "take right", "head right", "exit right", "merge right", "right at",
            "turn right at", "right onto", "right on", "right in", "right for"
        ),
        Direction.STRAIGHT to listOf(
            "go straight", "continue straight", "straight ahead", "keep straight",
            "proceed straight", "head straight", "continue on", "continue",
            "stay straight", "follow road", "head north", "head south", 
            "head east", "head west", "head", "north", "south", "east", "west"
        ),
        Direction.U_TURN to listOf(
            "u-turn", "u turn", "make a u-turn", "turn around", "flip a u-turn",
            "make u-turn", "u turn at"
        )
    )
    
    private val distancePattern = Pattern.compile(
        "\\b(\\d+(?:\\.\\d+)?)\\s*(m|meters?|km|kilometers?|mi|miles?|ft|feet?)\\b",
        Pattern.CASE_INSENSITIVE
    )
    
    private val maneuverPatterns = listOf(
        "roundabout", "traffic circle", "rotary",
        "exit", "ramp", "on-ramp", "off-ramp",
        "merge", "lane change", "change lanes",
        "fork", "split", "junction", "intersection",
        "highway", "freeway", "motorway",
        "bridge", "tunnel", "overpass", "underpass"
    )
    
    /**
     * Parse navigation data from notification text
     */
    fun parseNotification(notificationText: String): NavigationData? {
        if (notificationText.isBlank()) {
            Log.d(TAG, "Empty notification text")
            return null
        }
        
        Log.d(TAG, "Parsing notification: $notificationText")
        
        val direction = extractDirection(notificationText)
        val distance = extractDistance(notificationText)
        val maneuver = extractManeuver(notificationText)
        
        // Only return data if we found at least direction or distance
        if (direction != null || distance != null) {
            val navigationData = NavigationData(
                direction = direction,
                distance = distance,
                maneuver = maneuver
            )
            Log.i(TAG, "Parsed navigation data: $navigationData")
            return navigationData
        }
        
        Log.d(TAG, "No navigation data found in notification")
        return null
    }
    
    /**
     * Extract direction from notification text
     */
    private fun extractDirection(text: String): Direction? {
        val lowerText = text.lowercase()
        
        for ((direction, patterns) in directionPatterns) {
            for (pattern in patterns) {
                if (lowerText.contains(pattern)) {
                    Log.d(TAG, "Found direction: $direction")
                    return direction
                }
            }
        }
        
        return null
    }
    
    /**
     * Extract distance from notification text
     */
    private fun extractDistance(text: String): String? {
        val matcher = distancePattern.matcher(text)
        
        if (matcher.find()) {
            val value = matcher.group(1)
            val unit = matcher.group(2)?.lowercase()
            
            val distance = when (unit) {
                "m", "meter", "meters" -> "${value}m"
                "km", "kilometer", "kilometers" -> "${value}km"
                "mi", "mile", "miles" -> "${value}mi"
                "ft", "foot", "feet" -> "${value}ft"
                else -> "${value}${unit}"
            }
            
            Log.d(TAG, "Found distance: $distance")
            return distance
        }
        
        return null
    }
    
    /**
     * Extract maneuver type from notification text
     */
    private fun extractManeuver(text: String): String? {
        val lowerText = text.lowercase()
        
        for (pattern in maneuverPatterns) {
            if (lowerText.contains(pattern)) {
                Log.d(TAG, "Found maneuver: $pattern")
                return pattern.replaceFirstChar { it.uppercase() }
            }
        }
        
        return null
    }
    
    /**
     * Check if notification is from Google Maps
     */
    fun isGoogleMapsNotification(packageName: String, title: String?): Boolean {
        return packageName == "com.google.android.apps.maps" ||
                title?.contains("Google Maps", ignoreCase = true) == true ||
                title?.contains("Navigation", ignoreCase = true) == true
    }
    
    /**
     * Check if notification contains navigation information
     */
    fun isNavigationNotification(text: String): Boolean {
        val lowerText = text.lowercase()
        
        val navigationKeywords = listOf(
            "turn", "left", "right", "straight", "continue", "go",
            "exit", "merge", "ramp", "highway", "street", "road",
            "miles", "meters", "km", "feet", "yards",
            "in", "at", "onto", "toward", "via", "head",
            "north", "south", "east", "west", "navigate",
            "direction", "route", "destination", "arrive"
        )
        
        val isNavigation = navigationKeywords.any { lowerText.contains(it) }
        
        Log.d(TAG, "=== NAVIGATION DETECTION DEBUG ===")
        Log.d(TAG, "Input text: '$text'")
        Log.d(TAG, "Lowercase text: '$lowerText'")
        Log.d(TAG, "Is navigation: $isNavigation")
        
        if (isNavigation) {
            val matchedKeywords = navigationKeywords.filter { lowerText.contains(it) }
            Log.d(TAG, "Matched keywords: $matchedKeywords")
        }
        
        return isNavigation
    }
}
