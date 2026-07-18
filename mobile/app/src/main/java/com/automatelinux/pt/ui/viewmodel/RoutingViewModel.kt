package com.automatelinux.pt.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.automatelinux.pt.data.api.PtApi
import com.automatelinux.pt.data.model.GeocodeSuggestion
import com.automatelinux.pt.data.model.Itinerary
import com.automatelinux.pt.data.model.RouteResult
import com.automatelinux.pt.data.model.RouteSortMode
import com.automatelinux.pt.data.model.VehicleMarker
import com.automatelinux.pt.data.model.extractVehicleMarkers
import dagger.hilt.android.lifecycle.HiltViewModel
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
import javax.inject.Inject

// RoutingState + TrackedBus now live in commonMain (ui/viewmodel/RoutingState.kt).

@HiltViewModel
class RoutingViewModel @Inject constructor(
    private val api: PtApi
) : ViewModel() {

    private val _state = MutableStateFlow(RoutingState())
    val state: StateFlow<RoutingState> = _state.asStateFlow()
    private var trackingJob: Job? = null

    fun setOrigin(suggestion: GeocodeSuggestion?) {
        _state.value = _state.value.copy(origin = suggestion, results = null, error = null)
        clearDayOverview()
    }

    fun setDestination(suggestion: GeocodeSuggestion?) {
        _state.value = _state.value.copy(destination = suggestion, results = null, error = null)
        clearDayOverview()
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
    }

    fun search() {
        val s = _state.value
        val origin = s.origin ?: return
        val destination = s.destination ?: return

        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            try {
                val from = "${origin.lat},${origin.lon}"
                val to = "${destination.lat},${destination.lon}"
                // Instant.toString() is ISO-8601 (UTC "Z" form); same instant the API expects.
                val time = s.departureTime?.toString()
                val arriveBy = if (s.arriveBy) true else null

                val result = api.searchRoute(from = from, to = to, time = time, arriveBy = arriveBy)
                _state.value = _state.value.copy(
                    results = result,
                    selectedIndex = 0,
                    travelMode = TravelMode.TRANSIT,
                    loading = false
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    loading = false,
                    error = e.message ?: "Route search failed"
                )
            }
        }
    }

    fun trackBusOnLeg(legIndex: Int, lat: Double, lon: Double, lineName: String) {
        stopTracking()
        _state.value = _state.value.copy(
            trackedBus = TrackedBus(legIndex = legIndex, lineName = lineName, marker = null)
        )
        trackingJob = viewModelScope.launch {
            val stops = try { api.nearbyStops(lat, lon, 300) } catch (_: Exception) { emptyList() }
            val stationCode = stops.firstOrNull()?.stopCode
            if (stationCode == null) {
                // No SIRI-monitorable stop near the boarding point — clear the tracking
                // state so the UI doesn't show "Tracking..." forever.
                _state.value = _state.value.copy(trackedBus = null)
                return@launch
            }
            while (true) {
                try {
                    val response = api.getTransport(station = stationCode, line = lineName)
                    val markers = response.extractVehicleMarkers()
                    val best = markers.filter {
                        it.lineNumber.equals(lineName, ignoreCase = true)
                    }.minByOrNull { it.distanceFromStop }
                    _state.value = _state.value.copy(
                        trackedBus = _state.value.trackedBus?.copy(marker = best)
                    )
                } catch (_: Exception) {}
                delay(10_000)
            }
        }
    }

    fun stopTracking() {
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

    suspend fun geocode(text: String): List<GeocodeSuggestion> {
        return try {
            api.geocode(text)
        } catch (_: Exception) {
            emptyList()
        }
    }
}
