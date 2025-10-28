package com.tnvsai.yatramate

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
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Close
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
import org.json.JSONObject
import com.tnvsai.yatramate.ble.WorkingBLEService
import com.tnvsai.yatramate.model.Direction
import com.tnvsai.yatramate.model.NavigationData
import com.tnvsai.yatramate.model.PhoneCallData
import com.tnvsai.yatramate.model.CallState
import com.tnvsai.yatramate.notification.NotificationListenerService
import com.tnvsai.yatramate.notification.NotificationInfo
import com.tnvsai.yatramate.permission.PermissionHandler
import com.tnvsai.yatramate.service.NavigationService
import com.tnvsai.yatramate.ui.theme.SmartTheme
import com.tnvsai.yatramate.ui.screens.HomeScreen
import com.tnvsai.yatramate.utils.ETACalculator
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
        // Use the singleton BLE service from NavigationService if available
        NotificationListenerService.setBLEService(
            NavigationService.getInstance()?.getBLEService() ?: bleService
        )
        
        setContent {
            var darkMode by remember { mutableStateOf(false) }
            
            SmartTheme(darkTheme = darkMode) {
                // Use NavigationService's BLE instance if available, otherwise fallback to local instance
                var activeBLEService by remember { mutableStateOf(bleService) }
                
                // Update to use singleton BLE service when NavigationService is available
                LaunchedEffect(Unit) {
                    activeBLEService = NavigationService.getInstance()?.getBLEService() ?: bleService
                }
                
                NavigationApp(
                    darkMode = darkMode,
                    onDarkModeToggle = { darkMode = !darkMode },
                    permissionHandler = permissionHandler,
                    bleService = activeBLEService,
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
            // Get singleton instance instead of creating new one
            navigationService = NavigationService.getInstance()
        }
        
        // Start BLE scanning using NavigationService's BLE instance
        Log.i(TAG, "Starting BLE scanning")
        val navService = NavigationService.getInstance()
        navService?.getBLEService()?.startScanning() ?: run {
            Log.w(TAG, "NavigationService BLE not available, using local instance")
            bleService.startScanning()
        }
        
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
    showClearButton: Boolean = false,
    onClear: (() -> Unit)? = null,
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
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (showClearButton && onClear != null) {
                        TextButton(
                            onClick = { onClear() },
                            modifier = Modifier.height(28.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) {
                            Text("Clear All", fontSize = 12.sp)
                        }
                    }
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }
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
    darkMode: Boolean,
    onDarkModeToggle: () -> Unit,
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
    // Collect live connection status from StateFlow
    val connectionStatus by bleService.connectionStatus.collectAsState()
    val isScanning by bleService.isScanningState.collectAsState()
    val permissionStatus = permissionHandler.getPermissionStatus()
    
    // Tab state
    var selectedTab by remember { mutableStateOf(0) }
    
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
    
    var jsonInput by remember { mutableStateOf("") }
    var jsonError by remember { mutableStateOf<String?>(null) }
    var sendMode by remember { mutableStateOf("json") } // "json" or "keyvalue"
    var keyInput by remember { mutableStateOf("") }
    var valueInput by remember { mutableStateOf("") }
    var keyValuePairs by remember { mutableStateOf(listOf<Pair<String, String>>()) }
    
    // Phone call debug section
    var phoneCallSectionExpanded by remember { mutableStateOf(false) }
    var phoneDebugLogs by remember { mutableStateOf(emptyList<NotificationListenerService.Companion.PhoneDebugLog>()) }
    
    // Recent notifications state
    var recentNotifications by remember { mutableStateOf(listOf<NotificationInfo>()) }
    
    // Update recent notifications periodically
    LaunchedEffect(Unit) {
        while (true) {
            recentNotifications = NotificationListenerService.getRecentNotifications()
            delay(500) // Update every 500ms
        }
    }
    
    // Connect phone debug callback
    LaunchedEffect(Unit) {
        NotificationListenerService.setPhoneDebugLogCallback { log ->
            phoneDebugLogs = (listOf(log) + phoneDebugLogs).take(20)
        }
    }
    
    // Tab titles
    val tabs = listOf("Home", "Developer")
    
    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { 
                        Column(
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            Text(
                                text = "Yatra Mate",
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                text = "by tnvsai",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    },
                    actions = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Circle,
                                contentDescription = "Connection Status",
                                tint = if (connectionStatus.isConnected) Color(0xFF4CAF50) else Color(0xFFF44336),
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = if (connectionStatus.isConnected) "Connected" else "Disconnected",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            
                            Spacer(modifier = Modifier.width(16.dp))
                            
                            IconButton(onClick = onDarkModeToggle) {
                                Icon(
                                    imageVector = if (darkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                                    contentDescription = "Toggle dark mode",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                )
                // Tab Row
                TabRow(selectedTabIndex = selectedTab) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(title) }
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            when (selectedTab) {
                0 -> HomeScreen(
                    connectionStatus = connectionStatus,
                    isScanning = isScanning,
                    permissionStatus = permissionStatus,
                    permissionHandler = permissionHandler,
                    bleService = bleService,
                    onRequestPermissions = onRequestPermissions,
                    onStartService = onStartService,
                    onStopAllServices = onStopAllServices
                )
                1 -> LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
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
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Battery Optimization Status
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Battery Optimization: ${if (isBatteryOptimizationEnabled()) "Enabled" else "Disabled"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isBatteryOptimizationEnabled()) Color(0xFFFFA500) else Color(0xFF4CAF50)
                        )
                        Button(
                            onClick = {
                                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                activity.startActivity(intent)
                            },
                            modifier = Modifier.height(32.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3))
                        ) {
                            Text("Open Settings", fontSize = 12.sp)
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
                    
                    // Start/Stop Service Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onStartService,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                        ) {
                            Text("Start Services")
                        }
                        
                        Button(
                            onClick = onStopAllServices,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                        ) {
                            Text("Stop All")
                        }
                    }
                    Text(
                        text = "Start: Connect to MCU | Stop: Disconnect all services",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
            
            // Manual Send Card (Simplified)
            item {
                CollapsibleCard(
                    title = "Manual Send",
                    expanded = manualSectionExpanded,
                    onExpandChange = { manualSectionExpanded = it }
                ) {
                    // Mode selector tabs
                    TabRow(selectedTabIndex = if (sendMode == "json") 0 else 1) {
                        Tab(
                            selected = sendMode == "json", 
                            onClick = { sendMode = "json" },
                            text = { Text("Raw JSON") }
                        )
                        Tab(
                            selected = sendMode == "keyvalue", 
                            onClick = { sendMode = "keyvalue" },
                            text = { Text("Key-Value Builder") }
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Mode-specific UI
                    if (sendMode == "json") {
                        // JSON input mode
                        OutlinedTextField(
                        value = jsonInput,
                        onValueChange = { 
                            jsonInput = it
                            jsonError = null
                        },
                        label = { Text("JSON Data") },
                        placeholder = { Text("{\"type\":\"NAVIGATION\",\"direction\":\"right\",\"distance\":200,\"maneuver\":\"Turn right\"}") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 4,
                        maxLines = 8,
                        isError = jsonError != null
                        )
                    } else {
                        // Key-Value Builder mode
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = keyInput,
                                    onValueChange = { 
                                        keyInput = it
                                        jsonError = null
                                    },
                                    label = { Text("Key") },
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = valueInput,
                                    onValueChange = { 
                                        valueInput = it
                                        jsonError = null
                                    },
                                    label = { Text("Value") },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            
                            Button(
                                onClick = {
                                    if (keyInput.isNotBlank() && valueInput.isNotBlank()) {
                                        // Check for duplicate keys
                                        if (keyValuePairs.any { it.first == keyInput }) {
                                            jsonError = "Key '$keyInput' already exists"
                                        } else {
                                            keyValuePairs = keyValuePairs + (keyInput to valueInput)
                                            keyInput = ""
                                            valueInput = ""
                                            jsonError = null
                                        }
                                    } else {
                                        jsonError = "Both key and value must be filled"
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Add Entry")
                            }
                            
                            // Display added entries
                            if (keyValuePairs.isNotEmpty()) {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.05f))
                                ) {
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        Text(
                                            "Entries to send:",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(bottom = 4.dp)
                                        )
                                        keyValuePairs.forEachIndexed { index, (key, value) ->
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    "$key: $value",
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                                IconButton(
                                                    onClick = { 
                                                        keyValuePairs = keyValuePairs.filterIndexed { i, _ -> i != index }
                                                    },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.Close,
                                                        contentDescription = "Remove",
                                                        tint = Color.Red,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    
                    if (jsonError != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = jsonError!!,
                            color = Color.Red,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Send button (works for both modes)
                    Button(
                        onClick = {
                            val jsonToSend = if (sendMode == "json") {
                                // Validate and use raw JSON
                                if (jsonInput.isBlank()) {
                                    jsonError = "JSON cannot be empty"
                                    return@Button
                                }
                                try {
                                    JSONObject(jsonInput).toString()
                                } catch (e: Exception) {
                                    jsonError = "Invalid JSON: ${e.message}"
                                    return@Button
                                }
                            } else {
                                // Build JSON from key-value pairs
                                if (keyValuePairs.isEmpty()) {
                                    jsonError = "Add at least one key-value entry"
                                    return@Button
                                }
                                try {
                                    // Build JSON object with smart value type conversion
                                    val jsonMap = keyValuePairs.associate { (key, value) ->
                                        val processedValue = when {
                                            value.toIntOrNull() != null -> value.toInt()
                                            value.toDoubleOrNull() != null -> value.toDouble()
                                            value == "true" || value == "false" -> value.toBoolean()
                                            value == "null" -> null
                                            else -> value
                                        }
                                        key to processedValue
                                    }
                                    JSONObject(jsonMap).toString()
                                } catch (e: Exception) {
                                    jsonError = "Error building JSON: ${e.message}"
                                    return@Button
                                }
                            }
                            
                            // Send via BLE
                            bleService.sendRawData(jsonToSend)
                            addDebugLog("Sent: $jsonToSend")
                            
                            // Clear only JSON input, keep key-value pairs
                            if (sendMode == "json") {
                                jsonInput = ""
                            }
                            // Key-value pairs persist for next send
                            jsonError = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = (sendMode == "json" && jsonInput.isNotBlank()) || 
                                  (sendMode == "keyvalue" && keyValuePairs.isNotEmpty()),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("Send ${if (sendMode == "json") "JSON" else "${keyValuePairs.size} Entries"}")
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
                                    bleService.sendNavigationData(testData, forceSend = true)
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
                                    bleService.sendNavigationData(testData, forceSend = true)
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
                                    bleService.sendNavigationData(testData, forceSend = true)
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
                                    bleService.sendNavigationData(testData, forceSend = true)
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
                                    bleService.sendNavigationData(testData, forceSend = true)
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
                            bleService.sendNavigationData(testData, forceSend = true)
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
                     title = "Navigation History",
                     expanded = notificationSectionExpanded,
                     onExpandChange = { notificationSectionExpanded = it },
                     backgroundColor = Color.Blue.copy(alpha = 0.1f),
                     showClearButton = true,
                     onClear = {
                         NotificationListenerService.clearRecentNotifications()
                         recentNotifications = emptyList()
                     }
                 ) {
                     // Use state instead of getting fresh each time
                     
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

             // Phone Call Debug Card
             item {
                 CollapsibleCard(
                     title = "Call History (${phoneDebugLogs.size})",
                     expanded = phoneCallSectionExpanded,
                     onExpandChange = { phoneCallSectionExpanded = it },
                     backgroundColor = Color(0xFF9C27B0).copy(alpha = 0.1f),
                     showClearButton = true,
                     onClear = {
                         NotificationListenerService.clearPhoneDebugLogs()
                         phoneDebugLogs = emptyList()
                     }
                 ) {
                     if (phoneDebugLogs.isEmpty()) {
                         Text(
                             "No phone notifications received yet. Make or receive a call to see data.",
                             style = MaterialTheme.typography.bodySmall,
                             color = Color.Gray
                         )
                     } else {
                         Column(
                             modifier = Modifier
                                 .fillMaxWidth()
                                 .heightIn(max = 400.dp),
                             verticalArrangement = Arrangement.spacedBy(8.dp)
                         ) {
                             phoneDebugLogs.take(10).forEach { log ->
                                 Card(
                                     modifier = Modifier.fillMaxWidth(),
                                     colors = CardDefaults.cardColors(
                                         containerColor = when (log.state) {
                                             "INCOMING" -> Color(0xFF4CAF50).copy(alpha = 0.1f)
                                             "MISSED" -> Color(0xFFF44336).copy(alpha = 0.1f)
                                             "ONGOING" -> Color(0xFF2196F3).copy(alpha = 0.1f)
                                             "ENDED" -> Color(0xFF9E9E9E).copy(alpha = 0.1f)
                                             else -> Color.Black.copy(alpha = 0.05f)
                                         }
                                     )
                                 ) {
                                     Column(modifier = Modifier.padding(8.dp)) {
                                         Row(
                                             modifier = Modifier.fillMaxWidth(),
                                             horizontalArrangement = Arrangement.SpaceBetween
                                         ) {
                                             Text(
                                                 log.timestamp,
                                                 style = MaterialTheme.typography.bodySmall,
                                                 fontWeight = FontWeight.Bold
                                             )
                                             Text(
                                                 log.state,
                                                 style = MaterialTheme.typography.bodySmall,
                                                 fontWeight = FontWeight.Bold,
                                                 color = when (log.state) {
                                                     "INCOMING" -> Color(0xFF4CAF50)
                                                     "MISSED" -> Color(0xFFF44336)
                                                     "ONGOING" -> Color(0xFF2196F3)
                                                     "ENDED" -> Color(0xFF9E9E9E)
                                                     else -> Color.Gray
                                                 }
                                             )
                                         }
                                         Text(
                                             "Name: ${log.callerName ?: "Unknown"}",
                                             style = MaterialTheme.typography.bodySmall,
                                             modifier = Modifier.padding(top = 4.dp)
                                         )
                                         Text(
                                             "Number: ${log.callerNumber ?: "Unknown"}",
                                             style = MaterialTheme.typography.bodySmall
                                         )
                                         if (log.title != null) {
                                             Text(
                                                 "Title: ${log.title}",
                                                 style = MaterialTheme.typography.bodySmall,
                                                 color = Color.Gray,
                                                 modifier = Modifier.padding(top = 4.dp)
                                             )
                                         }
                                         if (log.text != null) {
                                             Text(
                                                 "Text: ${log.text}",
                                                 style = MaterialTheme.typography.bodySmall,
                                                 color = Color.Gray
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
                     title = "System Log (${debugLogs.size})",
                     expanded = debugSectionExpanded,
                     onExpandChange = { debugSectionExpanded = it },
                     backgroundColor = Color.Black.copy(alpha = 0.05f),
                     showClearButton = true,
                     onClear = { debugLogs = emptyList() }
                 ) {
                     Text(
                         text = "Status: ${if (connectionStatus.isConnected) "Connected" else "Disconnected"}",
                         style = MaterialTheme.typography.bodySmall,
                         color = if (connectionStatus.isConnected) Color.Green else Color.Red
                     )
                     
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
    }
}


