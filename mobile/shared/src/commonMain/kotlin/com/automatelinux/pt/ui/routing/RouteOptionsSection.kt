package com.automatelinux.pt.ui.routing

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.ElectricScooter
import androidx.compose.material.icons.filled.Speed
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
import com.automatelinux.pt.ui.viewmodel.SCOOTER_SPEED_CHOICES
import com.automatelinux.pt.ui.viewmodel.TransitFilter
import com.automatelinux.pt.util.AppStrings
import com.automatelinux.pt.util.LocalAppStrings

private val WALK_CHOICES = listOf(5, 10, 15, 20)

// A scooter covers ground a walk never could — 30 minutes is about 7 km at the
// router's bike speed — and that reach is the whole point of the option, so the
// ride choices start where the walk ones end. "No limit" is the server's ceiling.
private val RIDE_CHOICES = listOf(10, 20, 30, 45)

/** A mode group's name, as its chip shows it and as an empty result names the filter. */
fun TransitFilter.label(strings: AppStrings): String = when (this) {
    TransitFilter.BUS -> strings.busMode
    TransitFilter.TRAIN -> strings.trainMode
    TransitFilter.TRAM -> strings.tramMode
}

/**
 * Route options: which transit mode to route with (all, or one), whether the rider has a scooter
 * that travels with them, and how far they will walk — or ride — to a stop. Changing
 * anything re-runs the active search.
 */
@Composable
fun RouteOptionsSection(
    enabledModes: Set<TransitFilter>,
    maxWalkMinutes: Int?,
    scooter: Boolean,
    maxRideMinutes: Int?,
    scooterSpeedKmh: Int,
    onSelectMode: (TransitFilter) -> Unit,
    onMaxWalkChange: (Int?) -> Unit,
    onScooterChange: (Boolean) -> Unit,
    onMaxRideChange: (Int?) -> Unit,
    onScooterSpeedChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current

    Column(modifier = modifier.fillMaxWidth()) {
        TransitModeRow(enabledModes = enabledModes, onSelectMode = onSelectMode)

        Spacer(Modifier.height(4.dp))

        // Not a mode to include or exclude but a fact about the rider, so it has a
        // row of its own rather than a fourth chip in the modes row.
        FilterChip(
            selected = scooter,
            onClick = { onScooterChange(!scooter) },
            label = { Text(strings.scooterOption, maxLines = 1) },
            leadingIcon = {
                Icon(
                    Icons.Default.ElectricScooter,
                    contentDescription = null,
                    modifier = Modifier.size(FilterChipDefaults.IconSize)
                )
            }
        )
        if (scooter) {
            Text(
                text = strings.scooterHint,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(4.dp))

        // With a scooter the first/last mile is ridden, not walked: the cap row keeps
        // its place but asks about the ride, with choices sized for one.
        val capChoices = if (scooter) RIDE_CHOICES else WALK_CHOICES
        val cap = if (scooter) maxRideMinutes else maxWalkMinutes
        val onCapChange = if (scooter) onMaxRideChange else onMaxWalkChange
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                if (scooter) Icons.Default.ElectricScooter else Icons.AutoMirrored.Filled.DirectionsWalk,
                contentDescription = if (scooter) strings.maxRideLabel else strings.maxWalkLabel,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            WalkChip(
                selected = cap == null,
                label = strings.noWalkLimit,
                onClick = { onCapChange(null) }
            )
            capChoices.forEach { minutes ->
                WalkChip(
                    selected = cap == minutes,
                    label = strings.walkMinutesChip(minutes),
                    onClick = { onCapChange(minutes) }
                )
            }
        }

        // The router prices every ride at this speed, so it decides which rides beat
        // which buses — a 40 km/h scooter priced at a cyclist's 15 loses to buses it
        // would beat. The default is the legal limit; a faster scooter says so.
        if (scooter) {
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    Icons.Default.Speed,
                    contentDescription = strings.scooterSpeedLabel,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                SCOOTER_SPEED_CHOICES.forEach { kmh ->
                    WalkChip(
                        selected = scooterSpeedKmh == kmh,
                        label = strings.speedKmhChip(kmh),
                        onClick = { onScooterSpeedChange(kmh) }
                    )
                }
            }
        }
    }
}

/**
 * The mode row: [all] [bus] [train] [light rail], one selected at a time. "All" is
 * the default and visibly the selected chip, so the first tap on Bus reads as "bus
 * only", never as "hide buses". Shown in the search form, and again above the
 * results while anything but "all" is selected, so the filter can be cleared
 * without unfolding the form.
 */
@Composable
fun TransitModeRow(
    enabledModes: Set<TransitFilter>,
    onSelectMode: (TransitFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    val all = enabledModes.size == TransitFilter.entries.size
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        FilterChip(
            selected = all,
            onClick = { if (!all) onSelectMode(enabledModes.first()) },
            label = { Text(strings.allModes, maxLines = 1) }
        )
        ModeChip(TransitFilter.BUS, Icons.Default.DirectionsBus, enabledModes, onSelectMode)
        ModeChip(TransitFilter.TRAIN, Icons.Default.Train, enabledModes, onSelectMode)
        ModeChip(TransitFilter.TRAM, Icons.Default.Tram, enabledModes, onSelectMode)
    }
}

@Composable
private fun ModeChip(
    filter: TransitFilter,
    icon: ImageVector,
    enabledModes: Set<TransitFilter>,
    onSelectMode: (TransitFilter) -> Unit
) {
    // Lit only when this mode is the one selected — with "all" on, no mode chip is.
    val selected = enabledModes == setOf(filter)
    FilterChip(
        selected = selected,
        onClick = { onSelectMode(filter) },
        label = { Text(filter.label(LocalAppStrings.current), maxLines = 1) },
        leadingIcon = {
            Icon(icon, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize))
        }
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
