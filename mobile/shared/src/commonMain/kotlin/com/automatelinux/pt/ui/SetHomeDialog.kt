package com.automatelinux.pt.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.automatelinux.pt.data.model.GeocodeSuggestion
import com.automatelinux.pt.ui.components.AutocompleteField
import com.automatelinux.pt.util.LocalAppStrings

@Composable
fun SetHomeDialog(
    onGeocode: suspend (String) -> List<GeocodeSuggestion>,
    onSave: (GeocodeSuggestion) -> Unit,
    onDismiss: () -> Unit
) {
    val strings = LocalAppStrings.current
    var query by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.setHome) },
        text = {
            Column {
                AutocompleteField(
                    value = query,
                    onValueChange = { query = it },
                    label = strings.home,
                    onSearch = onGeocode,
                    emptyText = strings.nothingFound,
                    errorText = strings.searchUnavailable,
                    onSelect = { suggestion ->
                        onSave(suggestion)
                        onDismiss()
                    },
                    itemContent = { suggestion ->
                        Text(
                            text = suggestion.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(strings.cancel) }
        }
    )
}
