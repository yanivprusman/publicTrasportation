package com.automatelinux.pt.ui.lines

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Train
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.automatelinux.pt.data.model.LineVariantInfo
import com.automatelinux.pt.util.LocalAppStrings
import com.automatelinux.pt.util.formatDistance

private val COMMON_BUS_LINES = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "10",
    "11", "14", "17", "18", "19", "25", "26", "39", "47", "51",
    "56", "60", "61", "63", "67", "68", "82", "174", "240", "286", "480")

private val TRAIN_LINES = listOf("Israel Railways")

data class LineShapeData(
    val directions: Map<String, List<List<Double>>> = emptyMap(),
    /** route_long_name per direction, for naming where each direction goes. */
    val headsigns: Map<String, String> = emptyMap(),
    /** The line of this number being drawn, and every other one, nearest first. */
    val variant: String? = null,
    val variants: List<LineVariantInfo> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null
)

/**
 * Where a direction ends, from GTFS's "origin-city<->destination-city-N#": the
 * destination stop, with its city only when the stop's name does not already say it.
 * Null when the long name is not in that shape — the caller then numbers the direction.
 */
fun lineDestination(headsign: String?): String? {
    val to = headsign?.substringAfter("<->", "")?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val trimmed = to.replace(Regex("-\\d+#?$"), "")
    val stop = trimmed.substringBeforeLast('-').trim()
    val city = trimmed.substringAfterLast('-', "").trim()
    if (stop.isEmpty()) return trimmed
    return if (city.isEmpty() || stop.contains(city)) stop else "$stop, $city"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LinesBrowserPanel(
    selectedLine: String?,
    lineShapeData: LineShapeData,
    onSelectLine: (String) -> Unit,
    /** Show one direction of [selectedLine] on the map, full screen. */
    onShowDirection: (String) -> Unit,
    /** Draw another line of the same number instead — see [LineVariantInfo]. */
    onSelectVariant: (String) -> Unit,
    favoriteLines: Set<String> = emptySet(),
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    var searchText by remember { mutableStateOf("") }

    Column(modifier = modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
        OutlinedTextField(
            value = searchText,
            onValueChange = { searchText = it },
            label = { Text(strings.searchLine) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Search
            ),
            keyboardActions = KeyboardActions(
                onSearch = {
                    if (searchText.isNotBlank()) {
                        onSelectLine(searchText.trim())
                        searchText = ""
                    }
                }
            )
        )

        Spacer(Modifier.height(12.dp))

        if (favoriteLines.isNotEmpty()) {
            Text(
                text = strings.favoriteLines,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (line in favoriteLines.sorted()) {
                    LineBadgeChip(
                        line = line,
                        isSelected = selectedLine == line,
                        onClick = { onSelectLine(line) }
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        Text(
            text = strings.commonLines,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            for (line in COMMON_BUS_LINES) {
                LineBadgeChip(
                    line = line,
                    isSelected = selectedLine == line,
                    onClick = { onSelectLine(line) }
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        if (selectedLine != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Map,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "${strings.lineShape} $selectedLine",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Which of the country's lines of this number this is: without it,
                    // "64" drawn in Jerusalem reads as a broken map to a rider in the Negev.
                    val shown = lineShapeData.variants.firstOrNull { it.id == lineShapeData.variant }
                    if (shown != null && !lineShapeData.loading) {
                        Text(
                            text = variantCaption(shown, strings),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 28.dp)
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    if (lineShapeData.loading) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        }
                    } else if (lineShapeData.error != null) {
                        Text(
                            text = lineShapeData.error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else if (lineShapeData.directions.isNotEmpty()) {
                        for ((direction, _) in lineShapeData.directions) {
                            // GTFS numbers directions from 0; people don't.
                            val label = lineDestination(lineShapeData.headsigns[direction])
                                ?.let { strings.lineTowards(it) }
                                ?: strings.direction(direction.toIntOrNull()?.plus(1)?.toString() ?: direction)
                            // The route is drawn under this sheet, so a line read off a
                            // list could not actually be looked at. Each direction opens
                            // the map with that direction alone on it.
                            OutlinedButton(
                                onClick = { onShowDirection(direction) },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                            ) {
                                Icon(
                                    Icons.Default.Map,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 2,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        // (The polyline point count was developer telemetry, not information.)
                        val others = lineShapeData.variants.filter { it.id != lineShapeData.variant }
                        if (others.isNotEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = strings.otherLinesNumbered(selectedLine),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                            for (other in others) {
                                Text(
                                    text = variantCaption(other, strings),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onSelectVariant(other.id) }
                                        .padding(vertical = 8.dp)
                                )
                            }
                        }
                    } else {
                        Text(
                            text = strings.noShapeData,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(200.dp))
    }
}

/** "באר שבע ↔ מצפה רמון · מטרופולין · 120 m" — where it runs, who runs it, how far. */
private fun variantCaption(v: LineVariantInfo, strings: com.automatelinux.pt.util.AppStrings): String =
    listOfNotNull(
        v.label,
        v.agency.takeIf { it.isNotBlank() },
        v.distanceM?.let { formatDistance(it, strings) }
    ).joinToString(" · ")

@Composable
fun LineBadgeChip(
    line: String,
    isSelected: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clickable(onClick = onClick)
            .background(
                color = if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = line,
            color = if (isSelected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSecondaryContainer,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )
    }
}
