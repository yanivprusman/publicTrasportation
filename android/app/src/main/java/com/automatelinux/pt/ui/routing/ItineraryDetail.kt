package com.automatelinux.pt.ui.routing

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.automatelinux.pt.data.model.Itinerary
import com.automatelinux.pt.data.model.Place
import com.automatelinux.pt.data.model.RouteLeg
import com.automatelinux.pt.data.model.TransitMode
import com.automatelinux.pt.ui.map.getModeColorWithRoute
import com.automatelinux.pt.util.LocalAppStrings

private val TimelineLineColor = Color(0xFF444444)

@Composable
fun ItineraryDetail(
    itinerary: Itinerary,
    onLegClick: ((RouteLeg) -> Unit)? = null,
    onStopClick: ((Place) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    val transferText = if (itinerary.transfers != 1) strings.transferCount(itinerary.transfers) else strings.transferCount(1)

    Column(modifier = modifier.padding(8.dp)) {
        Text(
            text = "${formatTime(itinerary.startTime)} - ${formatTime(itinerary.endTime)} · ${strings.formatDuration(itinerary.duration)} · $transferText",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(8.dp))

        for ((index, leg) in itinerary.legs.withIndex()) {
            LegDetail(leg = leg, onClick = onLegClick?.let { { it(leg) } }, onStopClick = onStopClick)
            if (index < itinerary.legs.lastIndex) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        }
    }
}

@Composable
private fun LegDetail(leg: RouteLeg, onClick: (() -> Unit)? = null, onStopClick: ((Place) -> Unit)? = null) {
    val strings = LocalAppStrings.current
    var showStops by remember { mutableStateOf(false) }
    val color = Color(getModeColorWithRoute(leg.mode, leg.routeColor))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable {
                if (!leg.intermediateStops.isNullOrEmpty()) showStops = true
                onClick()
            } else Modifier)
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(80.dp)
                .background(color, RoundedCornerShape(2.dp))
        )

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${formatTime(leg.startTime)} - ${formatTime(leg.endTime)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            val description = when (leg.mode) {
                TransitMode.WALK -> strings.walkDescription(strings.formatDuration(leg.duration), leg.to.name)
                else -> {
                    val route = leg.routeShortName?.let { " $it" } ?: ""
                    strings.transitDescription(getModeLabel(leg.mode, strings), route, leg.to.name, strings.formatDuration(leg.duration))
                }
            }
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )

            leg.agencyName?.let { agency ->
                Text(
                    text = agency,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
                    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
                    Column(
                        modifier = Modifier
                            .padding(start = 8.dp, top = 4.dp)
                            .drawBehind {
                                val x = if (isRtl) size.width else 0f
                                drawLine(
                                    color = TimelineLineColor,
                                    start = Offset(x, 0f),
                                    end = Offset(x, size.height),
                                    strokeWidth = 2.dp.toPx()
                                )
                            }
                            .padding(start = 12.dp)
                    ) {
                        for (stop in stops) {
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
