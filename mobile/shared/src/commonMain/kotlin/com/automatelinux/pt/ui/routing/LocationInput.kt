package com.automatelinux.pt.ui.routing

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.outlined.Circle
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.automatelinux.pt.data.model.GeocodeSuggestion
import com.automatelinux.pt.ui.components.AutocompleteField
import com.automatelinux.pt.ui.components.PreSuggestion
import com.automatelinux.pt.ui.theme.CurrentLocationBlue
import com.automatelinux.pt.util.LocalAppStrings

/** What the field's leading marker says the value is. */
enum class LocationMarker { NONE, ORIGIN, DESTINATION }

@Composable
fun LocationInput(
    label: String,
    value: GeocodeSuggestion?,
    onSelect: (GeocodeSuggestion) -> Unit,
    onClear: () -> Unit,
    onGeocode: suspend (String) -> List<GeocodeSuggestion>,
    modifier: Modifier = Modifier,
    marker: LocationMarker = LocationMarker.NONE,
    /**
     * The value in this field is the device's own position. The marker becomes the
     * map's "you" dot and a caption says so — the reverse-geocoded street name in the
     * field is where you are, and on its own is indistinguishable from one you typed.
     */
    isCurrentLocation: Boolean = false,
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
        leadingIcon = when {
            isCurrentLocation -> {
                { CurrentLocationDot(description = strings.myLocation) }
            }
            marker == LocationMarker.ORIGIN -> {
                {
                    Icon(
                        Icons.Outlined.Circle,
                        contentDescription = strings.originMarker,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            marker == LocationMarker.DESTINATION -> {
                {
                    Icon(
                        Icons.Default.Place,
                        contentDescription = strings.destinationMarker,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            else -> null
        },
        trailingAccessory = if (onGpsClick == null) null else {
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
        },
        supportingText = if (!isCurrentLocation) null else {
            {
                Text(
                    text = strings.myLocation,
                    style = MaterialTheme.typography.labelSmall,
                    color = CurrentLocationBlue
                )
            }
        }
    )
}

/**
 * The map's location dot, drawn at field scale: same blue, same white ring. Copying
 * the map's own glyph is the whole point — two different blues would read as two
 * different things.
 */
@Composable
private fun CurrentLocationDot(description: String) {
    Box(
        modifier = Modifier
            .size(18.dp)
            .semantics { contentDescription = description }
            .background(Color.White, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(13.dp)
                .background(CurrentLocationBlue, CircleShape)
        )
    }
}
