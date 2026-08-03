package com.automatelinux.pt.ui.routing

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Train
import androidx.compose.material.icons.filled.Tram
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.automatelinux.pt.ui.viewmodel.TransitFilter
import com.automatelinux.pt.util.LocalAppStrings

private val WALK_CHOICES = listOf(5, 10, 15, 20)

/**
 * Route options: which transit modes to route with and how far the passenger
 * is willing to walk. Changing anything re-runs the active search.
 */
@Composable
fun RouteOptionsSection(
    enabledModes: Set<TransitFilter>,
    maxWalkMinutes: Int?,
    onToggleMode: (TransitFilter) -> Unit,
    onMaxWalkChange: (Int?) -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ModeChip(
                filter = TransitFilter.BUS,
                icon = Icons.Default.DirectionsBus,
                label = strings.busMode,
                enabledModes = enabledModes,
                onToggleMode = onToggleMode
            )
            ModeChip(
                filter = TransitFilter.TRAIN,
                icon = Icons.Default.Train,
                label = strings.trainMode,
                enabledModes = enabledModes,
                onToggleMode = onToggleMode
            )
            ModeChip(
                filter = TransitFilter.TRAM,
                icon = Icons.Default.Tram,
                label = strings.tramMode,
                enabledModes = enabledModes,
                onToggleMode = onToggleMode
            )
        }

        Spacer(Modifier.height(4.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                Icons.AutoMirrored.Filled.DirectionsWalk,
                contentDescription = strings.maxWalkLabel,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            WalkChip(
                selected = maxWalkMinutes == null,
                label = strings.noWalkLimit,
                onClick = { onMaxWalkChange(null) }
            )
            WALK_CHOICES.forEach { minutes ->
                WalkChip(
                    selected = maxWalkMinutes == minutes,
                    label = strings.walkMinutesChip(minutes),
                    onClick = { onMaxWalkChange(minutes) }
                )
            }
        }

        if (enabledModes.size < TransitFilter.entries.size) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = strings.filteredModesHint,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.tertiary
            )
        }
    }
}

@Composable
private fun RowScope.ModeChip(
    filter: TransitFilter,
    icon: ImageVector,
    label: String,
    enabledModes: Set<TransitFilter>,
    onToggleMode: (TransitFilter) -> Unit
) {
    val selected = filter in enabledModes
    FilterChip(
        selected = selected,
        onClick = { onToggleMode(filter) },
        label = { Text(label, maxLines = 1) },
        leadingIcon = {
            Icon(icon, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize))
        },
        modifier = Modifier.weight(1f)
    )
}

@Composable
private fun WalkChip(
    selected: Boolean,
    label: String,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, maxLines = 1) }
    )
}
