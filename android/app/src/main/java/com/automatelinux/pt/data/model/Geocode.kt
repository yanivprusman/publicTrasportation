package com.automatelinux.pt.data.model

data class GeocodeSuggestion(
    val name: String,
    val lat: Double,
    val lon: Double,
    val type: String? = null
)
