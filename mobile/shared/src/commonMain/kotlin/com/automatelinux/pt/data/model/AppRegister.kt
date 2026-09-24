package com.automatelinux.pt.data.model

import kotlinx.serialization.Serializable

/**
 * Registration payload. Declared in the shared module because that is where the
 * kotlinx-serialization compiler plugin is applied — a @Serializable class in
 * :app gets no generated serializer and fails at runtime, not compile time.
 */
@Serializable
data class AppRegisterRequest(
    val installId: String,
    val email: String,
    val phone: String
)

@Serializable
data class AppRegisterResponse(
    val ok: Boolean = false,
    val founderSince: String? = null
)

/**
 * Account deletion. The install id is the whole payload and the whole
 * credential: the phone that holds it is the one allowed to erase the account
 * behind it, which is why no email is accepted here or by the server.
 */
@Serializable
data class AppDeleteRequest(
    val installId: String
)

@Serializable
data class AppDeleteResponse(
    val ok: Boolean = false,
    val wasRegistered: Boolean = false
)
