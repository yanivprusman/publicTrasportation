package com.automatelinux.pt.ui.routing

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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.automatelinux.pt.data.model.Itinerary
import com.automatelinux.pt.data.model.RouteSortMode
import com.automatelinux.pt.util.LocalAppStrings

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RouteResults(
    sortedItineraries: List<Itinerary>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    loading: Boolean,
    error: String?,
    searched: Boolean = false,
    onRetry: (() -> Unit)? = null,
    sortMode: RouteSortMode = RouteSortMode.FASTEST,
    onSortChange: ((RouteSortMode) -> Unit)? = null,
    onEarlier: (() -> Unit)? = null,
    onLater: (() -> Unit)? = null,
    cardOpacity: Float = 0.6f,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current

    Column(modifier = modifier) {
        when {
            loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(8.dp))
                        Text(strings.searchingRoutes, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            error != null -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (onRetry != null) {
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = onRetry) {
                            Text(strings.retry)
                        }
                    }
                }
            }
            sortedItineraries.isNotEmpty() -> {
                Column {
                    if (onSortChange != null) {
                        FlowRow(
                            modifier = Modifier.padding(horizontal = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FilterChip(
                                selected = sortMode == RouteSortMode.FASTEST,
                                onClick = { onSortChange(RouteSortMode.FASTEST) },
                                label = { Text(strings.fastest, style = MaterialTheme.typography.labelSmall) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                )
                            )
                            FilterChip(
                                selected = sortMode == RouteSortMode.FEWER_TRANSFERS,
                                onClick = { onSortChange(RouteSortMode.FEWER_TRANSFERS) },
                                label = { Text(strings.fewerTransfers, style = MaterialTheme.typography.labelSmall) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                )
                            )
                            FilterChip(
                                selected = sortMode == RouteSortMode.LESS_WALKING,
                                onClick = { onSortChange(RouteSortMode.LESS_WALKING) },
                                label = { Text(strings.lessWalking, style = MaterialTheme.typography.labelSmall) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                )
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                    }

                    if (onEarlier != null) {
                        OutlinedButton(
                            onClick = onEarlier,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp)
                        ) {
                            Text(strings.earlier)
                        }
                        Spacer(Modifier.height(4.dp))
                    }

                    sortedItineraries.forEachIndexed { index, itinerary ->
                        ItineraryCard(
                            itinerary = itinerary,
                            selected = index == selectedIndex,
                            onClick = { onSelect(index) },
                            cardOpacity = cardOpacity
                        )
                    }

                    if (onLater != null) {
                        Spacer(Modifier.height(4.dp))
                        OutlinedButton(
                            onClick = onLater,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp)
                        ) {
                            Text(strings.later)
                        }
                    }
                }
            }
            // Search completed but returned nothing. Without this branch the
            // sheet renders blank, which is indistinguishable from "the button
            // did nothing" — always tell the user no route was found.
            searched -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = strings.noRoutesFound,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (onRetry != null) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = onRetry) {
                            Text(strings.retry)
                        }
                    }
                }
            }
        }
    }
}
