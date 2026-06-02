package com.automatelinux.pt.data.model

data class Place(
    val name: String,
    val lat: Double,
    val lon: Double
)

enum class TransitMode {
    WALK, BUS, RAIL, TRAM, SUBWAY, FERRY;

    companion object {
        fun fromString(s: String): TransitMode = when {
            s.equals("WALK", ignoreCase = true) -> WALK
            s.equals("BUS", ignoreCase = true) -> BUS
            s.equals("TRAM", ignoreCase = true) -> TRAM
            s.equals("SUBWAY", ignoreCase = true) -> SUBWAY
            s.equals("METRO", ignoreCase = true) -> SUBWAY
            s.equals("FERRY", ignoreCase = true) -> FERRY
            s.contains("RAIL", ignoreCase = true) -> RAIL
            else -> WALK
        }
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
) {
    val walkDuration: Long
        get() = legs.filter { it.mode == TransitMode.WALK }.sumOf { it.duration }

    fun estimateFare(): Double {
        var fare = 0.0
        for (leg in legs) {
            when (leg.mode) {
                TransitMode.BUS -> fare += 5.50
                TransitMode.TRAM -> fare += 5.50
                TransitMode.SUBWAY -> fare += 5.50
                TransitMode.RAIL -> fare += 15.0
                TransitMode.FERRY -> fare += 25.0
                TransitMode.WALK -> {}
            }
        }
        return fare
    }
}

enum class RouteSortMode {
    FASTEST, FEWER_TRANSFERS, LESS_WALKING
}

data class RouteResult(
    val itineraries: List<Itinerary>
)
