package com.tnvsai.yatramate.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

sealed class ConnectionState {
    data object Disconnected : ConnectionState()
    data object Scanning : ConnectionState()
    data object Connecting : ConnectionState()
    data class Connected(val deviceName: String?) : ConnectionState()
}

@Composable
fun ConnectionIndicator(
    state: ConnectionState,
    modifier: Modifier = Modifier,
    showLabel: Boolean = true
) {
    val (statusColor, statusIcon, statusText) = when (state) {
        is ConnectionState.Disconnected -> Triple(
            Color(0xFFE53935),
            Icons.Default.BluetoothDisabled,
            "Disconnected"
        )
        is ConnectionState.Scanning -> Triple(
            Color(0xFFFFC107),
            Icons.Default.BluetoothSearching,
            "Scanning..."
        )
        is ConnectionState.Connecting -> Triple(
            Color(0xFF2196F3),
            Icons.Default.BluetoothSearching,
            "Connecting..."
        )
        is ConnectionState.Connected -> Triple(
            Color(0xFF4CAF50),
            Icons.Default.BluetoothConnected,
            state.deviceName ?: "Connected"
        )
    }

    Surface(
        modifier = modifier,
        color = statusColor.copy(alpha = 0.1f),
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = statusIcon,
                contentDescription = null,
                tint = statusColor,
                modifier = Modifier.size(24.dp)
            )
            
            if (showLabel) {
                Column {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (state is ConnectionState.Connected && state.deviceName != null) {
                        Text(
                            text = "SMART-DISPLAY",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ConnectionDot(
    state: ConnectionState,
    modifier: Modifier = Modifier
) {
    val dotColor = when (state) {
        is ConnectionState.Disconnected -> Color(0xFFE53935)
        is ConnectionState.Scanning -> Color(0xFFFFC107)
        is ConnectionState.Connecting -> Color(0xFF2196F3)
        is ConnectionState.Connected -> Color(0xFF4CAF50)
    }
    
    // Pulse animation for scanning/connecting states
    val infiniteTransition = rememberInfiniteTransition(label = "connection_pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )
    
    val shouldPulse = state is ConnectionState.Scanning || state is ConnectionState.Connecting
    val animatedAlpha = if (shouldPulse) alpha else 1f

    Surface(
        modifier = modifier.size(12.dp),
        color = dotColor.copy(alpha = animatedAlpha),
        shape = androidx.compose.foundation.shape.CircleShape
    ) {}
}

