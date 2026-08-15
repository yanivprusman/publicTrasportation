package com.automatelinux.pt.ui.journey

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.automatelinux.pt.data.model.Itinerary
import com.automatelinux.pt.data.model.TransitMode
import com.automatelinux.pt.journey.JourneyPhase
import com.automatelinux.pt.journey.JourneyProgress
import com.automatelinux.pt.journey.JourneyText
import com.automatelinux.pt.ui.map.getModeColorWithRoute
import com.automatelinux.pt.ui.routing.formatTime
import com.automatelinux.pt.util.LocalAppStrings

/** The colour of "get ready, this is you" — the same amber the cards use for urgency. */
private val URGENT = Color(0xFFFFB74D)

/**
 * The live journey, docked at the bottom over the map.
 *
 * It deliberately does NOT take the screen: while travelling, the map — where you are,
 * where the route goes — is half the answer, and the old full-screen stepper covered
 * it. What this panel owns is the half a map cannot show: which stop is yours, and how
 * many are left before it.
 */
@Composable
fun JourneyPanel(
    itinerary: Itinerary,
    progress: JourneyProgress?,
    onEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    var expanded by remember { mutableStateOf(false) }
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
                Text(
                    text = strings.journeyEtaAt(formatTime(itinerary.endTime)),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                IconButton(onClick = onEnd, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = strings.journeyEnd,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
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
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                        contentDescription = strings.journeyAllSteps,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            LegPips(itinerary = itinerary, progress = progress, accent = accent)

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .heightIn(max = 260.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(top = 12.dp)
                ) {
                    itinerary.legs.forEachIndexed { index, leg ->
                        val done = (progress?.legIndex ?: 0) > index
                        val current = progress?.legIndex == index
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
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
                                text = formatTime(leg.startTime),
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

/** One pip per leg, filled up to where the rider has got to. */
@Composable
private fun LegPips(itinerary: Itinerary, progress: JourneyProgress?, accent: Color) {
    val reachedIndex = progress?.legIndex ?: 0
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        itinerary.legs.forEachIndexed { index, leg ->
            val reached = index <= reachedIndex
            val base = if (leg.mode == TransitMode.WALK) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                getModeColorWithRoute(leg.mode, leg.routeColor)
            }
            Box(
                modifier = Modifier
                    .weight(leg.duration.coerceAtLeast(1).toFloat())
                    .height(6.dp)
                    .background(
                        if (reached) base else base.copy(alpha = 0.25f),
                        RoundedCornerShape(3.dp)
                    )
            )
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
