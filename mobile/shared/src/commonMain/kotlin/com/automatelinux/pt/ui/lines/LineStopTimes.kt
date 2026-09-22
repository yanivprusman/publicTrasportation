package com.automatelinux.pt.ui.lines

import com.automatelinux.pt.data.model.RouteStopItem
import com.automatelinux.pt.data.model.UpcomingCall

/**
 * The tracked bus's expected arrival (ISO) at each row of a line's stop list, or null
 * where the feed gave none — the stops before the polled one, which it does not report.
 *
 * Matched by stop code, never by position: the list is the route's representative trip
 * and the tracked bus may run a variant of it, and SIRI's `Order` skips numbers.
 *
 * A loop line lists a stop twice. The calls are the tail of the trip, so a stop's calls
 * belong to its LAST rows: a bus that has already been round once has only the second
 * visit left, and giving its time to the first row would print a future time on a stop
 * the bus passed long ago.
 */
fun stopArrivalTimes(stops: List<RouteStopItem>, calls: List<UpcomingCall>): List<String?> {
    val times = arrayOfNulls<String>(stops.size)
    val callsByCode = calls.groupBy { it.stopCode }
    stops.indices
        .filter { stops[it].stopCode.isNotBlank() }
        .groupBy { stops[it].stopCode }
        .forEach { (code, rows) ->
            val codeCalls = callsByCode[code] ?: return@forEach
            rows.takeLast(codeCalls.size)
                .zip(codeCalls.takeLast(rows.size))
                .forEach { (row, call) -> times[row] = call.expectedArrival }
        }
    return times.toList()
}
