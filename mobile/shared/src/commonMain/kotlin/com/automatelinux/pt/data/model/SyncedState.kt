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
    val pricingNoticeAck: Boolean = false
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
