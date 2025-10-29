package com.tnvsai.yatramate.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tnvsai.yatramate.model.CallState
import com.tnvsai.yatramate.notification.*

/**
 * Unified history card component that displays all notification types
 */
@Composable
fun UnifiedHistoryCard(entry: UnifiedNotificationEntry) {
    val context = LocalContext.current
    val clipboardManager = remember { context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager }
    
    val cardColor = when (entry) {
        is NavigationNotificationEntry -> Color(0xFF4CAF50).copy(alpha = 0.1f)
        is PhoneCallNotificationEntry -> when (entry.callState) {
            CallState.INCOMING -> Color(0xFF4CAF50).copy(alpha = 0.1f)
            CallState.MISSED -> Color(0xFFF44336).copy(alpha = 0.1f)
            CallState.ONGOING -> Color(0xFF2196F3).copy(alpha = 0.1f)
            CallState.ENDED -> Color(0xFF9E9E9E).copy(alpha = 0.1f)
        }
        is MessageNotificationEntry -> Color(0xFF4CAF50).copy(alpha = 0.1f)
        is GenericNotificationEntry -> Color(0xFF2196F3).copy(alpha = 0.1f)
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header with timestamp and type indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    entry.timestampDisplay,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    NotificationTypeIcon(entry)
                    Text(
                        getNotificationTypeLabel(entry),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                    if (entry.sentToMCU) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Sent to MCU",
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(
                        onClick = {
                            val notificationText = formatNotificationForClipboard(entry)
                            val clip = ClipData.newPlainText("Notification Details", notificationText)
                            clipboardManager.setPrimaryClip(clip)
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy to clipboard",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Package name
            Text(
                "Package: ${entry.packageName}",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            
            // Type-specific content
            when (entry) {
                is NavigationNotificationEntry -> NavigationEntryContent(entry)
                is PhoneCallNotificationEntry -> PhoneCallEntryContent(entry)
                is MessageNotificationEntry -> MessageEntryContent(entry)
                is GenericNotificationEntry -> GenericEntryContent(entry)
            }
            
            // Debug info section (expandable or always visible)
            if (hasDebugInfo(entry.debugInfo)) {
                Spacer(modifier = Modifier.height(8.dp))
                Divider(modifier = Modifier.padding(vertical = 4.dp))
                DebugInfoContent(entry.debugInfo)
            }
        }
    }
}

@Composable
fun NotificationTypeIcon(entry: UnifiedNotificationEntry) {
    val (icon, color) = when (entry) {
        is NavigationNotificationEntry -> Icons.Default.Navigation to Color(0xFF4CAF50)
        is PhoneCallNotificationEntry -> Icons.Default.Phone to Color(0xFF2196F3)
        is MessageNotificationEntry -> Icons.Default.Message to Color(0xFF4CAF50)
        is GenericNotificationEntry -> Icons.Default.Notifications to Color(0xFF2196F3)
    }
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = color,
        modifier = Modifier.size(20.dp)
    )
}

fun getNotificationTypeLabel(entry: UnifiedNotificationEntry): String {
    return when (entry) {
        is NavigationNotificationEntry -> "Navigation"
        is PhoneCallNotificationEntry -> when (entry.callState) {
            CallState.INCOMING -> "Incoming Call"
            CallState.MISSED -> "Missed Call"
            CallState.ONGOING -> "Ongoing Call"
            CallState.ENDED -> "Call Ended"
        }
        is MessageNotificationEntry -> entry.appName
        is GenericNotificationEntry -> entry.notificationType.replaceFirstChar { it.uppercaseChar() }
    }
}

@Composable
fun NavigationEntryContent(entry: NavigationNotificationEntry) {
    Column {
        if (entry.title != null) {
            Text(
                "Title: ${entry.title}",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
        if (entry.text != null) {
            Text(
                "Text: ${entry.text}",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
        
        if (entry.isNavigation) {
            Row(
                modifier = Modifier.padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                entry.direction?.let {
                    Text(
                        "Direction: $it",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF4CAF50),
                        fontWeight = FontWeight.Medium
                    )
                }
                entry.distance?.let {
                    Text(
                        "Distance: $it",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF4CAF50),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            entry.maneuver?.let {
                Text(
                    "Maneuver: $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF4CAF50),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            entry.eta?.let {
                Text(
                    "ETA: $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF4CAF50),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        } else {
            Text(
                "⚠ Not a navigation notification",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
        }
        
        entry.parsingMethod?.let {
            Text(
                "Parser: $it${entry.parserName?.let { " ($it)" } ?: ""}",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
fun PhoneCallEntryContent(entry: PhoneCallNotificationEntry) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            entry.callerName?.let {
                Text(
                    "Name: $it",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
            }
            entry.callerNumber?.let {
                Text(
                    "Number: $it",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        
        if (entry.phoneNumberFromExtras != null) {
            Text(
                "From Extras: ${entry.phoneNumberFromExtras}",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        
        if (entry.title != null || entry.text != null) {
            Spacer(modifier = Modifier.height(4.dp))
            if (entry.title != null) {
                Text(
                    "Title: ${entry.title}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
            if (entry.text != null) {
                Text(
                    "Text: ${entry.text}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
        }
        
        Row(
            modifier = Modifier.padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (entry.wasOutgoing) {
                Text(
                    "Outgoing",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF2196F3)
                )
            }
            if (entry.wasAnswered) {
                Text(
                    "Answered",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF4CAF50)
                )
            }
            if (entry.wasMissed) {
                Text(
                    "Missed",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFF44336)
                )
            }
        }
        
        entry.deviceProfile?.let {
            Text(
                "Device Profile: $it",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        entry.detectionPattern?.let {
            Text(
                "Pattern: $it",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
        }
    }
}

@Composable
fun MessageEntryContent(entry: MessageNotificationEntry) {
    Column {
        entry.sender?.let {
            Text(
                "From: $it",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
        entry.message?.let {
            Text(
                "Message: $it",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
        entry.messageType?.let {
            Text(
                "Type: $it",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
        }
        if (entry.hasAttachment) {
            Text(
                "Has Attachment",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF2196F3)
            )
        }
        entry.groupName?.let {
            Text(
                "Group: $it",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
        }
    }
}

@Composable
fun GenericEntryContent(entry: GenericNotificationEntry) {
    Column {
        if (entry.title != null) {
            Text(
                "Title: ${entry.title}",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
        if (entry.text != null) {
            Text(
                "Text: ${entry.text}",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
        
        if (entry.extractedData.isNotEmpty()) {
            Text(
                "Extracted Data:",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
            )
            entry.extractedData.forEach { (key, value) ->
                Text(
                    "  $key: $value",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
        }
        
        if (entry.classificationKeywordsMatched.isNotEmpty()) {
            Text(
                "Matched Keywords: ${entry.classificationKeywordsMatched.joinToString(", ")}",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF4CAF50),
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        if (entry.classificationTitlePatternsMatched.isNotEmpty()) {
            Text(
                "Matched Patterns: ${entry.classificationTitlePatternsMatched.joinToString(", ")}",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF4CAF50),
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        if (entry.classificationAppMatched) {
            Text(
                "✓ App matched",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF4CAF50)
            )
        }
    }
}

@Composable
fun DebugInfoContent(debugInfo: DebugInfo) {
    Column {
        Text(
            "Debug Info:",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = Color.Gray,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        
        debugInfo.classificationConfidence?.let {
            Text(
                "  Confidence: $it",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                fontSize = 11.sp
            )
        }
        debugInfo.classificationMethod?.let {
            Text(
                "  Method: $it",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                fontSize = 11.sp
            )
        }
        if (debugInfo.bleTransmissionAttempts > 0) {
            Text(
                "  BLE Attempts: ${debugInfo.bleTransmissionAttempts} ${if (debugInfo.bleTransmissionSuccess) "✓" else "✗"}",
                style = MaterialTheme.typography.bodySmall,
                color = if (debugInfo.bleTransmissionSuccess) Color(0xFF4CAF50) else Color(0xFFF44336),
                fontSize = 11.sp
            )
        }
        if (debugInfo.bleTransmissionErrors.isNotEmpty()) {
            Text(
                "  BLE Errors: ${debugInfo.bleTransmissionErrors.joinToString(", ")}",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFF44336),
                fontSize = 11.sp
            )
        }
        debugInfo.transformerOutput?.let {
            Text(
                "  JSON: ${it.take(50)}${if (it.length > 50) "..." else ""}",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                fontSize = 11.sp
            )
        }
        if (debugInfo.parsingErrors.isNotEmpty()) {
            Text(
                "  Parsing Errors: ${debugInfo.parsingErrors.joinToString(", ")}",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFF44336),
                fontSize = 11.sp
            )
        }
        debugInfo.filterReason?.let {
            Text(
                "  Filtered: $it",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFFF9800),
                fontSize = 11.sp
            )
        }
    }
}

fun hasDebugInfo(debugInfo: DebugInfo): Boolean {
    return debugInfo.classificationConfidence != null ||
           debugInfo.classificationMethod != null ||
           debugInfo.bleTransmissionAttempts > 0 ||
           debugInfo.parsingErrors.isNotEmpty() ||
           debugInfo.filterReason != null ||
           debugInfo.transformerOutput != null
}

/**
 * Format notification entry into a readable text format for clipboard
 */
private fun formatNotificationForClipboard(entry: UnifiedNotificationEntry): String {
    return buildString {
        appendLine("=== Notification Details ===")
        appendLine("Type: ${getNotificationTypeLabel(entry)}")
        appendLine("Timestamp: ${entry.timestampDisplay}")
        appendLine("Package: ${entry.packageName}")
        appendLine("Sent to MCU: ${if (entry.sentToMCU) "Yes" else "No"}")
        appendLine()
        
        when (entry) {
            is NavigationNotificationEntry -> {
                appendLine("=== Navigation Information ===")
                entry.title?.let { appendLine("Title: $it") }
                entry.text?.let { appendLine("Text: $it") }
                entry.bigText?.let { appendLine("Big Text: $it") }
                entry.direction?.let { appendLine("Direction: $it") }
                entry.distance?.let { appendLine("Distance: $it") }
                entry.maneuver?.let { appendLine("Maneuver: $it") }
                entry.eta?.let { appendLine("ETA: $it") }
                entry.parsedData?.let { appendLine("Parsed Data: $it") }
                appendLine("Is Google Maps: ${entry.isGoogleMaps}")
                appendLine("Is Navigation: ${entry.isNavigation}")
                entry.parsingMethod?.let { appendLine("Parsing Method: $it") }
                entry.parserName?.let { appendLine("Parser Name: $it") }
            }
            is PhoneCallNotificationEntry -> {
                appendLine("=== Phone Call Information ===")
                entry.title?.let { appendLine("Title: $it") }
                entry.text?.let { appendLine("Text: $it") }
                entry.bigText?.let { appendLine("Big Text: $it") }
                appendLine("Call State: ${entry.callState}")
                entry.callerName?.let { appendLine("Caller Name: $it") }
                entry.callerNumber?.let { appendLine("Caller Number: $it") }
                appendLine("Duration: ${entry.duration}s")
                entry.deviceProfile?.let { appendLine("Device Profile: $it") }
                entry.detectionPattern?.let { appendLine("Detection Pattern: $it") }
                appendLine("Was Outgoing: ${entry.wasOutgoing}")
                appendLine("Was Answered: ${entry.wasAnswered}")
                appendLine("Was Missed: ${entry.wasMissed}")
                entry.phoneNumberFromExtras?.let { appendLine("Phone Number (from extras): $it") }
            }
            is MessageNotificationEntry -> {
                appendLine("=== Message Information ===")
                entry.title?.let { appendLine("Title: $it") }
                entry.text?.let { appendLine("Text: $it") }
                entry.bigText?.let { appendLine("Big Text: $it") }
                entry.sender?.let { appendLine("Sender: $it") }
                entry.message?.let { appendLine("Message: $it") }
                appendLine("App Name: ${entry.appName}")
                entry.messageType?.let { appendLine("Message Type: $it") }
                appendLine("Has Attachment: ${entry.hasAttachment}")
                entry.groupName?.let { appendLine("Group Name: $it") }
            }
            is GenericNotificationEntry -> {
                appendLine("=== Generic Notification Information ===")
                entry.title?.let { appendLine("Title: $it") }
                entry.text?.let { appendLine("Text: $it") }
                entry.bigText?.let { appendLine("Big Text: $it") }
                appendLine("Notification Type: ${entry.notificationType}")
                entry.mcuType?.let { appendLine("MCU Type: $entry.mcuType") }
                if (entry.extractedData.isNotEmpty()) {
                    appendLine("Extracted Data:")
                    entry.extractedData.forEach { (key, value) ->
                        appendLine("  $key: $value")
                    }
                }
                if (entry.classificationKeywordsMatched.isNotEmpty()) {
                    appendLine("Matched Keywords: ${entry.classificationKeywordsMatched.joinToString(", ")}")
                }
                if (entry.classificationTitlePatternsMatched.isNotEmpty()) {
                    appendLine("Matched Title Patterns: ${entry.classificationTitlePatternsMatched.joinToString(", ")}")
                }
                appendLine("App Matched: ${entry.classificationAppMatched}")
            }
        }
        
        if (hasDebugInfo(entry.debugInfo)) {
            appendLine()
            appendLine("=== Debug Information ===")
            entry.debugInfo.classificationConfidence?.let {
                appendLine("Classification Confidence: $it")
            }
            entry.debugInfo.classificationMethod?.let {
                appendLine("Classification Method: $it")
            }
            if (entry.debugInfo.parsingErrors.isNotEmpty()) {
                appendLine("Parsing Errors:")
                entry.debugInfo.parsingErrors.forEach { error ->
                    appendLine("  - $error")
                }
            }
            if (entry.debugInfo.bleTransmissionErrors.isNotEmpty()) {
                appendLine("BLE Transmission Errors:")
                entry.debugInfo.bleTransmissionErrors.forEach { error ->
                    appendLine("  - $error")
                }
            }
            entry.debugInfo.filterReason?.let {
                appendLine("Filter Reason: $it")
            }
            appendLine("BLE Transmission Attempts: ${entry.debugInfo.bleTransmissionAttempts}")
            appendLine("BLE Transmission Success: ${entry.debugInfo.bleTransmissionSuccess}")
            entry.debugInfo.transformerOutput?.let {
                appendLine("Transformer Output: $it")
            }
        }
    }
}

