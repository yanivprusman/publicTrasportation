package com.automatelinux.pt.ui.arrivals

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.DepartureBoard
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.automatelinux.pt.data.model.StopResult
import com.automatelinux.pt.ui.viewmodel.ArrivalsState
import com.automatelinux.pt.util.LocalAppStrings

@Composable
fun ArrivalsPanel(
    state: ArrivalsState,
    onStationSelect: (String, String) -> Unit,
    onLineFilterChange: (String) -> Unit,
    onShowVehicleMarkersChange: (Boolean) -> Unit,
    onSearchStops: suspend (String) -> List<StopResult>,
    onVehicleSelect: ((Double, Double) -> Unit)? = null,
    getDestinationName: (String?) -> String,
    nearbyStops: List<StopResult> = emptyList(),
    gpsNearbyStops: List<StopResult> = emptyList(),
    favoriteLines: Set<String> = emptySet(),
    onToggleFavoriteLine: ((String) -> Unit)? = null,
    favoriteStations: List<Pair<String, String>> = emptyList(),
    isStationFavorite: Boolean = false,
    onToggleFavoriteStation: (() -> Unit)? = null,
    onOpenBoard: (() -> Unit)? = null,
    onPinWidget: (() -> Unit)? = null,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current

    Column(modifier = modifier) {
        if (state.stationCode.isEmpty() && (favoriteStations.isNotEmpty() || favoriteLines.isNotEmpty())) {
            FavoritesSection(
                favoriteLines = favoriteLines,
                favoriteStations = favoriteStations,
                onStationSelect = onStationSelect,
                onToggleFavoriteLine = onToggleFavoriteLine
            )
        }

        TransportControls(
            stationCode = state.stationCode,
            stationName = state.stationName,
            onStationSelect = onStationSelect,
            lastUpdated = state.lastUpdated,
            showVehicleMarkers = state.showVehicleMarkers,
            onShowVehicleMarkersChange = onShowVehicleMarkersChange,
            onSearchStops = onSearchStops,
            isStationFavorite = isStationFavorite,
            onToggleFavoriteStation = onToggleFavoriteStation
        )

        // Quick-switch chips for the stops around the user's GPS position —
        // one tap moves the board to the stop they're standing at.
        if (gpsNearbyStops.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    Icons.Default.NearMe,
                    contentDescription = strings.nearbyStops,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                gpsNearbyStops.forEach { stop ->
                    FilterChip(
                        selected = stop.stopCode == state.stationCode,
                        onClick = { onStationSelect(stop.stopCode, stop.stopName) },
                        label = {
                            // FSI/PDI isolate the (often Hebrew) stop name so the
                            // "· 153m walk" suffix keeps its order in mixed bidi text.
                            Text(
                                "⁨${stop.stopName}⁩ · " +
                                    strings.walkingDistance(stop.distanceMeters)
                            )
                        }
                    )
                }
            }
        }

        if (onOpenBoard != null && state.stationCode.isNotEmpty()) {
            Button(
                onClick = onOpenBoard,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF14110A),
                    contentColor = Color(0xFFFFB300)
                )
            ) {
                Icon(
                    Icons.Default.DepartureBoard,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(strings.departureBoard, fontWeight = FontWeight.Bold)
            }
        }

        if (onPinWidget != null && state.stationCode.isNotEmpty()) {
            OutlinedButton(
                onClick = onPinWidget,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(
                    Icons.Default.Widgets,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(strings.pinWidget)
            }
        }

        StationArrivals(
            allVisits = state.allVisits,
            timetable = state.timetable,
            timetableLoading = state.timetableLoading,
            timetableError = state.timetableError,
            error = state.error,
            loading = state.loading,
            getDestinationName = getDestinationName,
            onVehicleSelect = onVehicleSelect,
            favoriteLines = favoriteLines,
            onToggleFavoriteLine = onToggleFavoriteLine,
            availableLines = state.availableLines,
            lineFilter = state.lineFilter,
            onLineFilterChange = onLineFilterChange,
            onRetry = onRetry
        )

        if (nearbyStops.isNotEmpty()) {
            NearbyStopsSection(
                stops = nearbyStops,
                onStopSelect = { stop -> onStationSelect(stop.stopCode, stop.stopName) }
            )
        }

        Spacer(Modifier.height(200.dp))
    }
}

@Composable
fun FavoritesSection(
    favoriteLines: Set<String>,
    favoriteStations: List<Pair<String, String>>,
    onStationSelect: (String, String) -> Unit,
    onToggleFavoriteLine: ((String) -> Unit)? = null
) {
    val strings = LocalAppStrings.current

    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 4.dp)
        ) {
            Icon(
                Icons.Default.Star,
                contentDescription = null,
                tint = Color(0xFFFFD700),
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = strings.favorites,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
        }

        if (favoriteStations.isNotEmpty()) {
            Text(
                text = strings.favoriteStations,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 2.dp)
            )
            favoriteStations.forEach { (code, name) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onStationSelect(code, name) }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = null,
                        tint = Color(0xFFFFD700),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(name, style = MaterialTheme.typography.bodyMedium)
                        Text(code, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        if (favoriteLines.isNotEmpty()) {
            Text(
                text = strings.favoriteLines,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 2.dp)
            )
            Row(
                modifier = Modifier.padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                favoriteLines.sorted().forEach { line ->
                    LineBadge(line)
                }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
fun NearbyStopsSection(
    stops: List<StopResult>,
    onStopSelect: (StopResult) -> Unit
) {
    val strings = LocalAppStrings.current

    Spacer(Modifier.height(8.dp))
    HorizontalDivider()

    Row(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.NearMe,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = strings.nearbyStops,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
    }

    stops.take(8).forEach { stop ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onStopSelect(stop) }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.AutoMirrored.Filled.DirectionsWalk,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(2.dp))
                Text(
                    text = strings.walkingDistance(stop.distanceMeters),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
    }
}
