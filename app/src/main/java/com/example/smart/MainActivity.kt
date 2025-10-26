package com.example.smart

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import com.example.smart.ble.WorkingBLEService
import com.example.smart.model.Direction
import com.example.smart.model.NavigationData
import com.example.smart.notification.NotificationListenerService
import com.example.smart.permission.PermissionHandler
import com.example.smart.service.NavigationService
import com.example.smart.ui.theme.SmartTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    
    companion object {
        private const val TAG = "MainActivity"
    }
    
    private lateinit var permissionHandler: PermissionHandler
    private lateinit var bleService: WorkingBLEService
    private var navigationService: NavigationService? = null
    
    private val notificationAccessLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            checkPermissionsAndStartService()
        }
    }
    
    private val bluetoothEnableLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            checkPermissionsAndStartService()
        }
    }
    
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            Log.i(TAG, "All permissions granted")
            checkPermissionsAndStartService()
        } else {
            Log.w(TAG, "Some permissions denied: $permissions")
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        Log.i(TAG, "MainActivity created")
        
        // Initialize services
        permissionHandler = PermissionHandler(this)
        bleService = WorkingBLEService(this)
        
        // Connect notification service to BLE service
        NotificationListenerService.setBLEService(bleService)
        
        setContent {
            SmartTheme {
                NavigationApp(
                    permissionHandler = permissionHandler,
                    bleService = bleService,
                    activity = this,
                    onRequestPermissions = { requestPermissions() },
                    onStartService = { startNavigationService() },
                    onStopService = { stopNavigationService() },
                    onSendManualData = { direction, distance, maneuver ->
                        sendManualNavigationData(direction, distance, maneuver)
                    }
                )
            }
        }
        
        // Check permissions and start service
        checkPermissionsAndStartService()
    }
    
    // Removed deprecated onRequestPermissionsResult - now using Activity Result API
    
    private fun checkPermissionsAndStartService() {
        if (permissionHandler.hasAllPermissions()) {
            Log.i(TAG, "All permissions granted, starting service")
            startNavigationService()
        } else {
            Log.w(TAG, "Missing permissions: ${permissionHandler.getMissingPermissions()}")
        }
    }
    
    private fun requestPermissions() {
        when {
            !permissionHandler.hasBLEPermissions() -> {
                val permissions = arrayOf(
                    android.Manifest.permission.BLUETOOTH_SCAN,
                    android.Manifest.permission.BLUETOOTH_CONNECT,
                    android.Manifest.permission.BLUETOOTH_ADVERTISE
                )
                permissionLauncher.launch(permissions)
            }
            !permissionHandler.hasLocationPermissions() -> {
                val permissions = arrayOf(
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                )
                permissionLauncher.launch(permissions)
            }
            !permissionHandler.hasNotificationPermissions() -> {
                val permissions = arrayOf(android.Manifest.permission.POST_NOTIFICATIONS)
                permissionLauncher.launch(permissions)
            }
            !permissionHandler.hasNotificationAccess() -> {
                permissionHandler.openNotificationAccessSettings(this)
            }
            !permissionHandler.isBluetoothEnabled() -> {
                permissionHandler.openBluetoothSettings(this)
            }
        }
    }
    
    private fun startNavigationService() {
        if (navigationService == null) {
            Log.i(TAG, "Starting navigation service")
            NavigationService.startService(this)
            navigationService = NavigationService()
        }
        
        // Also start BLE scanning
        Log.i(TAG, "Starting BLE scanning")
        bleService.startScanning()
        
        // Debug notification service status
        val notificationService = NotificationListenerService.getInstance()
        if (notificationService != null) {
            Log.i(TAG, "Notification Service Status:")
            Log.i(TAG, "Service Running: true")
            Log.i(TAG, "Notification Access: ${NotificationListenerService.hasNotificationAccess()}")
            Log.i(TAG, "BLE Service Set: ${NotificationListenerService.bleService != null}")
            Log.i(TAG, "Active Notifications: ${NotificationListenerService.getAllActiveNotifications().size}")
        } else {
            Log.w(TAG, "❌ NotificationListenerService is not running!")
            Log.w(TAG, "This means Google Maps notifications will not be captured.")
            Log.w(TAG, "Please enable notification access in Android Settings.")
        }
    }
    
    private fun stopNavigationService() {
        Log.i(TAG, "Stopping navigation service")
        NavigationService.stopService(this)
        navigationService = null
        
        // Also stop BLE
        Log.i(TAG, "Stopping BLE connection")
        bleService.disconnect()
    }
    
    private fun sendManualNavigationData(direction: String, distance: String, maneuver: String) {
        val navigationData = NavigationData(
            direction = Direction.values().find { it.name.equals(direction, ignoreCase = true) },
            distance = distance.takeIf { it.isNotBlank() },
            maneuver = maneuver.takeIf { it.isNotBlank() }
        )
        
        Log.i(TAG, "=== MANUAL DATA SEND DEBUG ===")
        Log.i(TAG, "Direction: '$direction' -> ${navigationData.direction}")
        Log.i(TAG, "Distance: '$distance' -> ${navigationData.distance}")
        Log.i(TAG, "Maneuver: '$maneuver' -> ${navigationData.maneuver}")
        Log.i(TAG, "BLE Connected: ${bleService.connectionStatus.value.isConnected}")
        Log.i(TAG, "Sending manual navigation data: $navigationData")
        
        bleService.sendNavigationData(navigationData)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "MainActivity destroyed")
        bleService.cleanup()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavigationApp(
    permissionHandler: PermissionHandler,
    bleService: WorkingBLEService,
    activity: ComponentActivity,
    onRequestPermissions: () -> Unit,
    onStartService: () -> Unit,
    onStopService: () -> Unit,
    onSendManualData: (String, String, String) -> Unit
) {
    var connectionStatus by remember { mutableStateOf(bleService.connectionStatus.value) }
    var isScanning by remember { mutableStateOf(bleService.isScanningState.value) }
    val permissionStatus = permissionHandler.getPermissionStatus()
    
    // Debug log state
    var debugLogs by remember { mutableStateOf(listOf<String>()) }
    var showDebugLogs by remember { mutableStateOf(false) }
    
    // Collect state updates
    LaunchedEffect(Unit) {
        bleService.connectionStatus.collect { connectionStatus = it }
    }
    
    LaunchedEffect(Unit) {
        bleService.isScanningState.collect { isScanning = it }
    }
    
    // Add debug log
    fun addDebugLog(message: String) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        debugLogs = (listOf("[$timestamp] $message") + debugLogs).take(20) // Keep last 20 logs
    }
    
    var manualDirection by remember { mutableStateOf("") }
    var manualDistance by remember { mutableStateOf("") }
    var manualManeuver by remember { mutableStateOf("") }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Navigation Monitor") }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Permission Status Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (permissionStatus.allGranted) Color.Green.copy(alpha = 0.1f) else Color.Red.copy(alpha = 0.1f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Permission Status",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = permissionStatus.getStatusText(),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (!permissionStatus.allGranted) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = onRequestPermissions,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Grant Permissions")
                            }
                        }
                        
                        if (!permissionStatus.notificationAccess) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    permissionHandler.openNotificationAccessSettings(activity)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFFFA500)
                                )
                            ) {
                                Text("Enable Notification Access")
                            }
                        }
                    }
                }
            }
            
            // BLE Connection Status Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (connectionStatus.isConnected) Color.Green.copy(alpha = 0.1f) else Color(0xFFFFA500).copy(alpha = 0.1f)
                        )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "BLE Connection",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Status: ${if (connectionStatus.isConnected) "Connected" else "Disconnected"}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "Internal Connected: ${bleService.connectionStatus.value.isConnected}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                        if (connectionStatus.deviceName != null) {
                        Text(
                            text = "Device: ${connectionStatus.deviceName ?: "None"}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        }
                        if (connectionStatus.queuedMessages > 0) {
                            Text(
                                text = "Queued Messages: ${connectionStatus.queuedMessages}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFFFFA500)
                            )
                        }
                        if (isScanning) {
                            Text(
                                text = "Scanning for ESP32...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.Blue
                            )
                        }
                    }
                }
            }
            
            // Manual Input Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
    Text(
                            text = "Manual Navigation Data",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // Direction Dropdown
                        var expanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = expanded,
                            onExpandedChange = { expanded = !expanded }
                        ) {
                            OutlinedTextField(
                                value = manualDirection,
                                onValueChange = { manualDirection = it },
                                label = { Text("Direction") },
                                readOnly = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor()
                            )
                            ExposedDropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false }
                            ) {
                                Direction.values().forEach { direction ->
                                    DropdownMenuItem(
                                        text = { Text(direction.displayName) },
                                        onClick = {
                                            manualDirection = direction.name
                                            expanded = false
                                        }
                                    )
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Distance Input
                        OutlinedTextField(
                            value = manualDistance,
                            onValueChange = { manualDistance = it },
                            label = { Text("Distance (e.g., 200m, 0.5km)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Maneuver Input
                        OutlinedTextField(
                            value = manualManeuver,
                            onValueChange = { manualManeuver = it },
                            label = { Text("Maneuver (e.g., roundabout, exit)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // Send Button
                        Button(
                            onClick = {
                                addDebugLog("Sending manual data: $manualDirection|$manualDistance|$manualManeuver")
                                onSendManualData(manualDirection, manualDistance, manualManeuver)
                                addDebugLog("Manual data sent")
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = manualDirection.isNotBlank() || manualDistance.isNotBlank() || manualManeuver.isNotBlank()
                        ) {
                            Text("Send Navigation Data")
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Test Maneuver Button
                        Button(
                            onClick = {
                                addDebugLog("Testing maneuver display...")
                                val testData = com.example.smart.model.NavigationData(
                                    direction = com.example.smart.model.Direction.LEFT,
                                    distance = "200",
                                    maneuver = "roundabout"
                                )
                                bleService.sendNavigationData(testData)
                                addDebugLog("Sent test data with maneuver: $testData")
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.Yellow
                            )
                        ) {
                            Text("Test Maneuver Display (left|200|roundabout)")
                        }
                    }
                }
            }
            
            // Debug Logs Section
            item {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Debug Logs",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Button(
                                onClick = { 
                                    debugLogs = emptyList()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.Red
                                )
                            ) {
                                Text("Clear Logs")
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Connection Status Summary
                        Text(
                            text = "Status: ${if (connectionStatus.isConnected) "Connected" else "Disconnected"} | Queued: ${connectionStatus.queuedMessages}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (connectionStatus.isConnected) Color.Green else Color.Red
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Scrollable Debug Log
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = Color.Black
                            )
                        ) {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                                    .padding(8.dp),
                                reverseLayout = true  // Show newest at bottom
                            ) {
                                if (debugLogs.isEmpty()) {
                                    item {
                                        Text(
                                            text = "No debug logs yet. Try sending data or starting navigation.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.Gray
                                        )
                                    }
                                } else {
                                    items(debugLogs) { log ->
                                        Text(
                                            text = log,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.Green,
                                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                            modifier = Modifier.padding(vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}