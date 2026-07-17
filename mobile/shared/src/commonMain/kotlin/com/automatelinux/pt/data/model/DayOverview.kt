package com.automatelinux.pt.data.model

import kotlinx.serialization.Serializable

@Serializable
data class DayLine(
    val mode: TransitMode,
    val name: String = ""
)

@Serializable
data class DayDeparture(
    val startTime: String,
    val endTime: String,
    val duration: Long,
    val transfers: Int = 0,
    val lines: List<DayLine> = emptyList()
)

@Serializable
data class DayOverviewResult(
    val departures: List<DayDeparture> = emptyList(),
    val truncated: Boolean = false
)
