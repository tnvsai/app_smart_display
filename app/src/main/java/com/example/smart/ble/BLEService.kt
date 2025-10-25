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
import com.example.smart.model.QueuedMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.UUID

/**
 * BLE Service for communicating with ESP32 device
 */
class BLEService(private val context: Context) {
    
    companion object {
        private const val TAG = "BLEService"
    }
    
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private val bluetoothLeScanner: BluetoothLeScanner? = bluetoothAdapter?.bluetoothLeScanner
    
    private var bluetoothGatt: BluetoothGatt? = null
    private var navigationCharacteristic: BluetoothGattCharacteristic? = null
    private var isScanning = false
    private var isConnected = false
    private var retryCount = 0
    
    private val messageQueue = mutableListOf<QueuedMessage>()
    private val handler = Handler(Looper.getMainLooper())
    
    // State flows for UI updates
    private val _connectionStatus = MutableStateFlow(BLEConnectionStatus())
    val connectionStatus: StateFlow<BLEConnectionStatus> = _connectionStatus.asStateFlow()
    
    private val _isScanning = MutableStateFlow(false)
    val isScanningState: StateFlow<Boolean> = _isScanning.asStateFlow()
    
    data class BLEConnectionStatus(
        val isConnected: Boolean = false,
        val deviceName: String? = null,
        val deviceAddress: String? = null,
        val queuedMessages: Int = 0
    )
    
    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val deviceName = device.name ?: "Unknown"
            
            Log.d(TAG, "Found device: $deviceName (${device.address})")
            
            if (deviceName.contains(BLEConstants.ESP32_DEVICE_NAME, ignoreCase = true)) {
                Log.i(TAG, "Found ESP32 device: $deviceName")
                stopScanning()
                connectToDevice(device)
            }
        }
        
        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "BLE scan failed with error: $errorCode")
            _isScanning.value = false
            isScanning = false
        }
    }
    
    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            Log.i(TAG, "=== CONNECTION STATE CHANGE ===")
            Log.i(TAG, "Device: ${gatt.device.name} (${gatt.device.address})")
            Log.i(TAG, "Status: $status (0=SUCCESS, 133=GATT_ERROR, 8=GATT_INSUFFICIENT_AUTHENTICATION)")
            Log.i(TAG, "New State: $newState (2=CONNECTED, 0=DISCONNECTED)")
            Log.i(TAG, "GATT_SUCCESS: ${BluetoothGatt.GATT_SUCCESS}")
            Log.i(TAG, "STATE_CONNECTED: ${BluetoothProfile.STATE_CONNECTED}")
            
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        Log.i(TAG, "✅ Connected to GATT server successfully!")
                        isConnected = true
                        bluetoothGatt = gatt // Store the GATT object - CRITICAL FIX!
                        retryCount = 0
                        
                        // Update connection status immediately
                        _connectionStatus.value = BLEConnectionStatus(
                            isConnected = true,
                            deviceName = gatt.device.name ?: "ESP32_BLE",
                            deviceAddress = gatt.device.address,
                            queuedMessages = messageQueue.size
                        )
                        
                        Log.i(TAG, "Connection status updated: ${_connectionStatus.value}")
                        Log.i(TAG, "Starting service discovery...")
                        
                        // Start service discovery
                        val discoveryStarted = gatt.discoverServices()
                        Log.i(TAG, "Service discovery started: $discoveryStarted")
                    } else {
                        Log.e(TAG, "❌ Connection failed with status: $status")
                        Log.e(TAG, "Common error codes:")
                        Log.e(TAG, "  133 = GATT_ERROR")
                        Log.e(TAG, "  8 = GATT_INSUFFICIENT_AUTHENTICATION")
                        Log.e(TAG, "  22 = GATT_INSUFFICIENT_ENCRYPTION")
                        isConnected = false
                        bluetoothGatt = null
                        _connectionStatus.value = _connectionStatus.value.copy(isConnected = false)
                    }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.i(TAG, "❌ Disconnected from GATT server")
                    isConnected = false
                    bluetoothGatt = null
                    navigationCharacteristic = null
                    _connectionStatus.value = BLEConnectionStatus(
                        isConnected = false,
                        deviceName = null,
                        deviceAddress = null,
                        queuedMessages = messageQueue.size
                    )
                    // Attempt to reconnect after delay
                    handler.postDelayed({
                        if (!isConnected) {
                            Log.i(TAG, "Attempting to reconnect...")
                            startScanning()
                        }
                    }, BLEConstants.RECONNECTION_DELAY)
                }
                else -> {
                    Log.i(TAG, "Other connection state: $newState")
                }
            }
        }
        
        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            Log.i(TAG, "=== SERVICE DISCOVERY DEBUG ===")
            Log.i(TAG, "Discovery status: $status (0=SUCCESS)")
            
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "✅ Services discovered successfully")
                
                // List all available services for debugging
                val services = gatt.services
                Log.i(TAG, "Available services count: ${services.size}")
                
                if (services.isEmpty()) {
                    Log.e(TAG, "❌ No services found!")
                    return
                }
                
                services.forEach { service ->
                    Log.i(TAG, "Service UUID: ${service.uuid}")
                    Log.i(TAG, "Service UUID string: ${service.uuid.toString()}")
                    service.characteristics.forEach { char ->
                        Log.i(TAG, "  Characteristic UUID: ${char.uuid}")
                        Log.i(TAG, "  Characteristic UUID string: ${char.uuid.toString()}")
                        Log.i(TAG, "  Characteristic properties: ${char.properties}")
                    }
                }
                
                // Try to find our service
                val targetServiceUUID = UUID.fromString(BLEConstants.NAVIGATION_SERVICE_UUID)
                val service = gatt.getService(targetServiceUUID)
                
                Log.i(TAG, "Looking for service: ${BLEConstants.NAVIGATION_SERVICE_UUID}")
                Log.i(TAG, "Target service UUID: $targetServiceUUID")
                Log.i(TAG, "Service found: $service")
                
                if (service != null) {
                    Log.i(TAG, "✅ Navigation service found!")
                    
                    // Try to find our characteristic
                    val targetCharUUID = UUID.fromString(BLEConstants.NAVIGATION_CHARACTERISTIC_UUID)
                    navigationCharacteristic = service.getCharacteristic(targetCharUUID)
                    
                    Log.i(TAG, "Looking for characteristic: ${BLEConstants.NAVIGATION_CHARACTERISTIC_UUID}")
                    Log.i(TAG, "Target characteristic UUID: $targetCharUUID")
                    Log.i(TAG, "Characteristic found: $navigationCharacteristic")
                    
                    if (navigationCharacteristic != null) {
                        Log.i(TAG, "✅ Navigation characteristic found and ready!")
                        Log.i(TAG, "Characteristic properties: ${navigationCharacteristic!!.properties}")
                        
                        // Update connection status to show we're fully ready
                        _connectionStatus.value = _connectionStatus.value.copy(
                            isConnected = true,
                            queuedMessages = messageQueue.size
                        )
                        
                        // Process any queued messages
                        processQueuedMessages()
                    } else {
                        Log.e(TAG, "❌ Navigation characteristic not found")
                        Log.e(TAG, "Available characteristics in service:")
                        service.characteristics.forEach { char ->
                            Log.e(TAG, "  - ${char.uuid} (${char.uuid.toString()})")
                        }
                    }
                } else {
                    Log.e(TAG, "❌ Navigation service not found")
                    Log.e(TAG, "Available services:")
                    services.forEach { svc ->
                        Log.e(TAG, "  - ${svc.uuid} (${svc.uuid.toString()})")
                    }
                }
            } else {
                Log.e(TAG, "❌ Service discovery failed with status: $status")
            }
        }
        
        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Data written successfully")
            } else {
                Log.e(TAG, "Failed to write data: $status")
            }
        }
    }
    
    /**
     * Start scanning for ESP32 device
     */
    @SuppressLint("MissingPermission")
    fun startScanning() {
        if (!hasPermissions()) {
            Log.e(TAG, "Missing required permissions")
            return
        }
        
        if (bluetoothAdapter?.isEnabled != true) {
            Log.e(TAG, "Bluetooth is not enabled")
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
            .setDeviceName(BLEConstants.ESP32_DEVICE_NAME)
            .build()
        
        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        
        try {
            bluetoothLeScanner?.startScan(listOf(scanFilter), scanSettings, scanCallback)
            isScanning = true
            _isScanning.value = true
            Log.i(TAG, "Started scanning for ESP32 device")
            
            // Stop scanning after timeout
            handler.postDelayed({
                if (isScanning) {
                    stopScanning()
                }
            }, BLEConstants.SCAN_TIMEOUT)
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception during scan: ${e.message}")
        }
    }
    
    /**
     * Stop scanning for devices
     */
    @SuppressLint("MissingPermission")
    fun stopScanning() {
        if (isScanning) {
            try {
                bluetoothLeScanner?.stopScan(scanCallback)
                isScanning = false
                _isScanning.value = false
                Log.i(TAG, "Stopped scanning")
            } catch (e: SecurityException) {
                Log.e(TAG, "Security exception during stop scan: ${e.message}")
            }
        }
    }
    
    /**
     * Connect to a specific device
     */
    @SuppressLint("MissingPermission")
    private fun connectToDevice(device: BluetoothDevice) {
        if (isConnected) {
            Log.d(TAG, "Already connected to a device")
            return
        }
        
        Log.i(TAG, "=== CONNECTING TO DEVICE ===")
        Log.i(TAG, "Device: ${device.name} (${device.address})")
        Log.i(TAG, "Device type: ${device.type}")
        Log.i(TAG, "Device bond state: ${device.bondState}")
        
        try {
            // Create GATT connection
            bluetoothGatt = device.connectGatt(context, false, gattCallback)
            
            // Update status with device info (but not connected yet)
            _connectionStatus.value = BLEConnectionStatus(
                isConnected = false,
                deviceName = device.name ?: "Unknown",
                deviceAddress = device.address,
                queuedMessages = messageQueue.size
            )
            
            Log.i(TAG, "GATT connection initiated - waiting for callback...")
            Log.i(TAG, "Connection will be established asynchronously")
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception during connection: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Exception during connection: ${e.message}")
        }
    }
    
    /**
     * Send navigation data to ESP32
     */
    @SuppressLint("MissingPermission")
    fun sendNavigationData(navigationData: NavigationData) {
        Log.i(TAG, "=== BLE SEND DEBUG ===")
        Log.i(TAG, "isConnected: $isConnected")
        Log.i(TAG, "navigationCharacteristic: $navigationCharacteristic")
        Log.i(TAG, "bluetoothGatt: $bluetoothGatt")
        
        // Check if we have all required components
        if (bluetoothGatt == null) {
            Log.e(TAG, "❌ BLOCKING SEND - bluetoothGatt is null")
            queueMessage(navigationData)
            return
        }
        
        if (navigationCharacteristic == null) {
            Log.e(TAG, "❌ BLOCKING SEND - navigationCharacteristic is null")
            Log.e(TAG, "This means service discovery failed or characteristic not found")
            queueMessage(navigationData)
            return
        }
        
        if (!isConnected) {
            Log.e(TAG, "❌ BLOCKING SEND - isConnected is false")
            queueMessage(navigationData)
            return
        }
        
        // Double-check actual connection state
        val actualState = bluetoothGatt!!.getConnectionState(bluetoothAdapter?.getRemoteDevice(bluetoothGatt!!.device.address))
        if (actualState != BluetoothProfile.STATE_CONNECTED) {
            Log.e(TAG, "❌ BLOCKING SEND - Actual connection state is not connected: $actualState")
            isConnected = false
            _connectionStatus.value = _connectionStatus.value.copy(isConnected = false)
            queueMessage(navigationData)
            return
        }
        
        Log.i(TAG, "✅ PROCEEDING WITH SEND - All conditions met")
        
        try {
            // Format: direction|distance|maneuver (matches ESP32 expectations)
            val direction = when (navigationData.direction) {
                Direction.LEFT -> "left"
                Direction.RIGHT -> "right"
                Direction.STRAIGHT -> "straight"
                Direction.U_TURN -> "uturn"
                else -> "straight"
            }
            
            // Extract numeric distance value (convert to meters)
            val distance = extractDistanceInMeters(navigationData.distance)
            
            val maneuver = navigationData.maneuver ?: ""
            
            val dataString = "$direction|$distance|$maneuver"
            val data = dataString.toByteArray()
            
            Log.i(TAG, "=== SENDING DATA TO ESP32 ===")
            Log.i(TAG, "Formatted data string: '$dataString'")
            Log.i(TAG, "Data bytes: ${data.contentToString()}")
            Log.i(TAG, "Data length: ${data.size}")
            Log.i(TAG, "Data as string: ${String(data)}")
            
            // Check characteristic properties
            val properties = navigationCharacteristic?.properties ?: 0
            Log.i(TAG, "Characteristic properties: $properties")
            Log.i(TAG, "Supports WRITE: ${(properties and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0}")
            Log.i(TAG, "Supports NOTIFY: ${(properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0}")
            Log.i(TAG, "Supports READ: ${(properties and BluetoothGattCharacteristic.PROPERTY_READ) != 0}")
            
            navigationCharacteristic?.value = data
            
            val writeResult = bluetoothGatt?.writeCharacteristic(navigationCharacteristic)
            Log.i(TAG, "Write characteristic result: $writeResult")
            
            if (writeResult == true) {
                Log.i(TAG, "Successfully sent navigation data: $dataString")
            } else {
                Log.e(TAG, "Failed to write characteristic")
                queueMessage(navigationData)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending navigation data: ${e.message}", e)
            queueMessage(navigationData)
        }
    }
    
    /**
     * Extract distance in meters from distance string
     */
    private fun extractDistanceInMeters(distanceString: String?): Int {
        if (distanceString.isNullOrBlank()) return 0
        
        val regex = """(\d+(?:\.\d+)?)\s*(m|meters?|km|kilometers?|mi|miles?|ft|feet?)""".toRegex(RegexOption.IGNORE_CASE)
        val match = regex.find(distanceString)
        
        if (match != null) {
            val value = match.groupValues[1].toFloatOrNull() ?: return 0
            val unit = match.groupValues[2].lowercase()
            
            return when {
                unit.startsWith("m") -> value.toInt() // meters
                unit.startsWith("km") -> (value * 1000).toInt() // kilometers to meters
                unit.startsWith("mi") -> (value * 1609.34).toInt() // miles to meters
                unit.startsWith("ft") -> (value * 0.3048).toInt() // feet to meters
                else -> value.toInt()
            }
        }
        
        return 0
    }
    
    /**
     * Queue a message for later sending
     */
    private fun queueMessage(navigationData: NavigationData) {
        messageQueue.add(QueuedMessage(navigationData))
        _connectionStatus.value = _connectionStatus.value.copy(
            queuedMessages = messageQueue.size
        )
        Log.d(TAG, "Queued message. Queue size: ${messageQueue.size}")
    }
    
    /**
     * Process all queued messages
     */
    private fun processQueuedMessages() {
        if (messageQueue.isEmpty()) return
        
        Log.i(TAG, "Processing ${messageQueue.size} queued messages")
        val messagesToProcess = messageQueue.toList()
        messageQueue.clear()
        
        messagesToProcess.forEach { queuedMessage ->
            sendNavigationData(queuedMessage.navigationData)
        }
        
        _connectionStatus.value = _connectionStatus.value.copy(queuedMessages = 0)
    }
    
    /**
     * Disconnect from current device
     */
    @SuppressLint("MissingPermission")
    fun disconnect() {
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
        navigationCharacteristic = null
        isConnected = false
        _connectionStatus.value = BLEConnectionStatus()
        stopScanning()
    }
    
    /**
     * Check if required permissions are granted
     */
    private fun hasPermissions(): Boolean {
        return ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.BLUETOOTH_SCAN
        ) == PackageManager.PERMISSION_GRANTED &&
        ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED &&
        ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * Force service discovery (for debugging)
     */
    @SuppressLint("MissingPermission")
    fun forceServiceDiscovery() {
        if (bluetoothGatt != null && isConnected) {
            Log.i(TAG, "Forcing service discovery...")
            bluetoothGatt?.discoverServices()
        } else {
            Log.w(TAG, "Cannot force service discovery - not connected")
        }
    }
    
    /**
     * Force connection status update (for debugging)
     */
    fun forceConnectionStatusUpdate() {
        Log.i(TAG, "=== FORCING CONNECTION STATUS UPDATE ===")
        Log.i(TAG, "Current isConnected: $isConnected")
        Log.i(TAG, "Current bluetoothGatt: $bluetoothGatt")
        Log.i(TAG, "Current navigationCharacteristic: $navigationCharacteristic")
        
        // Check actual BLE connection state
        if (bluetoothGatt != null) {
            val actualState = bluetoothGatt!!.getConnectionState(bluetoothAdapter?.getRemoteDevice(bluetoothGatt!!.device.address))
            Log.i(TAG, "Actual BLE connection state: $actualState")
            Log.i(TAG, "STATE_CONNECTED: ${BluetoothProfile.STATE_CONNECTED}")
            Log.i(TAG, "Is actually connected: ${actualState == BluetoothProfile.STATE_CONNECTED}")
            
            // Update isConnected based on actual state
            val wasConnected = isConnected
            isConnected = (actualState == BluetoothProfile.STATE_CONNECTED)
            
            Log.i(TAG, "Connection state changed: $wasConnected -> $isConnected")
            
            // If we just discovered we're connected, try to find the characteristic
            if (isConnected && !wasConnected) {
                Log.i(TAG, "Just discovered we're connected! Looking for characteristic...")
                findNavigationCharacteristic()
            }
        } else {
            Log.w(TAG, "bluetoothGatt is null - cannot check connection state")
        }
        
        // Force update the connection status
        _connectionStatus.value = BLEConnectionStatus(
            isConnected = isConnected,
            deviceName = bluetoothGatt?.device?.name ?: "ESP32_BLE",
            deviceAddress = bluetoothGatt?.device?.address,
            queuedMessages = messageQueue.size
        )
        
        Log.i(TAG, "Updated connection status: ${_connectionStatus.value}")
        
        // If we're connected but don't have the characteristic, try to discover services
        if (isConnected && navigationCharacteristic == null) {
            Log.i(TAG, "Connected but no characteristic - forcing service discovery...")
            bluetoothGatt?.discoverServices()
        }
    }
    
    /**
     * Try to find the navigation characteristic after connection
     */
    @SuppressLint("MissingPermission")
    private fun findNavigationCharacteristic() {
        if (bluetoothGatt == null) return
        
        val services = bluetoothGatt!!.services
        Log.i(TAG, "Looking for navigation characteristic in ${services.size} services")
        
        val targetServiceUUID = "12345678-1234-1234-1234-1234567890ab"
        val targetCharUUID = "abcd1234-5678-90ab-cdef-1234567890ab"
        
        services.forEach { service ->
            val serviceUUIDString = service.uuid.toString().lowercase()
            Log.i(TAG, "Checking service: $serviceUUIDString")
            if (serviceUUIDString == targetServiceUUID.lowercase()) {
                Log.i(TAG, "Found navigation service!")
                
                service.characteristics.forEach { char ->
                    val charUUIDString = char.uuid.toString().lowercase()
                    Log.i(TAG, "Checking characteristic: $charUUIDString")
                    if (charUUIDString == targetCharUUID.lowercase()) {
                        Log.i(TAG, "Found navigation characteristic!")
                        navigationCharacteristic = char
                        
                        // Update connection status
                        _connectionStatus.value = _connectionStatus.value.copy(isConnected = true, queuedMessages = messageQueue.size)
                        
                        // Process queued messages
                        Log.i(TAG, "Processing ${messageQueue.size} queued messages...")
                        processQueuedMessages()
                        return
                    }
                }
            }
        }
        
        Log.w(TAG, "Navigation characteristic not found, will retry...")
    }
    
    /**
     * Force process all queued messages (for debugging)
     */
    fun forceProcessQueuedMessages() {
        Log.i(TAG, "=== FORCING PROCESS QUEUED MESSAGES ===")
        Log.i(TAG, "Queued messages: ${messageQueue.size}")
        Log.i(TAG, "isConnected: $isConnected")
        Log.i(TAG, "navigationCharacteristic: $navigationCharacteristic")
        
        if (messageQueue.isNotEmpty()) {
            Log.i(TAG, "Processing ${messageQueue.size} queued messages...")
            processQueuedMessages()
        } else {
            Log.i(TAG, "No queued messages to process")
        }
    }
    
    /**
     * Force a connection attempt (for debugging)
     */
    @SuppressLint("MissingPermission")
    fun forceConnectionAttempt() {
        Log.i(TAG, "=== FORCING CONNECTION ATTEMPT ===")
        Log.i(TAG, "Current state: isConnected=$isConnected, bluetoothGatt=$bluetoothGatt")
        
        if (isConnected) {
            Log.i(TAG, "Already connected, disconnecting first...")
            bluetoothGatt?.disconnect()
            bluetoothGatt?.close()
            bluetoothGatt = null
            isConnected = false
        }
        
        Log.i(TAG, "Starting fresh connection attempt...")
        startScanning()
    }
    
    /**
     * Test if we can actually write to the characteristic
     */
    @SuppressLint("MissingPermission")
    fun testCharacteristicWrite() {
        Log.i(TAG, "=== TESTING CHARACTERISTIC WRITE ===")
        
        if (navigationCharacteristic == null) {
            Log.e(TAG, "❌ Characteristic is null - cannot test write")
            Log.e(TAG, "This means service discovery failed")
            return
        }
        
        if (bluetoothGatt == null) {
            Log.e(TAG, "❌ BluetoothGatt is null - cannot test write")
            return
        }
        
        try {
            val testData = "test|123|debug".toByteArray()
            navigationCharacteristic?.value = testData
            
            Log.i(TAG, "Setting characteristic value: ${String(testData)}")
            Log.i(TAG, "Characteristic properties: ${navigationCharacteristic!!.properties}")
            
            val writeResult = bluetoothGatt?.writeCharacteristic(navigationCharacteristic)
            Log.i(TAG, "Write characteristic result: $writeResult")
            
            if (writeResult == true) {
                Log.i(TAG, "✅ Test write initiated successfully")
                Log.i(TAG, "Data should appear in ESP32 Serial Monitor now")
            } else {
                Log.e(TAG, "❌ Test write failed")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Exception during test write: ${e.message}", e)
        }
    }
    
    /**
     * Force a simple test send (bypasses all checks)
     */
    @SuppressLint("MissingPermission")
    fun forceTestSend() {
        Log.i(TAG, "=== FORCE TEST SEND ===")
        
        if (bluetoothGatt == null) {
            Log.e(TAG, "❌ No GATT connection")
            return
        }
        
        if (navigationCharacteristic == null) {
            Log.e(TAG, "❌ No characteristic found")
            return
        }
        
        try {
            val testData = "left|200|force_test".toByteArray()
            Log.i(TAG, "Force sending: ${String(testData)}")
            
            navigationCharacteristic?.value = testData
            val result = bluetoothGatt?.writeCharacteristic(navigationCharacteristic)
            
            Log.i(TAG, "Force send result: $result")
            if (result == true) {
                Log.i(TAG, "✅ Force send successful - check ESP32!")
            } else {
                Log.e(TAG, "❌ Force send failed")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Force send exception: ${e.message}", e)
        }
    }
    
    /**
     * Check if BLE is working at all - basic connectivity test
     */
    @SuppressLint("MissingPermission")
    fun checkBLEBasicFunctionality() {
        Log.i(TAG, "=== BLE BASIC FUNCTIONALITY CHECK ===")
        
        // Check Bluetooth adapter
        if (bluetoothAdapter == null) {
            Log.e(TAG, "❌ Bluetooth adapter is null")
            return
        }
        
        if (!bluetoothAdapter!!.isEnabled) {
            Log.e(TAG, "❌ Bluetooth is not enabled")
            return
        }
        
        Log.i(TAG, "✅ Bluetooth adapter is available and enabled")
        
        // Check if we have a GATT connection
        if (bluetoothGatt == null) {
            Log.e(TAG, "❌ No GATT connection established")
            Log.i(TAG, "This means the app never connected to ESP32")
            return
        }
        
        Log.i(TAG, "✅ GATT connection exists")
        Log.i(TAG, "GATT device: ${bluetoothGatt!!.device.name} (${bluetoothGatt!!.device.address})")
        
        // Check actual connection state
        val actualState = bluetoothGatt!!.getConnectionState(bluetoothAdapter!!.getRemoteDevice(bluetoothGatt!!.device.address))
        Log.i(TAG, "Actual connection state: $actualState")
        Log.i(TAG, "STATE_CONNECTED: ${BluetoothProfile.STATE_CONNECTED}")
        
        if (actualState != BluetoothProfile.STATE_CONNECTED) {
            Log.e(TAG, "❌ Not actually connected to ESP32")
            Log.e(TAG, "This explains why data is not being sent")
            return
        }
        
        Log.i(TAG, "✅ Actually connected to ESP32")
        
        // Check if we have services
        val services = bluetoothGatt!!.services
        Log.i(TAG, "Available services: ${services.size}")
        
        if (services.isEmpty()) {
            Log.e(TAG, "❌ No services discovered")
            Log.e(TAG, "This means service discovery failed")
            return
        }
        
        Log.i(TAG, "✅ Services discovered")
        
        // Check if we have our characteristic
        if (navigationCharacteristic == null) {
            Log.e(TAG, "❌ Navigation characteristic not found")
            Log.e(TAG, "This means the ESP32 service/characteristic setup is wrong")
            return
        }
        
        Log.i(TAG, "✅ Navigation characteristic found")
        Log.i(TAG, "Characteristic UUID: ${navigationCharacteristic!!.uuid}")
        Log.i(TAG, "Characteristic properties: ${navigationCharacteristic!!.properties}")
        
        Log.i(TAG, "🎉 BLE is fully functional - data should be sendable!")
    }
    
    /**
     * Match nRF Connect approach - find exact service and characteristic
     */
    @SuppressLint("MissingPermission")
    fun matchNRFConnectApproach() {
        Log.i(TAG, "=== MATCHING NRF CONNECT APPROACH ===")
        
        if (bluetoothGatt == null) {
            Log.e(TAG, "❌ No GATT connection")
            return
        }
        
        val services = bluetoothGatt!!.services
        Log.i(TAG, "Total services found: ${services.size}")
        
        // Look for the exact service UUID from nRF Connect
        val targetServiceUUID = "12345678-1234-1234-1234-1234567890ab"
        val targetCharUUID = "abcd1234-5678-90ab-cdef-1234567890ab"
        
        Log.i(TAG, "Looking for service: $targetServiceUUID")
        Log.i(TAG, "Looking for characteristic: $targetCharUUID")
        
        var foundService = false
        var foundCharacteristic = false
        
        services.forEach { service ->
            val serviceUUIDString = service.uuid.toString().lowercase()
            Log.i(TAG, "Service found: $serviceUUIDString")
            
            if (serviceUUIDString == targetServiceUUID.lowercase()) {
                foundService = true
                Log.i(TAG, "✅ Found target service!")
                
                service.characteristics.forEach { char ->
                    val charUUIDString = char.uuid.toString().lowercase()
                    Log.i(TAG, "  Characteristic: $charUUIDString")
                    Log.i(TAG, "  Properties: ${char.properties}")
                    
                    if (charUUIDString == targetCharUUID.lowercase()) {
                        foundCharacteristic = true
                        Log.i(TAG, "  ✅ Found target characteristic!")
                        Log.i(TAG, "  Properties: ${char.properties}")
                        
                        // Check if it supports WRITE
                        val supportsWrite = (char.properties and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0
                        Log.i(TAG, "  Supports WRITE: $supportsWrite")
                        
                        if (supportsWrite) {
                            Log.i(TAG, "  ✅ Characteristic supports WRITE - we can send data!")
                            
                            // Try to send test data exactly like nRF Connect
                            try {
                                val testData = "left|259|turn".toByteArray()
                                char.value = testData
                                val writeResult = bluetoothGatt!!.writeCharacteristic(char)
                                Log.i(TAG, "  Test write result: $writeResult")
                                
                                if (writeResult) {
                                    Log.i(TAG, "  ✅ Successfully sent test data!")
                                } else {
                                    Log.e(TAG, "  ❌ Failed to send test data")
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "  ❌ Exception during test write: ${e.message}")
                            }
                        } else {
                            Log.e(TAG, "  ❌ Characteristic does NOT support WRITE")
                        }
                    }
                }
            }
        }
        
        if (!foundService) {
            Log.e(TAG, "❌ Target service not found")
        }
        if (!foundCharacteristic) {
            Log.e(TAG, "❌ Target characteristic not found")
        }
        
        if (foundService && foundCharacteristic) {
            Log.i(TAG, "🎉 Found exact same service and characteristic as nRF Connect!")
        }
    }
    
    /**
     * Get detailed connection status for debugging
     */
    fun getDetailedStatus(): String {
        return buildString {
            appendLine("isConnected: $isConnected")
            appendLine("bluetoothGatt: $bluetoothGatt")
            appendLine("navigationCharacteristic: $navigationCharacteristic")
            appendLine("queuedMessages: ${messageQueue.size}")
            appendLine("isScanning: $isScanning")
        }
    }
    
    /**
     * Clean up resources
     */
    fun cleanup() {
        disconnect()
        handler.removeCallbacksAndMessages(null)
    }
}
