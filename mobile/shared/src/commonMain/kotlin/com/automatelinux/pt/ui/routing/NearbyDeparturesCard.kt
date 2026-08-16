package com.automatelinux.pt.ui.routing

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DepartureBoard
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.automatelinux.pt.data.model.TransitMode
import com.automatelinux.pt.data.model.lineLabel
import com.automatelinux.pt.ui.map.getModeColor
import com.automatelinux.pt.ui.map.onColorFor
import com.automatelinux.pt.ui.viewmodel.DepartureEntry
import com.automatelinux.pt.ui.viewmodel.NearbyBoard
import com.automatelinux.pt.util.formatTime
import com.automatelinux.pt.util.LocalAppStrings
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock

/**
 * What is leaving the stop you are standing at, shown before anything is asked.
 *
 * Every other app in this category answers "what's coming" without a query — Bus
 * Nearby opens onto exactly this — while ours kept the same data behind a tab. The
 * card only exists while no trip is planned; the moment there is a destination, the
 * itineraries are the better answer and this makes way for them.
 */
@Composable
fun NearbyDeparturesCard(
    board: NearbyBoard,
    onOpenBoard: (stopCode: String, stopName: String) -> Unit,
    cardOpacity: Float = 0.6f,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current

    // The countdown is relative to now, so the card re-reads the clock even between
    // refreshes — otherwise "3 min" sits there while the bus leaves.
    var now by remember { mutableStateOf(Clock.System.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(10_000)
            now = Clock.System.now()
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onOpenBoard(board.stopCode, board.stopName) },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = cardOpacity)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.DepartureBoard,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = board.stopName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (board.loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp
                    )
                }
            }

            // Stop code and distance: the number on the pole is how you know this is
            // the stop in front of you and not the one across the junction.
            Text(
                text = listOfNotNull(
                    strings.stopCodeLabel(board.stopCode).takeIf { board.stopCode.isNotBlank() },
                    board.distanceMeters.takeIf { it > 0 }?.let { strings.walkDistance(it) }
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, bottom = 6.dp)
            )

            when {
                board.error != null -> Text(
                    text = board.error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )

                board.departures.isEmpty() && !board.loading -> Text(
                    text = strings.boardNone,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                else -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (departure in board.departures.take(MAX_ROWS)) {
                        NearbyDepartureRow(departure, now, board.stopNames)
                    }
                }
            }
        }
    }
}

@Composable
private fun NearbyDepartureRow(
    departure: DepartureEntry,
    now: kotlinx.datetime.Instant,
    stopNames: Map<String, String>
) {
    val strings = LocalAppStrings.current
    val minutes = (departure.time - now).inWholeMinutes

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val badgeColor = getModeColor(TransitMode.BUS)
        Box(
            modifier = Modifier
                .widthIn(min = 34.dp)
                .background(badgeColor, RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = departure.line,
                color = onColorFor(badgeColor),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false,
                textAlign = TextAlign.Center
            )
        }

        Spacer(Modifier.width(10.dp))
        Text(
            text = departure.destination(stopNames),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        // Green with a wave for a vehicle that is reporting, plain for the timetable —
        // the convention Bus Nearby uses and the one riders here already read.
        if (departure.isLive) {
            Icon(
                Icons.Default.Sensors,
                contentDescription = strings.departureLive,
                tint = LIVE_GREEN,
                modifier = Modifier.size(13.dp)
            )
            Spacer(Modifier.width(3.dp))
        }
        Text(
            text = when {
                minutes <= 0 -> strings.arrivalNow
                minutes < 60 -> strings.arrivalInMin(minutes)
                else -> formatTime(departure.time.toString())
            },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = if (departure.isLive) LIVE_GREEN else MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * Where this departure is headed, from whichever source produced it. Live visits name
 * the destination by stop code, which only the feed's own map can resolve; the
 * timetable carries a headsign, whose underscores are a GTFS artefact.
 */
private fun DepartureEntry.destination(stopNames: Map<String, String>): String =
    visit?.monitoredVehicleJourney?.destinationRef?.let { stopNames[it] }?.takeIf { it.isNotBlank() }
        ?: stopTime?.headsign?.replace('_', ' ')?.takeIf { it.isNotBlank() }
        ?: ""

private val LIVE_GREEN = Color(0xFF66BB6A)
private const val MAX_ROWS = 6
