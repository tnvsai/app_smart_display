package com.tnvsai.yatramate.ble

/**
 * BLE constants for ESP32 communication
 */
object BLEConstants {
    // ESP32 device name to scan for
    const val ESP32_DEVICE_NAME = "ESP32_BLE"
    
    // Service UUID for navigation data (matches ESP32 code)
    const val NAVIGATION_SERVICE_UUID = "12345678-1234-1234-1234-1234567890ab"
    
    // Characteristic UUID for writing navigation data (matches ESP32 code)
    const val NAVIGATION_CHARACTERISTIC_UUID = "abcd1234-5678-90ab-cdef-1234567890ab"
    
    // Scan timeout in milliseconds
    const val SCAN_TIMEOUT = 10000L
    
    // Connection timeout in milliseconds
    const val CONNECTION_TIMEOUT = 5000L
    
    // Reconnection delay in milliseconds
    const val RECONNECTION_DELAY = 5000L
    
    // Maximum retry attempts for connection
    const val MAX_RETRY_ATTEMPTS = 3
}


