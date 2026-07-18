package com.automatelinux.pt.ui.arrivals

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.automatelinux.pt.data.model.StopTimeEntry
import com.automatelinux.pt.data.model.lineLabel
import com.automatelinux.pt.util.AppStrings
import com.automatelinux.pt.util.LocalAppStrings
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

private const val MAX_ROWS = 14

// Full scheduled departure board for the selected station, straight from the GTFS
// timetable — it keeps answering "when is the next one?" at hours when the live
// SIRI feed is empty (nights, Shabbat). Line chips filter the board per line.
@Composable
fun StationTimetable(
    stationCode: String,
    entries: List<StopTimeEntry>,
    loading: Boolean,
    error: Boolean,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    var selectedLine by remember(stationCode) { mutableStateOf<String?>(null) }
    var now by remember { mutableStateOf(Clock.System.now()) }
    LaunchedEffect(entries) {
        while (true) {
            now = Clock.System.now()
            delay(30_000)
        }
    }

    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Spacer(Modifier.height(8.dp))
        HorizontalDivider()
        Row(
            modifier = Modifier.padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Schedule,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(6.dp))
            Column {
                Text(
                    text = strings.timetable,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = strings.timetableCaption,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        when {
            loading -> Row(
                modifier = Modifier.padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            }

            error -> Text(
                text = strings.timetableUnavailable,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            else -> {
                val upcoming = remember(entries, now) {
                    entries.filter { entry ->
                        val dep = entry.departureInstant() ?: return@filter false
                        (dep - now).inWholeMinutes >= -1
                    }
                }
                if (upcoming.isEmpty()) {
                    Text(
                        text = strings.timetableNone,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    val lines = remember(upcoming) {
                        upcoming.map { it.lineLabel }.distinct()
                            .sortedWith(compareBy({ it.toIntOrNull() ?: Int.MAX_VALUE }, { it }))
                    }
                    if (lines.size > 1) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FilterChip(
                                selected = selectedLine == null,
                                onClick = { selectedLine = null },
                                label = { Text(strings.allLinesChip) }
                            )
                            lines.forEach { line ->
                                FilterChip(
                                    selected = selectedLine == line,
                                    onClick = {
                                        selectedLine = if (selectedLine == line) null else line
                                    },
                                    label = { Text(line) }
                                )
                            }
                        }
                    }
                    val shown = upcoming
                        .filter { selectedLine == null || it.lineLabel == selectedLine }
                        .take(MAX_ROWS)
                    shown.forEach { entry ->
                        TimetableRow(entry = entry, now = now, strings = strings)
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    }
                }
            }
        }
    }
}

private fun StopTimeEntry.departureInstant(): Instant? {
    val iso = place.departure ?: place.scheduledDeparture ?: return null
    return try {
        Instant.parse(iso)
    } catch (_: Exception) {
        null
    }
}

@Composable
private fun TimetableRow(
    entry: StopTimeEntry,
    now: Instant,
    strings: AppStrings
) {
    val departure = entry.departureInstant() ?: return
    val tz = TimeZone.currentSystemDefault()
    val local = departure.toLocalDateTime(tz)
    val timeText = "${local.hour.toString().padStart(2, '0')}:${
        local.minute.toString().padStart(2, '0')
    }"
    val minutes = (departure - now).inWholeMinutes
    val isTomorrow = local.date != now.toLocalDateTime(tz).date
    val countdown = when {
        isTomorrow -> strings.timetableTomorrow
        minutes <= 0 -> strings.boardNow
        minutes <= 120 -> strings.arrivalInMin(minutes)
        else -> null
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LineBadge(entry.lineLabel)
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.headsign.replace('_', ' '),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (entry.agencyName.isNotBlank()) {
                Text(
                    text = entry.agencyName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = timeText,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            if (countdown != null) {
                Text(
                    text = countdown,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isTomorrow) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                )
            }
        }
    }
}
