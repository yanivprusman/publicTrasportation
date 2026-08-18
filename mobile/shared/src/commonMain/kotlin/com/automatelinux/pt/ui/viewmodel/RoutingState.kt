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

/**
 * A live sighting of the ride a result card is offering: the operator says a vehicle
 * on that line will call at that stop at [expected].
 *
 * Every competitor marks a live time apart from a timetabled one — Bus Nearby in green,
 * Moovit as "arrival time is accurate", Maps as "(delayed)" against "(scheduled)". The
 * timetable is what MOTIS plans on; this is what the road is actually doing.
 */
data class LiveBoarding(
    /** ISO time the vehicle is expected at the boarding stop. */
    val expected: String,
    /** Seconds later than the timetable; negative means running early. */
    val deltaSeconds: Long,
    val vehicleRef: String? = null
)

/**
 * What is leaving the stop you are standing at, before you have asked anything.
 *
 * Bus Nearby opens straight onto this and it is the strongest idea in the category:
 * most journeys start with "what's coming", not "plan me a route". We already had
 * every piece — nearby stops, the SIRI feed, the timetable — behind a tab nobody
 * lands on.
 */
data class NearbyBoard(
    val stopCode: String,
    val stopName: String,
    val distanceMeters: Int,
    val departures: List<DepartureEntry> = emptyList(),
    /**
     * SIRI's stop-code → name map from the same response.
     *
     * A live visit names its destination by code (`DestinationRef`); without this the
     * row would show a bare number, and showing the line's own name in its place would
     * be inventing a destination.
     */
    val stopNames: Map<String, String> = emptyMap(),
    val loading: Boolean = true,
    /** Set when the board could not be built; the card says so rather than sitting empty. */
    val error: String? = null
)

/**
 * A line's stops, in driving order, for the sheet the tracked card opens.
 * The route_id encodes the direction in this feed, so this is the sequence the
 * tracked bus itself will drive — not a both-directions muddle.
 */
data class LineStopsUi(
    val routeId: String,
    val lineNumber: String = "",
    val headsign: String = "",
    val stops: List<com.automatelinux.pt.data.model.RouteStopItem> = emptyList(),
    val loading: Boolean = true,
    val error: Boolean = false
)

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
    /** Scheduled departure (ISO) of the tracked leg, when known. Lets the card say
     *  "hasn't started its run yet" instead of the alarming "nothing is reporting". */
    val scheduledStart: String? = null,
    /** Where this leg puts you down — the stop you are riding to, not the line's terminus. */
    val destination: String = "",
    /** The SIRI-monitored stop the polling asks about; kept so a pause can resume. */
    val stationCode: String? = null,
    /**
     * Whether [stationCode] is a stop the USER chose (their arrivals board, their
     * itinerary's boarding stop) — as opposed to whichever stop happened to report
     * a tapped map marker, which can be any stop up the road. Only a chosen stop
     * may be labeled "your stop"; labeling the reporting stop that way told a
     * rider at Midreshet Ben-Gurion that מחנה אריאל שרון was their stop.
     */
    val stationIsUsers: Boolean = false,
    /**
     * That stop's name, for the card. The countdown is the arrival at THIS stop — and
     * when tracking starts from a tapped map marker, this is whichever stop reported
     * the vehicle, generally not the one the user is standing at. Leaving it unnamed
     * let "in 3min" read as "at my station" for a bus that had already passed it.
     */
    val stationName: String? = null,
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
    /**
     * Live boardings for the results on screen, keyed by [liveBoardingKey].
     *
     * Absent means "the feed says nothing about this ride", which is the normal case
     * outside the next hour and is shown as a timetable mark rather than a live one —
     * never as a live time we do not have.
     */
    val liveBoardings: Map<String, LiveBoarding> = emptyMap(),
    /** The zero-input answer: departures from the nearest stop, while no trip is planned. */
    val nearbyBoard: NearbyBoard? = null,
    /**
     * For the OPEN itinerary: the same line's departures after the one being
     * ridden, keyed by leg index and already formatted ("14:05"). What happens
     * if this bus is missed — the question every other app answers on the card.
     */
    val nextDepartures: Map<Int, List<String>> = emptyMap(),
    val selectedIndex: Int = 0,
    val loading: Boolean = false,
    val error: String? = null,
    val sortMode: RouteSortMode = RouteSortMode.FASTEST,
    val travelMode: TravelMode = TravelMode.TRANSIT,
    val enabledModes: Set<TransitFilter> = TransitFilter.entries.toSet(),
    val maxWalkMinutes: Int? = null,
    val trackedBus: TrackedBus? = null,
    /**
     * The stop list of one line, opened from the tracked-bus card's line badge.
     * Non-null while the sheet is on screen; the tracked bus stays live under it.
     */
    val lineStops: LineStopsUi? = null,
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


/**
 * How a result card and a live sighting find each other: the stop it leaves from, the
 * line, and the timetabled minute.
 *
 * The scheduled time is part of the key on purpose — a stop can have the same line
 * three times in one result set, and without it the 15:39 and the 16:39 would share a
 * sighting and one of them would lie.
 */
fun liveBoardingKey(stopCode: String, line: String, scheduledStart: String): String =
    "$stopCode|$line|$scheduledStart"
