package com.automatelinux.pt.journey

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.automatelinux.pt.data.model.Itinerary
import com.automatelinux.pt.util.AppStrings
import com.automatelinux.pt.util.EnStrings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

/**
 * The one live journey: it owns the cursor, runs the engine on a tick, and is the
 * only way to start or stop a trip.
 *
 * The itinerary lives here rather than in a service Intent — it is large, the panel
 * and the service have to agree on the *same* one, and a journey is singular by
 * nature. The engine runs here rather than in the service so a rider who refused
 * location still gets a working journey off the timetable; the service adds the two
 * things a foreground process is actually for, a live position and notifications
 * that arrive with the screen off.
 */
object JourneySession {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var tick: Job? = null

    private val _itinerary = MutableStateFlow<Itinerary?>(null)
    val itinerary: StateFlow<Itinerary?> = _itinerary.asStateFlow()

    private val _progress = MutableStateFlow<JourneyProgress?>(null)
    val progress: StateFlow<JourneyProgress?> = _progress.asStateFlow()

    /** Alerts the rider must be told about. Replay 0: a missed alert is stale news. */
    private val _alerts = MutableSharedFlow<JourneyAlert>(extraBufferCapacity = 8)
    val alerts: SharedFlow<JourneyAlert> = _alerts.asSharedFlow()

    /** True while a journey is running — the panel shows exactly when this is true. */
    val active: Boolean get() = _itinerary.value != null

    /** Whether this journey is following a live position or only the timetable. */
    var liveTracking: Boolean = false
        private set

    /** Chosen when the journey starts; the service needs it off the UI thread. */
    @Volatile
    var strings: AppStrings = EnStrings
        private set

    private var cursor = JourneyCursor()
    @Volatile
    private var lastFix: GeoFix? = null

    fun start(context: Context, itinerary: Itinerary, strings: AppStrings, live: Boolean) {
        this.strings = strings
        liveTracking = live
        cursor = JourneyCursor()
        lastFix = null
        _itinerary.value = itinerary
        _progress.value = null

        tick?.cancel()
        tick = scope.launch {
            while (true) {
                advance()
                delay(TICK_MS)
            }
        }

        if (live) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, JourneyService::class.java).setAction(JourneyService.ACTION_START)
            )
        }
    }

    fun stop(context: Context) {
        if (liveTracking) {
            context.startService(
                Intent(context, JourneyService::class.java).setAction(JourneyService.ACTION_STOP)
            )
        }
        clear()
    }

    /** Ends the trip without touching the service — the service's own way out. */
    internal fun clear() {
        tick?.cancel()
        tick = null
        _itinerary.value = null
        _progress.value = null
        lastFix = null
        cursor = JourneyCursor()
        liveTracking = false
    }

    /** A fresh position from the service. Recomputes immediately: it is news. */
    internal fun onFix(fix: GeoFix) {
        lastFix = fix
        scope.launch { advance() }
    }

    private suspend fun advance() {
        val trip = _itinerary.value ?: return
        val update = stepJourney(
            itinerary = trip,
            cursor = cursor,
            fix = lastFix,
            nowMs = Clock.System.now().toEpochMilliseconds()
        )
        cursor = update.cursor
        _progress.value = update.progress
        update.alerts.forEach { _alerts.emit(it) }
    }

    private const val TICK_MS = 1_000L
}
