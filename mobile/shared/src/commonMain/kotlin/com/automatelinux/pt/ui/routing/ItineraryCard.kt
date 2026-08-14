package com.automatelinux.pt.ui.routing

import kotlinx.datetime.toLocalDateTime
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import kotlin.time.Duration.Companion.hours
import kotlinx.datetime.Instant
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.automatelinux.pt.util.toFixed
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.automatelinux.pt.data.model.Itinerary
import com.automatelinux.pt.data.model.RouteLeg
import com.automatelinux.pt.data.model.TransitMode
import com.automatelinux.pt.ui.map.getModeColorWithRoute
import com.automatelinux.pt.ui.map.onColorFor
import com.automatelinux.pt.util.AppStrings
import com.automatelinux.pt.util.LocalAppStrings

@Composable
fun ItineraryCard(
    itinerary: Itinerary,
    selected: Boolean,
    onClick: () -> Unit,
    /**
     * The wall clock, ticked by the caller. Passed in rather than read here so every
     * card on screen counts down off one clock and one coroutine — and so a card that
     * is never given a clock cannot silently show a frozen countdown.
     */
    now: Instant,
    /**
     * Departure times of this card's line from the same stop, after this one —
     * computed by the list, which is the only place that can see the siblings.
     */
    laterDepartures: List<String> = emptyList(),
    cardOpacity: Float = 0.6f,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .then(
                if (selected) Modifier.border(
                    2.dp,
                    Color(0xFF222222),
                    RoundedCornerShape(12.dp)
                ) else Modifier
            )
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            // The user's opacity setting thins the card's GROUND, not its ink:
            // a whole-card alpha() faded times and line pills into the map and
            // made every unselected route unreadable. The map still shows
            // through the surface; the content stays at full contrast.
            containerColor = if (selected) Color(0xFF1A1A1A)
            else MaterialTheme.colorScheme.surface.copy(alpha = cardOpacity)
        )
    ) {
        val textColor = if (selected) Color.White else Color.Unspecified
        val secondaryTextColor = if (selected) Color(0xFFBBBBBB)
            else MaterialTheme.colorScheme.onSurfaceVariant
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = strings.formatDuration(itinerary.duration),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
                val fare = itinerary.fareTotal
                if (fare != null && fare > 0) {
                    Text(
                        // 12.5 is a real price in this tariff, not a rounding artefact —
                        // print the agorot when they exist and not when they don't.
                        text = strings.fareEstimate(
                            if (fare % 1.0 == 0.0) "₪${fare.toFixed(0)}" else "₪${fare.toFixed(2)}"
                        ),
                        modifier = Modifier
                            .background(Color(0xFF1B5E20).copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF81C784)
                    )
                }
                Text(
                    text = if (itinerary.transfers == 0) strings.direct else strings.transferCount(itinerary.transfers),
                    modifier = Modifier
                        .background(Color(0xFF2A2A2A), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = secondaryTextColor
                )
                Text(
                    text = "${formatTime(itinerary.startTime)} - ${formatTime(itinerary.endTime)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = secondaryTextColor
                )
            }

            // Leg widths are proportional to each leg's share of travel time, so the
            // walk/ride balance of a route is readable at a glance
            val totalLegSeconds = itinerary.legs.sumOf { it.duration }
            Row(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .fillMaxWidth()
                    .height(22.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                for (leg in itinerary.legs) {
                    val isWalk = leg.mode == TransitMode.WALK
                    val color = if (isWalk) Color(0xFF616161)
                        else getModeColorWithRoute(leg.mode, leg.routeColor)
                    val share = if (totalLegSeconds > 0) leg.duration.toFloat() / totalLegSeconds
                        else 1f / itinerary.legs.size
                    Box(
                        modifier = Modifier
                            // Floor tiny legs so every segment stays visible
                            .weight(maxOf(share, 0.08f))
                            .fillMaxHeight()
                            .background(color, RoundedCornerShape(5.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isWalk) {
                            Icon(
                                Icons.AutoMirrored.Filled.DirectionsWalk,
                                contentDescription = strings.walkMode,
                                tint = Color.White.copy(alpha = 0.9f),
                                modifier = Modifier.size(14.dp)
                            )
                        } else {
                            Text(
                                text = legPillLabel(leg, strings),
                                color = onColorFor(color),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }

            // The card's time range starts when you leave the house, not when the bus
            // pulls out — and the bus is the part you can miss. Name the first ride and
            // when it goes, with a countdown that keeps ticking while the list sits open.
            BoardingLine(
                itinerary = itinerary,
                laterDepartures = laterDepartures,
                now = now,
                secondaryTextColor = secondaryTextColor,
                textColor = textColor
            )
        }
    }
}

/**
 * "64 · departs 11:09 · in 7 min" — the first ride of the itinerary.
 * Renders nothing for a walk-only itinerary or an unparsable timestamp: a boarding
 * time this app is not sure of is worse than none.
 */
@Composable
private fun BoardingLine(
    itinerary: Itinerary,
    laterDepartures: List<String>,
    now: Instant,
    secondaryTextColor: Color,
    textColor: Color
) {
    val strings = LocalAppStrings.current
    val leg = itinerary.firstRide ?: return
    val departure = try {
        Instant.parse(leg.startTime)
    } catch (_: Exception) {
        return
    }

    Column(modifier = Modifier.padding(top = 6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val badgeColor = getModeColorWithRoute(leg.mode, leg.routeColor)
            Box(
                modifier = Modifier
                    .background(badgeColor, RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 1.dp)
            ) {
                Text(
                    text = legPillLabel(leg, strings),
                    color = onColorFor(badgeColor),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false
                )
            }
            Spacer(Modifier.width(6.dp))
            // Where the number came from, as a glyph rather than a word: every
            // competitor marks this, and a word repeated on every card is noise
            // where a mark is scanned. Green wave = a vehicle is reporting;
            // clock = the timetable, which is all MOTIS has without a GTFS-RT feed.
            Icon(
                imageVector = if (leg.realTime) Icons.Default.Sensors else Icons.Default.Schedule,
                contentDescription = if (leg.realTime) strings.departureLive
                    else strings.departureTimetable,
                tint = if (leg.realTime) Color(0xFF66BB6A) else secondaryTextColor,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = strings.departsAt(formatTime(leg.startTime)),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = textColor
            )

            val remaining = (departure - now).inWholeSeconds
            val countdown = when {
                remaining < -60 -> strings.departureGone
                remaining < 60 -> strings.departsNow
                // Past a few hours the countdown says less than the clock time already
                // does — and on a search for another day it would be noise.
                remaining <= 3.hours.inWholeSeconds -> strings.departsIn(strings.formatDuration(remaining))
                else -> null
            }
            if (countdown != null) {
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "·",
                    style = MaterialTheme.typography.bodyMedium,
                    color = secondaryTextColor
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = countdown,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (remaining <= URGENT_SECONDS) FontWeight.Bold else FontWeight.Normal,
                    color = when {
                        remaining < -60 -> Color(0xFFEF9A9A)
                        remaining <= URGENT_SECONDS -> Color(0xFFFFB74D)
                        else -> secondaryTextColor
                    }
                )
            }
        }

        // Which pole it leaves from, and when the next ones go. All three
        // competitors name the boarding stop on the results row; two of them print
        // the following departures, which is what turns "can I make it" into "and
        // if I can't".
        val boarding = buildList {
            if (leg.from.name.isNotBlank()) add(strings.boardingFrom(leg.from.name))
            if (laterDepartures.isNotEmpty()) {
                add(strings.thenDepartures(laterDepartures.joinToString(" · ")))
            }
        }
        if (boarding.isNotEmpty()) {
            Text(
                text = boarding.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = secondaryTextColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

/**
 * The next departures of the SAME line from the SAME stop, taken from the other
 * itineraries already on screen.
 *
 * No extra request: a result set for one journey almost always contains the same
 * line several times, so the timetable for that stop is already in hand. Only later
 * departures count, and only from the identical boarding stop — the same line number
 * leaves two different poles in this country.
 */
fun laterDeparturesOf(target: Itinerary, all: List<Itinerary>, limit: Int = 2): List<String> {
    val ride = target.firstRide ?: return emptyList()
    val stop = ride.fromStopCode ?: ride.from.name
    return all.asSequence()
        .mapNotNull { it.firstRide }
        .filter { it.routeShortName == ride.routeShortName }
        .filter { (it.fromStopCode ?: it.from.name) == stop }
        .filter { it.startTime > ride.startTime }
        .map { it.startTime }
        .distinct()
        .sorted()
        .take(limit)
        .map { formatTime(it) }
        .toList()
}

/** Under five minutes you are running for it — the countdown says so in colour. */
private const val URGENT_SECONDS = 5 * 60L

/**
 * Rail "short names" in the Israeli GTFS are whole route strings
 * ("באר שבע<->תל אביב..."); a summary pill names the MODE and leaves the full route
 * to the detail timeline. Bus/tram numbers are short and stay.
 */
fun legPillLabel(leg: RouteLeg, strings: AppStrings): String =
    leg.routeShortName?.takeIf { it.length <= 10 } ?: getModeLabel(leg.mode, strings)

fun formatTime(isoString: String): String {
    return try {
        // Instant.parse accepts ISO-8601 with offset (both ZonedDateTime and OffsetDateTime forms).
        val local = kotlinx.datetime.Instant.parse(isoString)
            .toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault())
        val h = local.hour.toString().padStart(2, '0')
        val m = local.minute.toString().padStart(2, '0')
        "$h:$m"
    } catch (_: Exception) {
        isoString.substringAfter("T").take(5)
    }
}

fun getModeLabel(mode: TransitMode, strings: AppStrings): String = when (mode) {
    TransitMode.WALK -> strings.walkMode
    TransitMode.BUS -> strings.busMode
    TransitMode.RAIL -> strings.trainMode
    TransitMode.TRAM -> strings.tramMode
    TransitMode.SUBWAY -> strings.subwayMode
    TransitMode.FERRY -> strings.ferryMode
    TransitMode.BIKE -> strings.bikeMode
    TransitMode.CAR -> strings.carMode
}
