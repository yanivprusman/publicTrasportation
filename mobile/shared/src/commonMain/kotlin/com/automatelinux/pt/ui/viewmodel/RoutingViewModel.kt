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
        syncNearbyBoard()
    }

    fun setDestination(suggestion: GeocodeSuggestion?) {
        val previous = _state.value.destination
        _state.value = _state.value.copy(destination = suggestion, results = null, error = null)
        clearDayOverview()
        if (!sameCoords(previous, suggestion)) autoSearchIfReady()
        syncNearbyBoard()
    }

    /**
     * The nearby board exists exactly while there is a place to stand and nowhere to go.
     * Both endpoints drive it, so it appears on the first GPS fix and gets out of the
     * way the moment a destination is chosen — without either caller knowing it exists.
     */
    private fun syncNearbyBoard() {
        val s = _state.value
        val origin = s.origin
        if (s.destination != null || origin == null) {
            clearNearbyBoard()
            return
        }
        // Same stop as last time means the board already running is the right one;
        // restarting it would blank a card the user is reading.
        val current = s.nearbyBoard
        if (current != null && nearbyBoardOrigin == origin.lat to origin.lon) return
        nearbyBoardOrigin = origin.lat to origin.lon
        refreshNearbyBoard(origin.lat, origin.lon)
    }

    private var nearbyBoardOrigin: Pair<Double, Double>? = null

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

        /**
         * How far from the timetable a sighting may be and still be THIS run.
         *
         * An hour is generous for a late bus and still tight enough that the next
         * scheduled run of the same line — rarely under an hour apart on the routes
         * this app plans — cannot be mistaken for it.
         */
        private val LIVE_MATCH_WINDOW = 60.minutes

        /** The feed updates about every 30s; a minute keeps the card fresh cheaply. */
        private const val LIVE_REFRESH_MS = 60_000L

        /** Far enough to find a pole across the road, near enough to be YOUR stop. */
        private const val NEARBY_BOARD_RADIUS_M = 400

        /** Six rows fit above the fold; the arrivals board holds the long tail. */
        private const val NEARBY_BOARD_ROWS = 12

        private const val NEARBY_BOARD_REFRESH_MS = 45_000L

        private fun restoreModes(store: SettingsStore): Set<TransitFilter> {
            val saved = store.routeModes
            val restored = TransitFilter.entries.filter { it.apiKey in saved }.toSet()
            return restored.ifEmpty { TransitFilter.entries.toSet() }
        }
    }

    // Filter chips can re-fire search while one is in flight; only the newest
    // request may write results, or a slow stale response would overwrite them.
    private var searchSeq = 0

    /** One ride to look up live: which pole, which direction, which timetabled minute. */
    private data class LiveRideKey(
        val stopCode: String,
        val routeId: String,
        val line: String,
        val scheduled: String
    )

    private var nearbyBoardJob: Job? = null

    /**
     * Fill the zero-input board: what is leaving the nearest stop, right now.
     *
     * Runs only while there is nothing else to show — the moment a destination is
     * chosen, the planner's own results are the answer and this stops. Live sightings
     * and the timetable are merged by the same [buildDepartures] the arrivals board
     * uses, so a stop shows one list rather than two competing ones.
     */
    fun refreshNearbyBoard(lat: Double, lon: Double) {
        nearbyBoardJob?.cancel()
        nearbyBoardJob = viewModelScope.launch {
            val stop = try {
                api.nearbyStops(lat, lon, NEARBY_BOARD_RADIUS_M).firstOrNull()
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    nearbyBoard = NearbyBoard(
                        stopCode = "", stopName = "", distanceMeters = 0,
                        loading = false, error = e.message ?: "nearby stops failed"
                    )
                )
                return@launch
            }
            if (stop == null) {
                // No monitorable stop within range is an answer, not a failure.
                _state.value = _state.value.copy(nearbyBoard = null)
                return@launch
            }

            _state.value = _state.value.copy(
                nearbyBoard = NearbyBoard(
                    stopCode = stop.stopCode,
                    stopName = stop.stopName,
                    distanceMeters = stop.distanceMeters,
                    loading = true
                )
            )

            while (true) {
                val siri = try {
                    api.getTransport(station = stop.stopCode)
                } catch (_: Exception) {
                    null
                }
                val visits = siri?.siri?.serviceDelivery?.stopMonitoringDelivery
                    ?.flatMap { it.monitoredStopVisit ?: emptyList() }
                    ?: emptyList()
                // The timetable is keyed by MOTIS's stop id, not the code on the pole;
                // passing the code answers 404 and the board silently loses every
                // scheduled row, leaving only whatever happens to be reporting live.
                val timetable = if (stop.id.isBlank()) emptyList() else try {
                    api.getStoptimes(stop.id, NEARBY_BOARD_ROWS).stopTimes
                } catch (_: Exception) {
                    emptyList()
                }

                val departures = buildDepartures(visits, timetable, "", Clock.System.now())
                _state.value = _state.value.copy(
                    // An empty board is not an error: at 3am the stop really has
                    // nothing, and the card says that in words rather than blaming
                    // the network for it.
                    nearbyBoard = _state.value.nearbyBoard?.copy(
                        departures = departures,
                        stopNames = siri?.stopNames ?: _state.value.nearbyBoard?.stopNames.orEmpty(),
                        loading = false,
                        error = null
                    )
                )
                delay(NEARBY_BOARD_REFRESH_MS)
            }
        }
    }

    fun clearNearbyBoard() {
        nearbyBoardJob?.cancel()
        nearbyBoardJob = null
        if (_state.value.nearbyBoard != null) {
            _state.value = _state.value.copy(nearbyBoard = null)
        }
    }

    private var liveBoardingJob: Job? = null

    /**
     * Ask the operator feed what is actually coming, for the rides on screen.
     *
     * One request per boarding stop, not per card: a result set for one journey almost
     * always boards at the same pole, so this is normally a single call refreshed on a
     * minute. Only the FIRST ride of each itinerary is matched — that is the one the
     * countdown is about and the one you can miss; a connection an hour out has no
     * useful live answer yet.
     */
    private fun startLiveBoardingPolling(seq: Int, itineraries: List<Itinerary>) {
        liveBoardingJob?.cancel()

        // routeId, not the line name: SIRI reports LineRef, which is the GTFS route
        // id, and "64" leaves this stop under two of them — one per direction. A ride
        // without a route id gets no live mark rather than a possibly opposite bus.
        val rides = itineraries.mapNotNull { itinerary ->
            val ride = itinerary.firstRide ?: return@mapNotNull null
            val stop = ride.fromStopCode ?: return@mapNotNull null
            val route = ride.routeId ?: return@mapNotNull null
            val line = ride.routeShortName ?: return@mapNotNull null
            LiveRideKey(stop, route, line, ride.startTime)
        }
        if (rides.isEmpty()) return

        // More than a handful of distinct poles means a result set spread over a city;
        // asking about all of them would cost more than the answer is worth.
        val stops = rides.map { it.stopCode }.distinct().take(3)

        liveBoardingJob = viewModelScope.launch {
            while (true) {
                val found = mutableMapOf<String, LiveBoarding>()
                for (stop in stops) {
                    val visits = try {
                        api.getTransport(station = stop)
                            .siri?.serviceDelivery?.stopMonitoringDelivery
                            ?.flatMap { it.monitoredStopVisit ?: emptyList() }
                            ?: emptyList()
                    } catch (_: Exception) {
                        // A feed that is down says nothing; the cards keep their
                        // timetable mark, which is the honest thing to show.
                        continue
                    }

                    for ((rideStop, route, line, scheduled) in rides.filter { it.stopCode == stop }) {
                        val scheduledAt = try {
                            Instant.parse(scheduled)
                        } catch (_: Exception) {
                            continue
                        }
                        val best = visits.asSequence()
                            .filter { it.monitoredVehicleJourney?.lineRef == route }
                            .mapNotNull { visit ->
                                val journey = visit.monitoredVehicleJourney
                                val expectedRaw = journey?.monitoredCall?.expectedArrivalTime
                                    ?: return@mapNotNull null
                                val expected = try {
                                    Instant.parse(expectedRaw)
                                } catch (_: Exception) {
                                    return@mapNotNull null
                                }
                                Triple(expectedRaw, expected, journey.vehicleRef)
                            }
                            // A vehicle reporting an hour either side of the timetable is
                            // a different run of the same line, not this one running late.
                            .filter {
                                (it.second - scheduledAt).absoluteValue <= LIVE_MATCH_WINDOW
                            }
                            .minByOrNull { (it.second - scheduledAt).absoluteValue }

                        if (best != null) {
                            found[liveBoardingKey(rideStop, line, scheduled)] = LiveBoarding(
                                expected = best.first,
                                deltaSeconds = (best.second - scheduledAt).inWholeSeconds,
                                vehicleRef = best.third
                            )
                        }
                    }
                }

                if (seq != searchSeq) return@launch
                _state.value = _state.value.copy(liveBoardings = found)
                delay(LIVE_REFRESH_MS)
            }
        }
    }

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
                // A stitched via trip has no name at the seam between its two halves —
                // the server clears MOTIS's "START"/"END" sentinels there. Only this
                // side knows what the rider picked, so it fills the name back in.
                val result = if (viaPlace != null) renameViaBoundaries(raw, viaPlace.name) else raw
                if (seq != searchSeq) return@launch
                _state.value = _state.value.copy(
                    results = result,
                    liveBoardings = emptyMap(),
                    selectedIndex = 0,
                    travelMode = TravelMode.TRANSIT,
                    loading = false
                )
                startLiveBoardingPolling(seq, result.itineraries)
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
                // The trip's own two ends stay nameless: they are the rider's pins,
                // and only the seam in the middle is the via place.
                if (i > 0 && renamed.from.name.isBlank()) {
                    renamed = renamed.copy(from = renamed.from.copy(name = viaName))
                }
                if (i < itin.legs.lastIndex && renamed.to.name.isBlank()) {
                    renamed = renamed.copy(to = renamed.to.copy(name = viaName))
                }
                renamed
            })
        })

    private var nextDeparturesJob: Job? = null

    /**
     * For the itinerary the rider has open: the same line's departures AFTER the
     * one this trip rides, per ride leg — the answer to "and if I miss it?".
     *
     * Resolved through the timetable stop nearest each boarding point, filtered
     * to the leg's published line. A leg whose stop or line cannot be resolved
     * simply contributes nothing — the row shows no "also at" rather than times
     * that might belong to another pole.
     */
    fun loadNextDepartures(itinerary: Itinerary) {
        nextDeparturesJob?.cancel()
        _state.value = _state.value.copy(nextDepartures = emptyMap())
        nextDeparturesJob = viewModelScope.launch {
            val found = mutableMapOf<Int, List<String>>()
            itinerary.legs.forEachIndexed { index, leg ->
                val line = leg.routeShortName?.takeIf {
                    it.isNotBlank() && leg.mode != com.automatelinux.pt.data.model.TransitMode.WALK
                } ?: return@forEachIndexed
                val ridden = runCatching { Instant.parse(leg.startTime) }.getOrNull()
                    ?: return@forEachIndexed
                try {
                    val stop = api.nearbyStops(leg.from.lat, leg.from.lon, 150)
                        .firstOrNull { it.id.isNotBlank() } ?: return@forEachIndexed
                    val later = api.getStoptimes(stop.id, 40).stopTimes
                        .filter { it.routeShortName.equals(line, ignoreCase = true) }
                        .mapNotNull { entry ->
                            (entry.place.departure ?: entry.place.scheduledDeparture)
                                ?.let { iso -> runCatching { Instant.parse(iso) }.getOrNull() }
                        }
                        .filter { it > ridden }
                        .sorted()
                        .take(2)
                        .map { com.automatelinux.pt.util.formatTime(it.toString()) }
                    if (later.isNotEmpty()) found[index] = later
                } catch (_: Exception) {
                    // This leg's "also at" is a nicety; the trip stands without it.
                }
            }
            _state.value = _state.value.copy(nextDepartures = found)
        }
    }

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
            val monitoredStop = stops.firstOrNull()
            val stationCode = monitoredStop?.stopCode
            if (stationCode == null) {
                updateTracking(seq) { it.copy(status = TrackingStatus.NO_MONITORED_STOP) }
                return@launch
            }
            // Kept so polling can be resumed after a pause without re-resolving it.
            updateTracking(seq) {
                it.copy(stationCode = stationCode, stationName = monitoredStop.stopName)
            }
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
                    updateTracking(seq) {
                        it.copy(stopLat = stop.lat, stopLon = stop.lon, stationName = stop.stopName)
                    }
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
    //
    // Deliberately does NOT catch: an unreachable server (or one answering with a
    // sign-in page instead of JSON) used to arrive at the user as "Nothing found —
    // try a different spelling", which blames them for an outage. The field that
    // ran the search decides what to show; this reports what happened.
    suspend fun geocode(text: String, near: String? = null): List<GeocodeSuggestion> =
        api.geocode(text, near)
}
