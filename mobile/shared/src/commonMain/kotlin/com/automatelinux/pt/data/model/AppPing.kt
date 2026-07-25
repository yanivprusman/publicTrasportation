package com.automatelinux.pt.data.model

import kotlinx.serialization.Serializable

/**
 * Anonymous install/usage ping payload.
 *
 * Lives in the shared module because that is where the kotlinx-serialization
 * compiler plugin is applied — a @Serializable class declared in :app has no
 * generated serializer and fails at runtime, not at compile time.
 *
 * [installId] is a random per-install UUID. Nothing here identifies a person.
 */
@Serializable
data class AppPingRequest(
    val installId: String,
    val appVersion: Int,
    val event: String = "launch",
    val platform: String = "android",
    val referrer: String? = null
)

@Serializable
data class AppPingResponse(
    val ok: Boolean = false,
    val newActiveDay: Boolean = false
)
