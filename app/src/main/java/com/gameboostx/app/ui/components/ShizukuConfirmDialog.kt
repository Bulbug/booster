package com.gameboostx.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gameboostx.app.ui.boost.PendingShizukuAction

/**
 * Every privileged (Shizuku) operation shows this before running — spec §13 requires
 * WHAT/WHY/WHAT MAY CHANGE/HOW TO RESTORE before every Apply/Cancel choice.
 */
@Composable
fun ShizukuConfirmDialog(
    action: PendingShizukuAction,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(action.title) },
        text = {
            Column {
                LabeledLine("WHAT IT DOES", action.what)
                LabeledLine("WHY IT IS NEEDED", action.why)
                LabeledLine("WHAT MAY CHANGE", action.mayChange)
                LabeledLine("HOW TO RESTORE IT", action.howToRestore)
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Apply") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
    )
}

@Composable
private fun LabeledLine(label: String, value: String) {
    Column(Modifier.padding(bottom = 10.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
