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
import java.util.UUID

/**
 * Minimal BLE Service for testing basic communication with ESP32
 * Purpose: Connect to ESP32 and send "Hello World" data
 */
class SimpleBLEService(private val context: Context) {
    
    companion object {
        private const val TAG = "SimpleBLEService"
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
    
    private val handler = Handler(Looper.getMainLooper())
    
    // Simple callback interface for UI updates
    interface SimpleBLECallback {
        fun onConnectionStatusChanged(connected: Boolean, deviceName: String?, deviceAddress: String?)
        fun onLogMessage(message: String)
    }
    
    private var callback: SimpleBLECallback? = null
    
    fun setCallback(callback: SimpleBLECallback) {
        this.callback = callback
    }
    
    private fun log(message: String) {
        Log.i(TAG, message)
        callback?.onLogMessage(message)
    }
    
    private fun hasPermissions(): Boolean {
        return ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
               ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
               ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }
    
    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val deviceName = device.name ?: "Unknown"
            
            log("Found device: $deviceName (${device.address})")
            
            if (deviceName.contains(ESP32_DEVICE_NAME, ignoreCase = true)) {
                log("✅ Found ESP32 device: $deviceName")
                stopScanning()
                connectToDevice(device)
            }
        }
        
        override fun onScanFailed(errorCode: Int) {
            log("❌ BLE scan failed with error: $errorCode")
            isScanning = false
        }
    }
    
    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            log("=== GATT CONNECTION STATE CHANGE ===")
            log("Device: ${gatt.device.name} (${gatt.device.address})")
            log("Status: $status (0=SUCCESS)")
            log("New State: $newState (2=CONNECTED, 0=DISCONNECTED)")
            
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        log("✅ GATT CONNECTION SUCCESSFUL!")
                        isConnected = true
                        bluetoothGatt = gatt
                        
                        callback?.onConnectionStatusChanged(true, gatt.device.name, gatt.device.address)
                        
                        log("Starting service discovery...")
                        val discoveryStarted = gatt.discoverServices()
                        log("Service discovery started: $discoveryStarted")
                    } else {
                        log("❌ GATT connection failed with status: $status")
                        isConnected = false
                        callback?.onConnectionStatusChanged(false, null, null)
                    }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    log("❌ GATT DISCONNECTED")
                    isConnected = false
                    bluetoothGatt = null
                    navigationCharacteristic = null
                    callback?.onConnectionStatusChanged(false, null, null)
                }
            }
        }
        
        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            log("=== SERVICES DISCOVERED ===")
            log("Status: $status")
            
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val services = gatt.services
                log("Found ${services.size} services")
                
                val targetServiceUUID = UUID.fromString(SERVICE_UUID)
                val service = gatt.getService(targetServiceUUID)
                
                if (service != null) {
                    log("✅ Found target service: $SERVICE_UUID")
                    
                    val targetCharUUID = UUID.fromString(CHARACTERISTIC_UUID)
                    navigationCharacteristic = service.getCharacteristic(targetCharUUID)
                    
                    if (navigationCharacteristic != null) {
                        log("✅ Found target characteristic: $CHARACTERISTIC_UUID")
                        val properties = navigationCharacteristic!!.properties
                        log("Characteristic properties: $properties")
                        log("Supports WRITE: ${(properties and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0}")
                        log("Supports NOTIFY: ${(properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0}")
                        log("Supports READ: ${(properties and BluetoothGattCharacteristic.PROPERTY_READ) != 0}")
                        log("🎉 READY TO SEND DATA!")
                    } else {
                        log("❌ Target characteristic not found")
                    }
                } else {
                    log("❌ Target service not found")
                    log("Available services:")
                    services.forEach { svc ->
                        log("  - ${svc.uuid}")
                    }
                }
            } else {
                log("❌ Service discovery failed with status: $status")
            }
        }
        
        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                log("✅ Data written successfully to ESP32!")
            } else {
                log("❌ Failed to write data: $status")
            }
        }
    }
    
    @SuppressLint("MissingPermission")
    fun startConnection() {
        log("=== STARTING SIMPLE BLE CONNECTION ===")
        
        if (!hasPermissions()) {
            log("❌ Missing required permissions")
            return
        }
        
        if (bluetoothAdapter?.isEnabled != true) {
            log("❌ Bluetooth is not enabled")
            return
        }
        
        if (isScanning) {
            log("Already scanning")
            return
        }
        
        if (isConnected) {
            log("Already connected")
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
            log("Started scanning for ESP32 device...")
            
            // Stop scanning after timeout
            handler.postDelayed({
                if (isScanning) {
                    stopScanning()
                    log("Scan timeout - stopping scan")
                }
            }, SCAN_TIMEOUT)
        } catch (e: SecurityException) {
            log("❌ Security exception during scan: ${e.message}")
        }
    }
    
    @SuppressLint("MissingPermission")
    private fun stopScanning() {
        if (isScanning) {
            try {
                bluetoothLeScanner?.stopScan(scanCallback)
                isScanning = false
                log("Stopped scanning")
            } catch (e: SecurityException) {
                log("❌ Security exception during stop scan: ${e.message}")
            }
        }
    }
    
    @SuppressLint("MissingPermission")
    private fun connectToDevice(device: BluetoothDevice) {
        log("=== CONNECTING TO DEVICE ===")
        log("Device: ${device.name} (${device.address})")
        
        try {
            bluetoothGatt = device.connectGatt(context, false, gattCallback)
            log("GATT connection initiated - waiting for callback...")
        } catch (e: SecurityException) {
            log("❌ Security exception during connection: ${e.message}")
        } catch (e: Exception) {
            log("❌ Exception during connection: ${e.message}")
        }
    }
    
    @SuppressLint("MissingPermission")
    fun sendHelloWorld() {
        log("=== SENDING HELLO WORLD ===")
        
        if (!isConnected) {
            log("❌ Not connected - cannot send data")
            return
        }
        
        if (navigationCharacteristic == null) {
            log("❌ Characteristic not found - cannot send data")
            return
        }
        
        val testData = "test|100|hello"
        val data = testData.toByteArray()
        
        log("Sending data: '$testData'")
        log("Data bytes: ${data.contentToString()}")
        log("Data length: ${data.size}")
        
        navigationCharacteristic?.value = data
        val writeResult = bluetoothGatt?.writeCharacteristic(navigationCharacteristic)
        log("Write result: $writeResult")
        
        if (writeResult == true) {
            log("✅ Write command sent successfully!")
        } else {
            log("❌ Failed to send write command")
        }
    }
    
    @SuppressLint("MissingPermission")
    fun disconnect() {
        log("=== DISCONNECTING ===")
        
        if (bluetoothGatt != null) {
            bluetoothGatt?.disconnect()
            bluetoothGatt?.close()
            bluetoothGatt = null
        }
        
        isConnected = false
        navigationCharacteristic = null
        callback?.onConnectionStatusChanged(false, null, null)
        log("Disconnected")
    }
    
    fun cleanup() {
        log("=== CLEANUP ===")
        stopScanning()
        disconnect()
    }
}
