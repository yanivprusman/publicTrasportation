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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.automatelinux.pt.data.model.GeocodeSuggestion
import com.automatelinux.pt.ui.viewmodel.RoutingState
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
    onGpsClick: (() -> Unit)? = null,
    gpsLoading: Boolean = false,
    cardOpacity: Float = 0.6f,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
        LocationInput(
            label = "From",
            value = state.origin,
            onSelect = { onOriginSelect(it) },
            onClear = { onOriginSelect(null) },
            onGeocode = onGeocode,
            showGpsButton = true,
            onGpsClick = onGpsClick,
            gpsLoading = gpsLoading,
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            IconButton(onClick = onSwap) {
                Icon(
                    Icons.Default.SwapVert,
                    contentDescription = "Swap origin and destination",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        LocationInput(
            label = "To",
            value = state.destination,
            onSelect = { onDestinationSelect(it) },
            onClear = { onDestinationSelect(null) },
            onGeocode = onGeocode,
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
            Text("  Search Routes", modifier = Modifier.padding(start = 4.dp))
        }

        Spacer(Modifier.height(8.dp))

        RouteResults(
            results = state.results,
            selectedIndex = state.selectedIndex,
            onSelect = onSelectItinerary,
            loading = state.loading,
            error = state.error,
            onRetry = onSearch,
            cardOpacity = cardOpacity
        )

        state.selectedItinerary?.let { itinerary ->
            Spacer(Modifier.height(8.dp))
            ItineraryDetail(itinerary = itinerary)
        }
    }
}
