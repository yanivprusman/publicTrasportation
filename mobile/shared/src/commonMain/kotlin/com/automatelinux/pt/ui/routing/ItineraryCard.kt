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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.automatelinux.pt.data.model.Itinerary
import com.automatelinux.pt.data.model.TransitMode
import com.automatelinux.pt.ui.map.getModeColorWithRoute
import com.automatelinux.pt.util.AppStrings
import com.automatelinux.pt.util.LocalAppStrings

@Composable
fun ItineraryCard(
    itinerary: Itinerary,
    selected: Boolean,
    onClick: () -> Unit,
    cardOpacity: Float = 0.6f,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .alpha(if (selected) 1f else cardOpacity)
            .then(
                if (selected) Modifier.border(
                    2.dp,
                    Color(0xFF222222),
                    RoundedCornerShape(12.dp)
                ) else Modifier
            )
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) Color(0xFF1A1A1A)
            else MaterialTheme.colorScheme.surface
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
                val fare = itinerary.estimateFare()
                if (fare > 0) {
                    Text(
                        text = strings.fareEstimate("₪${fare.toFixed(0)}"),
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
                                text = leg.routeShortName ?: getModeLabel(leg.mode, strings),
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }
        }
    }
}

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
