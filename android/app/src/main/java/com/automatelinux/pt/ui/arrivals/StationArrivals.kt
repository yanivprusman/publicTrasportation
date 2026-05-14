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
import com.automatelinux.pt.util.AppStrings
import com.automatelinux.pt.util.LocalAppStrings
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
    val strings = LocalAppStrings.current
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
            text = strings.monitoredVehicles(visits.size),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )

        if (visits.isEmpty() && !loading) {
            Text(
                text = strings.noVehiclesFound,
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
            Text(strings.headerLine, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.7f))
            Text(strings.headerDir, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.5f))
            Text(strings.headerDest, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.5f))
            Text(strings.headerArrival, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(strings.headerDist, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.7f))
        }
        HorizontalDivider()

        Column {
            visits.forEachIndexed { index, visit ->
                val journey = visit.monitoredVehicleJourney ?: return@forEachIndexed
                val call = journey.monitoredCall
                val arrivalDisplay = formatArrivalTime(call?.expectedArrivalTime, tick, strings)
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
                            Text(strings.vehicleRef(journey.vehicleRef ?: strings.notAvailable), style = MaterialTheme.typography.bodySmall)
                            call?.distanceFromStop?.let {
                                Text(strings.distanceMeters(it), style = MaterialTheme.typography.bodySmall)
                            }
                            call?.expectedArrivalTime?.let {
                                Text(strings.fullArrival(it), style = MaterialTheme.typography.bodySmall)
                            }
                            val loc = journey.vehicleLocation
                            if (loc != null && onVehicleSelect != null) {
                                Spacer(Modifier.height(4.dp))
                                Button(onClick = { onVehicleSelect(loc.latitude, loc.longitude) }) {
                                    Text(strings.showOnMap)
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

fun formatArrivalTime(isoString: String?, tick: Long, strings: AppStrings): ArrivalDisplay {
    if (isoString == null) return ArrivalDisplay("—")
    return try {
        val arrival = ZonedDateTime.parse(isoString)
        val now = ZonedDateTime.now()
        val diff = Duration.between(now, arrival)
        val minutes = diff.toMinutes()
        val timeStr = arrival.toLocalTime().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))

        when {
            minutes <= 0 -> ArrivalDisplay(strings.arrivalNow, timeStr)
            minutes <= 60 -> ArrivalDisplay(strings.arrivalInMin(minutes), timeStr)
            else -> ArrivalDisplay(timeStr)
        }
    } catch (_: Exception) {
        ArrivalDisplay(isoString.substringAfter("T").take(5))
    }
}
