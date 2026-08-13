package com.automatelinux.pt.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.automatelinux.pt.data.api.PtApi
import com.automatelinux.pt.data.model.GeocodeSuggestion
import com.automatelinux.pt.data.model.Itinerary
import com.automatelinux.pt.data.model.RouteResult
import com.automatelinux.pt.data.model.RouteSortMode
import com.automatelinux.pt.data.model.VehicleMarker
import com.automatelinux.pt.data.model.WheelchairAccess
import com.automatelinux.pt.data.model.extractVehicleMarkers
import com.automatelinux.pt.util.SettingsStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.minutes

// RoutingState + TrackedBus now live in commonMain (ui/viewmodel/RoutingState.kt).

class RoutingViewModel(
    private val api: PtApi,
    private val settingsStore: SettingsStore
) : ViewModel() {

    private val _state = MutableStateFlow(RoutingState(
        enabledModes = restoreModes(settingsStore),
        maxWalkMinutes = settingsStore.maxWalkMinutes.takeIf { it in 1..60 }
    ))
    val state: StateFlow<RoutingState> = _state.asStateFlow()
    private var trackingJob: Job? = null
    private var trackSeq = 0

    fun toggleModeFilter(filter: TransitFilter) {
        val current = _state.value.enabledModes
        // The last enabled mode can't be turned off — an all-off filter means "no routes".
        if (current == setOf(filter)) return
        val updated = if (filter in current) current - filter else current + filter
        settingsStore.routeModes = updated.map { it.apiKey }.toSet()
        _state.value = _state.value.copy(enabledModes = updated)
        researchIfSearched()
    }

    fun setMaxWalk(minutes: Int?) {
        settingsStore.maxWalkMinutes = minutes ?: 0
        _state.value = _state.value.copy(maxWalkMinutes = minutes)
        researchIfSearched()
    }

    private fun researchIfSearched() {
        val s = _state.value
        if (s.origin != null && s.destination != null && (s.results != null || s.loading)) search()
    }

    fun setOrigin(suggestion: GeocodeSuggestion?) {
        val previous = _state.value.origin
        _state.value = _state.value.copy(origin = suggestion, results = null, error = null)
        clearDayOverview()
        if (!sameCoords(previous, suggestion)) autoSearchIfReady()
    }

    fun setDestination(suggestion: GeocodeSuggestion?) {
        val previous = _state.value.destination
        _state.value = _state.value.copy(destination = suggestion, results = null, error = null)
        clearDayOverview()
        if (!sameCoords(previous, suggestion)) autoSearchIfReady()
    }

    // Picking the second endpoint IS the request — nobody fills From and To and then
    // wants a blank screen. The button stays for re-running after time/filter edits.
    // Address resolution replaces a placeholder name at identical coords; that must
    // not re-fire the search, hence the sameCoords gate at each entry point.
    private fun sameCoords(a: GeocodeSuggestion?, b: GeocodeSuggestion?): Boolean =
        a != null && b != null && a.lat == b.lat && a.lon == b.lon

    private fun autoSearchIfReady() {
        val s = _state.value
        if (s.origin != null && s.destination != null) search()
    }

    fun showViaField() {
        _state.value = _state.value.copy(viaFieldVisible = true)
    }

    fun setVia(suggestion: GeocodeSuggestion?) {
        val previous = _state.value.via
        _state.value = _state.value.copy(via = suggestion, results = null, error = null)
        clearDayOverview()
        if (!sameCoords(previous, suggestion)) autoSearchIfReady()
    }

    fun removeVia() {
        val s = _state.value
        if (s.via == null) {
            // Field was shown but never filled — just hide it, keep any results.
            _state.value = s.copy(viaFieldVisible = false)
            return
        }
        _state.value = s.copy(via = null, viaFieldVisible = false, results = null, error = null)
        clearDayOverview()
        autoSearchIfReady()
    }

    fun setDepartureTime(time: Instant?) {
        _state.value = _state.value.copy(departureTime = time)
    }

    fun setArriveBy(arriveBy: Boolean) {
        _state.value = _state.value.copy(arriveBy = arriveBy)
    }

    fun selectItinerary(index: Int) {
        _state.value = _state.value.copy(selectedIndex = index)
    }

    fun setSortMode(mode: RouteSortMode) {
        _state.value = _state.value.copy(sortMode = mode, selectedIndex = 0)
    }

    fun setTravelMode(mode: TravelMode) {
        _state.value = _state.value.copy(travelMode = mode)
    }

    fun searchEarlier() {
        val s = _state.value
        val earliest = s.results?.itineraries?.minByOrNull { it.startTime }?.startTime
        if (earliest != null) {
            try {
                val t = Instant.parse(earliest).minus(30.minutes)
                _state.value = _state.value.copy(departureTime = t, arriveBy = false)
                search()
            } catch (_: Exception) { search() }
        } else {
            val t = (s.departureTime ?: Clock.System.now()).minus(30.minutes)
            _state.value = _state.value.copy(departureTime = t, arriveBy = false)
            search()
        }
    }

    fun searchLater() {
        val s = _state.value
        val latest = s.results?.itineraries?.maxByOrNull { it.startTime }?.startTime
        if (latest != null) {
            try {
                val t = Instant.parse(latest).plus(5.minutes)
                _state.value = _state.value.copy(departureTime = t, arriveBy = false)
                search()
            } catch (_: Exception) { search() }
        } else {
            val t = (s.departureTime ?: Clock.System.now()).plus(30.minutes)
            _state.value = _state.value.copy(departureTime = t, arriveBy = false)
            search()
        }
    }

    fun swapOriginDestination() {
        val s = _state.value
        _state.value = s.copy(
            origin = s.destination,
            destination = s.origin,
            results = null,
            error = null
        )
        clearDayOverview()
        autoSearchIfReady()
    }

    private fun clearDayOverview() {
        _state.value = _state.value.copy(
            showDayOverview = false,
            dayOverview = null,
            dayError = null,
            selectedDayIndex = null
        )
    }

    fun toggleDayOverview() {
        val s = _state.value
        if (s.showDayOverview) {
            _state.value = s.copy(showDayOverview = false)
            return
        }
        _state.value = s.copy(showDayOverview = true)
        if (s.dayOverview == null && !s.dayLoading) loadDayOverview()
    }

    fun loadDayOverview() {
        val s = _state.value
        val origin = s.origin ?: return
        val destination = s.destination ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(
                dayLoading = true, dayError = null, dayOverview = null, selectedDayIndex = null
            )
            try {
                // The service day: today 04:00 to 02:00 tomorrow, local time. Buses
                // leaving after midnight belong to today's chart, not tomorrow's.
                val tz = TimeZone.currentSystemDefault()
                val today = Clock.System.now().toLocalDateTime(tz).date
                val start = today.atTime(4, 0).toInstant(tz)
                val end = today.plus(1, DateTimeUnit.DAY).atTime(2, 0).toInstant(tz)
                val result = api.dayOverview(
                    from = "${origin.lat},${origin.lon}",
                    to = "${destination.lat},${destination.lon}",
                    start = start.toString(),
                    end = end.toString()
                )
                _state.value = _state.value.copy(dayOverview = result, dayLoading = false)
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    dayLoading = false,
                    dayError = e.message ?: "Day overview failed"
                )
            }
        }
    }

    fun selectDayDeparture(index: Int?) {
        _state.value = _state.value.copy(selectedDayIndex = index)
    }

    fun pickDayDeparture(startTimeIso: String) {
        // Search one minute before the chosen departure so MOTIS includes it
        // in the results rather than starting from the departure after it.
        val t = try { Instant.parse(startTimeIso).minus(1.minutes) } catch (_: Exception) { return }
        _state.value = _state.value.copy(
            departureTime = t,
            arriveBy = false,
            showDayOverview = false
        )
        search()
    }

    fun setOriginFromCoords(lat: Double, lon: Double, name: String? = null, placeholder: String = MAP_LOCATION_LABEL) {
        val displayName = name ?: placeholder
        setOrigin(GeocodeSuggestion(name = displayName, lat = lat, lon = lon))
        if (name == null) resolveAddress(lat, lon) { setOrigin(it) }
    }

    fun setDestinationFromCoords(lat: Double, lon: Double, name: String? = null, placeholder: String = MAP_LOCATION_LABEL) {
        val displayName = name ?: placeholder
        setDestination(GeocodeSuggestion(name = displayName, lat = lat, lon = lon))
        if (name == null) resolveAddress(lat, lon) { setDestination(it) }
    }

    private fun resolveAddress(lat: Double, lon: Double, apply: (GeocodeSuggestion) -> Unit) {
        viewModelScope.launch {
            try {
                val results = api.reverseGeocode(lat, lon)
                val resolved = results.firstOrNull() ?: return@launch
                apply(GeocodeSuggestion(name = resolved.name, lat = lat, lon = lon))
            } catch (_: Exception) { }
        }
    }

    companion object {
        private const val MAP_LOCATION_LABEL = "Selected location"

        private fun restoreModes(store: SettingsStore): Set<TransitFilter> {
            val saved = store.routeModes
            val restored = TransitFilter.entries.filter { it.apiKey in saved }.toSet()
            return restored.ifEmpty { TransitFilter.entries.toSet() }
        }
    }

    // Filter chips can re-fire search while one is in flight; only the newest
    // request may write results, or a slow stale response would overwrite them.
    private var searchSeq = 0

    fun search() {
        val s = _state.value
        val origin = s.origin ?: return
        val destination = s.destination ?: return
        val seq = ++searchSeq

        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            try {
                val from = "${origin.lat},${origin.lon}"
                val to = "${destination.lat},${destination.lon}"
                // Instant.toString() is ISO-8601 (UTC "Z" form); same instant the API expects.
                val time = s.departureTime?.toString()
                val arriveBy = if (s.arriveBy) true else null
                // Omit ?modes= when every group is enabled — that is the backend default
                // and keeps the unfiltered search on the warm server-side cache key.
                val modes = if (s.enabledModes.size == TransitFilter.entries.size) null
                else s.enabledModes.map { it.apiKey }.sorted().joinToString(",")

                val viaPlace = s.via
                val via = viaPlace?.let { "${it.lat},${it.lon}" }

                val raw = api.searchRoute(
                    from = from, to = to, via = via, time = time, arriveBy = arriveBy,
                    modes = modes, maxWalk = s.maxWalkMinutes
                )
                // Stitched via trips carry MOTIS's literal "END"/"START" place names at
                // the seam between the two halves; show the chosen stop's name instead.
                val result = if (viaPlace != null) renameViaBoundaries(raw, viaPlace.name) else raw
                if (seq != searchSeq) return@launch
                _state.value = _state.value.copy(
                    results = result,
                    selectedIndex = 0,
                    travelMode = TravelMode.TRANSIT,
                    loading = false
                )
            } catch (e: Exception) {
                if (seq != searchSeq) return@launch
                _state.value = _state.value.copy(
                    loading = false,
                    error = e.message ?: "Route search failed"
                )
            }
        }
    }

    private fun renameViaBoundaries(result: RouteResult, viaName: String): RouteResult =
        result.copy(itineraries = result.itineraries.map { itin ->
            itin.copy(legs = itin.legs.mapIndexed { i, leg ->
                var renamed = leg
                if (i > 0 && renamed.from.name == "START") {
                    renamed = renamed.copy(from = renamed.from.copy(name = viaName))
                }
                if (i < itin.legs.lastIndex && renamed.to.name == "END") {
                    renamed = renamed.copy(to = renamed.to.copy(name = viaName))
                }
                renamed
            })
        })

    fun trackBusOnLeg(
        legIndex: Int,
        lat: Double,
        lon: Double,
        lineName: String,
        access: WheelchairAccess = WheelchairAccess.UNKNOWN,
        destination: String = "",
        tripId: String? = null,
        scheduledStart: String? = null
    ) {
        stopTracking()
        val seq = ++trackSeq
        _state.value = _state.value.copy(
            trackedBus = TrackedBus(
                legIndex = legIndex,
                scheduledStart = scheduledStart,
                lineName = lineName,
                access = access,
                destination = destination,
                stopLat = lat,
                stopLon = lon
            )
        )
        trackingJob = viewModelScope.launch {
            // Geometry comes from the trip, never the line number: /api/line-shape
            // resolves "60" to Tel Aviv's and Haifa's line 60 as readily as the
            // Negev's. Static for the trip, so fetched once alongside the polling.
            if (tripId != null) {
                launch {
                    val points = try {
                        api.getTripShape(tripId).points
                    } catch (_: Exception) {
                        emptyList()
                    }
                    if (points.isNotEmpty()) updateTracking(seq) { it.copy(shape = points) }
                }
            }
            val stops = try { api.nearbyStops(lat, lon, 300) } catch (_: Exception) { emptyList() }
            val stationCode = stops.firstOrNull()?.stopCode
            if (stationCode == null) {
                updateTracking(seq) { it.copy(status = TrackingStatus.NO_MONITORED_STOP) }
                return@launch
            }
            // Kept so polling can be resumed after a pause without re-resolving it.
            updateTracking(seq) { it.copy(stationCode = stationCode) }
            pollTrackedBus(seq, stationCode, lineName)
        }
    }

    private suspend fun pollTrackedBus(seq: Int, stationCode: String, lineName: String) {
        while (true) {
            try {
                // Deliberately unfiltered: the API's LineRef is the operator's internal
                // id (line 64 is LineRef 11057), so passing the published name matched
                // nothing and tracking could never find a bus. PublishedLineName is
                // what riders call the line, and it is filtered for below.
                val response = api.getTransport(station = stationCode)
                // Soonest to arrive first, because index 0 is the bus the card follows by
                // default and "my bus" is the next one in.
                //
                // This sorted by DistanceFromStop, which sounds like "nearest first" and
                // is not: the field counts UP as a vehicle drives, so it is trip progress,
                // and the smallest value belongs to whichever bus set off most recently —
                // on a long line, the one furthest away. Tracking could open on a bus an
                // hour behind the one the user was waiting for.
                val candidates = response.extractVehicleMarkers()
                    .filter { it.lineNumber.equals(lineName, ignoreCase = true) }
                    .sortedBy { marker ->
                        runCatching { Instant.parse(marker.expectedArrival) }.getOrNull()
                            ?: Instant.DISTANT_FUTURE
                    }
                updateTracking(seq) { tracked ->
                    tracked.copy(
                        status = if (candidates.isEmpty()) {
                            TrackingStatus.NO_VEHICLE
                        } else {
                            TrackingStatus.LIVE
                        },
                        candidates = candidates,
                        // Buses reorder as they move, so the chosen one is followed by
                        // its ref. Following the index would silently swap the card onto
                        // a different bus the moment two of them cross.
                        selectedIndex = candidates
                            .indexOfFirst {
                                it.vehicleRef == (tracked.marker?.vehicleRef
                                    ?: tracked.preferredVehicleRef)
                            }
                            .takeIf { it >= 0 } ?: 0
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                updateTracking(seq) { it.copy(status = TrackingStatus.ERROR) }
            }
            delay(10_000)
        }
    }

    /**
     * Stops the network polling while keeping what is being tracked.
     *
     * A live position is only worth fetching while somebody can see it. Left
     * running, this hit the operator's feed every 10 seconds with the screen off
     * for as long as the app stayed in memory.
     */
    fun pauseTracking() {
        trackingJob?.cancel()
        trackingJob = null
    }

    /** Resumes polling for the bus already in [RoutingState.trackedBus]. */
    fun resumeTracking() {
        if (trackingJob != null) return
        val tracked = _state.value.trackedBus ?: return
        val stationCode = tracked.stationCode ?: return
        val seq = trackSeq
        trackingJob = viewModelScope.launch {
            pollTrackedBus(seq, stationCode, tracked.lineName)
        }
    }

    /**
     * Track a bus from a stop you are standing at, with no journey planned.
     *
     * The itinerary path exists because tracking grew out of the route planner,
     * but "where is the bus coming to my stop" is the more common question and
     * had no door of its own. Everything needed is already in the arrival:
     * the monitored stop, the line, the route (SIRI LineRef = GTFS route_id) and
     * the headsign.
     */
    fun trackBusAtStop(
        stationCode: String,
        lineName: String,
        destination: String = "",
        routeId: String? = null,
        vehicleRef: String? = null
    ) {
        stopTracking()
        val seq = ++trackSeq
        _state.value = _state.value.copy(
            trackedBus = TrackedBus(
                // No leg to point back at; the itinerary list uses this to mark
                // which leg is being tracked and -1 simply matches none of them.
                legIndex = -1,
                lineName = lineName,
                destination = destination,
                stationCode = stationCode
            )
        )
        trackingJob = viewModelScope.launch {
            if (routeId != null) {
                launch {
                    val points = try {
                        api.getRouteShape(routeId).points
                    } catch (_: Exception) {
                        emptyList()
                    }
                    if (points.isNotEmpty()) updateTracking(seq) { it.copy(shape = points) }
                }
            }
            // The stop's own coordinates, so the camera can frame it with the bus.
            launch {
                val stop = try {
                    api.searchStops(stationCode).firstOrNull { it.stopCode == stationCode }
                } catch (_: Exception) {
                    null
                }
                if (stop != null) {
                    updateTracking(seq) { it.copy(stopLat = stop.lat, stopLon = stop.lon) }
                }
            }
            // Follow the vehicle the user actually tapped, not whichever is nearest.
            if (vehicleRef != null) {
                updateTracking(seq) { it.copy(preferredVehicleRef = vehicleRef) }
            }
            pollTrackedBus(seq, stationCode, lineName)
        }
    }

    /** Follow a different one of the vehicles the feed reports for this line. */
    fun selectTrackedVehicle(index: Int) {
        updateTracking(trackSeq) { tracked ->
            if (index in tracked.candidates.indices) tracked.copy(selectedIndex = index) else tracked
        }
    }

    /** Applies [transform] only while [seq] is still the live tracking session. */
    private fun updateTracking(seq: Int, transform: (TrackedBus) -> TrackedBus) {
        if (seq != trackSeq) return
        val tracked = _state.value.trackedBus ?: return
        _state.value = _state.value.copy(trackedBus = transform(tracked))
    }

    fun stopTracking() {
        trackSeq++
        trackingJob?.cancel()
        trackingJob = null
        _state.value = _state.value.copy(trackedBus = null)
    }

    fun debugFill(autoSearch: Boolean = true, origin: GeocodeSuggestion, destination: GeocodeSuggestion) {
        _state.value = _state.value.copy(origin = origin, destination = destination)
        if (autoSearch) {
            search()
        }
    }

    suspend fun getLineShape(line: String): Map<String, List<List<Double>>> {
        return api.getLineShape(line)
    }

    // near = "lat,lon" viewport bias: the same words mean the nearer place.
    suspend fun geocode(text: String, near: String? = null): List<GeocodeSuggestion> {
        return try {
            api.geocode(text, near)
        } catch (_: Exception) {
            emptyList()
        }
    }
}
