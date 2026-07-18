package com.automatelinux.pt.data.model

import kotlinx.serialization.Serializable

@Serializable
data class StoptimesResponse(
    val stopTimes: List<StopTimeEntry> = emptyList()
)

@Serializable
data class StopTimeEntry(
    val place: StopTimePlace = StopTimePlace(),
    val mode: String = "BUS",
    val realTime: Boolean = false,
    val headsign: String = "",
    val routeShortName: String = "",
    val displayName: String = "",
    val agencyName: String = ""
)

@Serializable
data class StopTimePlace(
    val departure: String? = null,
    val scheduledDeparture: String? = null
)

// Rail routes often carry no short name; the display name (and as a last resort
// the mode) keeps the line badge from rendering as "?".
val StopTimeEntry.lineLabel: String
    get() = routeShortName.ifBlank { displayName }.ifBlank { mode }
