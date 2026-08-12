package com.automatelinux.pt.ui.arrivals

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GpsFixed
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.automatelinux.pt.data.model.MonitoredStopVisit
import com.automatelinux.pt.data.model.StopTimeEntry
import com.automatelinux.pt.ui.viewmodel.DepartureEntry
import com.automatelinux.pt.ui.viewmodel.buildDepartures
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

internal val LiveGreen = Color(0xFF4CAF50)

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

// Stable identity for a visit across refreshes — the list reorders every poll,
// so expansion must not be keyed by index.
private fun visitKey(visit: MonitoredStopVisit): String {
    val journey = visit.monitoredVehicleJourney
    return journey?.vehicleRef
        ?: "${journey?.publishedLineName}|${journey?.destinationRef}|${journey?.monitoredCall?.expectedArrivalTime}"
}

// Presentation of one board entry. Live rows lead with the countdown (that is
// the realtime information); scheduled rows lead with the timetable clock time.
private data class EntryDisplay(
    val countdown: String?,
    val clockTime: String,
    val destination: String,
    val isTomorrow: Boolean
)

private fun formatEntry(
    entry: DepartureEntry,
    now: Instant,
    strings: AppStrings,
    getDestinationName: (String?) -> String
): EntryDisplay {
    val tz = TimeZone.currentSystemDefault()
    val local = entry.time.toLocalDateTime(tz)
    val clockTime = local.hour.toString().padStart(2, '0') + ":" +
        local.minute.toString().padStart(2, '0')
    val minutes = (entry.time - now).inWholeMinutes
    val isTomorrow = local.date != now.toLocalDateTime(tz).date
    val countdown = when {
        isTomorrow -> strings.timetableTomorrow
        minutes <= 0 -> strings.arrivalNow
        minutes <= 120 -> strings.arrivalInMin(minutes)
        else -> null
    }
    val destination = entry.visit?.monitoredVehicleJourney
        ?.let { getDestinationName(it.destinationRef) }
        ?: entry.stopTime?.headsign?.replace('_', ' ')
        ?: ""
    return EntryDisplay(countdown, clockTime, destination, isTomorrow)
}

@Composable
private fun LiveScheduledTag(isLive: Boolean, agency: String?) {
    val strings = LocalAppStrings.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (isLive) {
            Text("●", style = MaterialTheme.typography.labelSmall, color = LiveGreen)
            Spacer(Modifier.width(3.dp))
            Text(
                strings.liveTag,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Text(
                strings.scheduledTag,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!agency.isNullOrBlank()) {
                Text(
                    " · $agency",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun NextArrivalHero(
    entry: DepartureEntry,
    now: Instant,
    getDestinationName: (String?) -> String,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    val display = formatEntry(entry, now, strings, getDestinationName)

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
            LineBadge(entry.line)
            Spacer(Modifier.width(12.dp))
            Text(
                text = display.countdown ?: display.clockTime,
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(Modifier.height(2.dp))
        if (display.destination.isNotBlank()) {
            Text(
                text = strings.toDestination(display.destination),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            LiveScheduledTag(isLive = entry.isLive, agency = null)
            if (display.countdown != null) {
                Text(
                    " · ${display.clockTime}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// The unified departure board: live SIRI arrivals and GTFS scheduled departures
// merged into one chronological list. Live rows expand to vehicle details.
@Composable
fun StationArrivals(
    allVisits: List<MonitoredStopVisit>,
    timetable: List<StopTimeEntry>,
    timetableLoading: Boolean,
    timetableError: Boolean,
    error: String?,
    loading: Boolean,
    getDestinationName: (String?) -> String,
    onVehicleSelect: ((Double, Double) -> Unit)? = null,
    /** (line, destination, routeId, vehicleRef) -> open live tracking for this arrival. */
    onTrackVehicle: ((String, String, String?, String?) -> Unit)? = null,
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

    val now = remember(tick, allVisits, timetable) { Clock.System.now() }
    val departures = remember(allVisits, timetable, lineFilter, now) {
        buildDepartures(allVisits, timetable, lineFilter, now)
    }

    Column(modifier = modifier) {
        if (error != null && allVisits.isEmpty() && timetable.isEmpty()) {
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

        departures.firstOrNull()?.let { first ->
            NextArrivalHero(
                entry = first,
                now = now,
                getDestinationName = getDestinationName
            )
            HorizontalDivider()
        }

        Text(
            text = strings.departuresTitle,
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

        if (departures.isEmpty() && (loading || timetableLoading)) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            }
            return@Column
        }

        if (departures.isEmpty()) {
            Text(
                text = strings.boardNone,
                modifier = Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (timetableError) {
                TimetableUnavailableNote()
            }
            return@Column
        }

        Column {
            departures.forEach { entry ->
                val key = entry.visit?.let { visitKey(it) }
                DepartureRow(
                    entry = entry,
                    now = now,
                    isExpanded = key != null && key == expandedKey,
                    onToggleExpand = {
                        if (key != null) {
                            expandedKey = if (expandedKey == key) null else key
                        }
                    },
                    getDestinationName = getDestinationName,
                    onVehicleSelect = onVehicleSelect,
                    onTrackVehicle = onTrackVehicle,
                    favoriteLines = favoriteLines,
                    onToggleFavoriteLine = onToggleFavoriteLine
                )
            }
        }

        if (timetableError) {
            TimetableUnavailableNote()
        }
    }
}

@Composable
private fun TimetableUnavailableNote() {
    val strings = LocalAppStrings.current
    Text(
        text = strings.timetableUnavailable,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
    )
}

@Composable
private fun DepartureRow(
    entry: DepartureEntry,
    now: Instant,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    getDestinationName: (String?) -> String,
    onVehicleSelect: ((Double, Double) -> Unit)?,
    onTrackVehicle: ((String, String, String?, String?) -> Unit)?,
    favoriteLines: Set<String>,
    onToggleFavoriteLine: ((String) -> Unit)?
) {
    val strings = LocalAppStrings.current
    val display = formatEntry(entry, now, strings, getDestinationName)
    val journey = entry.visit?.monitoredVehicleJourney

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = entry.isLive) { onToggleExpand() }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LineBadge(entry.line)
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = display.destination,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                LiveScheduledTag(
                    isLive = entry.isLive,
                    agency = entry.stopTime?.agencyName
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                if (entry.isLive) {
                    Text(
                        text = display.countdown ?: display.clockTime,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (display.countdown != null) {
                        Text(
                            text = display.clockTime,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Text(
                        text = display.clockTime,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    if (display.countdown != null) {
                        Text(
                            text = display.countdown,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (display.isTomorrow) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.primary
                            }
                        )
                    }
                }
            }
        }

        if (journey != null) {
            AnimatedVisibility(visible = isExpanded) {
                val call = journey.monitoredCall
                Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                    Text(
                        strings.vehicleRef(journey.vehicleRef ?: strings.notAvailable),
                        style = MaterialTheme.typography.bodySmall
                    )
                    call?.distanceFromStop?.let {
                        Text(strings.distanceMeters(it), style = MaterialTheme.typography.bodySmall)
                    }
                    call?.expectedArrivalTime?.let {
                        Text(strings.fullArrival(it), style = MaterialTheme.typography.bodySmall)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // The live position is what people open a transit app for, so
                        // it leads: full tracking, not a pan to a dot. "Show on Map"
                        // stays for a quick look without leaving the arrivals list.
                        val trackLine = entry.line
                        if (onTrackVehicle != null && trackLine != null) {
                            Button(onClick = {
                                onTrackVehicle(
                                    trackLine,
                                    display.destination,
                                    journey.lineRef,
                                    journey.vehicleRef
                                )
                            }) {
                                Icon(
                                    Icons.Default.GpsFixed,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(strings.trackBus)
                            }
                            Spacer(Modifier.width(8.dp))
                        }
                        val loc = journey.vehicleLocation
                        if (loc != null && onVehicleSelect != null) {
                            TextButton(onClick = { onVehicleSelect(loc.latitude, loc.longitude) }) {
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
                                    contentDescription = if (isFav) {
                                        strings.removeFavorite
                                    } else {
                                        strings.addFavorite
                                    },
                                    tint = if (isFav) {
                                        Color(0xFFFFD700)
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
    }
}

data class ArrivalDisplay(val primary: String, val secondary: String? = null)

// Kept for the home-screen departures widget, which formats SIRI times directly.
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
