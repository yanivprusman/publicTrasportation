package com.automatelinux.pt.data.model

import kotlinx.serialization.Serializable

@Serializable
data class GeocodeSuggestion(
    val name: String,
    val lat: Double,
    val lon: Double,
    val type: String? = null
)
