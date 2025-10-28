package com.tnvsai.yatramate.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

// Remember each field to detect changes
class NavigationData(
    val direction: String,
    val distance: String,
    val maneuver: String,
    val eta: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavigationDataDisplay(
    direction: String,
    distance: String,
    maneuver: String,
    eta: String,
    modifier: Modifier = Modifier
) {
    // Track previous values to trigger animations
    var prevData by remember { mutableStateOf(NavigationData(direction, distance, maneuver, eta)) }
    
    // Check if any field changed
    val directionChanged = prevData.direction != direction
    val distanceChanged = prevData.distance != distance
    val maneuverChanged = prevData.maneuver != maneuver
    val etaChanged = prevData.eta != eta
    
    // Update previous data
    LaunchedEffect(direction, distance, maneuver, eta) {
        prevData = NavigationData(direction, distance, maneuver, eta)
    }
    
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Direction with crossfade animation
            AnimatedContent(
                targetState = direction,
                transitionSpec = {
                    if (directionChanged) {
                        fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
                    } else {
                        EnterTransition.None togetherWith ExitTransition.None
                    }
                },
                label = "direction_animation"
            ) { text ->
                Text(
                    text = text,
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            
            // Distance with crossfade animation
            AnimatedContent(
                targetState = distance,
                transitionSpec = {
                    if (distanceChanged) {
                        fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
                    } else {
                        EnterTransition.None togetherWith ExitTransition.None
                    }
                },
                label = "distance_animation"
            ) { text ->
                Text(
                    text = text,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f)
                )
            }
            
            Divider(
                modifier = Modifier
                    .fillMaxWidth(0.5f)
                    .padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.3f)
            )
            
            // Maneuver with crossfade animation
            AnimatedContent(
                targetState = maneuver,
                transitionSpec = {
                    if (maneuverChanged) {
                        fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
                    } else {
                        EnterTransition.None togetherWith ExitTransition.None
                    }
                },
                label = "maneuver_animation"
            ) { text ->
                Text(
                    text = text,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center
                )
            }
            
            // ETA with crossfade animation
            AnimatedContent(
                targetState = eta,
                transitionSpec = {
                    if (etaChanged) {
                        fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
                    } else {
                        EnterTransition.None togetherWith ExitTransition.None
                    }
                },
                label = "eta_animation"
            ) { text ->
                Text(
                    text = "ETA: $text",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }
        }
    }
}


