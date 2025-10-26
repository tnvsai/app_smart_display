package com.example.smart.notification

import android.app.Notification
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.smart.ble.WorkingBLEService
import com.example.smart.model.NavigationData
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

        // Route notification based on package name
        when (packageName) {
            "com.google.android.apps.maps" -> handleGoogleMapsNotification(sbn, title, text, bigText)
            "com.android.server.telecom", "com.android.dialer" -> handlePhoneNotification(sbn, title, text, bigText)
            // Future: Music apps
            else -> Log.d(TAG, "Notification from other app: $packageName")
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
        // Future implementation for phone call notifications
        Log.d(TAG, "Phone notification received - future feature")
        
        // Store notification for debugging
        storeNotificationForDebugging(sbn.packageName, title, text, bigText, false, false, null)
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
