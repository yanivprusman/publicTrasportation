package com.automatelinux.pt.data.model

import kotlinx.serialization.Serializable

/**
 * What the app keeps outside its own storage so a reinstall does not lose the
 * account — see the Android `IdentityVault`. Nothing here is a secret: it is the
 * same install id and email the server already holds, kept so a returning user
 * is recognised instead of being asked to register a second time.
 *
 * Declared in the shared module because that is where the kotlinx-serialization
 * compiler plugin is applied — a @Serializable class in :app gets no generated
 * serializer.
 */
@Serializable
data class VaultedIdentity(
    val installId: String,
    val email: String,
    val founderSince: String? = null
)
