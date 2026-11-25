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
import com.automatelinux.pt.ui.components.PtSegment
import com.automatelinux.pt.ui.components.PtSegmented
import com.automatelinux.pt.ui.components.PtSwitchRow
import androidx.compose.foundation.layout.padding
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

        Spacer(Modifier.height(8.dp))

        // With a scooter the first/last mile is ridden, not walked: the cap row keeps
        // its place but asks about the ride, with choices sized for one.
        val capChoices = if (scooter) RIDE_CHOICES else WALK_CHOICES
        val cap = if (scooter) maxRideMinutes else maxWalkMinutes
        val onCapChange = if (scooter) onMaxRideChange else onMaxWalkChange
        LabelledTrack(
            icon = if (scooter) Icons.Default.ElectricScooter else Icons.AutoMirrored.Filled.DirectionsWalk,
            description = if (scooter) strings.maxRideLabel else strings.maxWalkLabel,
            segments = listOf(PtSegment(strings.noWalkLimit)) +
                capChoices.map { PtSegment(strings.walkMinutesChip(it)) },
            selectedIndex = if (cap == null) 0 else capChoices.indexOf(cap) + 1,
            onSelect = { index -> onCapChange(if (index == 0) null else capChoices[index - 1]) }
        )

        // The router prices every ride at this speed, so it decides which rides beat
        // which buses — a 40 km/h scooter priced at a cyclist's 15 loses to buses it
        // would beat. The default is the legal limit; a faster scooter says so.
        if (scooter) {
            Spacer(Modifier.height(8.dp))
            LabelledTrack(
                icon = Icons.Default.Speed,
                description = strings.scooterSpeedLabel,
                segments = SCOOTER_SPEED_CHOICES.map { PtSegment(strings.speedKmhChip(it)) },
                selectedIndex = SCOOTER_SPEED_CHOICES.indexOf(scooterSpeedKmh),
                onSelect = { onScooterSpeedChange(SCOOTER_SPEED_CHOICES[it]) }
            )
        }

        Spacer(Modifier.height(4.dp))

        // Not a mode to include or exclude but a fact about the rider — on or off —
        // so it is a switch on a line of its own, not a fourth choice in the modes.
        PtSwitchRow(
            checked = scooter,
            onCheckedChange = onScooterChange,
            label = strings.scooterOption,
            icon = Icons.Default.ElectricScooter,
            modifier = Modifier.fillMaxWidth()
        )
        if (scooter) {
            Text(
                text = strings.scooterHint,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 34.dp)
            )
        }
    }
}

/** A track with the glyph of what it sets in front of it. */
@Composable
private fun LabelledTrack(
    icon: ImageVector,
    description: String,
    segments: List<PtSegment>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            icon,
            contentDescription = description,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, end = 10.dp).size(20.dp)
        )
        PtSegmented(
            segments = segments,
            selectedIndex = selectedIndex,
            onSelect = onSelect,
            modifier = Modifier.weight(1f)
        )
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
    val filters = listOf(TransitFilter.BUS, TransitFilter.TRAIN, TransitFilter.TRAM)
    val icons = listOf(Icons.Default.DirectionsBus, Icons.Default.Train, Icons.Default.Tram)
    PtSegmented(
        segments = listOf(PtSegment(strings.allModes)) +
            filters.mapIndexed { i, filter -> PtSegment(filter.label(strings), icons[i]) },
        selectedIndex = if (all) 0 else filters.indexOf(enabledModes.singleOrNull()) + 1,
        onSelect = { index ->
            if (index == 0) {
                if (!all) onSelectMode(enabledModes.first())
            } else {
                onSelectMode(filters[index - 1])
            }
        },
        modifier = modifier.fillMaxWidth()
    )
}
