package com.example.smart.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.smart.MainActivity
import com.example.smart.R
import com.example.smart.ble.WorkingBLEService
import com.example.smart.notification.NotificationListenerService

/**
 * Foreground service to keep the navigation monitoring active
 */
class NavigationService : Service() {
    
    companion object {
        private const val TAG = "NavigationService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "navigation_service_channel"
        private const val CHANNEL_NAME = "Navigation Service"
        const val ACTION_STOP_SERVICE = "com.example.smart.STOP_SERVICE"
        const val ACTION_START_SERVICE = "com.example.smart.START_SERVICE"
        
        // Static instance to share BLE service
        @Volatile
        private var instance: NavigationService? = null
        fun getInstance(): NavigationService? = instance
        
        fun startService(context: Context) {
            val intent = Intent(context, NavigationService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        fun stopService(context: Context) {
            val intent = Intent(context, NavigationService::class.java)
            context.stopService(intent)
        }
    }
    
    private var bleService: WorkingBLEService? = null
    private var notificationManager: NotificationManager? = null
    
    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "NavigationService created")
        
        // Set static instance
        instance = this
        
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
        
        // Initialize BLE service
        bleService = WorkingBLEService(this)
        
        // Set BLE service in notification listener
        NotificationListenerService.setBLEService(bleService!!)
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_SERVICE -> {
                Log.i(TAG, "Stop action received from notification")
                
                // Disconnect BLE
                bleService?.cleanup()
                
                // Null BLE reference in notification listener
                NotificationListenerService.setBLEService(null)
                
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START_SERVICE -> {
                Log.i(TAG, "Start action received from notification")
                
                // Ensure BLE service is initialized (if it was null for some reason)
                if (bleService == null) {
                    Log.w(TAG, "BLE service was null, reinitializing...")
                    bleService = WorkingBLEService(this)
                    NotificationListenerService.setBLEService(bleService!!)
                }
                
                // Start BLE scanning to connect to MCU
                bleService?.startScanning()
                
                // Restart foreground notification
                startForeground(NOTIFICATION_ID, createNotification("Connecting to MCU..."))
                
                return START_STICKY
            }
        }
        
        Log.i(TAG, "NavigationService started")
        
        // Start BLE scanning
        bleService?.startScanning()
        
        // Start foreground service
        startForeground(NOTIFICATION_ID, createNotification("Navigation monitoring active"))
        
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "NavigationService destroyed")
        
        // Clear static instance
        instance = null
        
        // Clean up BLE service
        bleService?.cleanup()
        bleService = null
    }
    
    /**
     * Create notification channel for Android O+
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Navigation monitoring service"
                setShowBadge(false)
            }
            
            notificationManager?.createNotificationChannel(channel)
        }
    }
    
    /**
     * Create notification for foreground service
     */
    private fun createNotification(contentText: String): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Stop Service Action
        val stopIntent = Intent(this, NavigationService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Start Service Action
        val startIntent = Intent(this, NavigationService::class.java).apply {
            action = ACTION_START_SERVICE
        }
        val startPendingIntent = PendingIntent.getService(
            this,
            2,
            startIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Navigation Monitor")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(R.drawable.ic_launcher_foreground, "Stop", stopPendingIntent)
            .addAction(R.drawable.ic_launcher_foreground, "Start", startPendingIntent)
            .build()
    }
    
    /**
     * Update notification content
     */
    fun updateNotification(contentText: String) {
        val notification = createNotification(contentText)
        notificationManager?.notify(NOTIFICATION_ID, notification)
    }
    
    /**
     * Get BLE service instance
     */
    fun getBLEService(): WorkingBLEService? = bleService
}
