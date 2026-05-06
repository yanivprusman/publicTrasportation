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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.automatelinux.pt.data.model.Itinerary
import com.automatelinux.pt.data.model.TransitMode
import com.automatelinux.pt.ui.map.getModeColor
import com.automatelinux.pt.ui.map.getModeColorWithRoute

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ItineraryCard(
    itinerary: Itinerary,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .then(
                if (selected) Modifier.border(
                    2.dp,
                    MaterialTheme.colorScheme.primary,
                    RoundedCornerShape(12.dp)
                ) else Modifier
            )
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatDuration(itinerary.duration),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (itinerary.transfers == 0) "Direct" else "${itinerary.transfers} transfer${if (itinerary.transfers > 1) "s" else ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${formatTime(itinerary.startTime)} - ${formatTime(itinerary.endTime)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
                        TransitMode.WALK -> "Walk ${formatDuration(leg.duration)}"
                        else -> "${getModeLabel(leg.mode)} ${leg.routeShortName ?: ""}"
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

fun formatDuration(seconds: Long): String {
    val mins = seconds / 60
    return when {
        mins < 60 -> "${mins} min"
        mins % 60 == 0L -> "${mins / 60}h"
        else -> "${mins / 60}h ${mins % 60}min"
    }
}

fun formatTime(isoString: String): String {
    return try {
        val zdt = java.time.ZonedDateTime.parse(isoString)
        zdt.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
    } catch (_: Exception) {
        try {
            val odt = java.time.OffsetDateTime.parse(isoString)
            odt.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
        } catch (_: Exception) {
            isoString.substringAfter("T").take(5)
        }
    }
}

fun getModeLabel(mode: TransitMode): String = when (mode) {
    TransitMode.WALK -> "Walk"
    TransitMode.BUS -> "Bus"
    TransitMode.RAIL -> "Train"
    TransitMode.TRAM -> "Tram"
    TransitMode.SUBWAY -> "Subway"
    TransitMode.FERRY -> "Ferry"
}
