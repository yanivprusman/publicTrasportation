package com.automatelinux.pt.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.automatelinux.pt.data.model.GeocodeSuggestion

@Composable
fun SavePlaceDialog(
    target: GeocodeSuggestion,
    onSaveHome: () -> Unit,
    onSaveWork: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save as") },
        text = { Text(target.name) },
        confirmButton = {
            TextButton(onClick = onSaveHome) { Text("Home") }
        },
        dismissButton = {
            TextButton(onClick = onSaveWork) { Text("Work") }
        }
    )
}
