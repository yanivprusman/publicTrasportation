package com.automatelinux.pt.ui.routing

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Accessible
import androidx.compose.material.icons.filled.DirectionsBoat
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.DirectionsSubway
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Train
import androidx.compose.material.icons.filled.Tram
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.automatelinux.pt.data.model.Itinerary
import com.automatelinux.pt.data.model.Place
import com.automatelinux.pt.data.model.RouteLeg
import com.automatelinux.pt.data.model.TransitMode
import com.automatelinux.pt.data.model.WheelchairAccess
import com.automatelinux.pt.data.model.access
import com.automatelinux.pt.ui.map.getModeColorWithRoute
import com.automatelinux.pt.ui.map.onColorFor
import com.automatelinux.pt.util.toFixed
import com.automatelinux.pt.util.LocalAppStrings
import kotlinx.datetime.Instant

private val TimeGutterWidth = 46.dp
private val SpineWidth = 22.dp
private val WalkSpineColor = Color(0xFF9E9E9E)
private val DestinationDotColor = Color(0xFFE53935)
private val WaitChipColor = Color(0xFFFFB300)
private val AccessibleGreen = Color(0xFF4CAF50)

private fun legSpineColor(leg: RouteLeg): Color =
    if (leg.mode == TransitMode.WALK) WalkSpineColor
    else getModeColorWithRoute(leg.mode, leg.routeColor)

/** Seconds spent waiting at the transfer point between two legs; 0 when negligible or unparseable */
private fun waitSecondsBetween(prev: RouteLeg, next: RouteLeg): Long = try {
    val gap = (Instant.parse(next.startTime) - Instant.parse(prev.endTime)).inWholeSeconds
    if (gap >= 60) gap else 0
} catch (_: Exception) {
    0
}

private fun modeIcon(mode: TransitMode): ImageVector = when (mode) {
    TransitMode.WALK -> Icons.AutoMirrored.Filled.DirectionsWalk
    TransitMode.BUS -> Icons.Default.DirectionsBus
    TransitMode.RAIL -> Icons.Default.Train
    TransitMode.TRAM -> Icons.Default.Tram
    TransitMode.SUBWAY -> Icons.Default.DirectionsSubway
    TransitMode.FERRY -> Icons.Default.DirectionsBoat
    TransitMode.BIKE -> Icons.AutoMirrored.Filled.DirectionsBike
    TransitMode.CAR -> Icons.Default.DirectionsCar
}

@Composable
fun ItineraryDetail(
    itinerary: Itinerary,
    onLegClick: ((RouteLeg) -> Unit)? = null,
    onStopClick: ((Place) -> Unit)? = null,
    onTrackBus: ((Int, RouteLeg) -> Unit)? = null,
    trackedLegIndex: Int? = null,
    onSetReminder: ((RouteLeg) -> Unit)? = null,
    activeReminderLegIndex: Int? = null,
    onCancelReminder: (() -> Unit)? = null,
    onStartJourney: (() -> Unit)? = null,
    onShareTrip: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    val transferText = if (itinerary.transfers == 0) strings.direct else strings.transferCount(itinerary.transfers)
    val legs = itinerary.legs

    Column(modifier = modifier.padding(8.dp)) {
        Text(
            text = "${formatTime(itinerary.startTime)} - ${formatTime(itinerary.endTime)} · ${strings.formatDuration(itinerary.duration)} · $transferText",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )

        if (onStartJourney != null || onShareTrip != null) {
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                if (onStartJourney != null) {
                    Button(
                        onClick = onStartJourney,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            Icons.Default.Navigation,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(strings.startJourney, fontWeight = FontWeight.Bold)
                    }
                }
                if (onShareTrip != null) {
                    if (onStartJourney != null) Spacer(Modifier.width(8.dp))
                    FilledTonalButton(
                        onClick = onShareTrip,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            Icons.Default.Share,
                            contentDescription = strings.shareTrip,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        if (legs.isNotEmpty()) {
            TimelineNode(
                kind = NodeKind.ORIGIN,
                time = legs[0].startTime,
                name = legs[0].from.name,
                topLeg = null,
                bottomLeg = legs[0],
                onClick = onStopClick?.let { { it(legs[0].from) } }
            )
            for ((index, leg) in legs.withIndex()) {
                LegSegment(
                    leg = leg,
                    legIndex = index,
                    onClick = onLegClick?.let { { it(leg) } },
                    onStopClick = onStopClick,
                    onTrackBus = onTrackBus,
                    isTracking = trackedLegIndex == index,
                    onSetReminder = onSetReminder,
                    hasReminder = activeReminderLegIndex == index,
                    onCancelReminder = onCancelReminder
                )
                val next = legs.getOrNull(index + 1)
                if (next != null) {
                    val waitSeconds = waitSecondsBetween(leg, next)
                    TimelineNode(
                        kind = NodeKind.TRANSFER,
                        time = leg.endTime,
                        name = leg.to.name,
                        topLeg = leg,
                        bottomLeg = next,
                        waitText = if (waitSeconds > 0) strings.waitFor(strings.formatDuration(waitSeconds)) else null,
                        onClick = onStopClick?.let { { it(leg.to) } }
                    )
                } else {
                    TimelineNode(
                        kind = NodeKind.DESTINATION,
                        time = leg.endTime,
                        name = leg.to.name,
                        topLeg = leg,
                        bottomLeg = null,
                        onClick = onStopClick?.let { { it(leg.to) } }
                    )
                }
            }
        }
    }
}

private enum class NodeKind { ORIGIN, TRANSFER, DESTINATION }

private fun DrawScope.drawSpineSegment(color: Color, dashed: Boolean, startY: Float, endY: Float) {
    val x = size.width / 2f
    if (dashed) {
        drawLine(
            color = color,
            start = Offset(x, startY),
            end = Offset(x, endY),
            strokeWidth = 3.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx()))
        )
    } else {
        drawLine(
            color = color,
            start = Offset(x, startY),
            end = Offset(x, endY),
            strokeWidth = 5.dp.toPx()
        )
    }
}

@Composable
private fun TimelineNode(
    kind: NodeKind,
    time: String,
    name: String,
    topLeg: RouteLeg?,
    bottomLeg: RouteLeg?,
    waitText: String? = null,
    onClick: (() -> Unit)? = null
) {
    val surfaceColor = MaterialTheme.colorScheme.surface
    val ringColor = MaterialTheme.colorScheme.onSurface
    val dotColor = when (kind) {
        NodeKind.ORIGIN -> MaterialTheme.colorScheme.primary
        NodeKind.DESTINATION -> DestinationDotColor
        NodeKind.TRANSFER -> ringColor
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 34.dp)
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = formatTime(time),
            modifier = Modifier.width(TimeGutterWidth),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold
        )
        Box(
            modifier = Modifier
                .width(SpineWidth)
                .fillMaxHeight()
                .drawBehind {
                    val cy = size.height / 2f
                    topLeg?.let {
                        drawSpineSegment(legSpineColor(it), it.mode == TransitMode.WALK, 0f, cy)
                    }
                    bottomLeg?.let {
                        drawSpineSegment(legSpineColor(it), it.mode == TransitMode.WALK, cy, size.height)
                    }
                    when (kind) {
                        NodeKind.TRANSFER -> {
                            drawCircle(surfaceColor, radius = 5.dp.toPx(), center = Offset(size.width / 2f, cy))
                            drawCircle(
                                ringColor,
                                radius = 5.dp.toPx(),
                                center = Offset(size.width / 2f, cy),
                                style = Stroke(width = 2.5.dp.toPx())
                            )
                        }
                        else -> drawCircle(dotColor, radius = 6.5.dp.toPx(), center = Offset(size.width / 2f, cy))
                    }
                }
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = name,
            modifier = Modifier
                .weight(1f, fill = false)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (kind == NodeKind.TRANSFER) FontWeight.Medium else FontWeight.Bold
        )
        if (waitText != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = waitText,
                modifier = Modifier
                    .background(WaitChipColor.copy(alpha = 0.18f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelSmall,
                color = WaitChipColor,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun LegSegment(
    leg: RouteLeg,
    legIndex: Int,
    onClick: (() -> Unit)? = null,
    onStopClick: ((Place) -> Unit)? = null,
    onTrackBus: ((Int, RouteLeg) -> Unit)? = null,
    isTracking: Boolean = false,
    onSetReminder: ((RouteLeg) -> Unit)? = null,
    hasReminder: Boolean = false,
    onCancelReminder: (() -> Unit)? = null
) {
    val strings = LocalAppStrings.current
    var showStops by remember { mutableStateOf(false) }
    val isTransit = leg.mode != TransitMode.WALK
    val color = legSpineColor(leg)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .then(if (onClick != null) Modifier.clickable {
                if (!leg.intermediateStops.isNullOrEmpty()) showStops = true
                onClick()
            } else Modifier)
    ) {
        Spacer(Modifier.width(TimeGutterWidth))
        Box(
            modifier = Modifier
                .width(SpineWidth)
                .fillMaxHeight()
                .drawBehind {
                    drawSpineSegment(color, !isTransit, 0f, size.height)
                }
        )
        Spacer(Modifier.width(10.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 8.dp)
        ) {
            if (!isTransit) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.AutoMirrored.Filled.DirectionsWalk,
                        contentDescription = null,
                        tint = WalkSpineColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        // "Walk 6 min" does not say whether that is around the corner
                        // or across a junction; both Moovit and Maps print the metres.
                        text = leg.distanceMeters
                            ?.let { "${strings.walkMode} ${strings.walkDistance(it)} · ${strings.formatDuration(leg.duration)}" }
                            ?: "${strings.walkMode} ${strings.formatDuration(leg.duration)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        modifier = Modifier.background(color, RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            modeIcon(leg.mode),
                            contentDescription = getModeLabel(leg.mode, strings),
                            tint = onColorFor(color),
                            modifier = Modifier.size(14.dp)
                        )
                        leg.routeShortName?.let { route ->
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = route,
                                color = onColorFor(color),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = strings.formatDuration(leg.duration),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    // What this ride costs on its own, so the journey total on the
                    // card is explicable rather than asserted.
                    leg.fare?.let { fare ->
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (fare % 1.0 == 0.0) "₪${fare.toFixed(0)}" else "₪${fare.toFixed(2)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF81C784)
                        )
                    }
                    // Whether you can board at all outranks the agency's name, so it
                    // sits on the headline row. UNKNOWN says nothing: for someone who
                    // depends on this, a silent guess is worse than no answer.
                    if (leg.access != WheelchairAccess.UNKNOWN) {
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            Icons.Default.Accessible,
                            contentDescription = if (leg.access == WheelchairAccess.ACCESSIBLE) {
                                strings.accessAccessible
                            } else {
                                strings.accessNotAccessible
                            },
                            modifier = Modifier.size(15.dp),
                            tint = if (leg.access == WheelchairAccess.ACCESSIBLE) {
                                AccessibleGreen
                            } else {
                                MaterialTheme.colorScheme.error
                            }
                        )
                    }
                }

                leg.agencyName?.let { agency ->
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = agency,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (onTrackBus != null) {
                        FilledTonalButton(
                            onClick = { onTrackBus(legIndex, leg) },
                            modifier = Modifier.height(30.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            colors = if (isTracking) ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.tertiary,
                                contentColor = MaterialTheme.colorScheme.onTertiary
                            ) else ButtonDefaults.filledTonalButtonColors()
                        ) {
                            Icon(
                                Icons.Default.GpsFixed,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = if (isTracking) strings.trackingBus else strings.trackBus,
                                fontSize = 11.sp
                            )
                        }
                    }

                    if (onSetReminder != null) {
                        FilledTonalButton(
                            onClick = {
                                if (hasReminder) onCancelReminder?.invoke()
                                else onSetReminder(leg)
                            },
                            modifier = Modifier.height(30.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            colors = if (hasReminder) ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            ) else ButtonDefaults.filledTonalButtonColors()
                        ) {
                            Icon(
                                if (hasReminder) Icons.Default.NotificationsOff else Icons.Default.Notifications,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = if (hasReminder) strings.cancelReminder else strings.departureReminder,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                val stops = leg.intermediateStops
                if (!stops.isNullOrEmpty()) {
                    Text(
                        text = if (showStops) strings.hideStops(stops.size) else strings.showStops(stops.size),
                        modifier = Modifier
                            .clickable { showStops = !showStops }
                            .padding(vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )

                    AnimatedVisibility(visible = showStops) {
                        Column(modifier = Modifier.padding(top = 2.dp)) {
                            for (stop in stops) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .background(color.copy(alpha = 0.7f), RoundedCornerShape(3.dp))
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = stop.name,
                                        modifier = if (onStopClick != null) Modifier
                                            .clickable { onStopClick(stop) }
                                            .padding(vertical = 2.dp)
                                        else Modifier.padding(vertical = 2.dp),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (onStopClick != null) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant
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
