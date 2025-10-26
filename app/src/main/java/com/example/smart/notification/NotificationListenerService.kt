package com.example.smart.notification

import android.app.Notification
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.smart.ble.WorkingBLEService
import com.example.smart.model.NavigationData
import com.example.smart.notification.PhoneCallParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * Data class to store notification information for debugging
 */
data class NotificationInfo(
    val timestamp: String,
    val packageName: String,
    val title: String?,
    val text: String?,
    val bigText: String?,
    val isGoogleMaps: Boolean,
    val isNavigation: Boolean,
    val parsedData: String?
)

/**
 * Service to listen for Google Maps navigation notifications
 */
class NotificationListenerService : NotificationListenerService() {
    
    companion object {
        private const val TAG = "NotificationListener"
        private var instance: NotificationListenerService? = null
        var bleService: WorkingBLEService? = null
            private set
        
        // Store recent notifications for debugging
        private val recentNotifications = mutableListOf<NotificationInfo>()
        private const val MAX_RECENT_NOTIFICATIONS = 20
        
        // Debug log callback
        var debugLogCallback: ((String) -> Unit)? = null
            private set
        
        fun setBLEService(bleService: WorkingBLEService?) {
            this.bleService = bleService
        }
        
        fun setDebugLogCallback(callback: (String) -> Unit) {
            debugLogCallback = callback
        }
        
        fun getInstance(): NotificationListenerService? = instance
        
        fun getRecentNotifications(): List<NotificationInfo> = recentNotifications.toList()
        
        fun clearRecentNotifications() {
            recentNotifications.clear()
        }
        
        /**
         * Check if we have permission to access notifications (static method)
         */
        fun hasNotificationAccess(): Boolean {
            return try {
                instance?.activeNotifications != null
            } catch (e: SecurityException) {
                false
            }
        }
        
        /**
         * Get all active notifications (static method)
         */
        fun getAllActiveNotifications(): List<StatusBarNotification> {
            return try {
                instance?.activeNotifications?.toList() ?: emptyList()
            } catch (e: SecurityException) {
                emptyList()
            }
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.i(TAG, "NotificationListenerService created")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        instance = null
        Log.i(TAG, "NotificationListenerService destroyed")
    }
    
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)

        val packageName = sbn.packageName
        val notification = sbn.notification
        val title = notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        val bigText = notification.extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()

        // Log ALL notifications for debugging
        Log.i(TAG, "=== NOTIFICATION RECEIVED ===")
        Log.i(TAG, "Package: $packageName")
        Log.i(TAG, "Title: $title")
        Log.i(TAG, "Text: $text")
        Log.i(TAG, "BigText: $bigText")
        Log.i(TAG, "BLE Service Available: ${bleService != null}")
        
        // Enhanced debugging for phone notifications
        if (PhoneCallParser.isPhoneCallNotification(packageName, title, text)) {
            Log.i(TAG, "🔔 PHONE NOTIFICATION DETECTED!")
            Log.i(TAG, "Package matches phone app: $packageName")
        }

        // Route notification based on package name
        when (packageName) {
            "com.google.android.apps.maps" -> handleGoogleMapsNotification(sbn, title, text, bigText)
            
            // Universal phone package routing - All Indian device brands
            "com.android.server.telecom", 
            "com.android.dialer",
            "com.android.incallui",
            "com.android.phone",
            "com.android.telecom",
            
            // Samsung (India's #1 brand - 30% market share)
            "com.samsung.android.dialer",
            "com.samsung.android.incallui",
            "com.samsung.android.phone",
            
            // Xiaomi/Redmi (India's #2 brand - 20% market share)
            "com.miui.phone",
            "com.miui.incallui",
            "com.miui.dialer",
            
            // OnePlus (8% market share)
            "com.oneplus.incallui",
            "com.oneplus.dialer",
            "com.oneplus.phone",
            
            // Realme (12% market share)
            "com.coloros.incallui",
            "com.coloros.dialer",
            "com.coloros.phone",
            
            // Vivo (10% market share)
            "com.vivo.incallui",
            "com.vivo.dialer",
            "com.vivo.phone",
            
            // Oppo (8% market share)
            "com.oppo.incallui",
            "com.oppo.dialer",
            "com.oppo.phone",
            
            // Google Pixel (5% market share)
            "com.google.android.dialer",
            "com.google.android.incallui",
            
            // Motorola (3% market share)
            "com.motorola.dialer",
            "com.motorola.incallui",
            "com.motorola.phone",
            
            // Huawei/Honor
            "com.huawei.contacts",
            "com.huawei.incallui",
            "com.huawei.dialer",
            "com.hihonor.incallui",
            
            // Nokia
            "com.nokia.dialer",
            "com.nokia.incallui",
            
            // Generic fallbacks
            "com.android.contacts",
            "com.android.calllog" -> handlePhoneNotification(sbn, title, text, bigText)
            
            else -> {
                // Check if it's a phone notification by content analysis
                if (PhoneCallParser.isPhoneCallNotification(packageName, title, text)) {
                    Log.i(TAG, "Detected phone notification by content analysis: $packageName")
                    handlePhoneNotification(sbn, title, text, bigText)
                } else {
                    Log.d(TAG, "Notification from other app: $packageName")
                }
            }
        }
    }

    private fun handleGoogleMapsNotification(sbn: StatusBarNotification, title: String?, text: String?, bigText: String?) {
        // Combine all text sources
        val fullText = buildString {
            title?.let { append("$it ") }
            text?.let { append("$it ") }
            bigText?.let { append("$it ") }
        }.trim()

        val isNavigation = NotificationParser.isNavigationNotification(fullText)

        // Parse navigation data if it's a navigation notification
        var parsedData: String? = null
        if (isNavigation) {
            val navigationData = NotificationParser.parseNotification(fullText)
            parsedData = navigationData?.toString()

            if (navigationData != null) {
                Log.i(TAG, "✅ PARSED NAVIGATION DATA: $navigationData")
                
                // Send to debug log
                debugLogCallback?.invoke("Google Maps: ${navigationData.direction?.name} - ${navigationData.distance}")

                // Send to BLE service
                bleService?.let { service ->
                    Log.i(TAG, "Sending to BLE service...")
                    CoroutineScope(Dispatchers.IO).launch {
                        service.sendNavigationData(navigationData)
                    }
                } ?: Log.e(TAG, "❌ BLE SERVICE NOT AVAILABLE!")
            }
        }

        // Store notification for debugging
        storeNotificationForDebugging(sbn.packageName, title, text, bigText, true, isNavigation, parsedData)
    }

    private fun handlePhoneNotification(sbn: StatusBarNotification, title: String?, text: String?, bigText: String?) {
        Log.i(TAG, "=== PHONE NOTIFICATION RECEIVED ===")
        Log.i(TAG, "Package: ${sbn.packageName}")
        Log.i(TAG, "Title: $title")
        Log.i(TAG, "Text: $text")
        Log.i(TAG, "BigText: $bigText")
        
        // Extract phone number from notification extras
        val extras = sbn.notification.extras
        var phoneNumber: String? = null
        
        // Try to get phone number from various common keys
        extras?.let {
            phoneNumber = it.getCharSequence("android.phoneNumber")?.toString()
                ?: it.getCharSequence("android.phone_number")?.toString()
                ?: it.getCharSequence("phone_number")?.toString()
                ?: it.getCharSequence("number")?.toString()
                ?: it.getString("android.phoneNumber")
                ?: it.getString("phone_number")
                ?: it.getString("number")
            
            if (phoneNumber != null) {
                Log.i(TAG, "📞 Phone number found in extras: $phoneNumber")
            } else {
                Log.d(TAG, "No phone number in extras, available keys: ${it.keySet()}")
            }
        }
        
        // Check if it's Samsung dialer specifically
        if (sbn.packageName.contains("samsung")) {
            Log.i(TAG, "🔍 SAMSUNG DIALER DETECTED - Enhanced debugging")
            Log.i(TAG, "Notification ID: ${sbn.id}")
            Log.i(TAG, "Post time: ${sbn.postTime}")
            Log.i(TAG, "Full notification extras: ${sbn.notification.extras}")
            Log.i(TAG, "All extras keys: ${extras?.keySet()}")
        }
        
        // Parse phone call data
        val phoneCallData = PhoneCallParser.parsePhoneNotification(
            sbn.packageName,
            title,
            text,
            bigText,
            phoneNumber
        )
        
        if (phoneCallData != null) {
            Log.i(TAG, "✅ PARSED PHONE CALL DATA: $phoneCallData")
            
            // Send to debug log
            debugLogCallback?.invoke("Phone: ${phoneCallData.callerName} - ${phoneCallData.callState.displayName}")
            
            // Send to BLE service
            bleService?.let { service ->
                Log.i(TAG, "Sending phone call data to BLE service...")
                CoroutineScope(Dispatchers.IO).launch {
                    service.sendPhoneCallData(phoneCallData)
                }
            } ?: Log.e(TAG, "❌ BLE SERVICE NOT AVAILABLE!")
            
            // Store notification for debugging
            storeNotificationForDebugging(
                sbn.packageName, 
                title, 
                text, 
                bigText, 
                false, 
                true, 
                phoneCallData.toString()
            )
        } else {
            Log.w(TAG, "Failed to parse phone call data")
            // Store notification for debugging
            storeNotificationForDebugging(sbn.packageName, title, text, bigText, false, false, null)
        }
    }

    private fun storeNotificationForDebugging(
        packageName: String,
        title: String?,
        text: String?,
        bigText: String?,
        isGoogleMaps: Boolean,
        isNavigation: Boolean,
        parsedData: String?
    ) {
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val notificationInfo = NotificationInfo(
            timestamp = timestamp,
            packageName = packageName,
            title = title,
            text = text,
            bigText = bigText,
            isGoogleMaps = isGoogleMaps,
            isNavigation = isNavigation,
            parsedData = parsedData
        )

        // Add to recent notifications list
        recentNotifications.add(0, notificationInfo) // Add to beginning
        if (recentNotifications.size > MAX_RECENT_NOTIFICATIONS) {
            recentNotifications.removeAt(recentNotifications.size - 1) // Remove oldest
        }

        Log.i(TAG, "Stored notification for debugging. Total stored: ${recentNotifications.size}")
    }
    
    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        super.onNotificationRemoved(sbn)
        Log.d(TAG, "Notification removed: ${sbn.packageName}")
    }
    
    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i(TAG, "Notification listener connected")
    }
    
    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.w(TAG, "Notification listener disconnected")
    }
    
    /**
     * Get all active notifications (for debugging)
     */
    fun getAllActiveNotifications(): List<StatusBarNotification> {
        return try {
            activeNotifications?.toList() ?: emptyList()
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception getting active notifications: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * Check if we have permission to access notifications
     */
    fun hasNotificationAccess(): Boolean {
        return try {
            activeNotifications != null
        } catch (e: SecurityException) {
            false
        }
    }
    
    /**
     * Check if the service is running and has notification access
     */
    fun isServiceRunning(): Boolean {
        return instance != null && hasNotificationAccess()
    }
    
}
