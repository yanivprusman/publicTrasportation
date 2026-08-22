package com.automatelinux.pt.ui.journey

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.automatelinux.pt.data.model.Itinerary
import com.automatelinux.pt.data.model.TransitMode
import com.automatelinux.pt.journey.JourneyLiveInfo
import com.automatelinux.pt.journey.JourneyPhase
import com.automatelinux.pt.journey.JourneyProgress
import com.automatelinux.pt.journey.legFraction
import com.automatelinux.pt.journey.JourneyText
import com.automatelinux.pt.ui.map.getModeColorWithRoute
import com.automatelinux.pt.util.formatTime
import com.automatelinux.pt.util.LocalAppStrings
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock

/** The colour of "get ready, this is you" — the same amber the cards use for urgency. */
private val URGENT = Color(0xFFFFB74D)

/** The colour of a live sighting — the same green the tracking chip already speaks. */
private val LIVE_GREEN = Color(0xFF66BB6A)

/** How long the end-journey button stays armed before it goes back to being an ✕. */
private const val CONFIRM_END_TIMEOUT_MS = 4_000L

/**
 * The live journey, docked at the bottom over the map.
 *
 * It deliberately does NOT take the screen: while travelling, the map — where you are,
 * where the route goes — is half the answer, and the old full-screen stepper covered
 * it. What this panel owns is the half a map cannot show: which stop is yours, and how
 * many are left before it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun JourneyPanel(
    itinerary: Itinerary,
    progress: JourneyProgress?,
    onEnd: () -> Unit,
    modifier: Modifier = Modifier,
    /** What the SIRI feed says about the bus being walked to or waited for. */
    live: JourneyLiveInfo? = null,
    /** Frame this leg on the map — every step row and the headline answer a tap. */
    onFocusLeg: ((Int) -> Unit)? = null,
    /**
     * Re-plan the trip so it begins where this leg boards / ends where it alights.
     * Offered from a long press on a step, because it replaces the whole journey —
     * a tap keeps meaning only "show me on the map".
     */
    onLegAsStart: ((Int) -> Unit)? = null,
    onLegAsEnd: ((Int) -> Unit)? = null,
    /** Fly the map to the live bus itself; wired to a tap on the live banner. */
    onFocusVehicle: ((Double, Double) -> Unit)? = null,
    /** Hand out a link that shows this journey live. Green while somebody can watch. */
    onShare: (() -> Unit)? = null,
    sharing: Boolean = false,
    /** Hands the fare off to the payment app, without leaving the live journey. */
    onPay: (() -> Unit)? = null
) {
    val strings = LocalAppStrings.current
    var expanded by remember { mutableStateOf(false) }
    // One leg means the step list can only repeat the headline word for word, so
    // there is nothing to expand into.
    val hasSteps = itinerary.legs.size > 1
    // Ending a live journey is one tap from the arrival time, and the tap that ends
    // it cannot be taken back — the trip has to be started again from the results.
    // So the first tap arms and the second ends, and it disarms itself rather than
    // sitting there armed for the rest of the ride. (journeyEndTitle/journeyEndConfirm
    // were written for a dialog that never shipped; a modal over a live journey is
    // heavier than this, so only the confirm label is used.)
    var confirmEnd by remember { mutableStateOf(false) }
    LaunchedEffect(confirmEnd) {
        if (confirmEnd) {
            delay(CONFIRM_END_TIMEOUT_MS)
            confirmEnd = false
        }
    }
    val urgent = progress?.alightImminent == true
    val accent by animateColorAsState(
        targetValue = if (urgent) URGENT else MaterialTheme.colorScheme.primary,
        label = "journeyAccent"
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        shadowElevation = 12.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // A band of colour that goes amber the moment the rider has to act. It is
            // the one part of the panel readable from arm's length on a moving bus.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(accent, RoundedCornerShape(2.dp))
            )
            Spacer(Modifier.height(10.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = strings.journeyLabel.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    color = accent
                )
                Spacer(Modifier.width(8.dp))
                TrackingChip(positionKnown = progress?.positionKnown == true)
                Spacer(Modifier.width(8.dp))
                if (confirmEnd) {
                    // Armed. The confirming tap gets the whole rest of the row at
                    // full thumb height — a label-sized target was missed three
                    // times in a row from a moving bus (#226), and a tap that falls
                    // short of it must not land on the expand chevron underneath.
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(URGENT.copy(alpha = 0.18f))
                            .clickable(onClick = onEnd),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = strings.journeyEndConfirm,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = URGENT,
                            maxLines = 1
                        )
                    }
                } else {
                    Text(
                        text = strings.journeyEtaAt(formatTime(itinerary.endTime)),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (onShare != null) {
                        IconButton(
                            onClick = onShare,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.Share,
                                contentDescription = strings.journeyShareLive,
                                modifier = Modifier.size(18.dp),
                                tint = if (sharing) LIVE_GREEN
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(
                        // A finished trip has nothing left to protect — once the
                        // rider has arrived, the ✕ just closes the panel.
                        onClick = {
                            if (progress?.phase == JourneyPhase.ARRIVED) onEnd()
                            else confirmEnd = true
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = strings.journeyEnd,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(6.dp))

            // The line every other app leads with: where the actual bus is. Shown
            // only while a boarding stop is ahead and the report is fresh — silence,
            // never a guess. Tapping it flies the map to the bus itself.
            JourneyText.liveBanner(
                live, itinerary,
                Clock.System.now().toEpochMilliseconds(), strings
            )?.let { banner ->
                val vehicle = live?.vehicle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(LIVE_GREEN.copy(alpha = 0.12f))
                        .let { m ->
                            if (vehicle != null && onFocusVehicle != null) {
                                m.clickable { onFocusVehicle(vehicle.lat, vehicle.lon) }
                            } else m
                        }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.DirectionsBus,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = LIVE_GREEN
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = banner,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = LIVE_GREEN,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = strings.journeyLiveTag,
                        style = MaterialTheme.typography.labelSmall,
                        color = LIVE_GREEN.copy(alpha = 0.8f),
                        maxLines = 1
                    )
                }
                Spacer(Modifier.height(8.dp))
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .let { m ->
                            // The headline answers a tap with the map: frame the leg
                            // it is talking about.
                            val idx = progress?.legIndex
                            if (onFocusLeg != null && idx != null && idx < itinerary.legs.size) {
                                m.clip(RoundedCornerShape(8.dp)).clickable { onFocusLeg(idx) }
                            } else m
                        }
                ) {
                    Text(
                        text = JourneyText.headline(progress, strings),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (urgent) URGENT else MaterialTheme.colorScheme.onSurface
                    )
                    JourneyText.detail(progress, strings)?.let { detail ->
                        Text(
                            text = detail,
                            modifier = Modifier.padding(top = 2.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (hasSteps) {
                    IconButton(onClick = { expanded = !expanded }) {
                        Icon(
                            imageVector = if (expanded) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                            contentDescription = strings.journeyAllSteps,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            LegPips(itinerary = itinerary, progress = progress, accent = accent)

            AnimatedVisibility(visible = expanded && hasSteps) {
                // Which step's long-press menu is open, if any. Lives with the list:
                // collapsing the steps takes the menu down with them.
                var legMenuFor by remember { mutableStateOf<Int?>(null) }
                Column(
                    modifier = Modifier
                        .heightIn(max = 260.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(top = 12.dp)
                ) {
                    itinerary.legs.forEachIndexed { index, leg ->
                        val done = (progress?.legIndex ?: 0) > index
                        val current = progress?.legIndex == index
                        val editable = onLegAsStart != null && onLegAsEnd != null
                        Box {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .let { m ->
                                        // A step answers a tap by showing itself on the
                                        // map — the row is the question "where is that?".
                                        // Holding it instead edits the trip around it.
                                        when {
                                            editable -> m.combinedClickable(
                                                onClick = { onFocusLeg?.invoke(index) },
                                                onLongClick = { legMenuFor = index }
                                            )
                                            onFocusLeg != null -> m.clickable { onFocusLeg(index) }
                                            else -> m
                                        }
                                    }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val color = if (leg.mode == TransitMode.WALK) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    getModeColorWithRoute(leg.mode, leg.routeColor)
                                }
                                Icon(
                                    imageVector = if (leg.mode == TransitMode.WALK) {
                                        Icons.AutoMirrored.Filled.DirectionsWalk
                                    } else {
                                        Icons.Default.DirectionsBus
                                    },
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = color
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    // Every row shows the next instant it is about. For a
                                    // leg not yet begun that is when it begins; for the one
                                    // underway it is when it ends, because its start time is
                                    // already in the past and reads, next to a live headline,
                                    // as if it were now.
                                    text = formatTime(if (current) leg.endTime else leg.startTime),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = legLine(leg.routeShortName, leg.mode, leg.to.name, strings),
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (current) FontWeight.Bold else FontWeight.Normal,
                                    color = when {
                                        current -> MaterialTheme.colorScheme.onSurface
                                        done -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (editable) {
                                DropdownMenu(
                                    expanded = legMenuFor == index,
                                    onDismissRequest = { legMenuFor = null }
                                ) {
                                    // Each item names the actual stop it would pin, so
                                    // the choice reads as a plan, not an abstraction.
                                    // The trip's own two ends are the rider's pins and
                                    // have no name; those fall back to "here".
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                if (leg.from.name.isBlank()) strings.journeyStartHere
                                                else strings.journeyStartFrom(leg.from.name)
                                            )
                                        },
                                        onClick = {
                                            legMenuFor = null
                                            onLegAsStart?.invoke(index)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                if (leg.to.name.isBlank()) strings.journeyEndHere
                                                else strings.journeyEndAt(leg.to.name)
                                            )
                                        },
                                        onClick = {
                                            legMenuFor = null
                                            onLegAsEnd?.invoke(index)
                                        }
                                    )
                                }
                            }
                        }
                    }
                    if (onPay != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(onClick = onPay)
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.CreditCard,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = strings.payFare,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun legLine(
    routeShortName: String?,
    mode: TransitMode,
    destination: String,
    strings: com.automatelinux.pt.util.AppStrings
): String = if (mode == TransitMode.WALK) {
    // The walk that ends the trip ends at the rider's own pin, which has no name.
    if (destination.isBlank()) strings.journeyWalkToDest else strings.journeyWalkTo(destination)
} else {
    "${JourneyText.rideName(routeShortName, mode, strings)} → $destination"
}

/** Says out loud whether the panel is following the rider or only the timetable. */
@Composable
private fun TrackingChip(positionKnown: Boolean) {
    val strings = LocalAppStrings.current
    val color = if (positionKnown) Color(0xFF66BB6A) else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (positionKnown) Icons.Default.MyLocation else Icons.Default.LocationOff,
            contentDescription = null,
            modifier = Modifier.size(12.dp),
            tint = color
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = if (positionKnown) strings.journeyFollowing else strings.journeyNoLocation,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            maxLines = 1
        )
    }
}

/**
 * One pip per leg, filled up to where the rider has got to — including *inside* the
 * leg being travelled.
 *
 * A whole-pip fill said nothing on the trip that needs it most: a walk-only journey
 * has exactly one pip, painted the dim grey every walk leg gets, so the single
 * affordance for "how far along am I" looked identical at the first step and the
 * last. The leg underway now fills by measured progress and wears the accent, so the
 * bar moves while the rider walks.
 */
@Composable
private fun LegPips(itinerary: Itinerary, progress: JourneyProgress?, accent: Color) {
    val reachedIndex = progress?.legIndex ?: 0
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        itinerary.legs.forEachIndexed { index, leg ->
            val current = index == reachedIndex
            val base = if (leg.mode == TransitMode.WALK) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                getModeColorWithRoute(leg.mode, leg.routeColor)
            }
            val fill = when {
                index < reachedIndex -> 1f
                current -> progress?.legFraction() ?: 0f
                else -> 0f
            }
            Box(
                modifier = Modifier
                    .weight(leg.duration.coerceAtLeast(1).toFloat())
                    .height(6.dp)
                    .background(base.copy(alpha = 0.25f), RoundedCornerShape(3.dp))
            ) {
                if (fill > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(fill)
                            .background(
                                if (current) accent else base,
                                RoundedCornerShape(3.dp)
                            )
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(
                    if (progress?.phase == JourneyPhase.ARRIVED) accent
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    RoundedCornerShape(3.dp)
                )
        )
    }
}
