package com.automatelinux.pt.analytics

import android.content.Context
import android.util.Log
import com.automatelinux.pt.data.model.VaultedIdentity
import com.google.android.gms.auth.blockstore.Blockstore
import com.google.android.gms.auth.blockstore.DeleteBytesRequest
import com.google.android.gms.auth.blockstore.RetrieveBytesRequest
import com.google.android.gms.auth.blockstore.StoreBytesData
import com.google.android.gms.tasks.Task
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Identity that outlives app storage.
 *
 * SharedPreferences are deleted when the app is uninstalled, so on reinstall the
 * app forgets who the user is: it asks them to register again and the server
 * sees a second install for the same person. Block Store is held by Play
 * services rather than by the app, so the token written here survives an
 * uninstall/reinstall and is carried across during a device-to-device or cloud
 * restore — which is what makes the "your early-user price follows you to your
 * next phone" promise on the registration screen actually true.
 *
 * Only an install UUID, the registered email and the founder date go in here:
 * enough to recognise a returning user, nothing that is not already on the
 * server. The payload is a few hundred bytes against Block Store's 4 KB limit.
 */
@Singleton
class IdentityVault @Inject constructor(
    @ApplicationContext context: Context
) {
    private companion object {
        const val TAG = "IdentityVault"
        const val KEY = "com.automatelinux.pt.identity"
    }

    private val client = Blockstore.getClient(context)
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Cloud backup is opt-in per entry and is only worth requesting when Play
     * services can encrypt it end-to-end; without a screen lock the data would
     * still restore, but Google's guidance is not to ship it to the cloud
     * unencrypted. Locally stored data still survives reinstall either way.
     */
    suspend fun save(installId: String, email: String, founderSince: String?) {
        val payload = json.encodeToString(
            VaultedIdentity.serializer(),
            VaultedIdentity(installId, email, founderSince)
        )
        try {
            val encrypted = client.isEndToEndEncryptionAvailable().awaitOrNull() ?: false
            val request = StoreBytesData.Builder()
                .setBytes(payload.toByteArray())
                .setKey(KEY)
                .setShouldBackupToCloud(encrypted)
                .build()
            client.storeBytes(request).awaitOrNull()
            Log.d(TAG, "identity vaulted (cloudBackup=$encrypted)")
        } catch (e: Exception) {
            // A device without Play services simply has no vault. That costs the
            // user a re-registration after a reinstall; it must not cost them
            // the app, so this is logged and dropped like the analytics pings.
            Log.d(TAG, "vault write failed", e)
        }
    }

    /** Returns the previously vaulted identity, or null on a genuinely new user. */
    suspend fun restore(): VaultedIdentity? = try {
        val request = RetrieveBytesRequest.Builder().setKeys(listOf(KEY)).build()
        val bytes = client.retrieveBytes(request).awaitOrNull()
            ?.blockstoreDataMap
            ?.get(KEY)
            ?.bytes
        if (bytes == null || bytes.isEmpty()) {
            null
        } else {
            json.decodeFromString(VaultedIdentity.serializer(), String(bytes))
        }
    } catch (e: Exception) {
        Log.d(TAG, "vault read failed", e)
        null
    }

    /** Clears the vault. Used when the user deliberately unregisters. */
    suspend fun clear() {
        try {
            client.deleteBytes(DeleteBytesRequest.Builder().setKeys(listOf(KEY)).build())
                .awaitOrNull()
        } catch (e: Exception) {
            Log.d(TAG, "vault clear failed", e)
        }
    }

    /**
     * Bridges a GMS [Task] into a coroutine. Written by hand rather than pulling
     * in kotlinx-coroutines-play-services for three call sites.
     */
    private suspend fun <T> Task<T>.awaitOrNull(): T? = suspendCancellableCoroutine { cont ->
        addOnSuccessListener { if (cont.isActive) cont.resume(it) }
        addOnFailureListener {
            Log.d(TAG, "blockstore task failed", it)
            if (cont.isActive) cont.resume(null)
        }
    }
}
