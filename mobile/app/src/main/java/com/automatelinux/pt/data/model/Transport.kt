package com.automatelinux.pt.data.model

import com.google.gson.annotations.SerializedName

data class SiriResponse(
    @SerializedName("Siri") val siri: SiriWrapper? = null,
    @SerializedName("_stopNames") val stopNames: Map<String, String>? = null
)

data class SiriWrapper(
    @SerializedName("ServiceDelivery") val serviceDelivery: ServiceDelivery? = null
)

data class ServiceDelivery(
    @SerializedName("StopMonitoringDelivery") val stopMonitoringDelivery: List<StopMonitoringDelivery>? = null
)

data class StopMonitoringDelivery(
    @SerializedName("MonitoredStopVisit") val monitoredStopVisit: List<MonitoredStopVisit>? = null
)

data class MonitoredStopVisit(
    @SerializedName("ItemIdentifier") val itemIdentifier: String? = null,
    @SerializedName("MonitoredVehicleJourney") val monitoredVehicleJourney: MonitoredVehicleJourney? = null
)

data class MonitoredVehicleJourney(
    @SerializedName("PublishedLineName") val publishedLineName: String? = null,
    @SerializedName("DirectionRef") val directionRef: String? = null,
    @SerializedName("DestinationRef") val destinationRef: String? = null,
    @SerializedName("VehicleRef") val vehicleRef: String? = null,
    @SerializedName("VehicleLocation") val vehicleLocation: VehicleLocation? = null,
    @SerializedName("MonitoredCall") val monitoredCall: MonitoredCall? = null
)

data class VehicleLocation(
    @SerializedName("Latitude") val latitude: Double,
    @SerializedName("Longitude") val longitude: Double
)

data class MonitoredCall(
    @SerializedName("ExpectedArrivalTime") val expectedArrivalTime: String? = null,
    @SerializedName("DistanceFromStop") val distanceFromStop: Int? = null
)

data class VehicleMarker(
    val lat: Double,
    val lon: Double,
    val vehicleRef: String,
    val lineNumber: String,
    val expectedArrival: String,
    val distanceFromStop: Int
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
            distanceFromStop = call.distanceFromStop ?: 0
        )
    }
}
