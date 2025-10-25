package com.example.smart

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.smart.ble.SimpleBLEService

class TestActivity : ComponentActivity() {
    
    private lateinit var simpleBLEService: SimpleBLEService
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        simpleBLEService = SimpleBLEService(this)
        
        setContent {
            TestScreen()
        }
    }
    
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun TestScreen() {
        var isConnected by remember { mutableStateOf(false) }
        var deviceName by remember { mutableStateOf<String?>(null) }
        var deviceAddress by remember { mutableStateOf<String?>(null) }
        var logMessages by remember { mutableStateOf(listOf<String>()) }
        
        // Set up callback
        LaunchedEffect(Unit) {
            simpleBLEService.setCallback(object : SimpleBLEService.SimpleBLECallback {
                override fun onConnectionStatusChanged(connected: Boolean, name: String?, address: String?) {
                    isConnected = connected
                    deviceName = name
                    deviceAddress = address
                }
                
                override fun onLogMessage(message: String) {
                    val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
                    logMessages = (listOf("[$timestamp] $message") + logMessages).take(50) // Keep last 50 logs
                }
            })
        }
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Title
            Text(
                text = "BLE Test - Simple Communication",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            // Connection Status Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (isConnected) Color(0xFFE8F5E8) else Color(0xFFFFEBEE)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Connection Status",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "Status: ${if (isConnected) "Connected" else "Disconnected"}",
                        color = if (isConnected) Color.Green else Color.Red
                    )
                    
                    if (deviceName != null) {
                        Text(text = "Device: $deviceName")
                    }
                    
                    if (deviceAddress != null) {
                        Text(text = "Address: $deviceAddress")
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Action Buttons
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Connect Button
                Button(
                    onClick = {
                        Log.i("TestActivity", "Starting connection...")
                        simpleBLEService.startConnection()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isConnected,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Blue
                    )
                ) {
                    Text("Connect to ESP32")
                }
                
                // Send Hello World Button
                Button(
                    onClick = {
                        Log.i("TestActivity", "Sending Hello World...")
                        simpleBLEService.sendHelloWorld()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = isConnected,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Green
                    )
                ) {
                    Text("Send Hello World")
                }
                
                // Disconnect Button
                Button(
                    onClick = {
                        Log.i("TestActivity", "Disconnecting...")
                        simpleBLEService.disconnect()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = isConnected,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Red
                    )
                ) {
                    Text("Disconnect")
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Log Display
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color.Black
                )
            ) {
                Column(
                    modifier = Modifier.padding(8.dp)
                ) {
                    Text(
                        text = "Debug Logs:",
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    if (logMessages.isEmpty()) {
                        Text(
                            text = "No logs yet. Click 'Connect to ESP32' to start.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    } else {
                        logMessages.forEach { log ->
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
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Instructions
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFF5F5F5)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Instructions:",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "1. Make sure ESP32 is running and shows 'Connected'",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "2. Click 'Connect to ESP32'",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "3. Wait for 'READY TO SEND DATA!' in logs",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "4. Click 'Send Hello World'",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "5. Check ESP32 Serial Monitor for received data",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        simpleBLEService.cleanup()
    }
}
