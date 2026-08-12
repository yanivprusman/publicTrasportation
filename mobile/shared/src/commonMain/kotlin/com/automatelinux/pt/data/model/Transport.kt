package com.automatelinux.pt.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SiriResponse(
    @SerialName("Siri") val siri: SiriWrapper? = null,
    @SerialName("_stopNames") val stopNames: Map<String, String>? = null
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
    @SerialName("DistanceFromStop") val distanceFromStop: Int? = null
)

// Domain type built from the SIRI response (not deserialized directly).
data class VehicleMarker(
    val lat: Double,
    val lon: Double,
    val vehicleRef: String,
    val lineNumber: String,
    val expectedArrival: String,
    val distanceFromStop: Int,
    /** SIRI RecordedAtTime, ISO-8601 with offset. Null when the feed omits it. */
    val recordedAt: String? = null,
    /** The monitored stop this was reported by; lets a tapped marker be tracked. */
    val stopCode: String? = null,
    /** SIRI LineRef, i.e. the GTFS route_id — draws the correct line. */
    val lineRef: String? = null,
    /** SIRI DestinationRef; resolved to a name through the response's stop names. */
    val destinationRef: String? = null
)

fun SiriResponse.extractVehicleMarkers(): List<VehicleMarker> {
    val visits = siri?.serviceDelivery?.stopMonitoringDelivery
        ?.flatMap { it.monitoredStopVisit ?: emptyList() } ?: emptyList()

    return visits.mapNotNull { visit ->
        val journey = visit.monitoredVehicleJourney ?: return@mapNotNull null
        val loc = journey.vehicleLocation ?: return@mapNotNull null
        val call = journey.monitoredCall ?: return@mapNotNull null
        val arrival = call.expectedArrivalTime ?: return@mapNotNull null

        VehicleMarker(
            lat = loc.latitude,
            lon = loc.longitude,
            vehicleRef = journey.vehicleRef ?: "",
            lineNumber = journey.publishedLineName ?: "",
            expectedArrival = arrival,
            distanceFromStop = call.distanceFromStop ?: 0,
            recordedAt = visit.recordedAtTime,
            stopCode = visit.monitoringRef,
            lineRef = journey.lineRef,
            destinationRef = journey.destinationRef
        )
    }
}
