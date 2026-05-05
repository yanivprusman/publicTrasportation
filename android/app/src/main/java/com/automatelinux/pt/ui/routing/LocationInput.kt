package com.automatelinux.pt.ui.routing

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.automatelinux.pt.data.model.GeocodeSuggestion
import com.automatelinux.pt.ui.components.AutocompleteField

@Composable
fun LocationInput(
    label: String,
    value: GeocodeSuggestion?,
    onSelect: (GeocodeSuggestion) -> Unit,
    onClear: () -> Unit,
    onGeocode: suspend (String) -> List<GeocodeSuggestion>,
    modifier: Modifier = Modifier,
    showGpsButton: Boolean = false,
    onGpsClick: (() -> Unit)? = null,
    gpsLoading: Boolean = false
) {
    var text by remember(value) { mutableStateOf(value?.name ?: "") }

    AutocompleteField(
        value = text,
        onValueChange = { newText ->
            text = newText
            if (newText != value?.name) {
                onClear()
            }
        },
        label = label,
        onSearch = onGeocode,
        onSelect = { suggestion ->
            text = suggestion.name
            onSelect(suggestion)
        },
        onClear = {
            text = ""
            onClear()
        },
        itemContent = { suggestion ->
            Text(
                text = suggestion.name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        modifier = modifier,
        leadingIcon = if (showGpsButton && onGpsClick != null) {
            {
                IconButton(onClick = onGpsClick, enabled = !gpsLoading) {
                    if (gpsLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Icon(
                            Icons.Default.MyLocation,
                            contentDescription = "Use current location",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        } else null
    )
}
