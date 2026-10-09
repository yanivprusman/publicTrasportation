package com.automatelinux.pt.data.model

import kotlinx.serialization.Serializable

/**
 * The part of a user's state that belongs to the account rather than to the
 * phone: what they starred, and what they have already been told.
 *
 * The server stores this as an opaque blob keyed on the account, so a field can
 * be added here without a database migration. Anything added must stay
 * defaulted — an older client will send a payload without it, and a newer one
 * must not fail to decode its own account's state.
 */
@Serializable
data class SyncedState(
    val favoriteStations: List<List<String>> = emptyList(),
    val favoriteLines: List<String> = emptyList(),
    val pricingNoticeAck: Boolean = false,
    val favoriteRoutes: List<FavoriteRoute> = emptyList()
)

/**
 * A starred trip: both ends, so one tap fills From and To and searches.
 *
 * timeOfDay ("HH:mm", local) is the clock time the route was saved with — only the
 * time, never the date: a trip saved for Sunday 06:00 is the 06:00 trip, and picking
 * it searches the next 06:00 to come. Null means it was saved with "Now".
 */
@Serializable
data class FavoriteRoute(
    val origin: GeocodeSuggestion,
    val destination: GeocodeSuggestion,
    val timeOfDay: String? = null,
    val arriveBy: Boolean = false
)

/** Wire shape of GET/POST /api/app/state. */
@Serializable
data class AppStateResponse(
    val ok: Boolean = false,
    val payload: SyncedState? = null,
    val updatedAt: Long = 0
)

@Serializable
data class AppStateRequest(
    val installId: String,
    val payload: SyncedState,
    val updatedAt: Long
)
