package com.tnvsai.yatramate.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * Animated version of StatusCard with smooth color and scale transitions
 */
@Composable
fun AnimatedStatusCard(
    title: String,
    subtitle: String? = null,
    statusType: StatusType = StatusType.INFO,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    // Animate colors when status changes
    val containerColor by animateColorAsState(
        targetValue = when (statusType) {
            StatusType.SUCCESS -> MaterialTheme.colorScheme.tertiaryContainer
            StatusType.WARNING -> MaterialTheme.colorScheme.errorContainer
            StatusType.ERROR -> MaterialTheme.colorScheme.errorContainer
            StatusType.INFO -> MaterialTheme.colorScheme.primaryContainer
        },
        animationSpec = tween(durationMillis = 400, easing = LinearEasing)
    )
    
    val iconColor by animateColorAsState(
        targetValue = when (statusType) {
            StatusType.SUCCESS -> MaterialTheme.colorScheme.onTertiaryContainer
            StatusType.WARNING -> MaterialTheme.colorScheme.onErrorContainer
            StatusType.ERROR -> MaterialTheme.colorScheme.onErrorContainer
            StatusType.INFO -> MaterialTheme.colorScheme.onPrimaryContainer
        },
        animationSpec = tween(durationMillis = 400, easing = LinearEasing)
    )
    
    val icon: ImageVector = when (statusType) {
        StatusType.SUCCESS -> Icons.Default.CheckCircle
        StatusType.WARNING -> Icons.Default.Warning
        StatusType.ERROR -> Icons.Default.Error
        StatusType.INFO -> Icons.Default.CheckCircle
    }
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        colors = CardDefaults.cardColors(containerColor = containerColor)
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
                contentDescription = null,
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
                    color = iconColor
                )
                subtitle?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = iconColor.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}


