package com.automatelinux.pt.ui.lines

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.automatelinux.pt.ui.arrivals.LineBadge
import com.automatelinux.pt.ui.viewmodel.LineStopsUi
import com.automatelinux.pt.util.AppStrings
import com.automatelinux.pt.util.LocalAppStrings
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/** The tracked-bus marker orange; marks the countdown's stop in the list too. */
private val BusOrange = Color(0xFFFB8C00)

/**
 * The line, spelled out: its number, its destination, and every stop it makes,
 * in driving order. Opened from the tracked-bus card's header — before this
 * existed there was no path from a tracked bus to its own route's details;
 * the Lines tab draws geometry only and knows nothing of stops.
 */
@Composable
fun LineStopsSheet(
    state: LineStopsUi,
    onClose: () -> Unit,
    /** Centers the map on a tapped stop — the list answers "where is that". */
    onStopTap: (Double, Double) -> Unit,
    /** The stop nearest the user (or their chosen one), marked "your stop". */
    boardingStopCode: String? = null,
    /**
     * The SIRI-monitored stop — where the card's countdown points. Marked in
     * bus-orange with the live countdown itself, so the list answers both
     * "where am I" and "where is the bus about to be" without conflating them.
     */
    etaStopCode: String? = null,
    /** ISO ExpectedArrivalTime at [etaStopCode], from the tracked marker. */
    etaArrivalIso: String? = null,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current

    // The countdown caption re-reads the clock like the card does — a frozen
    // "in 3min" under a live list would quietly go stale.
    var now by remember { mutableStateOf(Clock.System.now()) }
    if (etaArrivalIso != null) {
        LaunchedEffect(Unit) {
            while (true) {
                delay(1000)
                now = Clock.System.now()
            }
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LineBadge(state.lineNumber.ifBlank { "…" })
                Spacer(Modifier.width(10.dp))
                if (state.headsign.isNotBlank()) {
                    // Same icon-for-preposition as the tracked card: stop names are
                    // Hebrew, labels follow the app language, and a mirrored arrow
                    // reads correctly in both without bidi reordering surprises.
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = state.headsign,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
                IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = strings.close,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            when {
                state.loading -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 12.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = strings.lineStopsLoading,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                state.error -> Text(
                    text = strings.lineStopsError,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp)
                )

                else -> {
                    Text(
                        text = strings.lineStopsCount(state.stops.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, bottom = 6.dp)
                    )
                    // Bounded, not fillMaxHeight: the sheet sits over the map and the
                    // map must stay visible — the list is the index, the map the answer.
                    LazyColumn(modifier = Modifier.heightIn(max = 340.dp)) {
                        itemsIndexed(state.stops) { index, stop ->
                            val isBoarding =
                                boardingStopCode != null && stop.stopCode == boardingStopCode
                            val isEtaStop =
                                etaStopCode != null && stop.stopCode == etaStopCode
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onStopTap(stop.lat, stop.lon) }
                                    .padding(vertical = 6.dp)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(24.dp)
                                        .background(
                                            // "Your stop" outranks the bus's when they
                                            // coincide — you already know where the bus
                                            // is from the caption.
                                            color = when {
                                                isBoarding -> MaterialTheme.colorScheme.primary
                                                isEtaStop -> BusOrange
                                                else -> MaterialTheme.colorScheme.surfaceVariant
                                            },
                                            shape = CircleShape
                                        )
                                ) {
                                    Text(
                                        text = "${index + 1}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isBoarding || isEtaStop) {
                                            Color.White
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                }
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stop.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isBoarding || isEtaStop) {
                                            FontWeight.Bold
                                        } else {
                                            FontWeight.Normal
                                        },
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (isBoarding) {
                                        Text(
                                            text = strings.lineStopsYourStop,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    if (isEtaStop) {
                                        val eta = etaCaption(etaArrivalIso, now, strings)
                                        if (eta != null) {
                                            Text(
                                                text = eta,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = BusOrange
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * The live countdown at the monitored stop, phrased exactly like the card's
 * headline so the two never disagree. Null when the arrival time is absent or
 * unparseable — an unmarked stop beats a wrong number.
 */
private fun etaCaption(iso: String?, now: Instant, strings: AppStrings): String? {
    if (iso == null) return null
    val arrival = try {
        Instant.parse(iso)
    } catch (_: Exception) {
        return null
    }
    val minutes = (arrival - now).inWholeMinutes
    return if (minutes <= 0) strings.arrivalNow else strings.arrivalInMin(minutes)
}
