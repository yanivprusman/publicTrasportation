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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.automatelinux.pt.data.model.Itinerary
import com.automatelinux.pt.data.model.RouteLeg
import com.automatelinux.pt.data.model.TransitMode
import com.automatelinux.pt.ui.map.getModeColorWithRoute

@Composable
fun ItineraryDetail(
    itinerary: Itinerary,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(8.dp)) {
        Text(
            text = "${formatTime(itinerary.startTime)} - ${formatTime(itinerary.endTime)} · ${formatDuration(itinerary.duration)} · ${itinerary.transfers} transfer${if (itinerary.transfers != 1) "s" else ""}",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(8.dp))

        for ((index, leg) in itinerary.legs.withIndex()) {
            LegDetail(leg = leg)
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
private fun LegDetail(leg: RouteLeg) {
    var showStops by remember { mutableStateOf(false) }
    val color = Color(getModeColorWithRoute(leg.mode, leg.routeColor))

    Row(modifier = Modifier.fillMaxWidth()) {
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
                TransitMode.WALK -> "Walk ${formatDuration(leg.duration)} to ${leg.to.name}"
                else -> {
                    val route = leg.routeShortName?.let { " $it" } ?: ""
                    "${getModeLabel(leg.mode)}$route toward ${leg.to.name} — ${formatDuration(leg.duration)}"
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
                    text = if (showStops) "Hide ${stops.size} stops" else "Show ${stops.size} stops",
                    modifier = Modifier
                        .clickable { showStops = !showStops }
                        .padding(vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                AnimatedVisibility(visible = showStops) {
                    Column(modifier = Modifier.padding(start = 8.dp)) {
                        for (stop in stops) {
                            Text(
                                text = "· ${stop.name}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
