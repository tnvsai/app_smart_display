package com.tnvsai.yatramate.permission

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Handles runtime permissions for Android 12+
 */
class PermissionHandler(private val context: Context) {
    
    companion object {
        private const val TAG = "PermissionHandler"
        
        // Permission request codes
        const val REQUEST_BLE_PERMISSIONS = 1001
        const val REQUEST_LOCATION_PERMISSIONS = 1002
        const val REQUEST_NOTIFICATION_PERMISSIONS = 1003
        const val REQUEST_NOTIFICATION_ACCESS = 1004
        
        // All required permissions
        val BLE_PERMISSIONS = arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_ADVERTISE
        )
        
        val LOCATION_PERMISSIONS = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        
        val NOTIFICATION_PERMISSIONS = arrayOf(
            Manifest.permission.POST_NOTIFICATIONS
        )
    }
    
    /**
     * Check if all BLE permissions are granted
     */
    fun hasBLEPermissions(): Boolean {
        return BLE_PERMISSIONS.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    /**
     * Check if location permissions are granted
     */
    fun hasLocationPermissions(): Boolean {
        return LOCATION_PERMISSIONS.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    /**
     * Check if notification permissions are granted
     */
    fun hasNotificationPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Permission not required for older versions
        }
    }
    
    /**
     * Check if notification access is granted
     */
    fun hasNotificationAccess(): Boolean {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            notificationManager.isNotificationPolicyAccessGranted
        } else {
            true // Not applicable for older versions
        }
    }
    
    /**
     * Check if Bluetooth is enabled
     */
    fun isBluetoothEnabled(): Boolean {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter = bluetoothManager.adapter
        return bluetoothAdapter?.isEnabled == true
    }
    
    /**
     * Check if all required permissions are granted
     */
    fun hasAllPermissions(): Boolean {
        return hasBLEPermissions() && 
               hasLocationPermissions() && 
               hasNotificationPermissions() && 
               hasNotificationAccess() &&
               isBluetoothEnabled()
    }
    
    /**
     * Request BLE permissions
     */
    fun requestBLEPermissions(activity: Activity) {
        if (!hasBLEPermissions()) {
            Log.i(TAG, "Requesting BLE permissions")
            ActivityCompat.requestPermissions(
                activity,
                BLE_PERMISSIONS,
                REQUEST_BLE_PERMISSIONS
            )
        }
    }
    
    /**
     * Request location permissions
     */
    fun requestLocationPermissions(activity: Activity) {
        if (!hasLocationPermissions()) {
            Log.i(TAG, "Requesting location permissions")
            ActivityCompat.requestPermissions(
                activity,
                LOCATION_PERMISSIONS,
                REQUEST_LOCATION_PERMISSIONS
            )
        }
    }
    
    /**
     * Request notification permissions
     */
    fun requestNotificationPermissions(activity: Activity) {
        if (!hasNotificationPermissions()) {
            Log.i(TAG, "Requesting notification permissions")
            ActivityCompat.requestPermissions(
                activity,
                NOTIFICATION_PERMISSIONS,
                REQUEST_NOTIFICATION_PERMISSIONS
            )
        }
    }
    
    /**
     * Open notification access settings
     */
    fun openNotificationAccessSettings(activity: Activity) {
        Log.i(TAG, "Opening notification access settings")
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        activity.startActivityForResult(intent, REQUEST_NOTIFICATION_ACCESS)
    }
    
    /**
     * Open Bluetooth settings
     */
    fun openBluetoothSettings(activity: Activity) {
        Log.i(TAG, "Opening Bluetooth settings")
        val intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
        activity.startActivityForResult(intent, 1005)
    }
    
    /**
     * Open app settings for manual permission granting
     */
    fun openAppSettings(activity: Activity) {
        Log.i(TAG, "Opening app settings")
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        }
        activity.startActivity(intent)
    }
    
    /**
     * Get missing permissions list
     */
    fun getMissingPermissions(): List<String> {
        val missing = mutableListOf<String>()
        
        if (!hasBLEPermissions()) {
            missing.add("Bluetooth permissions")
        }
        
        if (!hasLocationPermissions()) {
            missing.add("Location permissions")
        }
        
        if (!hasNotificationPermissions()) {
            missing.add("Notification permissions")
        }
        
        if (!hasNotificationAccess()) {
            missing.add("Notification access")
        }
        
        if (!isBluetoothEnabled()) {
            missing.add("Bluetooth enabled")
        }
        
        return missing
    }
    
    /**
     * Get permission status summary
     */
    fun getPermissionStatus(): PermissionStatus {
        return PermissionStatus(
            blePermissions = hasBLEPermissions(),
            locationPermissions = hasLocationPermissions(),
            notificationPermissions = hasNotificationPermissions(),
            notificationAccess = hasNotificationAccess(),
            bluetoothEnabled = isBluetoothEnabled(),
            allGranted = hasAllPermissions()
        )
    }
    
    /**
     * Data class for permission status
     */
    data class PermissionStatus(
        val blePermissions: Boolean,
        val locationPermissions: Boolean,
        val notificationPermissions: Boolean,
        val notificationAccess: Boolean,
        val bluetoothEnabled: Boolean,
        val allGranted: Boolean
    ) {
        // Convenience properties for cleaner access
        val hasBLEPermissions get() = blePermissions
        val hasLocationPermissions get() = locationPermissions
        val hasNotificationPermissions get() = notificationPermissions
        val hasNotificationAccess get() = notificationAccess
        val isBluetoothEnabled get() = bluetoothEnabled
        
        fun getStatusText(): String {
            return buildString {
                appendLine("BLE Permissions: ${if (blePermissions) "✓" else "✗"}")
                appendLine("Location Permissions: ${if (locationPermissions) "✓" else "✗"}")
                appendLine("Notification Permissions: ${if (notificationPermissions) "✓" else "✗"}")
                appendLine("Notification Access: ${if (notificationAccess) "✓" else "✗"}")
                appendLine("Bluetooth Enabled: ${if (bluetoothEnabled) "✓" else "✗"}")
            }
        }
    }
}


