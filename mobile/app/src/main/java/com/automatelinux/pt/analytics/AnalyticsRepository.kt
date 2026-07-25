package com.automatelinux.pt.analytics

import android.content.Context
import android.util.Log
import com.automatelinux.pt.BuildConfig
import com.automatelinux.pt.data.api.PtApi
import com.automatelinux.pt.data.model.AppPingRequest
import com.automatelinux.pt.data.model.AppRegisterRequest
import com.automatelinux.pt.util.SettingsStore
import dagger.hilt.android.qualifiers.ApplicationContext
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
    private val store: SettingsStore
) {
    private companion object {
        const val TAG = "Analytics"
    }

    /** Stable per-install id, created on first use. */
    fun installId(): String =
        store.installId ?: UUID.randomUUID().toString().also { store.installId = it }

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
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException("Registration rejected"))
        }
    } catch (e: Exception) {
        Log.d(TAG, "registration failed", e)
        Result.failure(e)
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
