package com.automatelinux.pt.ui.routing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Accessible
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import com.automatelinux.pt.data.model.VehicleMarker
import com.automatelinux.pt.data.model.WheelchairAccess
import com.automatelinux.pt.ui.arrivals.LineBadge
import com.automatelinux.pt.ui.arrivals.LiveGreen
import com.automatelinux.pt.ui.viewmodel.TrackedBus
import com.automatelinux.pt.ui.viewmodel.TrackingStatus
import com.automatelinux.pt.util.AppStrings
import com.automatelinux.pt.util.LocalAppStrings
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

// A position the operator last saw a minute ago is not the same claim as one from five
// seconds ago, and a bus stuck in traffic looks exactly like a dead feed. The dot's colour
// is what separates the two at a glance.
private const val FRESH_SECONDS = 60L
private const val STALE_SECONDS = 180L
private val StaleAmber = Color(0xFFFFA726)

/**
 * The live-tracking card: which bus, how long until it reaches the boarding stop, and how
 * old that claim is. Sits over the map while [TrackedBus] is set.
 */
@Composable
fun TrackedBusCard(
    tracked: TrackedBus,
    onSelectVehicle: (Int) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    var now by remember { mutableStateOf(Clock.System.now()) }

    // The countdown and the age are both relative to now, so the card has to re-read the
    // clock even when no poll has landed — otherwise "12s ago" stays "12s ago" forever.
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            now = Clock.System.now()
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
                LineBadge(tracked.lineName)
                Spacer(Modifier.width(10.dp))

                // The line badge alone does not say which way it is going, and on a
                // two-direction line that is the difference between the right bus and
                // an hour lost.
                if (tracked.destination.isNotBlank()) {
                    // An arrow, not "to $name": stop names are Hebrew and the label is
                    // whatever language the app is in, so the wrapper renders as
                    // "<hebrew name> to" once bidi reorders it. The icon auto-mirrors
                    // and carries the same meaning without mixing directions in a string.
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = tracked.destination,
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
                        contentDescription = strings.stopTracking,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(Modifier.size(6.dp))

            val marker = tracked.marker
            if (tracked.status == TrackingStatus.LIVE && marker != null) {
                Text(
                    text = etaText(marker, now, strings),
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                Text(
                    text = statusText(tracked.status, strings),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            tracked.marker?.recordedAt?.let { recordedAt ->
                val age = ageSeconds(recordedAt, now)
                if (age != null) {
                    Spacer(Modifier.size(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "●",
                            style = MaterialTheme.typography.labelSmall,
                            color = freshnessColor(age)
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = strings.trackingPositionUpdated(agoText(age, strings)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // How far the bus still has to come. The feed reports metres to the
            // stop; it does not report stops-away, and that is not guessed here.
            tracked.marker?.takeIf { it.distanceFromStop > 0 }?.let { marker ->
                Spacer(Modifier.size(2.dp))
                Text(
                    text = strings.trackingDistanceAway(formatDistance(marker.distanceFromStop, strings)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Only stated when the timetable actually says; UNKNOWN shows nothing
            // rather than implying a service is boardable.
            if (tracked.access != WheelchairAccess.UNKNOWN) {
                Spacer(Modifier.size(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Accessible,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = if (tracked.access == WheelchairAccess.ACCESSIBLE) {
                            LiveGreen
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = if (tracked.access == WheelchairAccess.ACCESSIBLE) {
                            strings.accessAccessible
                        } else {
                            strings.accessNotAccessible
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Several buses run the same line. Picking the nearest one silently was a guess
            // the user could not see, let alone correct.
            if (tracked.candidates.size > 1) {
                Spacer(Modifier.size(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    IconButton(
                        onClick = { onSelectVehicle(tracked.selectedIndex - 1) },
                        enabled = tracked.selectedIndex > 0,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = strings.trackingOtherVehicle,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Text(
                        text = strings.trackingVehicleOf(
                            tracked.selectedIndex + 1,
                            tracked.candidates.size
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    IconButton(
                        onClick = { onSelectVehicle(tracked.selectedIndex + 1) },
                        enabled = tracked.selectedIndex < tracked.candidates.lastIndex,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = strings.trackingOtherVehicle,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun etaText(marker: VehicleMarker, now: Instant, strings: AppStrings): String {
    val arrival = try {
        Instant.parse(marker.expectedArrival)
    } catch (_: Exception) {
        return strings.trackingBus
    }
    val minutes = (arrival - now).inWholeMinutes
    return if (minutes <= 0) strings.arrivalNow else strings.arrivalInMin(minutes)
}

/** Age of the reported position in seconds, or null when the feed omitted / mangled it. */
private fun ageSeconds(recordedAt: String, now: Instant): Long? = try {
    (now - Instant.parse(recordedAt)).inWholeSeconds.coerceAtLeast(0)
} catch (_: Exception) {
    null
}

private fun agoText(seconds: Long, strings: AppStrings): String = when {
    seconds < 5 -> strings.justNow
    seconds < 60 -> strings.secondsAgo(seconds)
    else -> strings.minutesAgo(seconds / 60)
}

/** Metres below a kilometre, one decimal of a kilometre above it. */
private fun formatDistance(meters: Int, strings: AppStrings): String =
    if (meters < 1000) {
        strings.distanceM(meters)
    } else {
        val tenths = (meters + 50) / 100
        strings.distanceKm("${tenths / 10}.${tenths % 10}")
    }

private fun freshnessColor(seconds: Long): Color = when {
    seconds < FRESH_SECONDS -> LiveGreen
    seconds < STALE_SECONDS -> StaleAmber
    else -> Color(0xFFE53935)
}

private fun statusText(status: TrackingStatus, strings: AppStrings): String = when (status) {
    TrackingStatus.SEARCHING -> strings.trackingSearching
    TrackingStatus.NO_MONITORED_STOP -> strings.trackingNoMonitoredStop
    TrackingStatus.NO_VEHICLE -> strings.trackingNoVehicle
    TrackingStatus.ERROR -> strings.trackingError
    TrackingStatus.LIVE -> strings.trackingBus
}
