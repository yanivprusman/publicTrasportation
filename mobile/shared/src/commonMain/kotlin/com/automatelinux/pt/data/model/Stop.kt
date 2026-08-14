package com.automatelinux.pt.data.model

import kotlinx.serialization.Serializable

@Serializable
data class StopResult(
    val stopCode: String,
    val stopName: String,
    val lat: Double,
    val lon: Double,
    val distanceMeters: Int = 0,
    /**
     * The timetable id, feed-prefixed ("israel_26635").
     *
     * `/api/stoptimes` keys on this, not on [stopCode] — asking it for 13868 answers
     * 404. Empty only for a server too old to send it.
     */
    val id: String = ""
)
