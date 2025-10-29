package com.tnvsai.yatramate.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tnvsai.yatramate.config.models.NotificationType

/**
 * Card component for displaying and managing a notification type configuration
 */
@Composable
fun NotificationTypeCard(
    notificationType: NotificationType,
    isEnabled: Boolean,
    enabledApps: Set<String>,
    disabledApps: Set<String>,
    isExpanded: Boolean,
    onEnabledChanged: (Boolean) -> Unit,
    onExpandedChanged: (Boolean) -> Unit,
    onAppEnabledChanged: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isEnabled) 
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            else 
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    // Type icon
                    NotificationTypeIcon(notificationType.id)
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = notificationType.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = notificationType.id,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                // Enable/disable switch
                Switch(
                    checked = isEnabled,
                    onCheckedChange = onEnabledChanged
                )
            }
            
            // Priority and stats row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PriorityBadge(notificationType.priority)
                Text(
                    text = "${notificationType.apps.size} apps",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${notificationType.keywords.size} keywords",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "MCU: ${notificationType.mcuType}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // Expandable apps section
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .clickable { onExpandedChanged(!isExpanded) },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Apps (${notificationType.apps.size})",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // Expanded apps list
            if (isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    notificationType.apps.forEach { packageName ->
                        AppConfigRow(
                            packageName = packageName,
                            isEnabled = isAppEnabledForType(
                                packageName,
                                enabledApps,
                                disabledApps,
                                notificationType.enabledApps,
                                notificationType.disabledApps
                            ),
                            onEnabledChanged = { enabled ->
                                onAppEnabledChanged(packageName, enabled)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun NotificationTypeIcon(typeId: String) {
    val (icon, color) = when (typeId) {
        "navigation" -> Icons.Default.Navigation to Color(0xFF4CAF50)
        "phone_call" -> Icons.Default.Phone to Color(0xFF2196F3)
        "message" -> Icons.Default.Message to Color(0xFF4CAF50)
        "music" -> Icons.Default.MusicNote to Color(0xFF9C27B0)
        "battery" -> Icons.Default.BatteryChargingFull to Color(0xFFFF9800)
        "weather" -> Icons.Default.Cloud to Color(0xFF03A9F4)
        else -> Icons.Default.Notifications to Color(0xFF757575)
    }
    
    Icon(
        imageVector = icon,
        contentDescription = typeId,
        tint = color,
        modifier = Modifier.size(32.dp)
    )
}

@Composable
fun PriorityBadge(priority: String) {
    val (backgroundColor, textColor) = when (priority.lowercase()) {
        "high" -> Color(0xFFF44336) to Color.White
        "medium" -> Color(0xFFFF9800) to Color.White
        "low" -> Color(0xFF4CAF50) to Color.White
        else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    
    Surface(
        color = backgroundColor,
        shape = RoundedCornerShape(4.dp)
    ) {
        Text(
            text = priority.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun AppConfigRow(
    packageName: String,
    isEnabled: Boolean,
    onEnabledChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = getAppNameFromPackage(packageName),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        Switch(
            checked = isEnabled,
            onCheckedChange = onEnabledChanged,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

private fun isAppEnabledForType(
    packageName: String,
    runtimeEnabledApps: Set<String>,
    runtimeDisabledApps: Set<String>,
    configEnabledApps: List<String>,
    configDisabledApps: List<String>
): Boolean {
    // Check runtime disabled apps first (highest priority)
    if (runtimeDisabledApps.contains(packageName)) return false
    
    // Check runtime enabled apps (whitelist)
    if (runtimeEnabledApps.isNotEmpty()) {
        return runtimeEnabledApps.contains(packageName)
    }
    
    // Check config disabled apps
    if (configDisabledApps.contains(packageName)) return false
    
    // Check config enabled apps
    if (configEnabledApps.isNotEmpty()) {
        return configEnabledApps.contains(packageName)
    }
    
    // Default: enabled
    return true
}

private fun getAppNameFromPackage(packageName: String): String {
    return when {
        packageName.contains("maps", ignoreCase = true) -> "Google Maps"
        packageName.contains("whatsapp", ignoreCase = true) -> "WhatsApp"
        packageName.contains("telegram", ignoreCase = true) -> "Telegram"
        packageName.contains("spotify", ignoreCase = true) -> "Spotify"
        packageName.contains("youtube.music", ignoreCase = true) -> "YouTube Music"
        packageName.contains("systemui", ignoreCase = true) -> "System UI"
        packageName.contains("dialer", ignoreCase = true) -> "Phone"
        packageName.contains("messaging", ignoreCase = true) -> "Messages"
        packageName.contains("waze", ignoreCase = true) -> "Waze"
        else -> packageName.substringAfterLast(".")
    }
}

