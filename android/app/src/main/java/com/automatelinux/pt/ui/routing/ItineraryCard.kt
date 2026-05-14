package com.automatelinux.pt.ui.routing

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.automatelinux.pt.data.model.Itinerary
import com.automatelinux.pt.data.model.TransitMode
import com.automatelinux.pt.ui.map.getModeColor
import com.automatelinux.pt.ui.map.getModeColorWithRoute
import com.automatelinux.pt.util.AppStrings
import com.automatelinux.pt.util.LocalAppStrings

@OptIn(ExperimentalLayoutApi::class)
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
                Text(
                    text = if (itinerary.transfers == 0) strings.direct else strings.transferCount(itinerary.transfers),
                    style = MaterialTheme.typography.bodySmall,
                    color = secondaryTextColor
                )
                Text(
                    text = "${formatTime(itinerary.startTime)} - ${formatTime(itinerary.endTime)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = secondaryTextColor
                )
            }

            FlowRow(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                for (leg in itinerary.legs) {
                    val color = Color(getModeColorWithRoute(leg.mode, leg.routeColor))
                    val label = when (leg.mode) {
                        TransitMode.WALK -> "${strings.walkMode} ${strings.formatDuration(leg.duration)}"
                        else -> "${getModeLabel(leg.mode, strings)} ${leg.routeShortName ?: ""}"
                    }
                    Text(
                        text = label,
                        modifier = Modifier
                            .background(color.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = color,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

fun formatTime(isoString: String): String {
    val localZone = java.time.ZoneId.systemDefault()
    val fmt = java.time.format.DateTimeFormatter.ofPattern("HH:mm")
    return try {
        val zdt = java.time.ZonedDateTime.parse(isoString)
        zdt.withZoneSameInstant(localZone).format(fmt)
    } catch (_: Exception) {
        try {
            val odt = java.time.OffsetDateTime.parse(isoString)
            odt.atZoneSameInstant(localZone).format(fmt)
        } catch (_: Exception) {
            isoString.substringAfter("T").take(5)
        }
    }
}

fun getModeLabel(mode: TransitMode, strings: AppStrings): String = when (mode) {
    TransitMode.WALK -> strings.walkMode
    TransitMode.BUS -> strings.busMode
    TransitMode.RAIL -> strings.trainMode
    TransitMode.TRAM -> strings.tramMode
    TransitMode.SUBWAY -> strings.subwayMode
    TransitMode.FERRY -> strings.ferryMode
}
