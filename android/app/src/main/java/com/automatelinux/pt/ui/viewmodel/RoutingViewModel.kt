package com.automatelinux.pt.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.automatelinux.pt.data.api.PtApi
import com.automatelinux.pt.data.model.GeocodeSuggestion
import com.automatelinux.pt.data.model.Itinerary
import com.automatelinux.pt.data.model.RouteResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class RoutingState(
    val origin: GeocodeSuggestion? = null,
    val destination: GeocodeSuggestion? = null,
    val departureTime: ZonedDateTime? = null,
    val arriveBy: Boolean = false,
    val results: RouteResult? = null,
    val selectedIndex: Int = 0,
    val loading: Boolean = false,
    val error: String? = null
) {
    val selectedItinerary: Itinerary?
        get() = results?.itineraries?.getOrNull(selectedIndex)
}

@HiltViewModel
class RoutingViewModel @Inject constructor(
    private val api: PtApi
) : ViewModel() {

    private val _state = MutableStateFlow(RoutingState())
    val state: StateFlow<RoutingState> = _state.asStateFlow()

    fun setOrigin(suggestion: GeocodeSuggestion?) {
        _state.value = _state.value.copy(origin = suggestion, results = null, error = null)
    }

    fun setDestination(suggestion: GeocodeSuggestion?) {
        _state.value = _state.value.copy(destination = suggestion, results = null, error = null)
    }

    fun setDepartureTime(time: ZonedDateTime?) {
        _state.value = _state.value.copy(departureTime = time)
    }

    fun setArriveBy(arriveBy: Boolean) {
        _state.value = _state.value.copy(arriveBy = arriveBy)
    }

    fun selectItinerary(index: Int) {
        _state.value = _state.value.copy(selectedIndex = index)
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

    fun setOriginFromCoords(lat: Double, lon: Double, name: String? = null, resolveAddress: Boolean = false) {
        val displayName = name ?: "%.4f, %.4f".format(lat, lon)
        setOrigin(GeocodeSuggestion(name = displayName, lat = lat, lon = lon))
        if (resolveAddress) {
            viewModelScope.launch {
                try {
                    val results = api.reverseGeocode(lat, lon)
                    val resolved = results.firstOrNull() ?: return@launch
                    setOrigin(GeocodeSuggestion(name = resolved.name, lat = lat, lon = lon))
                } catch (_: Exception) { }
            }
        }
    }

    fun setDestinationFromCoords(lat: Double, lon: Double, name: String? = null) {
        val displayName = name ?: "%.4f, %.4f".format(lat, lon)
        setDestination(GeocodeSuggestion(name = displayName, lat = lat, lon = lon))
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
                val time = s.departureTime?.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
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

    suspend fun geocode(text: String): List<GeocodeSuggestion> {
        return try {
            api.geocode(text)
        } catch (_: Exception) {
            emptyList()
        }
    }
}
