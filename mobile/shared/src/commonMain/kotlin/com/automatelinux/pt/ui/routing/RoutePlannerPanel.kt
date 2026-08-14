package com.automatelinux.pt.ui.routing

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AddHome
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Work
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.AssistChip
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.automatelinux.pt.data.model.GeocodeSuggestion
import com.automatelinux.pt.data.model.Place
import com.automatelinux.pt.data.model.RouteLeg
import com.automatelinux.pt.data.model.RouteSortMode
import com.automatelinux.pt.ui.components.PreSuggestion
import com.automatelinux.pt.ui.viewmodel.RoutingState
import com.automatelinux.pt.ui.viewmodel.TransitFilter
import com.automatelinux.pt.ui.viewmodel.TravelMode
import com.automatelinux.pt.util.LocalAppStrings
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Composable
fun RoutePlannerPanel(
    state: RoutingState,
    onOriginSelect: (GeocodeSuggestion?) -> Unit,
    onDestinationSelect: (GeocodeSuggestion?) -> Unit,
    onViaSelect: (GeocodeSuggestion?) -> Unit,
    onShowViaField: () -> Unit,
    onRemoveVia: () -> Unit,
    onSwap: () -> Unit,
    onTimeChange: (Instant?) -> Unit,
    onArriveByChange: (Boolean) -> Unit,
    onSearch: () -> Unit,
    onSelectItinerary: (Int) -> Unit,
    onGeocode: suspend (String) -> List<GeocodeSuggestion>,
    onLegClick: ((RouteLeg) -> Unit)? = null,
    onStopClick: ((Place) -> Unit)? = null,
    onGpsClick: (() -> Unit)? = null,
    gpsLoading: Boolean = false,
    onGpsClickDestination: (() -> Unit)? = null,
    gpsLoadingDestination: Boolean = false,
    cardOpacity: Float = 0.6f,
    preSuggestions: List<PreSuggestion> = emptyList(),
    onLongPressSuggestion: ((GeocodeSuggestion) -> Unit)? = null,
    sortMode: RouteSortMode = RouteSortMode.FASTEST,
    onSortChange: ((RouteSortMode) -> Unit)? = null,
    onTravelModeChange: ((TravelMode) -> Unit)? = null,
    onToggleModeFilter: ((TransitFilter) -> Unit)? = null,
    onMaxWalkChange: ((Int?) -> Unit)? = null,
    onEarlier: (() -> Unit)? = null,
    onLater: (() -> Unit)? = null,
    homePlace: GeocodeSuggestion? = null,
    workPlace: GeocodeSuggestion? = null,
    onQuickRoute: ((GeocodeSuggestion, GeocodeSuggestion) -> Unit)? = null,
    onQuickDestination: ((GeocodeSuggestion) -> Unit)? = null,
    onSavePlace: ((GeocodeSuggestion) -> Unit)? = null,
    onSetHome: (() -> Unit)? = null,
    onTrackBus: ((Int, RouteLeg) -> Unit)? = null,
    trackedLegIndex: Int? = null,
    onSetReminder: ((RouteLeg) -> Unit)? = null,
    activeReminderLegIndex: Int? = null,
    onCancelReminder: (() -> Unit)? = null,
    onStartJourney: (() -> Unit)? = null,
    onShareTrip: (() -> Unit)? = null,
    onToggleDayOverview: (() -> Unit)? = null,
    onSelectDayDeparture: ((Int?) -> Unit)? = null,
    onPickDayDeparture: ((String) -> Unit)? = null,
    onRetryDayOverview: (() -> Unit)? = null,
    /** Open the full arrivals board for a stop — where the nearby card's long tail lives. */
    onOpenStopBoard: ((String, String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current

    // Results-first: once a search succeeds, the tall form folds into a one-line
    // summary so the itineraries — the thing the user asked for — sit at the top
    // of the sheet instead of a screen below it. Any successful search re-folds;
    // clearing an endpoint (results become null) unfolds automatically.
    var formCollapsed by remember { mutableStateOf(false) }
    LaunchedEffect(state.results) {
        formCollapsed = state.results?.itineraries?.isNotEmpty() == true
    }

    Column(modifier = modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
        if (formCollapsed && state.origin != null && state.destination != null) {
            CollapsedSearchSummary(
                state = state,
                onExpand = { formCollapsed = false },
                onSwap = onSwap
            )
            Spacer(Modifier.height(8.dp))
        } else {
        if (homePlace != null && workPlace != null && onQuickRoute != null && state.results == null) {
            FrequentRouteCard(
                homePlace = homePlace,
                workPlace = workPlace,
                onQuickRoute = onQuickRoute
            )
            Spacer(Modifier.height(8.dp))
        }

        LocationInput(
            label = strings.from,
            value = state.origin,
            onSelect = { onOriginSelect(it) },
            onClear = { onOriginSelect(null) },
            onGeocode = onGeocode,
            showGpsButton = true,
            onGpsClick = onGpsClick,
            gpsLoading = gpsLoading,
            preSuggestions = preSuggestions,
            onLongPressSuggestion = onLongPressSuggestion,
            modifier = Modifier.fillMaxWidth()
        )

        Box(modifier = Modifier.fillMaxWidth()) {
            if (!state.viaFieldVisible) {
                TextButton(
                    onClick = onShowViaField,
                    modifier = Modifier.align(Alignment.CenterStart)
                ) {
                    Icon(
                        Icons.Default.AddLocationAlt,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(strings.addStop, modifier = Modifier.padding(start = 4.dp))
                }
            }
            IconButton(onClick = onSwap, modifier = Modifier.align(Alignment.Center)) {
                Icon(
                    Icons.Default.SwapVert,
                    contentDescription = strings.swapOriginDestination,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        if (state.viaFieldVisible) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LocationInput(
                    label = strings.stopAlongTheWay,
                    value = state.via,
                    onSelect = { onViaSelect(it) },
                    onClear = { onViaSelect(null) },
                    onGeocode = onGeocode,
                    preSuggestions = preSuggestions,
                    onLongPressSuggestion = onLongPressSuggestion,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onRemoveVia) {
                    Icon(
                        Icons.Default.RemoveCircleOutline,
                        contentDescription = strings.removeStop,
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        LocationInput(
            label = strings.to,
            value = state.destination,
            onSelect = { onDestinationSelect(it) },
            onClear = { onDestinationSelect(null) },
            onGeocode = onGeocode,
            showGpsButton = true,
            onGpsClick = onGpsClickDestination,
            gpsLoading = gpsLoadingDestination,
            preSuggestions = preSuggestions,
            onLongPressSuggestion = onLongPressSuggestion,
            modifier = Modifier.fillMaxWidth()
        )

        val destination = state.destination
        if (destination == null &&
            (onQuickDestination != null && (homePlace != null || workPlace != null) ||
                onSetHome != null && homePlace == null)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (onQuickDestination != null) {
                    homePlace?.let { place ->
                        QuickDestinationChip(
                            label = strings.home,
                            icon = Icons.Default.Home,
                            onClick = { onQuickDestination(place) }
                        )
                    }
                    workPlace?.let { place ->
                        QuickDestinationChip(
                            label = strings.work,
                            icon = Icons.Default.Work,
                            onClick = { onQuickDestination(place) }
                        )
                    }
                }
                // The whole quick-destination feature is invisible until a home
                // exists — advertise it in the same spot the Home chip will occupy.
                if (onSetHome != null && homePlace == null) {
                    QuickDestinationChip(
                        label = strings.setHome,
                        icon = Icons.Default.AddHome,
                        onClick = onSetHome
                    )
                }
            }
        } else if (destination != null && onSavePlace != null &&
            homePlace == null && workPlace == null &&
            state.results == null && !state.loading
        ) {
            // Bootstrap: without a saved place the quick chips can never appear,
            // and the only other way to save one is a long-press on a suggestion.
            Row {
                QuickDestinationChip(
                    label = strings.saveAs,
                    icon = Icons.Default.BookmarkAdd,
                    onClick = { onSavePlace(destination) }
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        TimePickerSection(
            departureTime = state.departureTime,
            onTimeChange = onTimeChange,
            arriveBy = state.arriveBy,
            onArriveByChange = onArriveByChange
        )

        if (onToggleModeFilter != null && onMaxWalkChange != null) {
            Spacer(Modifier.height(4.dp))
            RouteOptionsSection(
                enabledModes = state.enabledModes,
                maxWalkMinutes = state.maxWalkMinutes,
                onToggleMode = onToggleModeFilter,
                onMaxWalkChange = onMaxWalkChange
            )
        }

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = onSearch,
            modifier = Modifier.fillMaxWidth(),
            enabled = state.origin != null && state.destination != null && !state.loading
        ) {
            Icon(Icons.Default.Search, contentDescription = null)
            Text("  ${strings.searchRoutes}", modifier = Modifier.padding(start = 4.dp))
        }

        // Day overview and street alternatives are point-to-point only — the
        // backend doesn't produce them for trips stitched through a via stop.
        if (onToggleDayOverview != null && state.origin != null && state.destination != null && state.via == null) {
            Spacer(Modifier.height(4.dp))
            OutlinedButton(
                onClick = onToggleDayOverview,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.BarChart, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("  ${strings.dayOverview}", modifier = Modifier.padding(start = 4.dp))
            }
            if (state.showDayOverview) {
                Spacer(Modifier.height(8.dp))
                DayOverviewSection(
                    data = state.dayOverview,
                    loading = state.dayLoading,
                    error = state.dayError,
                    selectedIndex = state.selectedDayIndex,
                    onSelect = { onSelectDayDeparture?.invoke(it) },
                    onShowTrip = { onPickDayDeparture?.invoke(it) },
                    onRetry = { onRetryDayOverview?.invoke() }
                )
            }
        }
        } // end expanded form

        Spacer(Modifier.height(8.dp))

        // Before a destination exists, the useful answer is not an empty results list —
        // it is what is leaving the stop you are standing at. Bus Nearby opens onto
        // exactly this; we had the data behind a tab nobody lands on.
        val board = state.nearbyBoard
        if (board != null && state.destination == null && state.results == null && !state.loading) {
            NearbyDeparturesCard(
                board = board,
                onOpenBoard = { code, name -> onOpenStopBoard?.invoke(code, name) },
                cardOpacity = cardOpacity
            )
            Spacer(Modifier.height(8.dp))
        }

        val results = state.results
        if (onTravelModeChange != null && state.via == null && results != null && !state.loading && state.error == null &&
            (results.itineraries.isNotEmpty() || results.alternatives.isNotEmpty())
        ) {
            TravelModeStrip(
                transitDuration = results.itineraries.minOfOrNull { it.duration },
                alternatives = results.alternatives,
                travelMode = state.travelMode,
                onSelect = onTravelModeChange
            )
            Spacer(Modifier.height(4.dp))
        }

        val alternative = state.selectedAlternative
        if (alternative != null && !state.loading && state.error == null) {
            DirectRouteCard(alternative = alternative)
        } else {
            RouteResults(
                sortedItineraries = state.sortedItineraries,
                selectedIndex = state.selectedIndex,
                onSelect = onSelectItinerary,
                loading = state.loading,
                error = state.error,
                searched = state.results != null,
                onRetry = onSearch,
                sortMode = sortMode,
                onSortChange = if (state.results?.itineraries?.isNotEmpty() == true) onSortChange else null,
                onEarlier = if (state.results?.itineraries?.isNotEmpty() == true) onEarlier else null,
                onLater = if (state.results?.itineraries?.isNotEmpty() == true) onLater else null,
                liveBoardings = state.liveBoardings,
                cardOpacity = cardOpacity
            )

            state.selectedItinerary?.let { itinerary ->
                Spacer(Modifier.height(8.dp))
                ItineraryDetail(
                    itinerary = itinerary,
                    onLegClick = onLegClick,
                    onStopClick = onStopClick,
                    onTrackBus = onTrackBus,
                    trackedLegIndex = trackedLegIndex,
                    onSetReminder = onSetReminder,
                    activeReminderLegIndex = activeReminderLegIndex,
                    onCancelReminder = onCancelReminder,
                    onStartJourney = onStartJourney,
                    onShareTrip = onShareTrip
                )
            }
        }
    }
}

/**
 * The folded form: origin → destination on one line, the time mode (and via stop)
 * under it. The whole card expands the form again; swap works without unfolding
 * because reversing a trip is the one edit people make mid-results.
 */
@Composable
private fun CollapsedSearchSummary(
    state: RoutingState,
    onExpand: () -> Unit,
    onSwap: () -> Unit,
) {
    val strings = LocalAppStrings.current
    val timeText = state.departureTime?.let { t ->
        val hm = t.toLocalDateTime(TimeZone.currentSystemDefault()).time.toString().take(5)
        if (state.arriveBy) "${strings.arriveBy} $hm" else "${strings.departAt} $hm"
    } ?: strings.now
    Card(
        onClick = onExpand,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp)
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        state.origin?.name.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 6.dp).size(14.dp)
                    )
                    Text(
                        state.destination?.name.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
                val via = state.via
                Text(
                    if (via != null) "$timeText · ${strings.stopAlongTheWay}: ${via.name}" else timeText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onSwap) {
                Icon(
                    Icons.Default.SwapVert,
                    contentDescription = strings.swapOriginDestination,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            IconButton(onClick = onExpand) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = strings.editSearch,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun QuickDestinationChip(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    AssistChip(
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        }
    )
}

@Composable
fun FrequentRouteCard(
    homePlace: GeocodeSuggestion,
    workPlace: GeocodeSuggestion,
    onQuickRoute: (GeocodeSuggestion, GeocodeSuggestion) -> Unit
) {
    val strings = LocalAppStrings.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onQuickRoute(homePlace, workPlace) },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = strings.frequentRoute,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Home,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = homePlace.name.take(15),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(Modifier.width(8.dp))
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                Icon(
                    Icons.Default.Work,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = workPlace.name.take(15),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}
