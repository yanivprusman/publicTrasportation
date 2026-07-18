package com.automatelinux.pt.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.automatelinux.pt.data.api.PtApi
import com.automatelinux.pt.data.model.MonitoredStopVisit
import com.automatelinux.pt.data.model.SiriResponse
import com.automatelinux.pt.data.model.StopResult
import com.automatelinux.pt.data.model.VehicleMarker
import com.automatelinux.pt.data.model.extractVehicleMarkers
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import javax.inject.Inject
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sqrt

// ArrivalsState now lives in commonMain (ui/viewmodel/ArrivalsState.kt).

private const val TIMETABLE_TTL_MS = 5 * 60_000L

@HiltViewModel
class ArrivalsViewModel @Inject constructor(
    private val api: PtApi
) : ViewModel() {

    private val _state = MutableStateFlow(ArrivalsState())
    val state: StateFlow<ArrivalsState> = _state.asStateFlow()

    private var pollingJob: Job? = null
    private var timetableJob: Job? = null

    fun setStationCode(code: String, name: String = "") {
        timetableJob?.cancel()
        _state.value = _state.value.copy(
            stationCode = code,
            stationName = name,
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
            _state.value = _state.value.copy(loading = true, error = null)
            try {
                val response = api.getTransport(station = _state.value.stationCode)
                _state.value = _state.value.copy(
                    siriData = response,
                    vehicleMarkers = response.extractVehicleMarkers(),
                    lastUpdated = Clock.System.now().toEpochMilliseconds(),
                    loading = false
                )
            } catch (e: Exception) {
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

    private fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = (lat1 - lat2) * 111_320.0
        val dLon = (lon1 - lon2) * 111_320.0 * cos(lat2 * PI / 180.0)
        return sqrt(dLat * dLat + dLon * dLon)
    }

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

    suspend fun fetchNearbyStops(lat: Double, lon: Double, radius: Int = 500): List<StopResult> {
        return try {
            api.nearbyStops(lat, lon, radius)
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun getDestinationName(destinationRef: String?): String {
        if (destinationRef == null) return ""
        return _state.value.siriData?.stopNames?.get(destinationRef) ?: destinationRef
    }

    override fun onCleared() {
        super.onCleared()
        stopPolling()
    }
}
