package com.tnvsai.yatramate.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
fun QuickActionPanel(
    actions: List<QuickAction>,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        actions.forEach { action ->
            QuickActionButton(
                action = action,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun QuickActionButton(
    action: QuickAction,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = action.onClick,
        modifier = modifier.height(56.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = when (action.type) {
                ActionType.PRIMARY -> MaterialTheme.colorScheme.primary
                ActionType.SECONDARY -> MaterialTheme.colorScheme.secondary
                ActionType.DANGER -> MaterialTheme.colorScheme.error
            }
        )
    ) {
        Text(
            text = action.label,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.semantics {
                contentDescription = action.label
            }
        )
    }
}

data class QuickAction(
    val label: String,
    val onClick: () -> Unit,
    val type: ActionType = ActionType.PRIMARY
)

enum class ActionType {
    PRIMARY, SECONDARY, DANGER
}


