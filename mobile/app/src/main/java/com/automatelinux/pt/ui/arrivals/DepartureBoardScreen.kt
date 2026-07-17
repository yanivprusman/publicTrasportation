package com.automatelinux.pt.ui.arrivals

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.automatelinux.pt.data.model.MonitoredStopVisit
import com.automatelinux.pt.ui.viewmodel.ArrivalsState
import com.automatelinux.pt.util.LocalAppStrings
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

// Amber-on-black palette of the LED departure boards at real Israeli stations.
private val BoardBlack = Color(0xFF060604)
private val BoardAmber = Color(0xFFFFB300)
private val BoardAmberBright = Color(0xFFFFD54F)
private val BoardAmberDim = Color(0xFF9A7B26)
private val BoardRowLine = Color(0xFF1F1A0E)

private fun arrivalEpochMs(visit: MonitoredStopVisit): Long {
    val iso = visit.monitoredVehicleJourney?.monitoredCall?.expectedArrivalTime
        ?: return Long.MAX_VALUE
    return try {
        Instant.parse(iso).toEpochMilliseconds()
    } catch (_: Exception) {
        Long.MAX_VALUE
    }
}

private fun clockText(now: Instant): String {
    val local = now.toLocalDateTime(TimeZone.currentSystemDefault()).time
    return local.hour.toString().padStart(2, '0') +
        ":" + local.minute.toString().padStart(2, '0') +
        ":" + local.second.toString().padStart(2, '0')
}

private fun hhmm(epochMs: Long): String {
    val local = Instant.fromEpochMilliseconds(epochMs)
        .toLocalDateTime(TimeZone.currentSystemDefault()).time
    return local.hour.toString().padStart(2, '0') +
        ":" + local.minute.toString().padStart(2, '0')
}

/**
 * Full-screen kiosk-style live departure board for the selected station.
 * Renders the same SIRI data the Arrivals tab polls, just big: ticking clock,
 * per-row minute countdowns, blinking LIVE badge. Screen stays on while shown.
 */
@Composable
fun DepartureBoardScreen(
    state: ArrivalsState,
    getDestinationName: (String?) -> String,
    onClose: () -> Unit
) {
    val strings = LocalAppStrings.current

    var now by remember { mutableStateOf(Clock.System.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            now = Clock.System.now()
        }
    }

    BackHandler(onBack = onClose)

    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    val visits = remember(state.siriData, state.lineFilter) {
        state.visits.sortedBy { arrivalEpochMs(it) }
    }

    val blink = rememberInfiniteTransition(label = "boardBlink")
    val blinkAlpha by blink.animateFloat(
        initialValue = 1f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(tween(650), RepeatMode.Reverse),
        label = "boardBlinkAlpha"
    )

    Surface(modifier = Modifier.fillMaxSize(), color = BoardBlack) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            BoardHeader(
                stationName = state.stationName.ifBlank { strings.boardStop(state.stationCode) },
                stationCode = state.stationCode,
                lineFilter = state.lineFilter.trim(),
                clock = clockText(now),
                blinkAlpha = blinkAlpha,
                onClose = onClose
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Text(
                    strings.headerLine,
                    color = BoardAmberDim,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(72.dp)
                )
                Text(
                    strings.headerDest,
                    color = BoardAmberDim,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    strings.headerArrival,
                    color = BoardAmberDim,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(88.dp)
                )
            }
            HorizontalDivider(color = BoardAmberDim, thickness = 2.dp)

            when {
                state.error != null -> BoardMessage(state.error!!)
                state.siriData == null -> BoardMessage(strings.boardLoading)
                visits.isEmpty() -> BoardMessage(strings.boardNone)
                else -> Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    visits.forEach { visit ->
                        BoardRow(
                            visit = visit,
                            nowMs = now.toEpochMilliseconds(),
                            blinkAlpha = blinkAlpha,
                            getDestinationName = getDestinationName
                        )
                    }
                }
            }

            BoardFooter(state.lastUpdated, now)
        }
    }
}

@Composable
private fun BoardHeader(
    stationName: String,
    stationCode: String,
    lineFilter: String,
    clock: String,
    blinkAlpha: Float,
    onClose: () -> Unit
) {
    val strings = LocalAppStrings.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stationName,
                color = BoardAmberBright,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "#$stationCode",
                    color = BoardAmberDim,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace
                )
                if (lineFilter.isNotEmpty()) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = strings.boardFilterNote(lineFilter),
                        color = BoardBlack,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(BoardAmberDim)
                            .padding(horizontal = 6.dp, vertical = 1.dp)
                    )
                }
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .alpha(blinkAlpha)
                        .clip(CircleShape)
                        .background(Color(0xFFE53935))
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    text = strings.boardLive,
                    color = Color(0xFFE57373),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = clock,
                color = BoardAmber,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
        IconButton(onClick = onClose) {
            Icon(
                Icons.Default.Close,
                contentDescription = strings.boardClose,
                tint = BoardAmber
            )
        }
    }
}

@Composable
private fun BoardRow(
    visit: MonitoredStopVisit,
    nowMs: Long,
    blinkAlpha: Float,
    getDestinationName: (String?) -> String
) {
    val strings = LocalAppStrings.current
    val journey = visit.monitoredVehicleJourney ?: return
    val call = journey.monitoredCall
    val destName = getDestinationName(journey.destinationRef)

    val arrivalMs = arrivalEpochMs(visit)
    val hasTime = arrivalMs != Long.MAX_VALUE
    val diffMin = if (hasTime) ((arrivalMs - nowMs) / 60_000L) else 0L

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.width(72.dp)) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(lineColor(journey.publishedLineName))
                    .widthIn(min = 56.dp)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = journey.publishedLineName ?: "—",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = destName.ifBlank { "—" },
                color = Color(0xFFFFECB3),
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            call?.distanceFromStop?.let { meters ->
                Text(
                    text = strings.distanceMeters(meters),
                    color = BoardAmberDim,
                    fontSize = 12.sp
                )
            }
        }
        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier.width(88.dp)
        ) {
            when {
                !hasTime -> Text(
                    text = "—",
                    color = BoardAmber,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold
                )
                diffMin < 1L -> Text(
                    text = strings.boardNow,
                    color = BoardAmberBright,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.alpha(blinkAlpha)
                )
                diffMin <= 60L -> Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = diffMin.toString(),
                        color = BoardAmber,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        text = strings.boardMinUnit,
                        color = BoardAmberDim,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                else -> Text(
                    text = hhmm(arrivalMs),
                    color = BoardAmber,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
            if (hasTime && diffMin in 1L..60L) {
                Text(
                    text = hhmm(arrivalMs),
                    color = BoardAmberDim,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
    HorizontalDivider(color = BoardRowLine)
}

@Composable
private fun BoardMessage(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = BoardAmberDim,
            fontSize = 18.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun BoardFooter(lastUpdated: Long?, now: Instant) {
    val strings = LocalAppStrings.current
    if (lastUpdated == null) {
        Spacer(Modifier.height(10.dp))
        return
    }
    val secs = (now.toEpochMilliseconds() - lastUpdated) / 1000L
    val ago = if (secs < 5) strings.justNow else strings.secondsAgo(secs)
    Text(
        text = strings.updatedAgo(ago),
        color = BoardAmberDim,
        fontSize = 12.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    )
}
