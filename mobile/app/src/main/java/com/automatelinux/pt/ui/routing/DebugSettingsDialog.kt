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
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.automatelinux.pt.data.model.GeocodeSuggestion
import com.automatelinux.pt.util.LocalAppStrings

@Composable
fun DebugSettingsDialog(
    autoSearch: Boolean,
    expandSheet: Boolean,
    locationIconStyle: String,
    fromSuggestion: GeocodeSuggestion,
    toSuggestion: GeocodeSuggestion,
    onConfirm: (autoSearch: Boolean, expandSheet: Boolean, locationIconStyle: String, from: GeocodeSuggestion, to: GeocodeSuggestion) -> Unit,
    onDismiss: () -> Unit,
    onGeocode: suspend (String) -> List<GeocodeSuggestion>
) {
    val strings = LocalAppStrings.current
    var localAutoSearch by remember { mutableStateOf(autoSearch) }
    var localExpandSheet by remember { mutableStateOf(expandSheet) }
    var localLocationIcon by remember { mutableStateOf(locationIconStyle) }
    var localFrom by remember { mutableStateOf(fromSuggestion) }
    var localTo by remember { mutableStateOf(toSuggestion) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.debugSettings) },
        text = {
            Column {
                Text(
                    strings.debugDescription,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    LocationInput(
                        label = strings.from,
                        value = localFrom,
                        onSelect = { localFrom = it },
                        onClear = { },
                        onGeocode = onGeocode,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    LocationInput(
                        label = strings.to,
                        value = localTo,
                        onSelect = { localTo = it },
                        onClear = { },
                        onGeocode = onGeocode,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
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
                        strings.autoSearchRoutes,
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
                        strings.expandBottomSheet,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text("Location icon", style = MaterialTheme.typography.bodyMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = localLocationIcon == "dot",
                        onClick = { localLocationIcon = "dot" }
                    )
                    Text("Blue dot", modifier = Modifier.padding(end = 16.dp))
                    RadioButton(
                        selected = localLocationIcon == "person",
                        onClick = { localLocationIcon = "person" }
                    )
                    Text("Person")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(localAutoSearch, localExpandSheet, localLocationIcon, localFrom, localTo) }) {
                Text(strings.save)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.cancel)
            }
        }
    )
}
