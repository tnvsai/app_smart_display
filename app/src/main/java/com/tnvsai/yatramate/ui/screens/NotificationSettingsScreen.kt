package com.tnvsai.yatramate.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tnvsai.yatramate.config.ConfigManager
import com.tnvsai.yatramate.config.ConfigPersistence
import com.tnvsai.yatramate.config.NotificationConfigManager
import com.tnvsai.yatramate.ui.components.NotificationTypeCard
import kotlinx.coroutines.launch
import androidx.compose.runtime.collectAsState

/**
 * Main settings screen for managing notification type configurations
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSettingsScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // Collect state from NotificationConfigManager
    val enabledTypes by NotificationConfigManager.enabledTypes.collectAsState()
    val enabledApps by NotificationConfigManager.enabledApps.collectAsState()
    val disabledApps by NotificationConfigManager.disabledApps.collectAsState()
    
    // Get all notification types from config
    val allTypes = remember { ConfigManager.getAllNotificationTypes() }
    
    // Track expanded states for each type
    val expandedStates = remember { mutableStateMapOf<String, Boolean>() }
    
    // UI state
    var showExportDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    var exportText by remember { mutableStateOf("") }
    var importText by remember { mutableStateOf("") }
    var showMessage by remember { mutableStateOf<String?>(null) }
    
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Notification Settings") },
                actions = {
                    IconButton(onClick = { showExportDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.FileDownload,
                            contentDescription = "Export Configuration"
                        )
                    }
                    IconButton(onClick = { showImportDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.FileUpload,
                            contentDescription = "Import Configuration"
                        )
                    }
                    IconButton(onClick = { showResetDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.RestartAlt,
                            contentDescription = "Reset to Defaults"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Summary card
            item {
                SummaryCard(
                    totalTypes = allTypes.size,
                    enabledTypes = enabledTypes.size,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            // Notification type cards
            items(allTypes, key = { it.id }) { type ->
                NotificationTypeCard(
                    notificationType = type,
                    isEnabled = enabledTypes.contains(type.id),
                    enabledApps = enabledApps[type.id] ?: emptySet(),
                    disabledApps = disabledApps[type.id] ?: emptySet(),
                    isExpanded = expandedStates[type.id] ?: false,
                    onEnabledChanged = { enabled ->
                        scope.launch {
                            if (enabled) {
                                NotificationConfigManager.enableType(type.id)
                            } else {
                                NotificationConfigManager.disableType(type.id)
                            }
                        }
                    },
                    onExpandedChanged = { expanded ->
                        expandedStates[type.id] = expanded
                    },
                    onAppEnabledChanged = { packageName, enabled ->
                        scope.launch {
                            if (enabled) {
                                NotificationConfigManager.enableAppForType(type.id, packageName)
                            } else {
                                NotificationConfigManager.disableAppForType(type.id, packageName)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
    
    // Export dialog
    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text("Export Configuration") },
            text = {
                Column {
                    Text(
                        text = "Copy this JSON configuration:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextField(
                        value = ConfigPersistence.exportConfiguration(),
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 10
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showExportDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
    
    // Import dialog
    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { 
                showImportDialog = false
                importText = ""
            },
            title = { Text("Import Configuration") },
            text = {
                Column {
                    Text(
                        text = "Paste JSON configuration:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextField(
                        value = importText,
                        onValueChange = { importText = it },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 10,
                        placeholder = { Text("Paste JSON here...") }
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        try {
                            ConfigPersistence.importConfiguration(importText)
                            NotificationConfigManager.loadFromConfig()
                            showMessage = "Configuration imported successfully"
                            showImportDialog = false
                            importText = ""
                        } catch (e: Exception) {
                            showMessage = "Import failed: ${e.message}"
                        }
                    }
                ) {
                    Text("Import")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { 
                        showImportDialog = false
                        importText = ""
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
    
    // Reset dialog
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset to Defaults") },
            text = { Text("Are you sure you want to reset all notification settings to default values?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            NotificationConfigManager.resetToDefaults()
                            showMessage = "Settings reset to defaults"
                            showResetDialog = false
                        }
                    }
                ) {
                    Text("Reset", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
    
    // Snackbar for messages
    showMessage?.let { message ->
        LaunchedEffect(message) {
            kotlinx.coroutines.delay(3000)
            showMessage = null
        }
    }
    
    if (showMessage != null) {
        Snackbar(
            modifier = Modifier.padding(16.dp),
            action = {
                TextButton(onClick = { showMessage = null }) {
                    Text("Dismiss")
                }
            }
        ) {
            Text(showMessage!!)
        }
    }
}

@Composable
fun SummaryCard(
    totalTypes: Int,
    enabledTypes: Int,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "$totalTypes",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Total Types",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "$enabledTypes",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    color = Color(0xFF4CAF50)
                )
                Text(
                    text = "Enabled",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${totalTypes - enabledTypes}",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    color = Color(0xFF757575)
                )
                Text(
                    text = "Disabled",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

