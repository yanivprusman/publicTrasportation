package com.automatelinux.pt.ui.routing

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsBoat
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.DirectionsSubway
import androidx.compose.material.icons.filled.Train
import androidx.compose.material.icons.filled.Tram
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.automatelinux.pt.data.model.Itinerary
import com.automatelinux.pt.data.model.RouteLeg
import com.automatelinux.pt.data.model.TransitMode
import com.automatelinux.pt.ui.map.getModeColorWithRoute
import com.automatelinux.pt.util.AppStrings
import com.automatelinux.pt.util.LocalAppStrings
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

private fun modeIcon(mode: TransitMode): ImageVector = when (mode) {
    TransitMode.WALK -> Icons.AutoMirrored.Filled.DirectionsWalk
    TransitMode.BUS -> Icons.Default.DirectionsBus
    TransitMode.RAIL -> Icons.Default.Train
    TransitMode.TRAM -> Icons.Default.Tram
    TransitMode.SUBWAY -> Icons.Default.DirectionsSubway
    TransitMode.FERRY -> Icons.Default.DirectionsBoat
}

// mm:ss under an hour, "Hh MMm" above — the live gap to a step's start time.
private fun formatGap(ms: Long): String {
    val totalSec = maxOf(0L, ms / 1000)
    val hours = totalSec / 3600
    if (hours > 0) {
        val mins = (totalSec % 3600) / 60
        return "${hours}h ${mins.toString().padStart(2, '0')}m"
    }
    val mins = totalSec / 60
    val secs = totalSec % 60
    return "$mins:${secs.toString().padStart(2, '0')}"
}

private data class StepInstruction(
    val headline: String,
    val detail: String?,
    val getOff: String?
)

private fun instructionFor(leg: RouteLeg, isLast: Boolean, strings: AppStrings): StepInstruction {
    if (leg.mode == TransitMode.WALK) {
        return StepInstruction(
            headline = if (isLast) strings.journeyWalkToDest else strings.journeyWalkTo(leg.to.name),
            detail = strings.journeyOnFoot(strings.formatDuration(leg.duration)),
            getOff = null
        )
    }
    val ride = getModeLabel(leg.mode, strings) +
        (leg.routeShortName?.let { " $it" } ?: "")
    val stopCount = leg.intermediateStops?.size ?: 0
    val detail = if (stopCount > 0) {
        "${strings.journeyStopsCount(stopCount)} · ${strings.formatDuration(leg.duration)}"
    } else {
        strings.formatDuration(leg.duration)
    }
    return StepInstruction(
        headline = strings.journeyTake(ride),
        detail = detail,
        getOff = leg.to.name
    )
}

@Composable
fun JourneyNavigator(
    itinerary: Itinerary,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    val legs = itinerary.legs
    var stepIndex by remember { mutableIntStateOf(0) }
    // "Arrived" is a virtual final step after the last leg.
    val arrivedIndex = legs.size
    val onArrived = stepIndex >= arrivedIndex

    // Live clock so the per-step departure countdown ticks.
    var nowMs by remember { mutableLongStateOf(Clock.System.now().toEpochMilliseconds()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowMs = Clock.System.now().toEpochMilliseconds()
            delay(1000)
        }
    }

    BackHandler(onBack = onClose)

    Surface(
        modifier = modifier
            .fillMaxSize()
            // Swallow all touches so the sheet and map below get nothing.
            .pointerInput(Unit) { detectTapGestures { } },
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = strings.journeyLabel.uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                    Text(
                        text = "${formatTime(itinerary.startTime)} → ${formatTime(itinerary.endTime)} · ${strings.formatDuration(itinerary.duration)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onClose) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = strings.journeyExit,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                legs.forEachIndexed { i, leg ->
                    val pipColor = Color(getModeColorWithRoute(leg.mode, leg.routeColor))
                    val reached = i <= stepIndex
                    Box(
                        modifier = Modifier
                            .size(if (i == stepIndex) 14.dp else 10.dp)
                            .background(
                                if (reached) pipColor else MaterialTheme.colorScheme.surfaceVariant,
                                CircleShape
                            )
                    )
                }
                Icon(
                    Icons.Default.Flag,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = if (onArrived) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.Center
            ) {
                if (onArrived) {
                    ArrivedCard(itinerary = itinerary, strings = strings)
                } else {
                    StepCard(
                        leg = legs[stepIndex],
                        isLast = stepIndex == legs.lastIndex,
                        nowMs = nowMs,
                        stepNumber = stepIndex + 1,
                        totalSteps = legs.size,
                        strings = strings
                    )
                    Spacer(Modifier.height(12.dp))
                    UpNextRow(
                        nextLeg = legs.getOrNull(stepIndex + 1),
                        strings = strings
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = { stepIndex = maxOf(0, stepIndex - 1) },
                    enabled = stepIndex > 0
                ) {
                    Text(strings.journeyBack)
                }
                Text(
                    text = if (onArrived) strings.journeyArrived
                        else strings.journeyStepOf(stepIndex + 1, legs.size),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (onArrived) {
                    Button(onClick = onClose) {
                        Text(strings.journeyDone, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Button(onClick = { stepIndex = minOf(arrivedIndex, stepIndex + 1) }) {
                        Text(
                            text = if (stepIndex == legs.lastIndex) strings.journeyArrive else strings.next,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StepCard(
    leg: RouteLeg,
    isLast: Boolean,
    nowMs: Long,
    stepNumber: Int,
    totalSteps: Int,
    strings: AppStrings
) {
    val legColor = Color(getModeColorWithRoute(leg.mode, leg.routeColor))
    val instruction = instructionFor(leg, isLast, strings)
    val startMs = remember(leg.startTime) {
        try {
            Instant.parse(leg.startTime).toEpochMilliseconds()
        } catch (_: Exception) {
            null
        }
    }
    val remaining = startMs?.let { it - nowMs }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(legColor.copy(alpha = 0.18f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    modeIcon(leg.mode),
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = legColor
                )
            }

            Spacer(Modifier.height(12.dp))

            Text(
                text = instruction.headline,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            if (leg.mode != TransitMode.WALK) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${strings.journeyBoardAt} ${leg.from.name}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            instruction.detail?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            instruction.getOff?.let { getOff ->
                Spacer(Modifier.height(12.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(legColor.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = strings.journeyGetOffAt.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = getOff,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            val verb = if (leg.mode == TransitMode.WALK) strings.journeyLeave else strings.journeyDeparts
            if (remaining != null && remaining > 0) {
                Text(
                    text = formatGap(remaining),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = legColor
                )
                Text(
                    text = "$verb · ${strings.journeyScheduled(formatTime(leg.startTime))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (remaining != null) {
                Text(
                    text = strings.now,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.tertiary
                )
                Text(
                    text = strings.journeyScheduled(formatTime(leg.startTime)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = strings.journeyStepOf(stepNumber, totalSteps),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun UpNextRow(nextLeg: RouteLeg?, strings: AppStrings) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = strings.journeyUpNext,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.width(10.dp))
        Icon(
            imageVector = if (nextLeg != null) modeIcon(nextLeg.mode) else Icons.Default.Flag,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = when {
                nextLeg == null -> strings.journeyArriveDest
                nextLeg.mode == TransitMode.WALK -> strings.journeyWalkTo(nextLeg.to.name)
                else -> {
                    val ride = getModeLabel(nextLeg.mode, strings) +
                        (nextLeg.routeShortName?.let { " $it" } ?: "")
                    "$ride → ${nextLeg.to.name}"
                }
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2
        )
    }
}

@Composable
private fun ArrivedCard(itinerary: Itinerary, strings: AppStrings) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.Flag,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = strings.journeyYouArrived,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            itinerary.legs.lastOrNull()?.to?.name?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = strings.journeyArrivedSummary(
                    formatTime(itinerary.endTime),
                    strings.formatDuration(itinerary.duration)
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
