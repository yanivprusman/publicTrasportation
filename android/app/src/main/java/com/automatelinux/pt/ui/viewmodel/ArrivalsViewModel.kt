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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ArrivalsState(
    val stationCode: String = "26472",
    val stationName: String = "",
    val siriData: SiriResponse? = null,
    val vehicleMarkers: List<VehicleMarker> = emptyList(),
    val showVehicleMarkers: Boolean = true,
    val lineFilter: String = "",
    val lastUpdated: Long? = null,
    val error: String? = null,
    val loading: Boolean = false
) {
    val visits: List<MonitoredStopVisit>
        get() {
            val allVisits = siriData?.siri?.serviceDelivery?.stopMonitoringDelivery
                ?.flatMap { it.monitoredStopVisit ?: emptyList() } ?: emptyList()

            return if (lineFilter.isBlank()) allVisits
            else allVisits.filter {
                it.monitoredVehicleJourney?.publishedLineName?.contains(lineFilter, ignoreCase = true) == true
            }
        }
}

@HiltViewModel
class ArrivalsViewModel @Inject constructor(
    private val api: PtApi
) : ViewModel() {

    private val _state = MutableStateFlow(ArrivalsState())
    val state: StateFlow<ArrivalsState> = _state.asStateFlow()

    private var pollingJob: Job? = null

    fun setStationCode(code: String, name: String = "") {
        _state.value = _state.value.copy(
            stationCode = code,
            stationName = name,
            siriData = null,
            error = null
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
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            try {
                val response = api.getTransport(station = _state.value.stationCode)
                _state.value = _state.value.copy(
                    siriData = response,
                    vehicleMarkers = response.extractVehicleMarkers(),
                    lastUpdated = System.currentTimeMillis(),
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

    fun getDestinationName(destinationRef: String?): String {
        if (destinationRef == null) return ""
        return _state.value.siriData?.stopNames?.get(destinationRef) ?: destinationRef
    }

    override fun onCleared() {
        super.onCleared()
        stopPolling()
    }
}
