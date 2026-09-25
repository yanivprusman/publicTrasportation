package com.automatelinux.pt.ui.viewmodel

import com.automatelinux.pt.data.model.Place
import com.automatelinux.pt.data.model.RouteLeg
import com.automatelinux.pt.data.model.TransitMode
import com.automatelinux.pt.data.model.VehicleMarker
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** pt #273 (markers blink out between polls) and pt #272 (route-only live buses). */
class VehicleRetentionTest {

    private fun bus(ref: String, lat: Double = 32.0, recordedAt: String? = null) =
        VehicleMarker(lat = lat, lon = 34.8, vehicleRef = ref, recordedAt = recordedAt)

    // One poll missing a bus used to erase it; the next drew it again.
    @Test
    fun aBusOnePollMissedStaysDrawn() {
        val first = mergeSightings(emptyMap(), listOf(bus("a"), bus("b")), nowMs = 0)
        val second = mergeSightings(first, listOf(bus("a")), nowMs = 15_000)
        assertEquals(setOf("a", "b"), second.keys)
    }

    // A failed poll reports nothing — which is no evidence at all.
    @Test
    fun aFailedPollErasesNothing() {
        val first = mergeSightings(emptyMap(), listOf(bus("a")), nowMs = 0)
        assertEquals(setOf("a"), mergeSightings(first, emptyList(), nowMs = 45_000).keys)
    }

    @Test
    fun aBusUnreportedPastTheRetentionLeaves() {
        val first = mergeSightings(emptyMap(), listOf(bus("a")), nowMs = 0)
        assertTrue(mergeSightings(first, emptyList(), nowMs = VEHICLE_RETAIN_MS + 1).isEmpty())
    }

    @Test
    fun aReportedBusMovesAndItsClockResets() {
        val first = mergeSightings(emptyMap(), listOf(bus("a", lat = 32.0)), nowMs = 0)
        val second = mergeSightings(first, listOf(bus("a", lat = 32.1)), nowMs = 40_000)
        assertEquals(32.1, second.getValue("a").marker.lat)
        assertEquals(1, mergeSightings(second, emptyList(), nowMs = 80_000).size)
    }

    @Test
    fun severalStopsReportingOneBusKeepTheNewestSighting() {
        val merged = mergeSightings(
            emptyMap(),
            listOf(
                bus("a", lat = 32.2, recordedAt = "2026-09-25T08:14:58+03:00"),
                bus("a", lat = 32.0, recordedAt = "2026-09-25T08:14:30+03:00")
            ),
            nowMs = 0
        )
        assertEquals(32.2, merged.getValue("a").marker.lat)
    }

    private val here = Place("x", 32.0, 34.8)
    private fun leg(mode: TransitMode, from: String?, to: String?) = RouteLeg(
        mode = mode, from = here, to = here, startTime = "", endTime = "", duration = 0,
        routeShortName = "64", routeId = "11057", fromStopCode = from, toStopCode = to
    )

    @Test
    fun eachTransitLegAsksItsBoardingAndAlightingStop() {
        val queries = routeVehicleQueries(
            listOf(
                leg(TransitMode.WALK, null, null),
                leg(TransitMode.BUS, "21022", "20699"),
                leg(TransitMode.WALK, null, null)
            )
        )
        assertEquals(listOf("21022", "20699"), queries.map { it.first })
    }

    @Test
    fun aRouteWithNoAskableStopOffersNoFilter() {
        assertTrue(routeVehicleQueries(listOf(leg(TransitMode.WALK, null, null))).isEmpty())
        assertTrue(routeVehicleQueries(listOf(leg(TransitMode.BUS, null, " "))).isEmpty())
    }

    // Kept, but not passed off as current: the map fades it.
    @Test
    fun onlyTheBusesThisPollMissedAreStale() {
        val first = mergeSightings(emptyMap(), listOf(bus("a"), bus("b")), nowMs = 0)
        val second = mergeSightings(first, listOf(bus("a")), nowMs = 15_000)
        assertEquals(setOf("b"), staleRefs(second, nowMs = 15_000))
    }
}
