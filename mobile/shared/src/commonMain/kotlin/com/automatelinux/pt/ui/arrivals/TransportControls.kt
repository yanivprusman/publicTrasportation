package com.automatelinux.pt.ui.arrivals

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import kotlinx.datetime.Clock
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.automatelinux.pt.data.model.StopResult
import com.automatelinux.pt.ui.components.AutocompleteField
import com.automatelinux.pt.util.LocalAppStrings
import kotlinx.coroutines.delay

@Composable
fun TransportControls(
    stationCode: String,
    stationName: String,
    onStationSelect: (String, String) -> Unit,
    lastUpdated: Long?,
    showVehicleMarkers: Boolean,
    onShowVehicleMarkersChange: (Boolean) -> Unit,
    onSearchStops: suspend (String) -> List<StopResult>,
    isStationFavorite: Boolean = false,
    onToggleFavoriteStation: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    var stationText by remember(stationName) { mutableStateOf(stationName) }
    var agoText by remember { mutableStateOf("") }
    var tick by remember { mutableLongStateOf(0L) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            tick++
        }
    }

    LaunchedEffect(lastUpdated, tick) {
        if (lastUpdated != null) {
            val seconds = (Clock.System.now().toEpochMilliseconds() - lastUpdated) / 1000
            agoText = when {
                seconds < 5 -> strings.justNow
                seconds < 60 -> strings.secondsAgo(seconds)
                else -> strings.minutesAgo(seconds / 60)
            }
        }
    }

    Column(modifier = modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
        AutocompleteField(
            value = stationText,
            onValueChange = { stationText = it },
            label = strings.station,
            onSearch = onSearchStops,
            onSelect = { stop ->
                stationText = "${stop.stopName} (${stop.stopCode})"
                onStationSelect(stop.stopCode, stop.stopName)
            },
            onClear = { stationText = "" },
            itemContent = { stop ->
                Column {
                    Text(
                        text = stop.stopName,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = stop.stopCode,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        if (lastUpdated != null && agoText.isNotEmpty()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Text(
                    text = strings.updatedAgo(agoText),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (stationCode.isNotEmpty() && onToggleFavoriteStation != null) {
                    IconButton(
                        onClick = onToggleFavoriteStation,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            if (isStationFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = if (isStationFavorite) {
                                strings.removeFavorite
                            } else {
                                strings.addFavorite
                            },
                            tint = if (isStationFavorite) Color(0xFFFFD700) else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = showVehicleMarkers,
                onCheckedChange = onShowVehicleMarkersChange
            )
            Text(strings.vehicles, style = MaterialTheme.typography.bodySmall)
        }
    }
}
