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
 * timeOfDay ("HH:mm", local) and dayOfWeek (ISO 1=Mon … 7=Sun) are when the route
 * was saved for, kept as a weekly slot rather than a date: a trip saved for Sunday
 * 06:00 searches the next Sunday 06:00, so it never points at a day already gone.
 * dayOfWeek null = every day (routes saved before the weekday was kept); timeOfDay
 * null = saved with "Now".
 */
@Serializable
data class FavoriteRoute(
    val origin: GeocodeSuggestion,
    val destination: GeocodeSuggestion,
    val timeOfDay: String? = null,
    val dayOfWeek: Int? = null,
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
