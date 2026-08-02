package com.automatelinux.pt.ui.viewmodel

import com.automatelinux.pt.data.model.MonitoredStopVisit
import com.automatelinux.pt.data.model.SiriResponse
import com.automatelinux.pt.data.model.StopResult
import com.automatelinux.pt.data.model.StopTimeEntry
import com.automatelinux.pt.data.model.VehicleMarker
import kotlinx.datetime.Instant

data class ArrivalsState(
    val stationCode: String = "26472",
    val stationName: String = "",
    // True once the user picked a station themselves (search, favorite, nearby row,
    // widget). Auto-select-nearest never overrides an explicit choice.
    val stationExplicitlyChosen: Boolean = false,
    // Stops around the user's GPS position (not the map center) for the
    // quick-switch chips, nearest first.
    val gpsNearbyStops: List<StopResult> = emptyList(),
    val siriData: SiriResponse? = null,
    val vehicleMarkers: List<VehicleMarker> = emptyList(),
    val showVehicleMarkers: Boolean = true,
    val lineFilter: String = "",
    val lastUpdated: Long? = null,
    val error: String? = null,
    val loading: Boolean = false,
    val timetable: List<StopTimeEntry> = emptyList(),
    val timetableForCode: String? = null,
    val timetableFetchedAt: Long? = null,
    val timetableLoading: Boolean = false,
    val timetableError: Boolean = false
) {
    // All monitored visits, soonest arrival first (server order is not guaranteed).
    val allVisits: List<MonitoredStopVisit>
        get() {
            val raw = siriData?.siri?.serviceDelivery?.stopMonitoringDelivery
                ?.flatMap { it.monitoredStopVisit ?: emptyList() } ?: emptyList()
            return raw.sortedBy { visit ->
                visit.monitoredVehicleJourney?.monitoredCall?.expectedArrivalTime
                    ?.let { runCatching { Instant.parse(it) }.getOrNull() }
                    ?: Instant.DISTANT_FUTURE
            }
        }

    // Lines present in the live feed, for the filter chips. Numeric lines sort numerically.
    val availableLines: List<String>
        get() = allVisits.mapNotNull { it.monitoredVehicleJourney?.publishedLineName }
            .distinct()
            .sortedWith(compareBy({ it.toIntOrNull() ?: Int.MAX_VALUE }, { it }))

    val visits: List<MonitoredStopVisit>
        get() = if (lineFilter.isBlank()) allVisits
        else allVisits.filter {
            it.monitoredVehicleJourney?.publishedLineName?.equals(lineFilter, ignoreCase = true) == true
        }
}
