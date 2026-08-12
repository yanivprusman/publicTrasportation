package com.automatelinux.pt.ui.viewmodel

import com.automatelinux.pt.data.model.DayOverviewResult
import com.automatelinux.pt.data.model.DirectAlternative
import com.automatelinux.pt.data.model.GeocodeSuggestion
import com.automatelinux.pt.data.model.Itinerary
import com.automatelinux.pt.data.model.RouteResult
import com.automatelinux.pt.data.model.RouteSortMode
import com.automatelinux.pt.data.model.TransitMode
import com.automatelinux.pt.data.model.VehicleMarker
import com.automatelinux.pt.data.model.WheelchairAccess
import kotlinx.datetime.Instant

/**
 * Why the tracking card is showing what it is showing.
 *
 * Tracking used to clear itself when it could not find anything, which left the button
 * looking broken. Every outcome is now a state the card can name.
 */
enum class TrackingStatus {
    /** Locating a monitorable stop, or the first poll has not come back yet. */
    SEARCHING,

    /** The feed is reporting at least one vehicle on this line. */
    LIVE,

    /** No SIRI-monitorable stop near the boarding point — tracking cannot work here. */
    NO_MONITORED_STOP,

    /** The stop is monitored, but no vehicle on this line is reporting right now. */
    NO_VEHICLE,

    /** The last poll failed. */
    ERROR
}

data class TrackedBus(
    val legIndex: Int,
    val lineName: String,
    val status: TrackingStatus = TrackingStatus.SEARCHING,
    /** Every vehicle the feed reports for this line, nearest to the boarding stop first. */
    val candidates: List<VehicleMarker> = emptyList(),
    /** Which of [candidates] the card and the map are showing. */
    val selectedIndex: Int = 0,
    /** Wheelchair access of the scheduled trip being boarded (from the itinerary leg). */
    val access: WheelchairAccess = WheelchairAccess.UNKNOWN,
    /** Where this leg puts you down — the stop you are riding to, not the line's terminus. */
    val destination: String = "",
    /** The SIRI-monitored stop the polling asks about; kept so a pause can resume. */
    val stationCode: String? = null,
    /** The vehicle the user picked out of a stop's arrivals, followed on first poll. */
    val preferredVehicleRef: String? = null,
    /** Where you board — the other half of the "where is it relative to me" question. */
    val stopLat: Double = 0.0,
    val stopLon: Double = 0.0,
    /**
     * The route this trip actually runs, as [lat, lon] pairs from GTFS shapes.txt.
     * Resolved through the trip id, never the line number — see /api/trip-shape.
     */
    val shape: List<List<Double>> = emptyList()
) {
    /** The vehicle currently being followed — what the map draws. */
    val marker: VehicleMarker?
        get() = candidates.getOrNull(selectedIndex)
}

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
    val via: GeocodeSuggestion? = null,
    val viaFieldVisible: Boolean = false,
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
