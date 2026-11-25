package com.automatelinux.pt.ui.routing

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import com.automatelinux.pt.ui.components.PtSegment
import com.automatelinux.pt.ui.components.PtSegmented
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
import com.automatelinux.pt.ui.util.PlatformBackHandler
import kotlinx.datetime.Clock

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RouteResults(
    sortedItineraries: List<Itinerary>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    loading: Boolean,
    error: String?,
    searched: Boolean = false,
    onRetry: (() -> Unit)? = null,
    /**
     * The mode groups the search was limited to, named ("Tram", "Bus / Train"), or
     * null when every mode was searched. An empty result under a filter is the
     * filter's doing far more often than the trip's, so it says so and offers the
     * one tap that undoes it — "try a different time" sent the rider the wrong way.
     */
    modeFilterLabel: String? = null,
    onShowAllModes: (() -> Unit)? = null,
    sortMode: RouteSortMode = RouteSortMode.ARRIVES_FIRST,
    onSortChange: ((RouteSortMode) -> Unit)? = null,
    onEarlier: (() -> Unit)? = null,
    onLater: (() -> Unit)? = null,
    /** What the operator feed says about the rides on screen, keyed by [liveBoardingKey]. */
    liveBoardings: Map<String, LiveBoarding> = emptyMap(),
    cardOpacity: Float = 0.6f,
    /** Rides with a departure reminder armed, by [reminderKey]. */
    reminderKeys: Set<String> = emptySet(),
    /** Arms or disarms the reminder for a card's first ride, from the card itself. */
    onToggleReminder: ((com.automatelinux.pt.data.model.RouteLeg) -> Unit)? = null,
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
    // Back closes the open card, not the app.
    PlatformBackHandler(enabled = expandedIndex != -1) { expandedIndex = -1 }

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
                        // One order at a time, so a track rather than four chips
                        // that wrapped onto a second line with one orphan on it.
                        val sorts = listOf(
                            RouteSortMode.ARRIVES_FIRST to strings.arrivesFirst,
                            RouteSortMode.FASTEST to strings.fastest,
                            RouteSortMode.FEWER_TRANSFERS to strings.fewerTransfers,
                            RouteSortMode.LESS_WALKING to strings.lessWalking
                        )
                        PtSegmented(
                            segments = sorts.map { PtSegment(it.second) },
                            selectedIndex = sorts.indexOfFirst { it.first == sortMode },
                            onSelect = { onSortChange(sorts[it].first) },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                            height = 38.dp,
                            dense = true
                        )
                        Spacer(Modifier.height(8.dp))
                    }

                    if (onEarlier != null) {
                        TimeShiftRow(strings.earlier, Icons.Default.KeyboardArrowUp, onEarlier)
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
                                cardOpacity = cardOpacity,
                                reminderArmed = itinerary.firstRide?.reminderKey() in reminderKeys,
                                onToggleReminder = onToggleReminder
                            )
                            if (open && detailContent != null) {
                                detailContent(itinerary)
                            }
                        }
                    }

                    if (onLater != null) {
                        Spacer(Modifier.height(4.dp))
                        TimeShiftRow(strings.later, Icons.Default.KeyboardArrowDown, onLater)
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
                    if (modeFilterLabel != null && onShowAllModes != null) {
                        Text(
                            text = strings.noRoutesWithModes(modeFilterLabel),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = onShowAllModes) {
                            Text(strings.showAllModes)
                        }
                    } else {
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
}

/**
 * "Earlier" / "Later": the list goes on in that direction. A quiet full-width row
 * with the arrow of the way it extends, not a hollow button competing with the cards.
 */
@Composable
private fun TimeShiftRow(label: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .height(40.dp)
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
    }
}
