package com.tnvsai.yatramate.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tnvsai.yatramate.ble.WorkingBLEService
import com.tnvsai.yatramate.notification.NotificationInfo
import com.tnvsai.yatramate.notification.NotificationListenerService
import com.tnvsai.yatramate.permission.PermissionHandler
import com.tnvsai.yatramate.ui.components.ActionType
import com.tnvsai.yatramate.ui.components.NavigationDataDisplay
import com.tnvsai.yatramate.ui.components.QuickAction
import com.tnvsai.yatramate.ui.components.QuickActionPanel
import com.tnvsai.yatramate.ui.components.StatusCard
import com.tnvsai.yatramate.ui.components.StatusType

/**
 * Helper function to parse HH:mm:ss timestamp to seconds since midnight
 */
fun parseTimestamp(timestamp: String): Long {
    return try {
        val parts = timestamp.split(":")
        if (parts.size == 3) {
            val hours = parts[0].toLongOrNull() ?: 0L
            val minutes = parts[1].toLongOrNull() ?: 0L
            val seconds = parts[2].toLongOrNull() ?: 0L
            hours * 3600 + minutes * 60 + seconds
        } else 0L
    } catch (e: Exception) { 0L }
}

/**
 * Home Screen - Primary navigation monitoring interface
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    connectionStatus: WorkingBLEService.BLEConnectionStatus,
    isScanning: Boolean,
    permissionStatus: PermissionHandler.PermissionStatus,
    permissionHandler: PermissionHandler,
    bleService: WorkingBLEService,
    onRequestPermissions: () -> Unit,
    onStartService: () -> Unit,
    onStopAllServices: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Collect actual navigation data from BLE service
    val currentNavData by bleService.currentNavigationData.collectAsState()
    
    // Use real data or fallback to defaults
    val navigationDirection = currentNavData?.direction?.displayName ?: "No navigation"
    val navigationDistance = currentNavData?.distance ?: "0m"
    val navigationManeuver = currentNavData?.maneuver ?: "Waiting for navigation..."
    val navigationETA = currentNavData?.eta ?: "0 min"
    
    // Performance optimization: Use derivedStateOf for computed values
    val isConnected = remember { derivedStateOf { connectionStatus.isConnected } }
    val connectionSubtitle = remember(connectionStatus) {
        derivedStateOf {
            if (connectionStatus.isConnected) {
                "Connected to ${connectionStatus.deviceName ?: "ESP32"}"
            } else {
                "Disconnected - Tap 'Start Services' to connect"
            }
        }
    }
    val connectionStatusType = remember(connectionStatus.isConnected) {
        derivedStateOf { if (connectionStatus.isConnected) StatusType.SUCCESS else StatusType.WARNING }
    }
    val permissionSubtitle = remember(permissionStatus) {
        derivedStateOf {
            when {
                permissionStatus.allGranted -> "All permissions granted ✓"
                !permissionStatus.hasNotificationAccess -> "Enable notification access"
                !permissionStatus.hasBLEPermissions -> "Grant BLE permissions"
                !permissionStatus.hasLocationPermissions -> "Grant location permissions"
                else -> "Some permissions missing"
            }
        }
    }
    val permissionStatusType = remember(permissionStatus.allGranted) {
        derivedStateOf { if (permissionStatus.allGranted) StatusType.SUCCESS else StatusType.ERROR }
    }
    // Get both navigation and call data for recent activity
    val recentNotifications = remember { NotificationListenerService.getRecentNotifications() }
    val phoneDebugLogs = remember { NotificationListenerService.getPhoneDebugLogs() }
    
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero: Navigation Data Display
        item(key = "nav-data") {
            NavigationDataDisplay(
                direction = navigationDirection,
                distance = navigationDistance,
                maneuver = navigationManeuver,
                eta = navigationETA
            )
        }
        
        // Connection Status
        item(key = "connection-status") {
            StatusCard(
                title = "Connection Status",
                subtitle = connectionSubtitle.value,
                statusType = connectionStatusType.value
            )
        }
        
        // Permissions Status
        item(key = "permissions-status") {
            StatusCard(
                title = "Permissions",
                subtitle = permissionSubtitle.value,
                statusType = permissionStatusType.value,
                onClick = if (!permissionStatus.allGranted) onRequestPermissions else null
            )
        }
        
        // Quick Actions
        item(key = "quick-actions") {
            QuickActionPanel(
                actions = listOf(
                    QuickAction(
                        label = if (isConnected.value) "Connected" else "Start Services",
                        onClick = { if (!isConnected.value) onStartService() },
                        type = if (isConnected.value) ActionType.SECONDARY else ActionType.PRIMARY
                    ),
                    QuickAction(
                        label = "Stop All",
                        onClick = onStopAllServices,
                        type = ActionType.DANGER
                    )
                )
            )
        }
        
        // Recent Activity (Last 3 Notifications)
        item(key = "recent-activity") {
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Recent Activity",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Combine navigation and call data, sort by timestamp (newest first)
                    val allActivity = remember(recentNotifications, phoneDebugLogs) {
                        val combined = mutableListOf<Any>()
                        recentNotifications.forEach { combined.add(it) }
                        phoneDebugLogs.forEach { combined.add(it) }
                        
                        // Sort by timestamp descending (newest first) and limit to 5
                        combined.sortedByDescending { item ->
                            when (item) {
                                is NotificationInfo -> parseTimestamp(item.timestamp)
                                is NotificationListenerService.Companion.PhoneDebugLog -> parseTimestamp(item.timestamp)
                                else -> 0L
                            }
                        }.take(5)
                    }
                    
                    if (allActivity.isEmpty()) {
                        Text(
                            text = "No activity yet. Start navigation or make a call to see data.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        allActivity.forEach { activity ->
                            when (activity) {
                                is NotificationInfo -> {
                                    Card(
                                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (activity.isNavigation) {
                                                MaterialTheme.colorScheme.primaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.surface
                                            }
                                        )
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(12.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = activity.timestamp,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Text(
                                                    text = "📱 Navigation",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            if (activity.text != null) {
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = (activity.text ?: "").take(50) + if ((activity.text?.length ?: 0) > 50) "..." else "",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    maxLines = 2
                                                )
                                            }
                                        }
                                    }
                                }
                                is NotificationListenerService.Companion.PhoneDebugLog -> {
                                    val call = activity
                                    val callColor = when (call.state) {
                                        "INCOMING" -> Color(0xFF4CAF50).copy(alpha = 0.2f)
                                        "ONGOING" -> Color(0xFF2196F3).copy(alpha = 0.2f)
                                        "MISSED" -> Color(0xFFF44336).copy(alpha = 0.2f)
                                        "ENDED" -> Color(0xFF9E9E9E).copy(alpha = 0.2f)
                                        else -> MaterialTheme.colorScheme.surface
                                    }
                                    Card(
                                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                        colors = CardDefaults.cardColors(containerColor = callColor)
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = call.timestamp,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Text(
                                                    text = "📞 Call - ${call.state}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "${call.callerName ?: "Unknown"} (${call.callerNumber ?: "Unknown"})",
                                                style = MaterialTheme.typography.bodySmall,
                                                maxLines = 1
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


