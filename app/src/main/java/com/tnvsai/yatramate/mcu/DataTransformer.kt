package com.tnvsai.yatramate.mcu

import com.tnvsai.yatramate.model.NavigationData
import com.tnvsai.yatramate.model.PhoneCallData

/**
 * Interface for transforming parsed data into MCU-specific formats
 * Allows support for different microcontroller protocols and data formats
 */
interface DataTransformer {
    /**
     * Transform navigation data to MCU format
     * @param data NavigationData to transform
     * @return String representation of data in MCU format (e.g., JSON)
     */
    fun transformNavigation(data: NavigationData): String
    
    /**
     * Transform phone call data to MCU format
     * @param data PhoneCallData to transform
     * @return String representation of data in MCU format (e.g., JSON)
     */
    fun transformPhoneCall(data: PhoneCallData): String
    
    /**
     * Transform generic notification data to MCU format
     * @param type Notification type ID (e.g., "weather", "message")
     * @param data Map of extracted notification data
     * @return String representation of data in MCU format (e.g., JSON)
     */
    fun transformNotification(type: String, data: Map<String, Any>): String
    
    /**
     * Get maximum payload size for this transformer
     * @return Maximum bytes allowed in a single transmission
     */
    fun getMaxPayloadSize(): Int
}
