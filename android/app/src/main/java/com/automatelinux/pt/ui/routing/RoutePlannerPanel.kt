package com.automatelinux.pt.ui.routing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.automatelinux.pt.data.model.GeocodeSuggestion
import com.automatelinux.pt.data.model.Place
import com.automatelinux.pt.data.model.RouteLeg
import com.automatelinux.pt.data.model.RouteSortMode
import com.automatelinux.pt.ui.components.PreSuggestion
import com.automatelinux.pt.ui.viewmodel.RoutingState
import com.automatelinux.pt.util.LocalAppStrings
import java.time.ZonedDateTime

@Composable
fun RoutePlannerPanel(
    state: RoutingState,
    onOriginSelect: (GeocodeSuggestion?) -> Unit,
    onDestinationSelect: (GeocodeSuggestion?) -> Unit,
    onSwap: () -> Unit,
    onTimeChange: (ZonedDateTime?) -> Unit,
    onArriveByChange: (Boolean) -> Unit,
    onSearch: () -> Unit,
    onSelectItinerary: (Int) -> Unit,
    onGeocode: suspend (String) -> List<GeocodeSuggestion>,
    onLegClick: ((RouteLeg) -> Unit)? = null,
    onStopClick: ((Place) -> Unit)? = null,
    onGpsClick: (() -> Unit)? = null,
    gpsLoading: Boolean = false,
    cardOpacity: Float = 0.6f,
    preSuggestions: List<PreSuggestion> = emptyList(),
    onLongPressSuggestion: ((GeocodeSuggestion) -> Unit)? = null,
    sortMode: RouteSortMode = RouteSortMode.FASTEST,
    onSortChange: ((RouteSortMode) -> Unit)? = null,
    onEarlier: (() -> Unit)? = null,
    onLater: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current

    Column(modifier = modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
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
            ItineraryDetail(itinerary = itinerary, onLegClick = onLegClick, onStopClick = onStopClick)
        }
    }
}
