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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Warning
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
import java.time.LocalTime

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
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current

    Column(modifier = modifier) {
        ServiceAlertBanner()

        TransportControls(
            stationCode = state.stationCode,
            stationName = state.stationName,
            onStationSelect = onStationSelect,
            lastUpdated = state.lastUpdated,
            lineFilter = state.lineFilter,
            onLineFilterChange = onLineFilterChange,
            showVehicleMarkers = state.showVehicleMarkers,
            onShowVehicleMarkersChange = onShowVehicleMarkersChange,
            onSearchStops = onSearchStops
        )

        StationArrivals(
            visits = state.visits,
            error = state.error,
            loading = state.loading,
            getDestinationName = getDestinationName,
            onVehicleSelect = onVehicleSelect
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
fun ServiceAlertBanner() {
    val strings = LocalAppStrings.current
    val hour = LocalTime.now().hour
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
