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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.automatelinux.pt.data.model.GeocodeSuggestion
import com.automatelinux.pt.ui.components.AutocompleteField
import com.automatelinux.pt.ui.components.PreSuggestion
import com.automatelinux.pt.util.LocalAppStrings

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
    gpsLoading: Boolean = false,
    preSuggestions: List<PreSuggestion> = emptyList(),
    onLongPressSuggestion: ((GeocodeSuggestion) -> Unit)? = null
) {
    val strings = LocalAppStrings.current
    var text by remember(value) { mutableStateOf(value?.name ?: "") }
    var programmatic by remember { mutableStateOf(false) }

    LaunchedEffect(value) {
        programmatic = true
    }

    AutocompleteField(
        value = text,
        onValueChange = { newText ->
            programmatic = false
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
        suppressSearch = programmatic,
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
        preSuggestions = preSuggestions,
        onLongPressSuggestion = onLongPressSuggestion,
        emptyText = LocalAppStrings.current.nothingFound,
        errorText = LocalAppStrings.current.searchUnavailable,
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
                            contentDescription = strings.useCurrentLocation,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        } else null
    )
}
