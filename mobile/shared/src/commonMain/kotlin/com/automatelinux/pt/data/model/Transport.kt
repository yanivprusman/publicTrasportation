package com.automatelinux.pt.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SiriResponse(
    @SerialName("Siri") val siri: SiriWrapper? = null,
    @SerialName("_stopNames") val stopNames: Map<String, String>? = null,
    /**
     * Vehicles, already interpreted by the server (lib/siri-vehicles.ts).
     *
     * The raw [siri] tree is still here because the arrivals board reads visits from it,
     * but nothing on this side walks it looking for vehicles any more. Both clients used
     * to, separately, and both concluded that DistanceFromStop was a distance to the stop.
     */
    @SerialName("_vehicles") val vehicles: List<VehicleMarker>? = null
)

@Serializable
data class SiriWrapper(
    @SerialName("ServiceDelivery") val serviceDelivery: ServiceDelivery? = null
)

@Serializable
data class ServiceDelivery(
    @SerialName("StopMonitoringDelivery") val stopMonitoringDelivery: List<StopMonitoringDelivery>? = null
)

@Serializable
data class StopMonitoringDelivery(
    @SerialName("MonitoredStopVisit") val monitoredStopVisit: List<MonitoredStopVisit>? = null
)

@Serializable
data class MonitoredStopVisit(
    @SerialName("ItemIdentifier") val itemIdentifier: String? = null,
    /** The stop this visit was reported for — the key to poll it again. */
    @SerialName("MonitoringRef") val monitoringRef: String? = null,
    // When the operator last heard from the vehicle — NOT when we polled. A stalled feed
    // and a bus stuck in traffic look identical on the map; only this tells them apart.
    @SerialName("RecordedAtTime") val recordedAtTime: String? = null,
    @SerialName("MonitoredVehicleJourney") val monitoredVehicleJourney: MonitoredVehicleJourney? = null
)

@Serializable
data class MonitoredVehicleJourney(
    @SerialName("PublishedLineName") val publishedLineName: String? = null,
    /**
     * The operator's internal line id, which is also the GTFS `route_id`
     * (LineRef 11057 = route 11057 = line 64, Beer Sheva <-> Mitzpe Ramon).
     * It identifies the route exactly, where the published name does not.
     */
    @SerialName("LineRef") val lineRef: String? = null,
    @SerialName("DirectionRef") val directionRef: String? = null,
    @SerialName("DestinationRef") val destinationRef: String? = null,
    @SerialName("VehicleRef") val vehicleRef: String? = null,
    @SerialName("VehicleLocation") val vehicleLocation: VehicleLocation? = null,
    @SerialName("MonitoredCall") val monitoredCall: MonitoredCall? = null
)

@Serializable
data class VehicleLocation(
    @SerialName("Latitude") val latitude: Double,
    @SerialName("Longitude") val longitude: Double
)

@Serializable
data class MonitoredCall(
    @SerialName("ExpectedArrivalTime") val expectedArrivalTime: String? = null,
    /**
     * Metres this vehicle has driven on its trip. The wire name is SIRI's
     * `DistanceFromStop`, which is why it kept being read as a distance TO the stop —
     * sampled over time it counts up at road speed. The property is named for what it
     * measures so the next reader cannot make that mistake from the call site.
     */
    @SerialName("DistanceFromStop") val tripTravelledMeters: Int? = null
)

/**
 * A vehicle, exactly as the server hands it over.
 *
 * Deserialized straight from `_vehicles` rather than assembled here: the shape and the
 * field names are the server's (lib/siri-vehicles.ts), so Android, iOS and the web all
 * read the same interpretation of the feed instead of each inventing one.
 */
@Serializable
data class VehicleMarker(
    val lat: Double,
    val lon: Double,
    val vehicleRef: String = "",
    val lineNumber: String = "",
    val expectedArrival: String = "",
    /**
     * Metres driven on this trip — NOT a distance to the stop, to the user, or to
     * anything else on screen. Anything phrased "X away" is computed from two positions;
     * see MainScreen's tracked-card distance.
     */
    val tripTravelledMeters: Int = 0,
    /** SIRI RecordedAtTime, ISO-8601 with offset. Null when the feed omits it. */
    val recordedAt: String? = null,
    /**
     * Compass degrees this vehicle is pointing — 0 is north, increasing clockwise. Null
     * when the feed had no reading.
     *
     * The server already normalised SIRI's `Bearing: "0"` sentinel away (see
     * lib/siri-vehicles.ts, which measures why 0 is "unknown" and not "north"), so any
     * value here is a real heading and needs no re-checking for zero at the draw site.
     */
    val bearingDegrees: Int? = null,
    /** The monitored stop this was reported by; lets a tapped marker be tracked. */
    val stopCode: String? = null,
    /** SIRI LineRef, i.e. the GTFS route_id — draws the correct line. */
    val lineRef: String? = null,
    val destinationRef: String? = null,
    /** Resolved from GTFS by the server. Empty when unknown — never the raw stop code. */
    val destinationName: String = ""
)

/** Vehicles the server already extracted; see [SiriResponse.vehicles]. */
fun SiriResponse.extractVehicleMarkers(): List<VehicleMarker> = vehicles ?: emptyList()

/**
 * One route's ordered stop list, from /api/route-stops. The route_id already
 * encodes the direction in this feed, so [stops] is the exact sequence a bus
 * on that route drives — first stop to last.
 */
@Serializable
data class RouteStopsResponse(
    val routeId: String = "",
    val lineNumber: String = "",
    val headsign: String = "",
    val stops: List<RouteStopItem> = emptyList()
)

@Serializable
data class RouteStopItem(
    val stopCode: String = "",
    val name: String = "",
    val lat: Double = 0.0,
    val lon: Double = 0.0
)

/**
 * The nearest bus that is actually reporting, at any distance — `/api/nearest-bus`.
 *
 * Answers a different question from the live-buses layer, which walks the stops nearest
 * the user and so can only ever report on the neighbourhood. When a whole region is quiet
 * — a Shabbat afternoon in Gush Dan, a village any day — "nothing here" and "nothing
 * anywhere" are different facts and the neighbourhood cannot tell them apart.
 *
 * [searchedFromMeters] is what the caller had already covered itself; the server skips
 * candidates inside it rather than re-asking stops the live layer already probed.
 */
@Serializable
data class NearestBusResponse(
    val found: Boolean = false,
    val vehicle: VehicleMarker? = null,
    val distanceMeters: Int? = null,
    val searchedMeters: Int = 0,
    val searchedFromMeters: Int = 0,
    val candidateStops: Int = 0,
    val siriRequests: Int = 0,
)
