package com.automatelinux.pt.data.model

import kotlinx.serialization.Serializable

@Serializable
data class GeocodeSuggestion(
    val name: String,
    val lat: Double,
    val lon: Double,
    val type: String? = null,
    // MOTIS place id (e.g. "israel_13684") — present on STOP results, used for stoptimes lookups.
    val id: String? = null
)
