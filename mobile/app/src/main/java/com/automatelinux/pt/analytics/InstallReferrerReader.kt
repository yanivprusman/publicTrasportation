package com.automatelinux.pt.analytics

import android.content.Context
import android.util.Log
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerStateListener
import com.automatelinux.pt.util.SettingsStore
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Reads the Play Install Referrer exactly once per install.
 *
 * Play hands the referrer string over only for a limited window after install,
 * and only on the first successful query — so the value is persisted the moment
 * it is read, and cleared only after the server has acknowledged it. Losing it
 * means permanently losing the attribution for that user.
 */
object InstallReferrerReader {

    private const val TAG = "InstallReferrer"

    suspend fun captureIfNeeded(context: Context, store: SettingsStore) {
        if (store.referrerChecked) return

        val referrer = queryReferrer(context)
        // Mark as checked regardless of outcome: Play will not produce a
        // referrer later for an install that never had one, and retrying on
        // every launch would bind a service connection for nothing.
        store.referrerChecked = true
        if (!referrer.isNullOrBlank()) {
            store.pendingReferrer = referrer
        }
    }

    private suspend fun queryReferrer(context: Context): String? =
        suspendCancellableCoroutine { cont ->
            val client = InstallReferrerClient.newBuilder(context).build()
            var resumed = false

            fun finish(value: String?) {
                if (resumed) return
                resumed = true
                try {
                    client.endConnection()
                } catch (_: Exception) {
                    // Connection teardown failing must not lose the value.
                }
                cont.resume(value)
            }

            try {
                client.startConnection(object : InstallReferrerStateListener {
                    override fun onInstallReferrerSetupFinished(responseCode: Int) {
                        if (responseCode != InstallReferrerClient.InstallReferrerResponse.OK) {
                            Log.d(TAG, "Install referrer unavailable, code=$responseCode")
                            finish(null)
                            return
                        }
                        val value = try {
                            client.installReferrer.installReferrer
                        } catch (e: Exception) {
                            Log.d(TAG, "Install referrer read failed", e)
                            null
                        }
                        finish(value)
                    }

                    override fun onInstallReferrerServiceDisconnected() {
                        finish(null)
                    }
                })
            } catch (e: Exception) {
                Log.d(TAG, "Install referrer connection failed", e)
                finish(null)
            }

            cont.invokeOnCancellation { finish(null) }
        }
}
