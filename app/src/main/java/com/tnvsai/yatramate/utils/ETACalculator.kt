package com.tnvsai.yatramate.utils

import com.tnvsai.yatramate.model.Direction

/**
 * Utility class for calculating Estimated Time of Arrival (ETA) based on distance and maneuver type
 */
object ETACalculator {
    
    /**
     * Calculate ETA based on distance and maneuver type
     * @param distance Distance string (e.g., "200m", "1.2km")
     * @param direction Navigation direction for context-aware speed calculation
     * @return Formatted ETA string (e.g., "2 min", "1h 15m")
     */
    fun calculateETA(distance: String, direction: Direction? = null): String {
        val distanceMeters = parseDistanceToMeters(distance)
        
        // Context-aware speed based on maneuver type
        val averageSpeed = when (direction) {
            Direction.SHARP_LEFT, Direction.SHARP_RIGHT -> 20f // Slower for sharp turns
            Direction.U_TURN -> 15f // Very slow for U-turns
            Direction.ROUNDABOUT_LEFT, Direction.ROUNDABOUT_RIGHT, Direction.ROUNDABOUT_STRAIGHT -> 25f
            Direction.MERGE_LEFT, Direction.MERGE_RIGHT -> 35f // Highway merging
            Direction.KEEP_LEFT, Direction.KEEP_RIGHT -> 40f // Highway driving
            Direction.DESTINATION_REACHED -> 0f // No movement
            else -> 30f // Default city speed
        }
        
        if (averageSpeed <= 0) return "Arrived"
        
        val speedMs = averageSpeed / 3.6f // Convert km/h to m/s
        val timeSeconds = distanceMeters / speedMs
        val minutes = (timeSeconds / 60).toInt()
        
        return when {
            minutes < 1 -> "< 1 min"
            minutes < 60 -> "$minutes min"
            else -> {
                val hours = minutes / 60
                val remainingMinutes = minutes % 60
                if (remainingMinutes == 0) "${hours}h" else "${hours}h ${remainingMinutes}m"
            }
        }
    }
    
    /**
     * Parse distance string to meters
     * @param distance Distance string (e.g., "200m", "1.2km")
     * @return Distance in meters
     */
    private fun parseDistanceToMeters(distance: String): Int {
        return when {
            distance.contains("km") -> {
                val value = distance.replace("km", "").toFloatOrNull() ?: 0f
                (value * 1000).toInt()
            }
            distance.contains("m") -> {
                distance.replace("m", "").toIntOrNull() ?: 0
            }
            else -> 0
        }
    }
    
    /**
     * Calculate ETA for test scenarios with custom speed
     * @param distance Distance string
     * @param customSpeed Custom speed in km/h
     * @return Formatted ETA string
     */
    fun calculateETAWithSpeed(distance: String, customSpeed: Float): String {
        val distanceMeters = parseDistanceToMeters(distance)
        
        if (customSpeed <= 0) return "Calculating..."
        
        val speedMs = customSpeed / 3.6f // Convert km/h to m/s
        val timeSeconds = distanceMeters / speedMs
        val minutes = (timeSeconds / 60).toInt()
        
        return when {
            minutes < 1 -> "< 1 min"
            minutes < 60 -> "$minutes min"
            else -> {
                val hours = minutes / 60
                val remainingMinutes = minutes % 60
                if (remainingMinutes == 0) "${hours}h" else "${hours}h ${remainingMinutes}m"
            }
        }
    }
}


