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
import kotlinx.datetime.Instant
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
    }

    fun setDestination(suggestion: GeocodeSuggestion?) {
        _state.value = _state.value.copy(destination = suggestion, results = null, error = null)
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
    }

    fun setOriginFromCoords(lat: Double, lon: Double, name: String? = null) {
        val displayName = name ?: MAP_LOCATION_LABEL
        setOrigin(GeocodeSuggestion(name = displayName, lat = lat, lon = lon))
        if (name == null) resolveAddress(lat, lon) { setOrigin(it) }
    }

    fun setDestinationFromCoords(lat: Double, lon: Double, name: String? = null) {
        val displayName = name ?: MAP_LOCATION_LABEL
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
            val stationCode = stops.firstOrNull()?.stopCode ?: return@launch
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
