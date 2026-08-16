package com.automatelinux.pt.ui.routing

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.automatelinux.pt.data.model.DayDeparture
import com.automatelinux.pt.data.model.DayOverviewResult
import com.automatelinux.pt.ui.map.getModeColor
import com.automatelinux.pt.util.formatTime
import com.automatelinux.pt.util.LocalAppStrings
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

private data class DayChartData(
    val departures: List<DayDeparture>,
    // Minutes since the chart day's local midnight, so departures after
    // midnight (25:30) continue the axis instead of wrapping to 01:30.
    val xsMinutes: List<Double>,
    val minX: Double,
    val maxX: Double,
    val maxY: Double,
    val hourTicks: List<Double>,
    val yTicks: List<Double>,
    val fastestDuration: Long
)

private fun buildChartData(departures: List<DayDeparture>): DayChartData? {
    val tz = TimeZone.currentSystemDefault()
    val parsed = departures.mapNotNull { dep ->
        try { dep to Instant.parse(dep.startTime) } catch (_: Exception) { null }
    }
    if (parsed.isEmpty()) return null
    val dayStartMs = parsed.first().second.toLocalDateTime(tz).date
        .atTime(0, 0).toInstant(tz).toEpochMilliseconds()
    val deps = parsed.map { it.first }
    val xs = parsed.map { (it.second.toEpochMilliseconds() - dayStartMs) / 60000.0 }
    val minX = floor(xs.min() / 60.0) * 60.0
    val maxX = maxOf(ceil(xs.max() / 60.0) * 60.0, minX + 120.0)
    val maxDurationMin = deps.maxOf { it.duration } / 60.0
    val maxY = maxOf(ceil(maxDurationMin / 10.0) * 10.0, 10.0)
    val spanHours = (maxX - minX) / 60.0
    val hourStep = if (spanHours <= 8) 1 else if (spanHours <= 16) 2 else 3
    val hourTicks = generateSequence(minX) { it + hourStep * 60.0 }.takeWhile { it <= maxX }.toList()
    val yTickStep = if (maxY <= 30) 10.0 else if (maxY <= 60) 20.0 else 30.0
    val yTicks = generateSequence(yTickStep) { it + yTickStep }.takeWhile { it <= maxY }.toList()
    return DayChartData(
        departures = deps,
        xsMinutes = xs,
        minX = minX,
        maxX = maxX,
        maxY = maxY,
        hourTicks = hourTicks,
        yTicks = yTicks,
        fastestDuration = deps.minOf { it.duration }
    )
}

@Composable
fun DayOverviewSection(
    data: DayOverviewResult?,
    loading: Boolean,
    error: String?,
    selectedIndex: Int?,
    onSelect: (Int?) -> Unit,
    onShowTrip: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            when {
                loading -> Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(strings.dayLoading, style = MaterialTheme.typography.bodyMedium)
                }

                error != null -> Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "${strings.dayFailed}: $error",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    TextButton(onClick = onRetry) { Text(strings.retry) }
                }

                else -> {
                    val chart = remember(data) { data?.let { buildChartData(it.departures) } }
                    if (chart == null) {
                        Text(
                            text = strings.dayNone,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    } else {
                        DayChart(
                            chart = chart,
                            truncated = data?.truncated == true,
                            selectedIndex = selectedIndex,
                            onSelect = onSelect,
                            onShowTrip = onShowTrip
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DayChart(
    chart: DayChartData,
    truncated: Boolean,
    selectedIndex: Int?,
    onSelect: (Int?) -> Unit,
    onShowTrip: (String) -> Unit
) {
    val strings = LocalAppStrings.current
    val textMeasurer = rememberTextMeasurer()

    val first = chart.departures.first()
    val last = chart.departures.last()

    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        DayStat(strings.dayFirst, formatTime(first.startTime))
        DayStat(strings.dayLast, formatTime(last.startTime))
        DayStat(strings.dayFastest, strings.formatDuration(chart.fastestDuration), highlight = true)
        DayStat(strings.dayDepartures, chart.departures.size.toString())
    }

    val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f)
    val axisTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    val barColor = MaterialTheme.colorScheme.primary
    val fastestColor = Color(0xFF4CAF50)
    val selectedColor = MaterialTheme.colorScheme.onSurface
    val axisStyle = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, color = axisTextColor)

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .pointerInput(chart) {
                detectTapGestures { offset ->
                    val mLeft = 30.dp.toPx()
                    val mRight = 8.dp.toPx()
                    val plotW = size.width - mLeft - mRight
                    var best = -1
                    var bestDist = Float.MAX_VALUE
                    chart.xsMinutes.forEachIndexed { i, m ->
                        val x = mLeft + ((m - chart.minX) / (chart.maxX - chart.minX) * plotW).toFloat()
                        val d = abs(offset.x - x)
                        if (d < bestDist) { bestDist = d; best = i }
                    }
                    if (best >= 0 && bestDist <= 16.dp.toPx()) {
                        onSelect(if (best == selectedIndex) null else best)
                    } else {
                        onSelect(null)
                    }
                }
            }
    ) {
        val mLeft = 30.dp.toPx()
        val mRight = 8.dp.toPx()
        val mTop = 6.dp.toPx()
        val mBottom = 18.dp.toPx()
        val plotW = size.width - mLeft - mRight
        val plotH = size.height - mTop - mBottom

        fun xFor(minutes: Double): Float =
            mLeft + ((minutes - chart.minX) / (chart.maxX - chart.minX) * plotW).toFloat()

        chart.yTicks.forEach { v ->
            val y = mTop + plotH - (v / chart.maxY * plotH).toFloat()
            drawLine(gridColor, Offset(mLeft, y), Offset(size.width - mRight, y), 1.dp.toPx())
            val label = textMeasurer.measure(v.toInt().toString(), axisStyle)
            drawText(
                label,
                topLeft = Offset(mLeft - label.size.width - 4.dp.toPx(), y - label.size.height / 2f)
            )
        }

        chart.hourTicks.forEach { m ->
            val x = xFor(m)
            drawLine(gridColor, Offset(x, mTop), Offset(x, mTop + plotH), 1.dp.toPx())
            val hour = (m.toInt() / 60) % 24
            val label = textMeasurer.measure(hour.toString().padStart(2, '0'), axisStyle)
            drawText(
                label,
                topLeft = Offset(x - label.size.width / 2f, mTop + plotH + 3.dp.toPx())
            )
        }

        drawLine(
            axisTextColor.copy(alpha = 0.5f),
            Offset(mLeft, mTop + plotH),
            Offset(size.width - mRight, mTop + plotH),
            1.5.dp.toPx()
        )

        val barW = (plotW / chart.departures.size * 0.6f)
            .coerceIn(3.dp.toPx(), 12.dp.toPx())
        chart.departures.forEachIndexed { i, dep ->
            val h = ((dep.duration / 60.0) / chart.maxY * plotH).toFloat().coerceAtLeast(3.dp.toPx())
            val x = xFor(chart.xsMinutes[i]) - barW / 2f
            val y = mTop + plotH - h
            val color = if (dep.duration == chart.fastestDuration) fastestColor else barColor
            drawRoundRect(
                color = color.copy(alpha = if (i == selectedIndex) 1f else 0.75f),
                topLeft = Offset(x, y),
                size = Size(barW, h),
                cornerRadius = CornerRadius(2.dp.toPx())
            )
            if (i == selectedIndex) {
                drawRoundRect(
                    color = selectedColor,
                    topLeft = Offset(x - 1.dp.toPx(), y - 1.dp.toPx()),
                    size = Size(barW + 2.dp.toPx(), h + 2.dp.toPx()),
                    cornerRadius = CornerRadius(2.dp.toPx()),
                    style = Stroke(width = 1.5.dp.toPx())
                )
            }
        }
    }

    Text(
        text = strings.dayCaption,
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )

    if (truncated) {
        Text(
            text = strings.dayTruncated(chart.departures.size),
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }

    val selected = selectedIndex?.let { chart.departures.getOrNull(it) }
    if (selected != null) {
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                    RoundedCornerShape(8.dp)
                )
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${formatTime(selected.startTime)} - ${formatTime(selected.endTime)}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${strings.formatDuration(selected.duration)} · " +
                        if (selected.transfers == 0) strings.direct
                        else strings.transferCount(selected.transfers),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (selected.lines.isNotEmpty()) {
                    FlowRow(
                        modifier = Modifier.padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        selected.lines.forEach { line ->
                            val color = getModeColor(line.mode)
                            Text(
                                text = line.name.ifEmpty { "•" },
                                modifier = Modifier
                                    .background(color.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = color
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            Button(onClick = { onShowTrip(selected.startTime) }) {
                Text(strings.dayShowTrip)
            }
        }
    }
}

@Composable
private fun DayStat(label: String, value: String, highlight: Boolean = false) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = if (highlight) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurface
        )
    }
}
