package com.automatelinux.pt.ui.arrivals

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.automatelinux.pt.data.model.MonitoredStopVisit
import com.automatelinux.pt.util.AppStrings
import com.automatelinux.pt.util.LocalAppStrings
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

private val lineColors = listOf(
    Color(0xFF4CAF50),
    Color(0xFFFF9800),
    Color(0xFF2196F3),
    Color(0xFFE91E63),
    Color(0xFF9C27B0),
    Color(0xFF00BCD4),
    Color(0xFF795548),
    Color(0xFFFF5722),
    Color(0xFF607D8B),
    Color(0xFF3F51B5),
)

fun lineColor(lineName: String?): Color {
    if (lineName.isNullOrBlank()) return Color(0xFF607D8B)
    val hash = lineName.hashCode().and(0x7FFFFFFF)
    return lineColors[hash % lineColors.size]
}

@Composable
fun LineBadge(lineName: String?, modifier: Modifier = Modifier) {
    val name = lineName ?: "?"
    val color = lineColor(lineName)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color)
            .widthIn(min = 36.dp)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = name,
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun NextArrivalHero(
    visit: MonitoredStopVisit,
    tick: Long,
    getDestinationName: (String?) -> String,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    val journey = visit.monitoredVehicleJourney ?: return
    val call = journey.monitoredCall
    val arrivalDisplay = formatArrivalTime(call?.expectedArrivalTime, tick, strings)
    val destName = getDestinationName(journey.destinationRef)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = strings.nextArrival,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            LineBadge(journey.publishedLineName)
            Spacer(Modifier.width(12.dp))
            Text(
                text = arrivalDisplay.primary,
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = "→ $destName",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        arrivalDisplay.secondary?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// Stable identity for a visit across refreshes — the list reorders every poll,
// so expansion must not be keyed by index.
private fun visitKey(visit: MonitoredStopVisit): String {
    val journey = visit.monitoredVehicleJourney
    return journey?.vehicleRef
        ?: "${journey?.publishedLineName}|${journey?.destinationRef}|${journey?.monitoredCall?.expectedArrivalTime}"
}

@Composable
fun StationArrivals(
    visits: List<MonitoredStopVisit>,
    error: String?,
    loading: Boolean,
    getDestinationName: (String?) -> String,
    onVehicleSelect: ((Double, Double) -> Unit)? = null,
    favoriteLines: Set<String> = emptySet(),
    onToggleFavoriteLine: ((String) -> Unit)? = null,
    availableLines: List<String> = emptyList(),
    lineFilter: String = "",
    onLineFilterChange: ((String) -> Unit)? = null,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    var expandedKey by remember { mutableStateOf<String?>(null) }
    var tick by remember { mutableLongStateOf(0L) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            tick++
        }
    }

    Column(modifier = modifier) {
        if (error != null && availableLines.isEmpty()) {
            // Nothing cached to show — full error state.
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = strings.arrivalsFetchError,
                    color = MaterialTheme.colorScheme.error
                )
                if (onRetry != null) {
                    OutlinedButton(
                        onClick = onRetry,
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Text(strings.retry)
                    }
                }
            }
            return@Column
        }

        if (error != null) {
            // Refresh failed but the previous board is still valid — keep showing it.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = strings.arrivalsRefreshFailed,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f)
                )
                if (onRetry != null) {
                    TextButton(onClick = onRetry) {
                        Text(strings.retry)
                    }
                }
            }
        }

        if (visits.isNotEmpty()) {
            NextArrivalHero(
                visit = visits.first(),
                tick = tick,
                getDestinationName = getDestinationName
            )
            HorizontalDivider()
        }

        Text(
            text = strings.monitoredVehicles(visits.size),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )

        if (availableLines.size > 1 && onLineFilterChange != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = lineFilter.isBlank(),
                    onClick = { onLineFilterChange("") },
                    label = { Text(strings.allLinesChip) }
                )
                availableLines.forEach { line ->
                    FilterChip(
                        selected = lineFilter.equals(line, ignoreCase = true),
                        onClick = {
                            onLineFilterChange(
                                if (lineFilter.equals(line, ignoreCase = true)) "" else line
                            )
                        },
                        label = { Text(line) }
                    )
                }
            }
        }

        if (visits.isEmpty() && loading) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            }
            return@Column
        }

        if (visits.isEmpty()) {
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
            Text(strings.headerLine, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.8f))
            Text(strings.headerDest, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.5f))
            Text(strings.headerArrival, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(strings.headerDist, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.7f))
        }
        HorizontalDivider()

        Column {
            visits.forEach { visit ->
                val journey = visit.monitoredVehicleJourney ?: return@forEach
                val call = journey.monitoredCall
                val arrivalDisplay = formatArrivalTime(call?.expectedArrivalTime, tick, strings)
                val destName = getDestinationName(journey.destinationRef)
                val distance = call?.distanceFromStop?.let { "${it}m" } ?: ""
                val key = visitKey(visit)
                val isExpanded = key == expandedKey

                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                expandedKey = if (isExpanded) null else key
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.weight(0.8f)) {
                            LineBadge(journey.publishedLineName)
                        }
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
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val loc = journey.vehicleLocation
                                if (loc != null && onVehicleSelect != null) {
                                    Button(onClick = { onVehicleSelect(loc.latitude, loc.longitude) }) {
                                        Text(strings.showOnMap)
                                    }
                                    Spacer(Modifier.width(8.dp))
                                }
                                val lineName = journey.publishedLineName
                                if (lineName != null && onToggleFavoriteLine != null) {
                                    val isFav = favoriteLines.contains(lineName)
                                    IconButton(onClick = { onToggleFavoriteLine(lineName) }) {
                                        Icon(
                                            if (isFav) Icons.Default.Star else Icons.Default.StarBorder,
                                            contentDescription = null,
                                            tint = if (isFav) Color(0xFFFFD700) else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
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
        val arrival = Instant.parse(isoString)
        val now = Clock.System.now()
        val minutes = (arrival - now).inWholeMinutes
        val local = arrival.toLocalDateTime(TimeZone.currentSystemDefault())
        val timeStr = local.hour.toString().padStart(2, '0') + ":" + local.minute.toString().padStart(2, '0')

        when {
            minutes <= 0 -> ArrivalDisplay(strings.arrivalNow, timeStr)
            minutes <= 60 -> ArrivalDisplay(strings.arrivalInMin(minutes), timeStr)
            else -> ArrivalDisplay(timeStr)
        }
    } catch (_: Exception) {
        ArrivalDisplay(isoString.substringAfter("T").take(5))
    }
}
