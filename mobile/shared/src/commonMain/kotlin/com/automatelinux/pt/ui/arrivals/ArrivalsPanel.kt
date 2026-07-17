package com.automatelinux.pt.ui.arrivals

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

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
    favoriteLines: Set<String> = emptySet(),
    onToggleFavoriteLine: ((String) -> Unit)? = null,
    favoriteStations: List<Pair<String, String>> = emptyList(),
    isStationFavorite: Boolean = false,
    onToggleFavoriteStation: (() -> Unit)? = null,
    onOpenBoard: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current

    Column(modifier = modifier) {
        ServiceAlertBanner()

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
            lineFilter = state.lineFilter,
            onLineFilterChange = onLineFilterChange,
            showVehicleMarkers = state.showVehicleMarkers,
            onShowVehicleMarkersChange = onShowVehicleMarkersChange,
            onSearchStops = onSearchStops,
            isStationFavorite = isStationFavorite,
            onToggleFavoriteStation = onToggleFavoriteStation
        )

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

        StationArrivals(
            visits = state.visits,
            error = state.error,
            loading = state.loading,
            getDestinationName = getDestinationName,
            onVehicleSelect = onVehicleSelect,
            favoriteLines = favoriteLines,
            onToggleFavoriteLine = onToggleFavoriteLine
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
fun ServiceAlertBanner() {
    val strings = LocalAppStrings.current
    val hour = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).hour
    val isPeak = hour in 7..9 || hour in 16..19

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isPeak) Color(0xFF1A0E00) else Color(0xFF0A1A0A)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (isPeak) Icons.Default.Warning else Icons.Default.Info,
                contentDescription = null,
                tint = if (isPeak) Color(0xFFFFB74D) else Color(0xFF81C784),
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (isPeak) strings.peakHoursNotice else strings.normalServiceNotice,
                style = MaterialTheme.typography.bodySmall,
                color = if (isPeak) Color(0xFFFFB74D) else Color(0xFF81C784)
            )
        }
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
