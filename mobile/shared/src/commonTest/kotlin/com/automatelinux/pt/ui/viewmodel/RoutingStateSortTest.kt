package com.automatelinux.pt.ui.viewmodel

import com.automatelinux.pt.data.model.Itinerary
import com.automatelinux.pt.data.model.RouteResult
import com.automatelinux.pt.data.model.RouteSortMode
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The reason ARRIVES_FIRST exists: Fastest ordered by trip length, which put a
 * 12:22→13:32 ride above the 10:52→12:03 one that actually gets you there first.
 */
class RoutingStateSortTest {

    private fun itinerary(start: String, end: String, duration: Long) = Itinerary(
        duration = duration,
        startTime = start,
        endTime = end,
        transfers = 0,
        legs = emptyList()
    )

    // The real result set from the report, as (start, end, duration seconds).
    private val shortest = itinerary("2026-08-19T08:28:00Z", "2026-08-19T09:34:00Z", 3960) // 1h 6m
    private val latest = itinerary("2026-08-19T09:22:00Z", "2026-08-19T10:32:00Z", 4200)   // 1h10m
    private val earliest = itinerary("2026-08-19T07:52:00Z", "2026-08-19T09:03:00Z", 4260) // 1h11m

    private fun state(sortMode: RouteSortMode? = null) = RoutingState(
        results = RouteResult(itineraries = listOf(shortest, latest, earliest))
    ).let { if (sortMode == null) it else it.copy(sortMode = sortMode) }

    @Test
    fun arrivesFirstIsTheDefaultSort() {
        assertEquals(RouteSortMode.ARRIVES_FIRST, RoutingState().sortMode)
    }

    @Test
    fun arrivesFirstOrdersByArrivalNotDuration() {
        assertEquals(
            listOf(earliest, shortest, latest),
            state().sortedItineraries
        )
    }

    @Test
    fun fastestStillOrdersByDuration() {
        assertEquals(
            listOf(shortest, latest, earliest),
            state(RouteSortMode.FASTEST).sortedItineraries
        )
    }

    @Test
    fun unparsableArrivalSinksToTheBottom() {
        val broken = itinerary("2026-08-19T08:00:00Z", "not-a-time", 60)
        val sorted = RoutingState(
            results = RouteResult(itineraries = listOf(broken, earliest))
        ).sortedItineraries
        assertEquals(listOf(earliest, broken), sorted)
    }
}
