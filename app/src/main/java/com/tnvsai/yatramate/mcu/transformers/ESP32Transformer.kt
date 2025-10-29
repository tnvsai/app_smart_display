package com.tnvsai.yatramate.mcu.transformers

import com.google.gson.Gson
import com.tnvsai.yatramate.config.ConfigManager
import com.tnvsai.yatramate.config.models.MCUFormat
import com.tnvsai.yatramate.mcu.DataTransformer
import com.tnvsai.yatramate.model.CallState
import com.tnvsai.yatramate.model.Direction
import com.tnvsai.yatramate.model.NavigationData
import com.tnvsai.yatramate.model.PhoneCallData
import android.util.Log
import java.util.HashMap

/**
 * ESP32-specific data transformer
 * Uses JSON format compatible with the ESP32 firmware
 */
class ESP32Transformer(private val format: MCUFormat) : DataTransformer {
    
    companion object {
        private const val TAG = "ESP32Transformer"
    }
    
    private val gson = Gson()
    
    override fun transformNavigation(data: NavigationData): String {
        try {
            Log.d(TAG, "=== TRANSFORMING NAVIGATION DATA ===")
            Log.d(TAG, "Input data: $data")
            Log.d(TAG, "Format: $format")
            
            // Map direction using configuration
            val directionStr = mapDirection(data.direction)
            Log.d(TAG, "Mapped direction: $directionStr")
            
            // Extract distance as numeric value in meters
            val distance = extractDistanceInMeters(data.distance)
            Log.d(TAG, "Extracted distance: $distance")
            
            // Create JSON data using HashMap
            val jsonData = HashMap<String, Any>()
            jsonData["type"] = "NAVIGATION"
            jsonData["direction"] = directionStr
            jsonData["distance"] = distance
            jsonData["maneuver"] = data.maneuver ?: ""
            
            // Add ETA if available
            if (data.eta != null) {
                jsonData["eta"] = data.eta
            }
            
            Log.d(TAG, "JSON data map: $jsonData")
            
            val result = gson.toJson(jsonData)
            Log.d(TAG, "Transformed navigation data: $result")
            return result
        } catch (e: Exception) {
            Log.e(TAG, "Error transforming navigation data: ${e.message}", e)
            Log.e(TAG, "Exception details: ${e.javaClass.simpleName}")
            e.printStackTrace()
            return "{}"
        }
    }
    
    override fun transformPhoneCall(data: PhoneCallData): String {
        try {
            Log.d(TAG, "=== TRANSFORMING PHONE CALL DATA ===")
            Log.d(TAG, "Input data: $data")
            Log.d(TAG, "Format: $format")
            
            // Map call state using configuration
            val callStateStr = mapCallState(data.callState)
            Log.d(TAG, "Mapped call state: $callStateStr")
            
            // Create JSON data using HashMap to avoid type inference issues
            val jsonData = HashMap<String, Any>()
            jsonData["type"] = "phone_call"
            jsonData["caller_name"] = data.callerName ?: ""
            jsonData["caller_number"] = data.callerNumber
            jsonData["call_state"] = callStateStr
            jsonData["duration"] = data.duration
            
            Log.d(TAG, "JSON data map: $jsonData")
            
            val result = gson.toJson(jsonData)
            Log.d(TAG, "Transformed phone call data: $result")
            return result
        } catch (e: Exception) {
            Log.e(TAG, "Error transforming phone call data: ${e.message}", e)
            Log.e(TAG, "Exception details: ${e.javaClass.simpleName}")
            e.printStackTrace()
            return "{}"
        }
    }
    
    override fun transformNotification(type: String, data: Map<String, Any>): String {
        try {
            val typeConfig = format.notificationTypes[type]
            if (typeConfig == null) {
                Log.w(TAG, "Unknown notification type: $type")
                return gson.toJson(mapOf("type" to "unknown", "data" to data))
            }
            
            val transformedData = mutableMapOf<String, Any>()
            transformedData["type"] = typeConfig.type
            
            // Map fields based on configuration
            typeConfig.fields.forEach { field ->
                data[field]?.let { value ->
                    transformedData[field] = value
                }
            }
            
            val result = gson.toJson(transformedData)
            Log.d(TAG, "Transformed notification data ($type): $result")
            return result
        } catch (e: Exception) {
            Log.e(TAG, "Error transforming notification data: ${e.message}", e)
            return "{}"
        }
    }
    
    override fun getMaxPayloadSize(): Int {
        return format.maxPayload.takeIf { it > 0 } ?: 512  // Default to 512 if config not loaded
    }
    
    /**
     * Map Direction enum to MCU string representation
     */
    private fun mapDirection(direction: Direction?): String {
        if (direction == null) return "straight"
        
        // Direct mapping without using config (which may not be loaded)
        return when (direction) {
            Direction.LEFT -> "left"
            Direction.RIGHT -> "right"
            Direction.STRAIGHT -> "straight"
            Direction.U_TURN -> "uturn"
            Direction.SHARP_LEFT -> "sharp_left"
            Direction.SHARP_RIGHT -> "sharp_right"
            Direction.SLIGHT_LEFT -> "slight_left"
            Direction.SLIGHT_RIGHT -> "slight_right"
            Direction.ROUNDABOUT_LEFT -> "roundabout_left"
            Direction.ROUNDABOUT_RIGHT -> "roundabout_right"
            Direction.ROUNDABOUT_STRAIGHT -> "roundabout_straight"
            Direction.MERGE_LEFT -> "merge_left"
            Direction.MERGE_RIGHT -> "merge_right"
            Direction.KEEP_LEFT -> "keep_left"
            Direction.KEEP_RIGHT -> "keep_right"
            Direction.DESTINATION_REACHED -> "destination"
            Direction.WAYPOINT_REACHED -> "waypoint"
            else -> "straight"
        }
    }
    
    /**
     * Map CallState enum to MCU string representation
     */
    private fun mapCallState(callState: CallState): String {
        // Direct mapping without using config (which may not be loaded)
        return when (callState) {
            CallState.INCOMING -> "INCOMING"
            CallState.ONGOING -> "ONGOING"
            CallState.MISSED -> "MISSED"
            CallState.ENDED -> "ENDED"
        }
    }
    
    /**
     * Extract distance value in meters
     */
    private fun extractDistanceInMeters(distance: String?): Int {
        if (distance.isNullOrBlank()) return 0
        
        val cleanDistance = distance.trim().lowercase()
        
        return when {
            cleanDistance.endsWith("km") -> {
                val value = cleanDistance.replace("km", "").trim().toFloatOrNull() ?: 0f
                (value * 1000).toInt()
            }
            cleanDistance.endsWith("m") -> {
                cleanDistance.replace("m", "").trim().toIntOrNull() ?: 0
            }
            cleanDistance.endsWith("mi") -> {
                val value = cleanDistance.replace("mi", "").trim().toFloatOrNull() ?: 0f
                (value * 1609.34).toInt()
            }
            cleanDistance.endsWith("ft") -> {
                val value = cleanDistance.replace("ft", "").trim().toFloatOrNull() ?: 0f
                (value * 0.3048).toInt()
            }
            else -> {
                cleanDistance.toIntOrNull() ?: 0
            }
        }
    }
}
