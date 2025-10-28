package com.tnvsai.yatramate.notification

import com.tnvsai.yatramate.model.Direction
import com.tnvsai.yatramate.model.NavigationData
import com.tnvsai.yatramate.utils.ETACalculator
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
        ),
        Direction.SHARP_LEFT to listOf(
            "sharp left", "sharp left turn", "turn sharp left", "veer sharp left"
        ),
        Direction.SHARP_RIGHT to listOf(
            "sharp right", "sharp right turn", "turn sharp right", "veer sharp right"
        ),
        Direction.SLIGHT_LEFT to listOf(
            "slight left", "slight left turn", "turn slight left", "veer slight left",
            "bear slight left", "keep slight left"
        ),
        Direction.SLIGHT_RIGHT to listOf(
            "slight right", "slight right turn", "turn slight right", "veer slight right",
            "bear slight right", "keep slight right"
        ),
        Direction.MERGE_LEFT to listOf(
            "merge left", "merge onto left", "merge to left lane", "left merge"
        ),
        Direction.MERGE_RIGHT to listOf(
            "merge right", "merge onto right", "merge to right lane", "right merge"
        ),
        Direction.KEEP_LEFT to listOf(
            "keep left", "stay left", "use left lane", "left lane", "keep in left lane"
        ),
        Direction.KEEP_RIGHT to listOf(
            "keep right", "stay right", "use right lane", "right lane", "keep in right lane",
            "use right 2 lanes", "use right lanes"
        ),
        Direction.DESTINATION_REACHED to listOf(
            "arrive at", "you have arrived", "destination is on", "reached your destination",
            "you've arrived", "arrived at destination", "destination reached"
        ),
        Direction.WAYPOINT_REACHED to listOf(
            "waypoint reached", "reached waypoint", "waypoint complete"
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
        
        // Check for special cases first
        val roundaboutResult = detectRoundabout(notificationText)
        val isDestination = detectDestination(notificationText)
        
        val direction = when {
            roundaboutResult != null -> roundaboutResult.first
            isDestination -> Direction.DESTINATION_REACHED
            else -> extractDirection(notificationText)
        }
        
        val distance = extractDistance(notificationText)
        var maneuver = when {
            roundaboutResult != null -> roundaboutResult.second
            isDestination -> "Destination reached"
            else -> extractManeuver(notificationText)
        }
        
        // If no specific maneuver found, use the full notification text as maneuver
        if (maneuver.isNullOrBlank()) {
            // Clean up the maneuver text by removing distance information
            maneuver = cleanManeuverText(notificationText.trim())
            Log.d(TAG, "No specific maneuver found, using cleaned text as maneuver: '$maneuver'")
        }
        
        // Only return data if we found at least direction or distance
        if (direction != null || distance != null) {
            val navigationData = NavigationData(
                direction = direction,
                distance = distance,
                maneuver = maneuver,
                eta = ETACalculator.calculateETA(distance ?: "0m", direction)
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
        Log.d(TAG, "=== DISTANCE EXTRACTION DEBUG ===")
        Log.d(TAG, "Input text: '$text'")
        Log.d(TAG, "Distance pattern: ${distancePattern.pattern()}")
        
        val matcher = distancePattern.matcher(text)
        
        if (matcher.find()) {
            val value = matcher.group(1)
            val unit = matcher.group(2)?.lowercase()
            
            Log.d(TAG, "Matched value: '$value', unit: '$unit'")
            
            val distance = when (unit) {
                "m", "meter", "meters" -> "${value}m"
                "km", "kilometer", "kilometers" -> "${value}km"
                "mi", "mile", "miles" -> "${value}mi"
                "ft", "foot", "feet" -> "${value}ft"
                else -> "${value}${unit}"
            }
            
            Log.d(TAG, "Found distance: $distance")
            return distance
        } else {
            Log.d(TAG, "No distance pattern matched")
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
    
    /**
     * Clean maneuver text by removing distance information
     */
    private fun cleanManeuverText(text: String): String {
        // Remove common distance patterns from the text
        val cleanedText = text
            .replace(Regex("\\b\\d+(?:\\.\\d+)?\\s*(m|meters?|km|kilometers?|mi|miles?|ft|feet?)\\b", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\bin\\s+\\d+\\s*(m|meters?|km|kilometers?|mi|miles?|ft|feet?)\\b", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\b\\d+\\s*(m|meters?|km|kilometers?|mi|miles?|ft|feet?)\\s+", RegexOption.IGNORE_CASE), "")
            .trim()
        
        Log.d(TAG, "Cleaned maneuver text: '$text' -> '$cleanedText'")
        return cleanedText
    }
    
    /**
     * Detect roundabout navigation and determine direction based on exit number
     */
    private fun detectRoundabout(text: String): Pair<Direction, String>? {
        val lowerText = text.lowercase()
        
        // Detect roundabout entry
        if (lowerText.contains("roundabout") || lowerText.contains("traffic circle")) {
            // Extract exit number: "take the 2nd exit"
            val exitPattern = Regex("(\\d+)(st|nd|rd|th)\\s+exit")
            val match = exitPattern.find(lowerText)
            
            if (match != null) {
                val exitNum = match.groupValues[1].toIntOrNull() ?: 0
                // Determine direction based on exit number
                return when {
                    exitNum == 1 -> Pair(Direction.ROUNDABOUT_LEFT, "1st exit")
                    exitNum == 2 -> Pair(Direction.ROUNDABOUT_STRAIGHT, "2nd exit")
                    exitNum >= 3 -> Pair(Direction.ROUNDABOUT_RIGHT, "${exitNum}th exit")
                    else -> Pair(Direction.ROUNDABOUT_STRAIGHT, "roundabout")
                }
            } else {
                // Generic roundabout without specific exit
                return Pair(Direction.ROUNDABOUT_STRAIGHT, "roundabout")
            }
        }
        return null
    }
    
    /**
     * Detect destination arrival
     */
    private fun detectDestination(text: String): Boolean {
        val destinationKeywords = listOf(
            "arrive", "arrived", "destination", "you have arrived",
            "your destination", "reached", "you've arrived"
        )
        return destinationKeywords.any { text.lowercase().contains(it) }
    }
}


