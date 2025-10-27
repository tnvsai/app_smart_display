package com.example.smart.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import com.example.smart.model.Direction
import com.example.smart.model.NavigationData
import com.example.smart.model.PhoneCallData
import com.example.smart.model.CallState
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Working BLE Service based on proven SimpleBLEService
 * Includes message queuing and integration with main app
 */
class WorkingBLEService(private val context: Context) {
    
    companion object {
        private const val TAG = "WorkingBLEService"
        private const val ESP32_DEVICE_NAME = "ESP32_BLE"
        private const val SERVICE_UUID = "12345678-1234-1234-1234-1234567890ab"
        private const val CHARACTERISTIC_UUID = "abcd1234-5678-90ab-cdef-1234567890ab"
        private const val SCAN_TIMEOUT = 10000L
    }
    
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private val bluetoothLeScanner: BluetoothLeScanner? = bluetoothAdapter?.bluetoothLeScanner
    
    private var bluetoothGatt: BluetoothGatt? = null
    private var navigationCharacteristic: BluetoothGattCharacteristic? = null
    private var isScanning = false
    private var isConnected = false
    
    // Store only the latest data (no queue - we only need current navigation info)
    private var lastNavigationData: NavigationData? = null
    private var lastPhoneCallData: PhoneCallData? = null
    private val handler = Handler(Looper.getMainLooper())
    private val gson = Gson()
    
    // State flows for UI updates (compatible with existing MainActivity)
    private val _connectionStatus = MutableStateFlow(BLEConnectionStatus())
    val connectionStatus: StateFlow<BLEConnectionStatus> = _connectionStatus.asStateFlow()
    
    private val _isScanning = MutableStateFlow(false)
    val isScanningState: StateFlow<Boolean> = _isScanning.asStateFlow()
    
    // Transmission logging
    private val _transmissionLog = MutableStateFlow<List<String>>(emptyList())
    val transmissionLog: StateFlow<List<String>> = _transmissionLog.asStateFlow()
    
    // Statistics tracking
    private var totalMessagesSent = 0
    private var sessionMessagesSent = 0
    private var lastMessageTimestamp: Long? = null
    private var messagesSentSuccess = 0
    private var messagesSentFailed = 0
    
    private val _transmissionStats = MutableStateFlow(TransmissionStats())
    val transmissionStats: StateFlow<TransmissionStats> = _transmissionStats.asStateFlow()
    
    // Connection history
    private val _connectionHistory = MutableStateFlow<List<ConnectionHistoryEntry>>(emptyList())
    val connectionHistory: StateFlow<List<ConnectionHistoryEntry>> = _connectionHistory.asStateFlow()
    
    private var currentConnectionStart: Long? = null
    
    data class BLEConnectionStatus(
        val isConnected: Boolean = false,
        val deviceName: String? = null,
        val deviceAddress: String? = null
    )
    
    data class TransmissionStats(
        val totalSent: Int = 0,
        val sessionSent: Int = 0,
        val successRate: Float = 0f,
        val lastMessageTime: String? = null
    )
    
    data class ConnectionHistoryEntry(
        val deviceName: String,
        val deviceAddress: String,
        val connectedAt: Long,
        val disconnectedAt: Long? = null,
        val duration: Long? = null
    )
    
    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val deviceName = device.name ?: "Unknown"
            
            Log.d(TAG, "Found device: $deviceName (${device.address})")
            
            if (deviceName.contains(ESP32_DEVICE_NAME, ignoreCase = true)) {
                Log.i(TAG, "✅ Found ESP32 device: $deviceName")
                stopScanning()
                connectToDevice(device)
            }
        }
        
        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "❌ BLE scan failed with error: $errorCode")
            isScanning = false
            _isScanning.value = false
        }
    }
    
    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            Log.i(TAG, "=== GATT CONNECTION STATE CHANGE ===")
            Log.i(TAG, "Device: ${gatt.device.name} (${gatt.device.address})")
            Log.i(TAG, "Status: $status (0=SUCCESS)")
            Log.i(TAG, "New State: $newState (2=CONNECTED, 0=DISCONNECTED)")
            
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        Log.i(TAG, "✅ GATT CONNECTION SUCCESSFUL!")
                        isConnected = true
                        bluetoothGatt = gatt
                        
                        val deviceName = gatt.device.name ?: "ESP32_BLE"
                        val deviceAddress = gatt.device.address
                        
                        _connectionStatus.value = BLEConnectionStatus(
                            isConnected = true,
                            deviceName = deviceName,
                            deviceAddress = deviceAddress
                        )
                        
                        // Add to connection history
                        addConnectionHistory(deviceName, deviceAddress, true)
                        
                        Log.i(TAG, "Starting service discovery...")
                        val discoveryStarted = gatt.discoverServices()
                        Log.i(TAG, "Service discovery started: $discoveryStarted")
                    } else {
                        Log.e(TAG, "❌ GATT connection failed with status: $status")
                        isConnected = false
                        _connectionStatus.value = _connectionStatus.value.copy(isConnected = false)
                    }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.i(TAG, "❌ GATT DISCONNECTED")
                    
                    // Add to connection history before clearing
                    val deviceName = gatt.device.name ?: "ESP32_BLE"
                    val deviceAddress = gatt.device.address
                    addConnectionHistory(deviceName, deviceAddress, false)
                    
                    isConnected = false
                    bluetoothGatt = null
                    navigationCharacteristic = null
                    _connectionStatus.value = BLEConnectionStatus(
                        isConnected = false,
                        deviceName = null,
                        deviceAddress = null
                    )
                }
            }
        }
        
        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            Log.i(TAG, "=== SERVICES DISCOVERED ===")
            Log.i(TAG, "Status: $status")
            
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val services = gatt.services
                Log.i(TAG, "Found ${services.size} services")
                
                val targetServiceUUID = UUID.fromString(SERVICE_UUID)
                val service = gatt.getService(targetServiceUUID)
                
                if (service != null) {
                    Log.i(TAG, "✅ Found target service: $SERVICE_UUID")
                    
                    val targetCharUUID = UUID.fromString(CHARACTERISTIC_UUID)
                    navigationCharacteristic = service.getCharacteristic(targetCharUUID)
                    
                    if (navigationCharacteristic != null) {
                        Log.i(TAG, "✅ Found target characteristic: $CHARACTERISTIC_UUID")
                        val properties = navigationCharacteristic!!.properties
                        Log.i(TAG, "Characteristic properties: $properties")
                        Log.i(TAG, "Supports WRITE: ${(properties and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0}")
                        Log.i(TAG, "🎉 READY TO SEND DATA!")
                        
                        // Send latest data if available
                        sendLatestDataIfConnected()
                    } else {
                        Log.e(TAG, "❌ Target characteristic not found")
                    }
                } else {
                    Log.e(TAG, "❌ Target service not found")
                    Log.e(TAG, "Available services:")
                    services.forEach { svc ->
                        Log.e(TAG, "  - ${svc.uuid}")
                    }
                }
            } else {
                Log.e(TAG, "❌ Service discovery failed with status: $status")
            }
        }
        
        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "✅ Data written successfully to ESP32!")
            } else {
                Log.e(TAG, "❌ Failed to write data: $status")
            }
        }
    }
    
    private fun hasPermissions(): Boolean {
        return ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
               ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
               ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }
    
    @SuppressLint("MissingPermission")
    fun startScanning() {
        Log.i(TAG, "=== STARTING BLE CONNECTION ===")
        
        if (!hasPermissions()) {
            Log.e(TAG, "❌ Missing required permissions")
            return
        }
        
        if (bluetoothAdapter?.isEnabled != true) {
            Log.e(TAG, "❌ Bluetooth is not enabled")
            return
        }
        
        if (isScanning) {
            Log.d(TAG, "Already scanning")
            return
        }
        
        if (isConnected) {
            Log.d(TAG, "Already connected")
            return
        }
        
        val scanFilter = ScanFilter.Builder()
            .setDeviceName(ESP32_DEVICE_NAME)
            .build()
        
        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        
        try {
            bluetoothLeScanner?.startScan(listOf(scanFilter), scanSettings, scanCallback)
            isScanning = true
            _isScanning.value = true
            Log.i(TAG, "Started scanning for ESP32 device...")
            
            // Stop scanning after timeout
            handler.postDelayed({
                if (isScanning) {
                    stopScanning()
                    Log.i(TAG, "Scan timeout - stopping scan")
                }
            }, SCAN_TIMEOUT)
        } catch (e: SecurityException) {
            Log.e(TAG, "❌ Security exception during scan: ${e.message}")
        }
    }
    
    @SuppressLint("MissingPermission")
    private fun stopScanning() {
        if (isScanning) {
            try {
                bluetoothLeScanner?.stopScan(scanCallback)
                isScanning = false
                _isScanning.value = false
                Log.i(TAG, "Stopped scanning")
            } catch (e: SecurityException) {
                Log.e(TAG, "❌ Security exception during stop scan: ${e.message}")
            }
        }
    }
    
    @SuppressLint("MissingPermission")
    private fun connectToDevice(device: BluetoothDevice) {
        Log.i(TAG, "=== CONNECTING TO DEVICE ===")
        Log.i(TAG, "Device: ${device.name} (${device.address})")
        
        try {
            bluetoothGatt = device.connectGatt(context, false, gattCallback)
            Log.i(TAG, "GATT connection initiated - waiting for callback...")
        } catch (e: SecurityException) {
            Log.e(TAG, "❌ Security exception during connection: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Exception during connection: ${e.message}")
        }
    }
    
    @SuppressLint("MissingPermission")
    fun sendNavigationData(navigationData: NavigationData) {
        // Add transmission log
        val timestamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
        val logMessage = "[$timestamp] TX: dir=${navigationData.direction?.name}, dist=${navigationData.distance}, man=${navigationData.maneuver}"
        Log.i(TAG, logMessage)
        _transmissionLog.value = (_transmissionLog.value + logMessage).takeLast(50)
        
        Log.i(TAG, "=== SENDING NAVIGATION DATA ===")
        Log.i(TAG, "Data: $navigationData")
        Log.i(TAG, "isConnected: $isConnected")
        Log.i(TAG, "navigationCharacteristic: $navigationCharacteristic")
        
        // Store latest data instead of queuing
        lastNavigationData = navigationData
        
        if (!isConnected || navigationCharacteristic == null) {
            Log.w(TAG, "Not connected - storing for later send")
            return
        }
        
        try {
            // Format data as JSON for flexibility
            val direction = when (navigationData.direction) {
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
            
            // Extract numeric distance value (convert to meters)
            val distance = extractDistanceInMeters(navigationData.distance)
            val maneuver = navigationData.maneuver ?: ""
            val icon = when (navigationData.direction) {
                Direction.LEFT -> "arrow_left"
                Direction.RIGHT -> "arrow_right"
                Direction.STRAIGHT -> "arrow_up"
                Direction.U_TURN -> "arrow_uturn"
                Direction.SHARP_LEFT -> "arrow_sharp_left"
                Direction.SHARP_RIGHT -> "arrow_sharp_right"
                Direction.SLIGHT_LEFT -> "arrow_slight_left"
                Direction.SLIGHT_RIGHT -> "arrow_slight_right"
                Direction.ROUNDABOUT_LEFT, Direction.ROUNDABOUT_RIGHT, Direction.ROUNDABOUT_STRAIGHT -> "roundabout"
                Direction.MERGE_LEFT, Direction.MERGE_RIGHT -> "merge"
                Direction.KEEP_LEFT, Direction.KEEP_RIGHT -> "lane"
                Direction.DESTINATION_REACHED -> "destination_flag"
                Direction.WAYPOINT_REACHED -> "waypoint"
                else -> "arrow_up"
            }
            
            // Create JSON data
            val jsonData = mutableMapOf(
                "type" to navigationData.type.displayName,
                "direction" to direction,
                "distance" to distance,
                "maneuver" to maneuver,
                "icon" to icon
            )
            
            // Add ETA if available (remove speed)
            navigationData.eta?.let { jsonData["eta"] = it }
            
            val dataString = gson.toJson(jsonData)
            val data = dataString.toByteArray()
            
            Log.i(TAG, "=== BLE JSON DATA TRANSMISSION DEBUG ===")
            Log.i(TAG, "Original NavigationData: $navigationData")
            Log.i(TAG, "JSON data: $dataString")
            Log.i(TAG, "Data bytes: ${data.contentToString()}")
            Log.i(TAG, "Data length: ${data.size}")
            
            navigationCharacteristic?.value = data
            val writeResult = bluetoothGatt?.writeCharacteristic(navigationCharacteristic)
            Log.i(TAG, "Write result: $writeResult")
            
            if (writeResult == true) {
                Log.i(TAG, "✅ Data sent successfully!")
                updateStats(true)
            } else {
                Log.e(TAG, "❌ Failed to send data")
                updateStats(false)
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error sending data: ${e.message}")
            updateStats(false)
        }
    }
    
    /**
     * Send phone call data to ESP32
     */
    fun sendPhoneCallData(phoneCallData: PhoneCallData) {
        // Store latest data instead of queuing
        lastPhoneCallData = phoneCallData
        
        if (!isConnected) {
            Log.w(TAG, "Not connected - storing for later send")
            return
        }
        
        try {
            // Create JSON data for phone call
            val jsonData = mutableMapOf(
                "type" to "phone_call",
                "caller_name" to (phoneCallData.callerName ?: ""),
                "caller_number" to phoneCallData.callerNumber,
                "call_state" to phoneCallData.callState.displayName,
                "duration" to phoneCallData.duration
            )
            
            val dataString = gson.toJson(jsonData)
            val data = dataString.toByteArray()
            
            Log.i(TAG, "=== BLE PHONE CALL DATA TRANSMISSION DEBUG ===")
            Log.i(TAG, "Original PhoneCallData: $phoneCallData")
            Log.i(TAG, "JSON data: $dataString")
            Log.i(TAG, "Data bytes: ${data.contentToString()}")
            Log.i(TAG, "Data length: ${data.size}")
            
            navigationCharacteristic?.value = data
            val writeResult = bluetoothGatt?.writeCharacteristic(navigationCharacteristic)
            Log.i(TAG, "Write result: $writeResult")
            
            if (writeResult == true) {
                Log.i(TAG, "✅ Phone call data sent successfully!")
                updateStats(true)
            } else {
                Log.e(TAG, "❌ Failed to send phone call data")
                updateStats(false)
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error sending phone call data: ${e.message}")
            updateStats(false)
        }
    }
    
    private fun updateStats(success: Boolean) {
        totalMessagesSent++
        sessionMessagesSent++
        if (success) messagesSentSuccess++ else messagesSentFailed++
        lastMessageTimestamp = System.currentTimeMillis()
        
        _transmissionStats.value = TransmissionStats(
            totalSent = totalMessagesSent,
            sessionSent = sessionMessagesSent,
            successRate = if (totalMessagesSent > 0) (messagesSentSuccess.toFloat() / totalMessagesSent * 100) else 0f,
            lastMessageTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(lastMessageTimestamp!!))
        )
    }
    
    fun resetSessionStats() {
        sessionMessagesSent = 0
    }
    
    fun addConnectionHistory(deviceName: String, deviceAddress: String, connected: Boolean) {
        if (connected) {
            currentConnectionStart = System.currentTimeMillis()
        } else if (currentConnectionStart != null) {
            val entry = ConnectionHistoryEntry(
                deviceName = deviceName,
                deviceAddress = deviceAddress,
                connectedAt = currentConnectionStart!!,
                disconnectedAt = System.currentTimeMillis(),
                duration = System.currentTimeMillis() - currentConnectionStart!!
            )
            _connectionHistory.value = (_connectionHistory.value + entry).takeLast(10)
            currentConnectionStart = null
        }
    }
    
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
            else -> {
                cleanDistance.toIntOrNull() ?: 0
            }
        }
    }
    
    @SuppressLint("MissingPermission")
    private fun sendLatestDataIfConnected() {
        Log.i(TAG, "Sending latest data if connected...")
        
        if (isConnected && navigationCharacteristic != null) {
            lastNavigationData?.let {
                Log.i(TAG, "Sending latest navigation data")
                sendNavigationData(it)
            }
            
            lastPhoneCallData?.let {
                Log.i(TAG, "Sending latest phone call data")
                sendPhoneCallData(it)
            }
        } else {
            Log.d(TAG, "Not connected, skipping")
        }
    }
    
    @SuppressLint("MissingPermission")
    fun disconnect() {
        Log.i(TAG, "=== DISCONNECTING ===")
        
        if (bluetoothGatt != null) {
            bluetoothGatt?.disconnect()
            bluetoothGatt?.close()
            bluetoothGatt = null
        }
        
        isConnected = false
        navigationCharacteristic = null
        _connectionStatus.value = BLEConnectionStatus(
            isConnected = false,
            deviceName = null,
            deviceAddress = null
        )
        Log.i(TAG, "Disconnected")
    }
    
    fun cleanup() {
        Log.i(TAG, "=== CLEANUP ===")
        stopScanning()
        disconnect()
    }
    
    // Additional methods for compatibility with MainActivity
    fun forceConnectionStatusUpdate() {
        Log.i(TAG, "=== FORCING CONNECTION STATUS UPDATE ===")
        Log.i(TAG, "Current isConnected: $isConnected")
        Log.i(TAG, "Current bluetoothGatt: $bluetoothGatt")
        Log.i(TAG, "Current navigationCharacteristic: $navigationCharacteristic")
        
        // Check actual BLE connection state
        if (bluetoothGatt != null) {
            val actualState = bluetoothGatt!!.getConnectionState(bluetoothAdapter?.getRemoteDevice(bluetoothGatt!!.device.address))
            Log.i(TAG, "Actual BLE connection state: $actualState")
            Log.i(TAG, "Is actually connected: ${actualState == BluetoothProfile.STATE_CONNECTED}")
            
            // Update isConnected based on actual state
            val wasConnected = isConnected
            isConnected = (actualState == BluetoothProfile.STATE_CONNECTED)
            
            Log.i(TAG, "Connection state changed: $wasConnected -> $isConnected")
        }
        
        // Force update the connection status
        _connectionStatus.value = BLEConnectionStatus(
            isConnected = isConnected,
            deviceName = bluetoothGatt?.device?.name ?: "ESP32_BLE",
            deviceAddress = bluetoothGatt?.device?.address
        )
        
        Log.i(TAG, "Updated connection status: ${_connectionStatus.value}")
    }
    
    fun forceConnectionAttempt() {
        Log.i(TAG, "=== FORCING CONNECTION ATTEMPT ===")
        Log.i(TAG, "Current state: isConnected=$isConnected, bluetoothGatt=$bluetoothGatt")
        
        if (isConnected) {
            Log.i(TAG, "Already connected, disconnecting first...")
            disconnect()
        }
        
        Log.i(TAG, "Starting fresh connection attempt...")
        startScanning()
    }
    
    fun forceProcessQueuedMessages() {
        // This method is kept for compatibility but now just sends latest data
        Log.i(TAG, "=== SENDING LATEST DATA ===")
        Log.i(TAG, "isConnected: $isConnected")
        Log.i(TAG, "navigationCharacteristic: $navigationCharacteristic")
        
        if (isConnected && navigationCharacteristic != null) {
            Log.i(TAG, "Sending latest data...")
            lastNavigationData?.let { sendNavigationData(it) }
            lastPhoneCallData?.let { sendPhoneCallData(it) }
        } else {
            Log.i(TAG, "Not connected, skipping")
        }
    }
}
