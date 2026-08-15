package com.automatelinux.pt.ui.routing

import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.automatelinux.pt.data.model.Itinerary
import com.automatelinux.pt.data.model.RouteSortMode
import com.automatelinux.pt.ui.viewmodel.LiveBoarding
import com.automatelinux.pt.util.LocalAppStrings
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
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
    /** What the operator feed says about the rides on screen, keyed by [liveBoardingKey]. */
    liveBoardings: Map<String, LiveBoarding> = emptyMap(),
    cardOpacity: Float = 0.6f,
    /**
     * The full timeline of one result, rendered by the caller and placed by this list
     * DIRECTLY UNDER the card that was tapped. It used to hang below the whole list,
     * so opening the fourth result changed something two screens away and the tap read
     * as a dead one. A detail belongs to a card, so it lives with it.
     */
    detailContent: (@Composable (Itinerary) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current

    // Which result is open. Local, because it is a view state and not a routing fact:
    // the selected index still drives what the map draws, and stays where it is when
    // a card is closed. -1 = the clean list, which is what a fresh search deserves.
    var expandedIndex by rememberSaveable { mutableIntStateOf(-1) }
    LaunchedEffect(sortedItineraries) { expandedIndex = -1 }

    // Opening a card near the bottom of the sheet grows content the user cannot see.
    // The requester rides the open card and pulls it — with its timeline — into view.
    val revealRequester = remember { BringIntoViewRequester() }
    var revealTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(revealTick) {
        if (revealTick > 0) {
            // One frame for the detail to compose and measure; scrolling to a rect
            // that has not been laid out yet lands on the card's old height.
            withFrameNanos { }
            revealRequester.bringIntoView()
        }
    }

    // One clock for the whole list: every card's "departs in ..." counts down off it,
    // so a list left open on screen never shows a boarding time that has quietly passed.
    var now by remember { mutableStateOf(Clock.System.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(10_000)
            now = Clock.System.now()
        }
    }

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
                        val open = index == expandedIndex
                        Column(
                            modifier = if (open) Modifier.bringIntoViewRequester(revealRequester)
                                else Modifier
                        ) {
                            ItineraryCard(
                                itinerary = itinerary,
                                selected = index == selectedIndex,
                                expanded = open,
                                onClick = {
                                    if (open) {
                                        expandedIndex = -1
                                    } else {
                                        expandedIndex = index
                                        onSelect(index)
                                        revealTick++
                                    }
                                },
                                now = now,
                                laterDepartures = laterDeparturesOf(itinerary, sortedItineraries),
                                live = liveBoardingFor(itinerary, liveBoardings),
                                cardOpacity = cardOpacity
                            )
                            if (open && detailContent != null) {
                                detailContent(itinerary)
                            }
                        }
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
