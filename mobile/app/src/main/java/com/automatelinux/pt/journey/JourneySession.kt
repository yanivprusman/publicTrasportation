package com.automatelinux.pt.journey

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.automatelinux.pt.data.api.PtApi
import com.automatelinux.pt.data.model.Itinerary
import com.automatelinux.pt.data.model.JourneyLiveUpdateRequest
import com.automatelinux.pt.data.model.ShareLeg
import com.automatelinux.pt.data.model.SharePosition
import com.automatelinux.pt.data.model.extractVehicleMarkers
import com.automatelinux.pt.util.AppStrings
import com.automatelinux.pt.util.EnStrings
import com.automatelinux.pt.util.TripLink
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
import org.koin.core.context.GlobalContext

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
    private var linger: Job? = null
    private var livePoll: Job? = null
    private var shareJob: Job? = null

    /** Set while this journey is being shared; the panel tints its share icon by it. */
    private val _shareToken = MutableStateFlow<String?>(null)
    val shareToken: StateFlow<String?> = _shareToken.asStateFlow()

    private val _itinerary = MutableStateFlow<Itinerary?>(null)
    val itinerary: StateFlow<Itinerary?> = _itinerary.asStateFlow()

    private val _progress = MutableStateFlow<JourneyProgress?>(null)
    val progress: StateFlow<JourneyProgress?> = _progress.asStateFlow()

    /**
     * What the live feed says about the bus being walked to or waited for.
     * Null whenever there is no such bus, no feed, or no sighting — the panel
     * shows silence then, never a guess.
     */
    private val _live = MutableStateFlow<JourneyLiveInfo?>(null)
    val live: StateFlow<JourneyLiveInfo?> = _live.asStateFlow()

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
        linger?.cancel()
        linger = null
        _itinerary.value = itinerary
        _progress.value = null
        _live.value = null

        tick?.cancel()
        tick = scope.launch {
            while (true) {
                advance()
                delay(TICK_MS)
            }
        }
        startLivePolling()

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
        linger?.cancel()
        linger = null
        livePoll?.cancel()
        livePoll = null
        shareJob?.cancel()
        shareJob = null
        // Tell watchers the trip is over rather than letting the page go dark.
        // Captured before the state is wiped; fired from the session scope, which
        // outlives this call.
        val endToken = _shareToken.value
        val endTrip = _itinerary.value
        if (endToken != null && endTrip != null) {
            scope.launch {
                try {
                    GlobalContext.get().get<PtApi>()
                        .journeyLivePost(shareRequest(endTrip, endToken, withLegs = false, ended = true))
                } catch (_: Exception) {
                    // The share ages out on the server on its own.
                }
            }
        }
        _shareToken.value = null
        _itinerary.value = null
        _progress.value = null
        _live.value = null
        lastFix = null
        cursor = JourneyCursor()
        liveTracking = false
    }

    /**
     * Starts sharing this journey (idempotent) and returns the link to hand out,
     * or null when there is no journey or the server cannot be reached.
     *
     * The link is the public web app's `/journey/<token>` page; the token exists
     * only on the server that minted it, so updates keep flowing to the same
     * backend the page reads from.
     */
    suspend fun shareLive(): String? {
        val trip = _itinerary.value ?: return null
        _shareToken.value?.let { return TripLink.liveJourneyUrl(it) }
        val api = GlobalContext.get().get<PtApi>()
        val token = try {
            api.journeyLivePost(shareRequest(trip, token = null, withLegs = true)).token
        } catch (_: Exception) {
            return null
        }
        _shareToken.value = token
        shareJob?.cancel()
        shareJob = scope.launch {
            while (true) {
                delay(SHARE_POST_MS)
                val current = _itinerary.value ?: break
                val t = _shareToken.value ?: break
                try {
                    api.journeyLivePost(shareRequest(current, t, withLegs = false))
                } catch (_: Exception) {
                    // A missed update leaves the page one beat stale; the next
                    // round carries the same truth.
                }
            }
        }
        return TripLink.liveJourneyUrl(token)
    }

    private fun shareRequest(
        trip: Itinerary,
        token: String?,
        withLegs: Boolean,
        ended: Boolean = false
    ): JourneyLiveUpdateRequest {
        val p = _progress.value
        return JourneyLiveUpdateRequest(
            token = token,
            headline = JourneyText.headline(p, strings),
            detail = JourneyText.detail(p, strings),
            etaIso = trip.endTime,
            destinationName = trip.legs.lastOrNull()?.to?.name ?: "",
            position = lastFix?.let { SharePosition(it.lat, it.lon) },
            legs = if (withLegs) {
                trip.legs.map { ShareLeg(it.mode.name, it.polyline, it.routeColor) }
            } else null,
            progressLegIndex = p?.legIndex ?: 0,
            ended = if (ended) true else null
        )
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
            nowMs = Clock.System.now().toEpochMilliseconds(),
            // Only a sighting of THIS leg's bus may move its board call.
            liveBoardingArrivalMs = _live.value
                ?.takeIf { it.legIndex == cursor.legIndex }
                ?.arrivalMs
        )
        cursor = update.cursor
        _progress.value = update.progress
        update.alerts.forEach { _alerts.emit(it) }

        // Arrival ends the journey by itself: linger long enough for "You've
        // arrived!" to be read, then put everything away. Before this lived here, a
        // journey run off the timetable alone (no service) ticked forever, and its
        // panel sat on the map until ended by hand.
        if (update.progress.phase == JourneyPhase.ARRIVED && linger == null) {
            linger = scope.launch {
                delay(ARRIVED_LINGER_MS)
                clear()
            }
        }
    }

    /**
     * Asks the SIRI feed where the bus being waited for actually is, for as long
     * as one is being waited for.
     *
     * Runs in the session rather than the UI so a pocketed phone still gets its
     * board call from the live sighting. It is quiet whenever no boarding stop is
     * ahead (which is most of a ride), and it degrades to nothing on error: a
     * journey without a feed is exactly the journey this app already knew how to
     * run — off the timetable, saying so.
     */
    private fun startLivePolling() {
        livePoll?.cancel()
        livePoll = scope.launch {
            val api = GlobalContext.get().get<PtApi>()
            while (true) {
                val trip = _itinerary.value ?: break
                val legIndex = JourneyLive.watchLegIndex(trip, _progress.value)
                val leg = legIndex?.let { trip.legs.getOrNull(it) }
                val stopCode = leg?.fromStopCode
                if (leg != null && legIndex != null && stopCode != null) {
                    try {
                        val markers = api.getTransport(station = stopCode).extractVehicleMarkers()
                        _live.value = JourneyLive.infoFrom(
                            legIndex = legIndex,
                            leg = leg,
                            markers = markers,
                            nowMs = Clock.System.now().toEpochMilliseconds()
                        )
                    } catch (_: Exception) {
                        // Keep the last report; the panel retires it by age, and the
                        // next round may succeed.
                    }
                } else {
                    _live.value = null
                }
                delay(LIVE_POLL_MS)
            }
        }
    }

    private const val TICK_MS = 1_000L
    private const val ARRIVED_LINGER_MS = 20_000L
    private const val LIVE_POLL_MS = 20_000L
    private const val SHARE_POST_MS = 10_000L
}
