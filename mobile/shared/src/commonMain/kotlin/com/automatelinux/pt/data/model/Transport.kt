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
    // When the operator last heard from the vehicle — NOT when we polled. A stalled feed
    // and a bus stuck in traffic look identical on the map; only this tells them apart.
    @SerialName("RecordedAtTime") val recordedAtTime: String? = null,
    @SerialName("MonitoredVehicleJourney") val monitoredVehicleJourney: MonitoredVehicleJourney? = null
)

@Serializable
data class MonitoredVehicleJourney(
    @SerialName("PublishedLineName") val publishedLineName: String? = null,
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
    val recordedAt: String? = null
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
            recordedAt = visit.recordedAtTime
        )
    }
}
