package com.automatelinux.pt.data.model

import kotlinx.serialization.Serializable

/**
 * Wire types for live journey sharing — the phone posts these to
 * `/api/journey-live`, and anyone holding the link watches the result.
 */

@Serializable
data class SharePosition(val lat: Double, val lon: Double)

@Serializable
data class ShareLeg(
    val mode: String,
    val polyline: String,
    val routeColor: String? = null
)

@Serializable
data class JourneyLiveUpdateRequest(
    /** Absent on the first post; the server mints one and it rides along after. */
    val token: String? = null,
    /** Localized by this app; the page renders it verbatim. */
    val headline: String,
    val detail: String? = null,
    val etaIso: String,
    val destinationName: String,
    val position: SharePosition? = null,
    /** Sent once, on the create — the route does not change mid-journey. */
    val legs: List<ShareLeg>? = null,
    val progressLegIndex: Int,
    val ended: Boolean? = null
)

@Serializable
data class JourneyLiveTokenResponse(val token: String)
