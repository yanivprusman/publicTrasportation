package com.automatelinux.pt.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.automatelinux.pt.data.model.GeocodeSuggestion
import com.automatelinux.pt.util.LocalAppStrings

@Composable
fun SavePlaceDialog(
    target: GeocodeSuggestion,
    onSaveHome: () -> Unit,
    onSaveWork: () -> Unit,
    onDismiss: () -> Unit
) {
    val strings = LocalAppStrings.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.saveAs) },
        text = { Text(target.name) },
        confirmButton = {
            TextButton(onClick = onSaveHome) { Text(strings.home) }
        },
        dismissButton = {
            TextButton(onClick = onSaveWork) { Text(strings.work) }
        }
    )
}
