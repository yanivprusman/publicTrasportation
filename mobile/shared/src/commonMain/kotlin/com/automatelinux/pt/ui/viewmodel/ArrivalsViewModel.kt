package com.automatelinux.pt.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.automatelinux.pt.data.api.PtApi
import com.automatelinux.pt.data.model.MonitoredStopVisit
import com.automatelinux.pt.data.model.SiriResponse
import com.automatelinux.pt.data.model.StopResult
import com.automatelinux.pt.data.model.VehicleMarker
import com.automatelinux.pt.data.model.extractVehicleMarkers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.automatelinux.pt.util.metersBetween
import kotlinx.datetime.Clock

// ArrivalsState now lives in commonMain (ui/viewmodel/ArrivalsState.kt).

private const val TIMETABLE_TTL_MS = 5 * 60_000L
private const val FAVORITE_SNAP_METERS = 150

private const val NEARBY_VEHICLE_INTERVAL_MS = 15_000L

/**
 * How many buses the search walks outward to find.
 *
 * A radius is the wrong unit for this question. In a city any radius finds buses; in a
 * village — Midreshet Ben Gurion, where this was reported — the nearest reporting bus can
 * be tens of kilometres away, and *every* fixed radius is either too small there or
 * absurd downtown. A count is the same promise in both: the nearest few buses, however
 * far out that turns out to be.
 */
private const val NEARBY_VEHICLE_TARGET_BUSES = 5

/** Stops queried concurrently per round; also the width of the parallel fan-out. */
private const val NEARBY_VEHICLE_ROUND_STOPS = 5

/**
 * The request budget for one poll, and the real bound on this search — four rounds of
 * [NEARBY_VEHICLE_ROUND_STOPS]. Walking outward has to stop somewhere, and it should stop
 * on work done rather than on distance: 20 SIRI requests is the cost, whether they cover
 * a block or half the Negev.
 */
private const val NEARBY_VEHICLE_MAX_STOPS = 20

/**
 * How far the stop *list* may reach. Nearly free, which is why it is generous: it is one
 * request whatever the radius, the server returns at most 100 stops and sorts them
 * nearest-first, and the walk above stops at the request budget long before distance
 * matters. Set to cover a rural area rather than a city block — the previous 2 km
 * ceiling was the whole bug.
 */
private const val NEARBY_VEHICLE_SEARCH_CEILING_M = 50_000

class ArrivalsViewModel(
    private val api: PtApi
) : ViewModel() {

    private val _state = MutableStateFlow(ArrivalsState())
    val state: StateFlow<ArrivalsState> = _state.asStateFlow()

    private var pollingJob: Job? = null
    private var timetableJob: Job? = null

    fun setStationCode(code: String, name: String = "") {
        applyStation(code, name, explicit = true)
    }

    private fun applyStation(code: String, name: String, explicit: Boolean) {
        timetableJob?.cancel()
        // Widget taps and other by-code entries arrive nameless; the code alone then
        // labels the field, the board header and the widget. Resolve the human name.
        if (name.isBlank() && code.isNotBlank()) {
            viewModelScope.launch {
                try {
                    val resolved = api.searchStops(code)
                        .firstOrNull { it.stopCode == code }?.stopName
                    if (resolved != null &&
                        _state.value.stationCode == code &&
                        _state.value.stationName.isBlank()
                    ) {
                        _state.value = _state.value.copy(stationName = resolved)
                    }
                } catch (_: Exception) {
                    // The code keeps labeling the station until a named selection.
                }
            }
        }
        _state.value = _state.value.copy(
            stationCode = code,
            stationName = name,
            stationExplicitlyChosen = explicit || _state.value.stationExplicitlyChosen,
            siriData = null,
            error = null,
            timetable = emptyList(),
            timetableForCode = null,
            timetableFetchedAt = null,
            timetableLoading = false,
            timetableError = false
        )
        fetchArrivals()
    }

    // Location-first selection, run when the Arrivals tab opens with a GPS fix:
    // fill the quick-switch chips and, unless the user explicitly picked a
    // station, select the stop they're standing at (a favorite within
    // FAVORITE_SNAP_METERS beats a marginally closer non-favorite).
    fun autoSelectNearestStation(lat: Double, lon: Double, favoriteCodes: Set<String>) {
        viewModelScope.launch {
            val stops = fetchNearbyStops(lat, lon, radius = 500)
            if (stops.isEmpty()) return@launch
            _state.value = _state.value.copy(gpsNearbyStops = stops.take(3))
            if (_state.value.stationExplicitlyChosen) return@launch
            val favorite = stops.firstOrNull {
                it.stopCode in favoriteCodes && it.distanceMeters <= FAVORITE_SNAP_METERS
            }
            val target = favorite ?: stops.first()
            if (target.stopCode == _state.value.stationCode) return@launch
            applyStation(target.stopCode, target.stopName, explicit = false)
        }
    }

    fun setLineFilter(filter: String) {
        _state.value = _state.value.copy(lineFilter = filter)
    }

    fun setShowVehicleMarkers(show: Boolean) {
        _state.value = _state.value.copy(showVehicleMarkers = show)
    }

    fun fetchArrivals() {
        // No station selected yet — nothing to fetch (the polling loop also lands here).
        if (_state.value.stationCode.isBlank()) return
        maybeFetchTimetable()
        viewModelScope.launch {
            val requestedStation = _state.value.stationCode
            _state.value = _state.value.copy(loading = true, error = null)
            try {
                val response = api.getTransport(station = requestedStation)
                // The station may have changed while this request was in flight
                // (auto-select, user tap) — a late response must not overwrite
                // the new station's board.
                if (_state.value.stationCode != requestedStation) return@launch
                _state.value = _state.value.copy(
                    siriData = response,
                    vehicleMarkers = response.extractVehicleMarkers(),
                    lastUpdated = Clock.System.now().toEpochMilliseconds(),
                    loading = false
                )
            } catch (e: Exception) {
                if (_state.value.stationCode != requestedStation) return@launch
                _state.value = _state.value.copy(
                    loading = false,
                    error = e.message ?: "Failed to fetch arrivals"
                )
            }
        }
    }

    // Scheduled timetable pipeline: stop code → coords via /api/stops → MOTIS stop id
    // via geocode (nearest same-named STOP) → /api/stoptimes. Refreshed on a TTL so the
    // 15s SIRI poll doesn't hammer three extra endpoints.
    private fun maybeFetchTimetable() {
        val s = _state.value
        if (timetableJob?.isActive == true) return
        val age = s.timetableFetchedAt?.let {
            Clock.System.now().toEpochMilliseconds() - it
        }
        if (s.timetableForCode == s.stationCode && age != null && age < TIMETABLE_TTL_MS) return
        val code = s.stationCode
        timetableJob = viewModelScope.launch {
            _state.value = _state.value.copy(
                timetableLoading = _state.value.timetableForCode != code,
                timetableError = false
            )
            try {
                val stop = api.searchStops(code).firstOrNull { it.stopCode == code }
                    ?: throw IllegalStateException("Stop $code not found in GTFS")
                val best = api.geocode(stop.stopName)
                    .filter { it.type == "STOP" && !it.id.isNullOrBlank() }
                    .minByOrNull { distanceMeters(it.lat, it.lon, stop.lat, stop.lon) }
                    ?.takeIf { distanceMeters(it.lat, it.lon, stop.lat, stop.lon) <= 250.0 }
                    ?: throw IllegalStateException("No schedule stop matches ${stop.stopName}")
                val response = api.getStoptimes(stopId = best.id!!, n = 30)
                _state.value = _state.value.copy(
                    timetable = response.stopTimes,
                    timetableForCode = code,
                    timetableFetchedAt = Clock.System.now().toEpochMilliseconds(),
                    timetableLoading = false
                )
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _state.value = _state.value.copy(
                    timetableLoading = false,
                    timetableError = true,
                    timetable = emptyList(),
                    timetableForCode = code,
                    timetableFetchedAt = Clock.System.now().toEpochMilliseconds()
                )
            }
        }
    }

    private fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double =
        metersBetween(lat1, lon1, lat2, lon2)

    fun startPolling() {
        stopPolling()
        pollingJob = viewModelScope.launch {
            while (true) {
                fetchArrivals()
                delay(15_000)
            }
        }
    }

    fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    suspend fun searchStops(query: String): List<StopResult> {
        return try {
            api.searchStops(query)
        } catch (_: Exception) {
            emptyList()
        }
    }

    private var nearbyVehiclesJob: Job? = null

    /**
     * Buses reporting around a point, for the map's live-buses mode.
     *
     * SIRI is monitored per stop, so "the buses around here" is the union of what the
     * stops report. The server returns them sorted nearest-first, which is what lets this
     * *walk outward*: a round of [NEARBY_VEHICLE_ROUND_STOPS] stops at a time, stopping
     * once it holds [NEARBY_VEHICLE_TARGET_BUSES] buses and has covered the screen, and
     * in any case once [NEARBY_VEHICLE_MAX_STOPS] requests are spent.
     *
     * That walk is the whole point. A fixed radius cannot serve both a city and a
     * village: 700 m, then 2 km, both drew an empty map in Midreshet Ben Gurion while
     * buses ran on the road outside. Asking for a number of buses instead of a distance
     * makes the promise identical in both places, and lets the sparse case pay for its
     * own extra rounds while the dense case still stops after one.
     *
     * [viewportRadiusMeters] no longer bounds the search, only its floor: the walk does
     * not stop early while the screen still shows ground it has not asked about.
     *
     * Two things are deliberately NOT bounded. The number of vehicles — [seen] is ordered
     * by whichever stop answered first, so truncating it would drop a different bus each
     * poll and make markers blink, and each marker is two canvas circles anyway. And the
     * search radius, which is now free: one stop-list request covers 50 km whether or not
     * the walk ever reaches that far.
     */
    fun startNearbyVehicles(lat: Double, lon: Double, viewportRadiusMeters: Double) {
        nearbyVehiclesJob?.cancel()
        val viewportRadius = viewportRadiusMeters.toInt()
        _state.value = _state.value.copy(
            nearbyVehiclesLoaded = false,
            nearbyVehiclesReachedMeters = 0,
            nearbyVehiclesNearestMeters = 0
        )
        nearbyVehiclesJob = viewModelScope.launch {
            while (true) {
                // One request, nearest-first, capped server-side. Everything below walks
                // this list rather than re-querying at a wider radius, so widening the
                // search costs SIRI requests only where it actually needs them.
                val stops = fetchNearbyStops(lat, lon, NEARBY_VEHICLE_SEARCH_CEILING_M)
                val seen = mutableMapOf<String, VehicleMarker>()
                // With no stops at all, the ceiling IS the answer: the search can honestly
                // say it looked 50 km out and found nowhere to ask.
                var reachedMeters = if (stops.isEmpty()) NEARBY_VEHICLE_SEARCH_CEILING_M else 0
                var queried = 0

                for (round in stops.chunked(NEARBY_VEHICLE_ROUND_STOPS)) {
                    if (queried >= NEARBY_VEHICLE_MAX_STOPS) break
                    // A round is issued concurrently: walking outward multiplies the
                    // requests, and sequentially they would outlast the poll interval.
                    val responses = coroutineScope {
                        round.map { stop ->
                            async {
                                try {
                                    api.getTransport(station = stop.stopCode)
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (_: Exception) {
                                    // One unreachable stop should not blank the others.
                                    null
                                }
                            }
                        }.awaitAll()
                    }
                    for (response in responses.filterNotNull()) {
                        // Harvested from the same response the markers come from — which is
                        // the only place a bus tapped here can have its destination named,
                        // since the arrivals board has never loaded these stops.
                                // One bus is reported by every stop still ahead of it, so it must be
                        // deduplicated or it draws several times.
                        response.extractVehicleMarkers().forEach { marker ->
                            seen.putIfAbsent(marker.vehicleRef, marker)
                        }
                    }

                    queried += round.size
                    reachedMeters = round.last().distanceMeters
                    if (seen.size >= NEARBY_VEHICLE_TARGET_BUSES && reachedMeters >= viewportRadius) break
                }

                val vehicles = seen.values.toList()
                _state.value = _state.value.copy(
                    nearbyVehicles = vehicles,
                    nearbyVehiclesLoaded = true,
                    nearbyVehiclesReachedMeters = reachedMeters,
                    nearbyVehiclesNearestMeters = vehicles.minOfOrNull {
                        distanceMeters(it.lat, it.lon, lat, lon).toInt()
                    } ?: 0
                )
                delay(NEARBY_VEHICLE_INTERVAL_MS)
            }
        }
    }

    fun stopNearbyVehicles() {
        nearbyVehiclesJob?.cancel()
        nearbyVehiclesJob = null
        _state.value = _state.value.copy(
            nearbyVehicles = emptyList(),
            nearbyVehiclesLoaded = false,
            nearbyVehiclesReachedMeters = 0,
            nearbyVehiclesNearestMeters = 0
        )
    }

    suspend fun fetchNearbyStops(lat: Double, lon: Double, radius: Int = 500): List<StopResult> {
        return try {
            api.nearbyStops(lat, lon, radius)
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * The name of a stop the CURRENT board is showing, or "" if it is not known.
     *
     * Only for the arrivals list, which renders visits from the response in [state]. A bus
     * tapped on the map carries its own [VehicleMarker.destinationName] from the server and
     * does not come through here.
     *
     * Returns "" rather than the ref. It ended in `?: destinationRef` once, which printed
     * "30 → 15657" — a stop code in the one position that means "this bus goes there".
     * Blank is honest, and the card drops the arrow with it.
     */
    fun getDestinationName(destinationRef: String?): String {
        if (destinationRef == null) return ""
        return _state.value.siriData?.stopNames?.get(destinationRef) ?: ""
    }

    override fun onCleared() {
        super.onCleared()
        stopPolling()
    }
}
