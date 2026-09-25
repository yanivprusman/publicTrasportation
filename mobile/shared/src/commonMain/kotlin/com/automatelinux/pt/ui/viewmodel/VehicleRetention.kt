package com.automatelinux.pt.ui.viewmodel

import com.automatelinux.pt.data.model.RouteLeg
import com.automatelinux.pt.data.model.TransitMode
import com.automatelinux.pt.data.model.VehicleMarker

/** A vehicle on the map, and when a poll last actually reported it. */
data class SeenVehicle(val marker: VehicleMarker, val lastSeenMs: Long)

/**
 * How long a bus stays drawn after the polls stop reporting it.
 *
 * Each poll used to *replace* the markers, so one poll that missed a bus erased it and
 * the next one drew it again: pt #273, "bus markers disappear on their own while I watch
 * them, then come back". A poll misses a bus that is still there for ordinary reasons — a
 * stop's request timed out on mobile data, the whole poll failed, or the outward walk
 * stopped a round earlier than last time and never asked the stop that reported it.
 *
 * Long enough to cover one missed poll at the slowest cadence (45 s), short enough that a
 * bus which really left does not stand frozen on the map for minutes.
 */
const val VEHICLE_RETAIN_MS = 50_000L

/**
 * The vehicles to draw after a poll: everything [fresh] reported, at its new position,
 * plus anything from [previous] seen within [retainMs] that this poll did not report.
 *
 * Absence from one poll is not evidence that a bus is gone, and a poll that failed
 * outright is no evidence at all. So a missing bus stays at its last reported position
 * until it has been unreported for [retainMs]. A bus several stops report keeps the
 * sighting with the newest `recordedAt`.
 */
fun mergeSightings(
    previous: Map<String, SeenVehicle>,
    fresh: List<VehicleMarker>,
    nowMs: Long,
    retainMs: Long = VEHICLE_RETAIN_MS
): Map<String, SeenVehicle> {
    val merged = LinkedHashMap<String, SeenVehicle>()
    for ((ref, seen) in previous) {
        if (nowMs - seen.lastSeenMs <= retainMs) merged[ref] = seen
    }
    val freshByRef = LinkedHashMap<String, VehicleMarker>()
    for (marker in fresh) {
        val existing = freshByRef[marker.vehicleRef]
        if (existing == null || (marker.recordedAt ?: "") > (existing.recordedAt ?: "")) {
            freshByRef[marker.vehicleRef] = marker
        }
    }
    for ((ref, marker) in freshByRef) merged[ref] = SeenVehicle(marker, nowMs)
    return merged
}

/** The vehicles in [sightings] that the poll at [nowMs] did not report. */
fun staleRefs(sightings: Map<String, SeenVehicle>, nowMs: Long): Set<String> =
    sightings.filterValues { it.lastSeenMs < nowMs }.keys

/**
 * The (stop code, leg) pairs the "my route only" filter asks: each transit leg's boarding
 * and alighting stop. Empty means the route has nothing live buses can be asked about (all
 * walking, or legs without stop codes), and the filter is not offered.
 */
fun routeVehicleQueries(legs: List<RouteLeg>): List<Pair<String, RouteLeg>> =
    legs.filter { it.mode != TransitMode.WALK }
        .flatMap { leg ->
            listOfNotNull(leg.fromStopCode, leg.toStopCode)
                .filter { it.isNotBlank() }
                .distinct()
                .map { it to leg }
        }
