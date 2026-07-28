package com.automatelinux.pt.analytics

import android.content.Context
import android.util.Log
import com.automatelinux.pt.BuildConfig
import com.automatelinux.pt.data.api.PtApi
import com.automatelinux.pt.data.model.AppPingRequest
import com.automatelinux.pt.data.model.AppRegisterRequest
import com.automatelinux.pt.data.model.AppStateRequest
import com.automatelinux.pt.util.SettingsStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Anonymous install and usage reporting.
 *
 * What leaves the device is an install UUID, the app version, an event name and
 * — once, if present — the Play install referrer. No account, no device id, no
 * ad id, no location, no route history.
 *
 * The server dedupes to one row per (install, event, day), so pinging on every
 * launch is free. Failures are logged and dropped: analytics must never block,
 * slow, or break the app for a user standing at a bus stop.
 */
@Singleton
class AnalyticsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: PtApi,
    private val store: SettingsStore,
    private val vault: IdentityVault
) {
    private companion object {
        const val TAG = "Analytics"
    }

    /** Whether this install already belongs to a known user. */
    enum class IdentityState { RESOLVING, REGISTERED, UNREGISTERED }

    private val _identityState = MutableStateFlow(IdentityState.RESOLVING)
    val identityState: StateFlow<IdentityState> = _identityState.asStateFlow()

    /** Stable per-install id, created on first use. */
    fun installId(): String =
        store.installId ?: UUID.randomUUID().toString().also { store.installId = it }

    /**
     * Works out who this install belongs to, and must run before anything else
     * reads [installId] — minting a fresh UUID first and restoring the vaulted
     * one afterwards would leave the server with two installs for one person.
     *
     * A reinstall wipes SharedPreferences but not the Block Store vault, so the
     * previous identity comes back here and the registration screen never
     * reappears. Users who registered before the vault existed get their
     * identity written into it on this pass, so their next reinstall is covered.
     */
    suspend fun resolveIdentity() {
        if (store.isRegistered) {
            vault.save(installId(), store.registeredEmail!!, store.founderSince)
        } else {
            val vaulted = vault.restore()
            if (vaulted == null) {
                _identityState.value = IdentityState.UNREGISTERED
                return
            }
            store.installId = vaulted.installId
            store.registeredEmail = vaulted.email
            vaulted.founderSince?.let { store.founderSince = it }
            Log.d(TAG, "identity restored from vault")
        }

        // A device with no local state is the reinstall path, and it has nothing
        // to render yet: waiting for the account's favourites costs one request,
        // whereas painting an empty list and filling it in a moment later reads
        // as data loss followed by a glitch. A device that already has state
        // paints first and reconciles behind the UI.
        val hadLocalState = store.stateUpdatedAt != 0L
        if (!hadLocalState) syncState()
        _identityState.value = IdentityState.REGISTERED
        if (hadLocalState) syncState()
    }

    /**
     * Counts a day the user actually opened the app, rather than a calendar day
     * since install — a twice-a-week commuter should not burn "days" while not
     * commuting. Returns true if this call opened a new active day.
     */
    private fun recordActiveDayLocally(): Boolean {
        val today = LocalDate.now().toString()
        if (store.lastActiveDay == today) return false
        store.lastActiveDay = today
        store.activeDays = store.activeDays + 1
        return true
    }

    suspend fun trackLaunch() {
        InstallReferrerReader.captureIfNeeded(context, store)
        recordActiveDayLocally()
        send("launch")
    }

    suspend fun trackEvent(event: String) = send(event)

    /**
     * Registers the user. Unlike the fire-and-forget pings, this must surface
     * failure: the user is waiting on it, and silently "succeeding" would leave
     * them believing they can be reached when they cannot.
     */
    suspend fun register(email: String, phone: String): Result<Unit> = try {
        val response = api.appRegister(
            AppRegisterRequest(installId = installId(), email = email, phone = phone)
        )
        if (response.ok) {
            store.registeredEmail = email
            response.founderSince?.let { store.founderSince = it }
            _identityState.value = IdentityState.REGISTERED
            // Vaulted immediately: if the user uninstalls before the next launch,
            // this is the only copy that will still exist.
            vault.save(installId(), email, store.founderSince)
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException("Registration rejected"))
        }
    } catch (e: Exception) {
        Log.d(TAG, "registration failed", e)
        Result.failure(e)
    }

    /**
     * Reconciles this device's favourites with the account's copy.
     *
     * Whichever side edited last wins outright — the alternative, merging two
     * sets, would silently resurrect stations the user deliberately unstarred
     * on their other phone. After a reinstall the local timestamp is 0, so the
     * server always wins and the favourites come back.
     *
     * Must run after [resolveIdentity]: an install the server cannot tie to an
     * account has nothing to sync, and would be told so.
     */
    suspend fun syncState() {
        if (!store.isRegistered) return
        try {
            val remote = api.appGetState(installId())
            val localUpdatedAt = store.stateUpdatedAt

            if (remote.payload != null && remote.updatedAt > localUpdatedAt) {
                store.applySyncedState(remote.payload!!, remote.updatedAt)
                Log.d(TAG, "state adopted from server (${remote.updatedAt})")
                return
            }
            // Nothing local worth pushing and nothing remote to take: a fresh
            // account that has never starred anything.
            if (localUpdatedAt == 0L) return

            pushState()
        } catch (e: Exception) {
            // Favourites are already on screen from local storage; a failed
            // sync must not interrupt someone checking their bus.
            Log.d(TAG, "state sync failed", e)
        }
    }

    /**
     * Sends this device's state up. Safe to call on every favourite toggle: the
     * server keeps whichever write is newest, so a burst of stars settles on the
     * last one regardless of the order they land in.
     */
    suspend fun pushState() {
        if (!store.isRegistered) return
        val updatedAt = store.stateUpdatedAt
        if (updatedAt == 0L) return
        try {
            api.appPutState(
                AppStateRequest(
                    installId = installId(),
                    payload = store.collectSyncedState(),
                    updatedAt = updatedAt
                )
            )
        } catch (e: Exception) {
            Log.d(TAG, "state push failed", e)
        }
    }

    private suspend fun send(event: String) {
        val id = installId()
        val referrer = store.pendingReferrer
        try {
            api.appPing(
                AppPingRequest(
                    installId = id,
                    appVersion = BuildConfig.VERSION_CODE,
                    event = event,
                    referrer = referrer
                )
            )
            // Only drop the referrer once the server has actually taken it.
            // Clearing it optimistically would lose the attribution for good if
            // the request failed in flight.
            if (referrer != null) store.pendingReferrer = null
        } catch (e: Exception) {
            Log.d(TAG, "ping failed for event=$event", e)
        }
    }
}
