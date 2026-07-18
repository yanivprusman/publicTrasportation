package com.automatelinux.pt.ui.viewmodel

import com.automatelinux.pt.data.model.DayOverviewResult
import com.automatelinux.pt.data.model.DirectAlternative
import com.automatelinux.pt.data.model.GeocodeSuggestion
import com.automatelinux.pt.data.model.Itinerary
import com.automatelinux.pt.data.model.RouteResult
import com.automatelinux.pt.data.model.RouteSortMode
import com.automatelinux.pt.data.model.TransitMode
import com.automatelinux.pt.data.model.VehicleMarker
import kotlinx.datetime.Instant

data class TrackedBus(
    val legIndex: Int,
    val lineName: String,
    val marker: VehicleMarker?
)

/** TRANSIT shows the itinerary list; BIKE/CAR show that direct street route. */
enum class TravelMode {
    TRANSIT, BIKE, CAR
}

/** Transit-mode filter groups understood by the backend's ?modes= parameter. */
enum class TransitFilter(val apiKey: String) {
    BUS("bus"), TRAIN("train"), TRAM("tram")
}

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
    val travelMode: TravelMode = TravelMode.TRANSIT,
    val enabledModes: Set<TransitFilter> = TransitFilter.entries.toSet(),
    val maxWalkMinutes: Int? = null,
    val trackedBus: TrackedBus? = null,
    val showDayOverview: Boolean = false,
    val dayOverview: DayOverviewResult? = null,
    val dayLoading: Boolean = false,
    val dayError: String? = null,
    val selectedDayIndex: Int? = null
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

    val selectedAlternative: DirectAlternative?
        get() = when (travelMode) {
            TravelMode.TRANSIT -> null
            TravelMode.BIKE -> results?.alternatives?.firstOrNull { it.mode == TransitMode.BIKE }
            TravelMode.CAR -> results?.alternatives?.firstOrNull { it.mode == TransitMode.CAR }
        }

    /** What the map should draw: the chosen street route, or the selected transit itinerary. */
    val displayedItinerary: Itinerary?
        get() = selectedAlternative?.itinerary ?: selectedItinerary
}
