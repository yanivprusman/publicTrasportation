package com.automatelinux.pt.data.model

import kotlinx.serialization.Serializable

@Serializable
data class StopResult(
    val stopCode: String,
    val stopName: String,
    val lat: Double,
    val lon: Double,
    val distanceMeters: Int = 0
)
