package com.automatelinux.pt.ui.viewmodel

import com.automatelinux.pt.data.model.GeocodeSuggestion
import com.automatelinux.pt.data.model.Itinerary
import com.automatelinux.pt.data.model.RouteResult
import com.automatelinux.pt.data.model.RouteSortMode
import com.automatelinux.pt.data.model.VehicleMarker
import kotlinx.datetime.Instant

data class TrackedBus(
    val legIndex: Int,
    val lineName: String,
    val marker: VehicleMarker?
)

data class RoutingState(
    val origin: GeocodeSuggestion? = null,
    val destination: GeocodeSuggestion? = null,
    val departureTime: Instant? = null,
    val arriveBy: Boolean = false,
    val results: RouteResult? = null,
    val selectedIndex: Int = 0,
    val loading: Boolean = false,
    val error: String? = null,
    val sortMode: RouteSortMode = RouteSortMode.FASTEST,
    val trackedBus: TrackedBus? = null
) {
    val sortedItineraries: List<Itinerary>
        get() {
            val itineraries = results?.itineraries ?: return emptyList()
            return when (sortMode) {
                RouteSortMode.FASTEST -> itineraries.sortedBy { it.duration }
                RouteSortMode.FEWER_TRANSFERS -> itineraries.sortedBy { it.transfers }
                RouteSortMode.LESS_WALKING -> itineraries.sortedBy { it.walkDuration }
            }
        }

    val selectedItinerary: Itinerary?
        get() = sortedItineraries.getOrNull(selectedIndex)
}
