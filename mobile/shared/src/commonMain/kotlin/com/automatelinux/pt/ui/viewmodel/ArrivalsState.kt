package com.automatelinux.pt.ui.viewmodel

import com.automatelinux.pt.data.model.MonitoredStopVisit
import com.automatelinux.pt.data.model.SiriResponse
import com.automatelinux.pt.data.model.StopResult
import com.automatelinux.pt.data.model.StopTimeEntry
import com.automatelinux.pt.data.model.VehicleMarker
import com.automatelinux.pt.data.model.lineLabel
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
    /** Vehicles reported around the map's centre, independent of the selected stop. */
    val nearbyVehicles: List<VehicleMarker> = emptyList(),
    /**
     * Whether a live-buses poll has completed since the mode was switched on. An empty
     * [nearbyVehicles] means "none reporting" only once this is true — before it, the
     * same emptiness means "not asked yet", and telling the user there are no buses at
     * that moment would be a guess.
     */
    val nearbyVehiclesLoaded: Boolean = false,
    /**
     * How far out the search actually walked before it stopped, so the UI can name it.
     * Without this an empty map is unexplainable: the user cannot tell a quiet area from
     * a search far smaller than the screen they are looking at.
     */
    val nearbyVehiclesReachedMeters: Int = 0,
    /**
     * Distance from the search centre to the nearest bus found. In a village the walk can
     * end well outside the screen, and buses drawn off-screen look identical to no buses
     * at all — this is what lets the map say "nearest one is 12 km away" instead.
     */
    val nearbyVehiclesNearestMeters: Int = 0,
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

    // Lines present in the live feed OR the timetable, for the filter chips.
    // Numeric lines sort numerically.
    val availableLines: List<String>
        get() = (
            allVisits.mapNotNull { it.monitoredVehicleJourney?.publishedLineName } +
                timetable.map { it.lineLabel }
            )
            .distinct()
            .sortedWith(compareBy({ it.toIntOrNull() ?: Int.MAX_VALUE }, { it }))

    val visits: List<MonitoredStopVisit>
        get() = if (lineFilter.isBlank()) allVisits
        else allVisits.filter {
            it.monitoredVehicleJourney?.publishedLineName?.equals(lineFilter, ignoreCase = true) == true
        }
}

// One row of the unified departure board — exactly one of visit/stopTime is set.
data class DepartureEntry(
    val time: Instant,
    val line: String,
    val visit: MonitoredStopVisit? = null,
    val stopTime: StopTimeEntry? = null
) {
    val isLive: Boolean get() = visit != null
}

private const val DEDUP_WINDOW_MINUTES = 6L
private const val MAX_BOARD_ROWS = 20

// Merge the live SIRI feed with the GTFS timetable into one chronological board.
// Dedup is one-to-one: each live bus consumes its NEAREST unconsumed same-line
// scheduled departure within the window, and that scheduled row is hidden. A
// blind "any live within N minutes" test either doubles a bus running early
// (window too small) or swallows genuine back-to-back departures on frequent
// lines (window too big); nearest-unmatched assignment does neither. Live
// entries without a parseable expected time are dropped (the board is strictly
// chronological).
fun buildDepartures(
    liveVisits: List<MonitoredStopVisit>,
    timetable: List<StopTimeEntry>,
    lineFilter: String,
    now: Instant
): List<DepartureEntry> {
    val live = liveVisits.mapNotNull { visit ->
        val journey = visit.monitoredVehicleJourney ?: return@mapNotNull null
        val instant = journey.monitoredCall?.expectedArrivalTime
            ?.let { runCatching { Instant.parse(it) }.getOrNull() }
            ?: return@mapNotNull null
        DepartureEntry(instant, journey.publishedLineName ?: "?", visit = visit)
    }.sortedBy { it.time }

    val scheduledAll = timetable.mapNotNull { entry ->
        val iso = entry.place.departure ?: entry.place.scheduledDeparture ?: return@mapNotNull null
        val instant = runCatching { Instant.parse(iso) }.getOrNull() ?: return@mapNotNull null
        DepartureEntry(instant, entry.lineLabel, stopTime = entry)
    }

    val consumed = BooleanArray(scheduledAll.size)
    live.forEach { l ->
        var best = -1
        var bestDiff = Long.MAX_VALUE
        scheduledAll.forEachIndexed { i, s ->
            if (consumed[i] || s.line != l.line) return@forEachIndexed
            val diff = (l.time - s.time).inWholeMinutes.let { if (it < 0) -it else it }
            if (diff <= DEDUP_WINDOW_MINUTES && diff < bestDiff) {
                best = i
                bestDiff = diff
            }
        }
        if (best >= 0) consumed[best] = true
    }
    val scheduled = scheduledAll.filterIndexed { i, _ -> !consumed[i] }

    return (live + scheduled)
        .filter { (it.time - now).inWholeMinutes >= -1 }
        .filter { lineFilter.isBlank() || it.line.equals(lineFilter, ignoreCase = true) }
        .sortedBy { it.time }
        .take(MAX_BOARD_ROWS)
}
