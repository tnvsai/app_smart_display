package com.example.smart

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import com.example.smart.ble.WorkingBLEService
import com.example.smart.model.Direction
import com.example.smart.model.NavigationData
import com.example.smart.model.PhoneCallData
import com.example.smart.model.CallState
import com.example.smart.notification.NotificationListenerService
import com.example.smart.permission.PermissionHandler
import com.example.smart.service.NavigationService
import com.example.smart.ui.theme.SmartTheme
import com.example.smart.utils.ETACalculator
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
        
        // Set debug log callback for notifications
        NotificationListenerService.setDebugLogCallback { message ->
            // This will be set from the composable
        }
        
        setContent {
            SmartTheme {
                NavigationApp(
                    permissionHandler = permissionHandler,
                    bleService = bleService,
                    activity = this,
                    onRequestPermissions = { requestPermissions() },
                    onStartService = { startNavigationService() },
                    onStopService = { stopNavigationService() },
                    onStopAllServices = { stopAllServices() },
                    onSendManualData = { direction, distance, maneuver ->
                        sendManualNavigationData(direction, distance, maneuver)
                    },
                    isBatteryOptimizationEnabled = { isBatteryOptimizationEnabled() },
                    onRequestDisableBatteryOptimization = { requestDisableBatteryOptimization() }
                )
            }
        }
        
        // Check permissions and start service
        checkPermissionsAndStartService()
    }
    
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
    
    private fun stopAllServices() {
        Log.i(TAG, "Stopping all services completely")
        
        // Stop navigation service (foreground service)
        NavigationService.stopService(this)
        navigationService = null
        
        // Stop BLE scanning and disconnect (cleanup handles both)
        bleService.cleanup()
        
        // Clear notification listener BLE reference
        NotificationListenerService.setBLEService(null)
        
        Log.i(TAG, "All services stopped. App remains open for manual restart.")
    }
    
    private fun isBatteryOptimizationEnabled(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            return !powerManager.isIgnoringBatteryOptimizations(packageName)
        }
        return false
    }
    
    private fun requestDisableBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        }
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
fun CollapsibleCard(
    title: String,
    expanded: Boolean,
    onExpandChange: (Boolean) -> Unit,
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onExpandChange(!expanded) }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }
            if (expanded) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                content()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NavigationApp(
    permissionHandler: PermissionHandler,
    bleService: WorkingBLEService,
    activity: ComponentActivity,
    onRequestPermissions: () -> Unit,
    onStartService: () -> Unit,
    onStopService: () -> Unit,
    onStopAllServices: () -> Unit,
    onSendManualData: (String, String, String) -> Unit,
    isBatteryOptimizationEnabled: () -> Boolean,
    onRequestDisableBatteryOptimization: () -> Unit
) {
    var connectionStatus by remember { mutableStateOf(bleService.connectionStatus.value) }
    var isScanning by remember { mutableStateOf(bleService.isScanningState.value) }
    val permissionStatus = permissionHandler.getPermissionStatus()
    
    // Collapsible section states
    var permissionSectionExpanded by remember { mutableStateOf(true) }
    var bleSectionExpanded by remember { mutableStateOf(true) }
    var manualSectionExpanded by remember { mutableStateOf(false) }
    var testingSectionExpanded by remember { mutableStateOf(false) }
    var notificationSectionExpanded by remember { mutableStateOf(false) }
    var debugSectionExpanded by remember { mutableStateOf(false) }
    
    // Debug log state
    var debugLogs by remember { mutableStateOf(listOf<String>()) }
    var showTransmissionLogs by remember { mutableStateOf(false) }
    
    // Collect state updates
    LaunchedEffect(Unit) {
        bleService.connectionStatus.collect { connectionStatus = it }
    }
    
    LaunchedEffect(Unit) {
        bleService.isScanningState.collect { isScanning = it }
    }
    
    // Add debug log
    fun addDebugLog(message: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        debugLogs = (listOf("[$timestamp] $message") + debugLogs).take(50)
    }
    
    // Connect notification listener to debug log
    LaunchedEffect(Unit) {
        NotificationListenerService.setDebugLogCallback { message ->
            addDebugLog(message)
        }
    }
    
    var manualDirection by remember { mutableStateOf("") }
    var manualDistance by remember { mutableStateOf("") }
    var manualManeuver by remember { mutableStateOf("") }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Navigation Monitor") },
                actions = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Circle,
                            contentDescription = "Connection Status",
                            tint = if (connectionStatus.isConnected) Color.Green else Color.Red,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = if (connectionStatus.isConnected) "Connected" else "Disconnected",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (connectionStatus.isConnected) Color.Green else Color.Red
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Permission Status Card
            item {
                CollapsibleCard(
                    title = "Permission Status",
                    expanded = permissionSectionExpanded,
                    onExpandChange = { permissionSectionExpanded = it },
                    backgroundColor = if (permissionStatus.allGranted) Color.Green.copy(alpha = 0.1f) else Color.Red.copy(alpha = 0.1f)
                ) {
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
                    
                    // Battery Optimization Warning
                    if (isBatteryOptimizationEnabled()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFA500).copy(alpha = 0.2f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    "⚠️ Battery Optimization Enabled",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    "This may stop the service. Disable for reliable operation.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = onRequestDisableBatteryOptimization,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Disable Battery Optimization")
                                }
                            }
                        }
                    }
                }
            }
            
            // BLE Connection Status Card
            item {
                CollapsibleCard(
                    title = "BLE Connection",
                    expanded = bleSectionExpanded,
                    onExpandChange = { bleSectionExpanded = it },
                    backgroundColor = if (connectionStatus.isConnected) Color.Green.copy(alpha = 0.1f) else Color(0xFFFFA500).copy(alpha = 0.1f)
                ) {
                    // Connection Status Info
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Status", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = if (connectionStatus.isConnected) "Connected" else "Disconnected",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = if (connectionStatus.isConnected) Color(0xFF4CAF50) else Color.Gray
                            )
                        }
                        
                        connectionStatus.deviceName?.let { deviceName ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Device", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text = deviceName,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                        
                        
                        if (isScanning) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Scan", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text = "Searching...",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF2196F3)
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Stop All Services Button
                    Button(
                        onClick = onStopAllServices,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                    ) {
                        Text("Stop All Services")
                    }
                    Text(
                        text = "Note: App will remain open. You can restart services manually.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
            
            // Manual Send Card (Simplified)
            item {
                CollapsibleCard(
                    title = "Quick Send",
                    expanded = manualSectionExpanded,
                    onExpandChange = { manualSectionExpanded = it }
                ) {
                    // Direction Dropdown
                    var directionExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = directionExpanded,
                        onExpandedChange = { directionExpanded = !directionExpanded }
                    ) {
                        OutlinedTextField(
                            value = manualDirection,
                            onValueChange = {},
                            label = { Text("Direction") },
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = directionExpanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = directionExpanded,
                            onDismissRequest = { directionExpanded = false }
                        ) {
                            Direction.values().forEach { direction ->
                                DropdownMenuItem(
                                    text = { Text(direction.displayName) },
                                    onClick = {
                                        manualDirection = direction.name
                                        directionExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = manualDistance,
                            onValueChange = { manualDistance = it },
                            label = { Text("Distance") },
                            placeholder = { Text("200m") },
                            modifier = Modifier.weight(1f)
                        )
                        
                        OutlinedTextField(
                            value = manualManeuver,
                            onValueChange = { manualManeuver = it },
                            label = { Text("Maneuver") },
                            placeholder = { Text("optional") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Button(
                        onClick = {
                            addDebugLog("Manual: $manualDirection|$manualDistance|$manualManeuver")
                            onSendManualData(manualDirection, manualDistance, manualManeuver)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = manualDirection.isNotBlank() && manualDistance.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("Send Data")
                    }
                }
            }
            
            // Test All Directions Card
            item {
                CollapsibleCard(
                    title = "Test Directions",
                    expanded = testingSectionExpanded,
                    onExpandChange = { testingSectionExpanded = it }
                ) {
                    // Test data inputs
                    var testDistance by remember { mutableStateOf("200m") }
                    var testManeuver by remember { mutableStateOf("Test maneuver") }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = testDistance,
                            onValueChange = { testDistance = it },
                            label = { Text("Distance") },
                            modifier = Modifier.weight(1f)
                        )
                        
                        OutlinedTextField(
                            value = testManeuver,
                            onValueChange = { testManeuver = it },
                            label = { Text("Maneuver") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Direction test grid
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            Direction.LEFT, Direction.RIGHT, Direction.STRAIGHT, Direction.U_TURN
                        ).forEach { direction ->
                            Button(
                                onClick = {
                                    val testData = NavigationData(
                                        direction = direction,
                                        distance = testDistance,
                                        maneuver = testManeuver,
                                        eta = ETACalculator.calculateETA(testDistance, direction)
                                    )
                                    bleService.sendNavigationData(testData)
                                    addDebugLog("Test: ${direction.name}")
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                            ) {
                                Text(direction.displayName, fontSize = 12.sp)
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(Direction.SHARP_LEFT, Direction.SHARP_RIGHT).forEach { direction ->
                            Button(
                                onClick = {
                                    val testData = NavigationData(
                                        direction = direction,
                                        distance = testDistance,
                                        maneuver = testManeuver,
                                        eta = ETACalculator.calculateETA(testDistance, direction)
                                    )
                                    bleService.sendNavigationData(testData)
                                    addDebugLog("Test: ${direction.name}")
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800))
                            ) {
                                Text(direction.displayName, fontSize = 12.sp)
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(Direction.SLIGHT_LEFT, Direction.SLIGHT_RIGHT).forEach { direction ->
                            Button(
                                onClick = {
                                    val testData = NavigationData(
                                        direction = direction,
                                        distance = testDistance,
                                        maneuver = testManeuver,
                                        eta = ETACalculator.calculateETA(testDistance, direction)
                                    )
                                    bleService.sendNavigationData(testData)
                                    addDebugLog("Test: ${direction.name}")
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00BCD4))
                            ) {
                                Text(direction.displayName, fontSize = 12.sp)
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            Direction.ROUNDABOUT_LEFT,
                            Direction.ROUNDABOUT_STRAIGHT,
                            Direction.ROUNDABOUT_RIGHT
                        ).forEach { direction ->
                            Button(
                                onClick = {
                                    val testData = NavigationData(
                                        direction = direction,
                                        distance = testDistance,
                                        maneuver = testManeuver,
                                        eta = ETACalculator.calculateETA(testDistance, direction)
                                    )
                                    bleService.sendNavigationData(testData)
                                    addDebugLog("Test: ${direction.name}")
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFEB3B))
                            ) {
                                Text(direction.displayName.replace("ROUNDABOUT_", ""), fontSize = 12.sp)
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            Direction.MERGE_LEFT, Direction.MERGE_RIGHT,
                            Direction.KEEP_LEFT, Direction.KEEP_RIGHT
                        ).forEach { direction ->
                            Button(
                                onClick = {
                                    val testData = NavigationData(
                                        direction = direction,
                                        distance = testDistance,
                                        maneuver = testManeuver,
                                        eta = ETACalculator.calculateETA(testDistance, direction)
                                    )
                                    bleService.sendNavigationData(testData)
                                    addDebugLog("Test: ${direction.name}")
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9C27B0))
                            ) {
                                Text(direction.displayName.replace("_", " "), fontSize = 10.sp)
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Button(
                        onClick = {
                            val testData = NavigationData(
                                direction = Direction.DESTINATION_REACHED,
                                distance = "0m",
                                maneuver = "You have arrived",
                                eta = ETACalculator.calculateETA("0m", Direction.DESTINATION_REACHED)
                            )
                            bleService.sendNavigationData(testData)
                            addDebugLog("Test: DESTINATION_REACHED")
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF44336))
                    ) {
                        Text("DESTINATION REACHED")
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    
                    // Phone Call Test Buttons
                    Text(
                        text = "Phone Call Tests",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                    
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val testData = PhoneCallData(
                                    callerName = "John Doe",
                                    callerNumber = "+1 234-567-8900",
                                    callState = CallState.INCOMING
                                )
                                bleService.sendPhoneCallData(testData)
                                addDebugLog("Test: Incoming Call")
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                        ) {
                            Text("Incoming Call", fontSize = 12.sp)
                        }
                        
                        Button(
                            onClick = {
                                val testData = PhoneCallData(
                                    callerName = "Jane Smith",
                                    callerNumber = "+1 555-123-4567",
                                    callState = CallState.ONGOING,
                                    duration = 125 // 2 minutes 5 seconds
                                )
                                bleService.sendPhoneCallData(testData)
                                addDebugLog("Test: Ongoing Call")
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3))
                        ) {
                            Text("Ongoing Call", fontSize = 12.sp)
                        }
                        
                        Button(
                            onClick = {
                                val testData = PhoneCallData(
                                    callerName = "Mom",
                                    callerNumber = "+1 555-987-6543",
                                    callState = CallState.MISSED
                                )
                                bleService.sendPhoneCallData(testData)
                                addDebugLog("Test: Missed Call")
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF44336))
                        ) {
                            Text("Missed Call", fontSize = 12.sp)
                        }
                        
                        Button(
                            onClick = {
                                val testData = PhoneCallData(
                                    callerName = "",
                                    callerNumber = "",
                                    callState = CallState.ENDED
                                )
                                bleService.sendPhoneCallData(testData)
                                addDebugLog("Test: Call Ended")
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9E9E9E))
                        ) {
                            Text("End Call", fontSize = 12.sp)
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    
                    // Test Sequence Mode
                    var isRunningSequence by remember { mutableStateOf(false) }
                    var sequenceDelay by remember { mutableStateOf("2") }
                    
                    Text(
                        text = "Auto Test All",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                    
                    OutlinedTextField(
                        value = sequenceDelay,
                        onValueChange = { sequenceDelay = it },
                        label = { Text("Delay between directions (seconds)") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        trailingIcon = {
                            Button(
                                onClick = {
                                    isRunningSequence = !isRunningSequence
                                    if (isRunningSequence) {
                                        activity.lifecycleScope.launch {
                                            val allDirections = Direction.values().filter { it != Direction.UNKNOWN }
                                            val delayMs = (sequenceDelay.toIntOrNull() ?: 2) * 1000L
                                            
                                            for (direction in allDirections) {
                                                if (!isRunningSequence) break
                                                
                                                val testData = NavigationData(
                                                    direction = direction,
                                                    distance = testDistance,
                                                    maneuver = testManeuver,
                                                    eta = ETACalculator.calculateETA(testDistance, direction)
                                                )
                                                bleService.sendNavigationData(testData)
                                                addDebugLog("Sequence: Testing ${direction.name}")
                                                
                                                delay(delayMs)
                                            }
                                            isRunningSequence = false
                                            addDebugLog("Sequence: Completed")
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isRunningSequence) Color.Red else Color(0xFF4CAF50)
                                )
                            ) {
                                Text(if (isRunningSequence) "Stop" else "Start", fontSize = 12.sp)
                            }
                        }
                    )
                }
            }
             
             // Recent Notifications Card (show when Google Maps sends data)
             item {
                 CollapsibleCard(
                     title = "Recent Maps Notifications",
                     expanded = notificationSectionExpanded,
                     onExpandChange = { notificationSectionExpanded = it },
                     backgroundColor = Color.Blue.copy(alpha = 0.1f)
                 ) {
                     val recentNotifications = NotificationListenerService.getRecentNotifications()
                     
                     if (recentNotifications.isEmpty()) {
                         Text(
                             "No Google Maps notifications received yet. Start navigation to see data.",
                             style = MaterialTheme.typography.bodySmall,
                             color = Color.Gray
                         )
                     } else {
                         Column(
                             modifier = Modifier.fillMaxWidth(),
                             verticalArrangement = Arrangement.spacedBy(8.dp)
                         ) {
                             recentNotifications.take(5).forEach { notif ->
                                 Card(
                                     modifier = Modifier.fillMaxWidth(),
                                     colors = CardDefaults.cardColors(
                                         containerColor = if (notif.isNavigation) Color.Green.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.05f)
                                     )
                                 ) {
                                     Column(modifier = Modifier.padding(8.dp)) {
                                         Row(
                                             modifier = Modifier.fillMaxWidth(),
                                             horizontalArrangement = Arrangement.SpaceBetween
                                         ) {
                                             Text(
                                                 notif.timestamp,
                                                 style = MaterialTheme.typography.bodySmall,
                                                 fontWeight = FontWeight.Bold
                                             )
                                             Text(
                                                 if (notif.isNavigation) "✓ Parsed" else "⚠ Not Navigation",
                                                 style = MaterialTheme.typography.bodySmall,
                                                 color = if (notif.isNavigation) Color.Green else Color.Gray
                                             )
                                         }
                                         if (notif.text != null) {
                                             Text(
                                                 notif.text,
                                                 style = MaterialTheme.typography.bodySmall,
                                                 modifier = Modifier.padding(top = 4.dp)
                                             )
                                         }
                                         if (notif.parsedData != null) {
                                             Text(
                                                 "Parsed: $notif.parsedData",
                                                 style = MaterialTheme.typography.bodySmall,
                                                 color = Color(0xFF4CAF50),
                                                 fontWeight = FontWeight.Medium,
                                                 modifier = Modifier.padding(top = 4.dp)
                                             )
                                         }
                                     }
                                 }
                             }
                         }
                     }
                 }
             }
             
             // Debug Logs Card
             item {
                 CollapsibleCard(
                     title = "Debug Logs (${debugLogs.size})",
                     expanded = debugSectionExpanded,
                     onExpandChange = { debugSectionExpanded = it },
                     backgroundColor = Color.Black.copy(alpha = 0.05f)
                 ) {
                     Row(
                         modifier = Modifier.fillMaxWidth(),
                         horizontalArrangement = Arrangement.SpaceBetween
                     ) {
                         Text(
                             text = "Status: ${if (connectionStatus.isConnected) "Connected" else "Disconnected"}",
                             style = MaterialTheme.typography.bodySmall,
                             color = if (connectionStatus.isConnected) Color.Green else Color.Red
                         )
                         Button(
                             onClick = { debugLogs = emptyList() },
                             colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                             modifier = Modifier.height(32.dp)
                         ) {
                             Text("Clear", fontSize = 12.sp)
                         }
                     }
                     
                     Spacer(modifier = Modifier.height(8.dp))
                     
                     Card(
                         modifier = Modifier.fillMaxWidth(),
                         colors = CardDefaults.cardColors(containerColor = Color.Black)
                     ) {
                         LazyColumn(
                             modifier = Modifier
                                 .fillMaxWidth()
                                 .height(200.dp)
                                 .padding(8.dp),
                             reverseLayout = true
                         ) {
                             if (debugLogs.isEmpty()) {
                                 item {
                                     Text(
                                         text = "No logs yet",
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
                                         fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
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
