package com.automatelinux.pt.ui.viewmodel

import com.automatelinux.pt.data.model.MonitoredStopVisit
import com.automatelinux.pt.data.model.SiriResponse
import com.automatelinux.pt.data.model.StopTimeEntry
import com.automatelinux.pt.data.model.VehicleMarker

data class ArrivalsState(
    val stationCode: String = "26472",
    val stationName: String = "",
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
