package com.tnvsai.yatramate.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

enum class StatusType {
    SUCCESS, WARNING, ERROR, INFO
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusCard(
    title: String,
    subtitle: String? = null,
    statusType: StatusType = StatusType.INFO,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val colors = CardDefaults.cardColors(
        containerColor = when (statusType) {
            StatusType.SUCCESS -> MaterialTheme.colorScheme.tertiaryContainer
            StatusType.WARNING -> MaterialTheme.colorScheme.errorContainer
            StatusType.ERROR -> MaterialTheme.colorScheme.errorContainer
            StatusType.INFO -> MaterialTheme.colorScheme.primaryContainer
        }
    )
    
    val iconColor = when (statusType) {
        StatusType.SUCCESS -> MaterialTheme.colorScheme.onTertiaryContainer
        StatusType.WARNING -> MaterialTheme.colorScheme.onErrorContainer
        StatusType.ERROR -> MaterialTheme.colorScheme.onErrorContainer
        StatusType.INFO -> MaterialTheme.colorScheme.onPrimaryContainer
    }
    
    val icon: ImageVector = when (statusType) {
        StatusType.SUCCESS -> Icons.Default.CheckCircle
        StatusType.WARNING -> Icons.Default.Warning
        StatusType.ERROR -> Icons.Default.Error
        StatusType.INFO -> Icons.Default.CheckCircle
    }
    
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = colors,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        onClick = onClick ?: {}
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = "${statusType.name} status",
                tint = iconColor,
                modifier = Modifier.size(32.dp)
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = when (statusType) {
                        StatusType.SUCCESS -> MaterialTheme.colorScheme.onTertiaryContainer
                        StatusType.WARNING -> MaterialTheme.colorScheme.onErrorContainer
                        StatusType.ERROR -> MaterialTheme.colorScheme.onErrorContainer
                        StatusType.INFO -> MaterialTheme.colorScheme.onPrimaryContainer
                    }
                )
                subtitle?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = when (statusType) {
                            StatusType.SUCCESS -> MaterialTheme.colorScheme.onTertiaryContainer
                            StatusType.WARNING -> MaterialTheme.colorScheme.onErrorContainer
                            StatusType.ERROR -> MaterialTheme.colorScheme.onErrorContainer
                            StatusType.INFO -> MaterialTheme.colorScheme.onPrimaryContainer
                        }.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}


