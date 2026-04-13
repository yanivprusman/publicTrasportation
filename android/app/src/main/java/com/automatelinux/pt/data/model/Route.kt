package com.automatelinux.pt.data.model

data class Place(
    val name: String,
    val lat: Double,
    val lon: Double
)

enum class TransitMode {
    WALK, BUS, RAIL, TRAM, SUBWAY;

    companion object {
        fun fromString(s: String): TransitMode =
            entries.firstOrNull { it.name.equals(s, ignoreCase = true) } ?: WALK
    }
}

data class RouteLeg(
    val mode: TransitMode,
    val from: Place,
    val to: Place,
    val startTime: String,
    val endTime: String,
    val duration: Long,
    val routeShortName: String? = null,
    val routeColor: String? = null,
    val agencyName: String? = null,
    val polyline: String = "",
    val intermediateStops: List<Place>? = null
)

data class Itinerary(
    val duration: Long,
    val startTime: String,
    val endTime: String,
    val transfers: Int,
    val legs: List<RouteLeg>
)

data class RouteResult(
    val itineraries: List<Itinerary>
)
