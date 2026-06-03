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
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.automatelinux.pt.data.model.GeocodeSuggestion
import com.automatelinux.pt.data.model.Place
import com.automatelinux.pt.data.model.RouteLeg
import com.automatelinux.pt.data.model.RouteSortMode
import com.automatelinux.pt.ui.components.PreSuggestion
import com.automatelinux.pt.ui.viewmodel.RoutingState
import com.automatelinux.pt.util.LocalAppStrings
import kotlinx.datetime.Instant

@Composable
fun RoutePlannerPanel(
    state: RoutingState,
    onOriginSelect: (GeocodeSuggestion?) -> Unit,
    onDestinationSelect: (GeocodeSuggestion?) -> Unit,
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
    onEarlier: (() -> Unit)? = null,
    onLater: (() -> Unit)? = null,
    homePlace: GeocodeSuggestion? = null,
    workPlace: GeocodeSuggestion? = null,
    onQuickRoute: ((GeocodeSuggestion, GeocodeSuggestion) -> Unit)? = null,
    onTrackBus: ((Int, RouteLeg) -> Unit)? = null,
    trackedLegIndex: Int? = null,
    onSetReminder: ((RouteLeg) -> Unit)? = null,
    activeReminderLegIndex: Int? = null,
    onCancelReminder: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current

    Column(modifier = modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
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

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            IconButton(onClick = onSwap) {
                Icon(
                    Icons.Default.SwapVert,
                    contentDescription = strings.swapOriginDestination,
                    tint = MaterialTheme.colorScheme.primary
                )
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

        Spacer(Modifier.height(8.dp))

        TimePickerSection(
            departureTime = state.departureTime,
            onTimeChange = onTimeChange,
            arriveBy = state.arriveBy,
            onArriveByChange = onArriveByChange
        )

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = onSearch,
            modifier = Modifier.fillMaxWidth(),
            enabled = state.origin != null && state.destination != null && !state.loading
        ) {
            Icon(Icons.Default.Search, contentDescription = null)
            Text("  ${strings.searchRoutes}", modifier = Modifier.padding(start = 4.dp))
        }

        Spacer(Modifier.height(8.dp))

        RouteResults(
            sortedItineraries = state.sortedItineraries,
            selectedIndex = state.selectedIndex,
            onSelect = onSelectItinerary,
            loading = state.loading,
            error = state.error,
            onRetry = onSearch,
            sortMode = sortMode,
            onSortChange = if (state.results != null && state.results.itineraries.isNotEmpty()) onSortChange else null,
            onEarlier = if (state.results != null && state.results.itineraries.isNotEmpty()) onEarlier else null,
            onLater = if (state.results != null && state.results.itineraries.isNotEmpty()) onLater else null,
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
                onCancelReminder = onCancelReminder
            )
        }
    }
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
