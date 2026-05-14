package com.automatelinux.pt.ui.routing

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun DebugSettingsDialog(
    autoSearch: Boolean,
    expandSheet: Boolean,
    onConfirm: (autoSearch: Boolean, expandSheet: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var localAutoSearch by remember { mutableStateOf(autoSearch) }
    var localExpandSheet by remember { mutableStateOf(expandSheet) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Debug Settings") },
        text = {
            Column {
                Text(
                    "Configure what happens when the debug button is tapped.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = localAutoSearch,
                        onCheckedChange = { localAutoSearch = it }
                    )
                    Text(
                        "Auto-search routes",
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = localExpandSheet,
                        onCheckedChange = { localExpandSheet = it }
                    )
                    Text(
                        "Expand bottom sheet",
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(localAutoSearch, localExpandSheet) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
