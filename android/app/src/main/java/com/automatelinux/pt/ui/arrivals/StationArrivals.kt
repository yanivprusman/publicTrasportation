package com.automatelinux.pt.ui.arrivals

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.automatelinux.pt.data.model.MonitoredStopVisit
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.ZonedDateTime

@Composable
fun StationArrivals(
    visits: List<MonitoredStopVisit>,
    error: String?,
    loading: Boolean,
    getDestinationName: (String?) -> String,
    onVehicleSelect: ((Double, Double) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var expandedIndex by remember { mutableIntStateOf(-1) }
    var tick by remember { mutableLongStateOf(0L) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            tick++
        }
    }

    Column(modifier = modifier) {
        if (error != null) {
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(16.dp)
            )
            return@Column
        }

        Text(
            text = "Monitored Vehicles: ${visits.size}",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )

        if (visits.isEmpty() && !loading) {
            Text(
                text = "No vehicles found",
                modifier = Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@Column
        }

        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Line", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.7f))
            Text("Dir", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.5f))
            Text("Dest", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.5f))
            Text("Arrival", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("Dist", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.7f))
        }
        HorizontalDivider()

        LazyColumn {
            itemsIndexed(visits) { index, visit ->
                val journey = visit.monitoredVehicleJourney ?: return@itemsIndexed
                val call = journey.monitoredCall
                val arrivalDisplay = formatArrivalTime(call?.expectedArrivalTime, tick)
                val destName = getDestinationName(journey.destinationRef)
                val distance = call?.distanceFromStop?.let { "${it}m" } ?: ""
                val isExpanded = index == expandedIndex

                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                expandedIndex = if (isExpanded) -1 else index
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(journey.publishedLineName ?: "", modifier = Modifier.weight(0.7f), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        Text(journey.directionRef ?: "", modifier = Modifier.weight(0.5f), style = MaterialTheme.typography.bodySmall)
                        Text(destName, modifier = Modifier.weight(1.5f), style = MaterialTheme.typography.bodySmall, maxLines = 1)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(arrivalDisplay.primary, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                            arrivalDisplay.secondary?.let {
                                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Text(distance, modifier = Modifier.weight(0.7f), style = MaterialTheme.typography.bodySmall)
                    }

                    AnimatedVisibility(visible = isExpanded) {
                        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                            Text("Vehicle: ${journey.vehicleRef ?: "N/A"}", style = MaterialTheme.typography.bodySmall)
                            call?.distanceFromStop?.let {
                                Text("Distance: ${it}m", style = MaterialTheme.typography.bodySmall)
                            }
                            call?.expectedArrivalTime?.let {
                                Text("Full arrival: $it", style = MaterialTheme.typography.bodySmall)
                            }
                            val loc = journey.vehicleLocation
                            if (loc != null && onVehicleSelect != null) {
                                Spacer(Modifier.height(4.dp))
                                Button(onClick = { onVehicleSelect(loc.latitude, loc.longitude) }) {
                                    Text("Show on map")
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                }
            }
        }
    }
}

data class ArrivalDisplay(val primary: String, val secondary: String? = null)

fun formatArrivalTime(isoString: String?, tick: Long): ArrivalDisplay {
    if (isoString == null) return ArrivalDisplay("—")
    return try {
        val arrival = ZonedDateTime.parse(isoString)
        val now = ZonedDateTime.now()
        val diff = Duration.between(now, arrival)
        val minutes = diff.toMinutes()
        val timeStr = arrival.toLocalTime().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))

        when {
            minutes <= 0 -> ArrivalDisplay("now", timeStr)
            minutes <= 60 -> ArrivalDisplay("in ${minutes}min", timeStr)
            else -> ArrivalDisplay(timeStr)
        }
    } catch (_: Exception) {
        ArrivalDisplay(isoString.substringAfter("T").take(5))
    }
}
