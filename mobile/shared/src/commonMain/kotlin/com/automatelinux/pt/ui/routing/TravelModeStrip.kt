package com.automatelinux.pt.ui.routing

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.automatelinux.pt.util.formatTime
import com.automatelinux.pt.util.toFixed
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.automatelinux.pt.data.model.DirectAlternative
import com.automatelinux.pt.data.model.TransitMode
import com.automatelinux.pt.ui.map.getModeColor
import com.automatelinux.pt.ui.viewmodel.TravelMode
import com.automatelinux.pt.util.LocalAppStrings

/**
 * Tappable transit / bike / car comparison chips shown above the route results.
 * Transit shows the fastest transit duration; bike and car show their direct
 * street route's duration. Chips only render for modes that have a route.
 */
@Composable
fun TravelModeStrip(
    transitDuration: Long?,
    alternatives: List<DirectAlternative>,
    travelMode: TravelMode,
    onSelect: (TravelMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current

    Row(
        modifier = modifier.padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (transitDuration != null) {
            ModeChip(
                selected = travelMode == TravelMode.TRANSIT,
                onClick = { onSelect(TravelMode.TRANSIT) },
                icon = Icons.Default.DirectionsBus,
                label = strings.compareTransit,
                duration = strings.formatDuration(transitDuration)
            )
        }
        for (alt in alternatives) {
            val mode = when (alt.mode) {
                TransitMode.BIKE -> TravelMode.BIKE
                TransitMode.CAR -> TravelMode.CAR
                else -> continue
            }
            ModeChip(
                selected = travelMode == mode,
                onClick = { onSelect(mode) },
                icon = if (mode == TravelMode.BIKE) Icons.AutoMirrored.Filled.DirectionsBike
                    else Icons.Default.DirectionsCar,
                label = if (mode == TravelMode.BIKE) strings.bikeMode else strings.carMode,
                duration = strings.formatDuration(alt.itinerary.duration)
            )
        }
    }
}

@Composable
private fun ModeChip(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    label: String,
    duration: String
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        leadingIcon = {
            Icon(icon, contentDescription = label, modifier = Modifier.size(16.dp))
        },
        label = {
            Column {
                Text(label, style = MaterialTheme.typography.labelSmall)
                Text(
                    duration,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary
        )
    )
}

/** Summary card for the chosen direct bike/car route: duration, distance, arrival, caveat. */
@Composable
fun DirectRouteCard(
    alternative: DirectAlternative,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    val isBike = alternative.mode == TransitMode.BIKE
    val modeColor = getModeColor(alternative.mode)
    val km = if (alternative.distance > 0) {
        val kmValue = alternative.distance / 1000.0
        if (alternative.distance >= 10_000) "${kmValue.toInt()}" else kmValue.toFixed(1)
    } else null

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(modeColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (isBike) Icons.AutoMirrored.Filled.DirectionsBike
                        else Icons.Default.DirectionsCar,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (isBike) strings.directBikeTitle else strings.directCarTitle,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = strings.formatDuration(alternative.itinerary.duration),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                if (km != null) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = strings.directKm(km),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = strings.directArrive(formatTime(alternative.itinerary.endTime)),
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (isBike) strings.directBikeNote else strings.directCarNote,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
