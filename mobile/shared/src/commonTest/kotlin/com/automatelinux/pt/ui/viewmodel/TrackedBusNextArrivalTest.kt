package com.automatelinux.pt.ui.viewmodel

import com.automatelinux.pt.data.model.VehicleMarker
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TrackedBusNextArrivalTest {
    private fun marker(ref: String, at: String, lineRef: String? = "100") =
        VehicleMarker(lat = 0.0, lon = 0.0, vehicleRef = ref, lineNumber = "236", expectedArrival = at, lineRef = lineRef)

    private fun bus(markers: List<VehicleMarker>, arrivals: List<LineArrival>, selected: Int = 0) =
        TrackedBus(legIndex = 0, lineName = "236", candidates = markers, selectedIndex = selected, lineArrivals = arrivals)

    @Test
    fun nextIsTheFirstVisitAfterTheFollowedBus_evenWithoutAPosition() {
        val tracked = bus(
            listOf(marker("A", "2026-11-16T09:12:00+02:00")),
            listOf(
                LineArrival("A", "100", "2026-11-16T09:12:00+02:00"),
                LineArrival("", "100", "2026-11-16T09:52:00+02:00"),
                LineArrival("", "100", "2026-11-16T09:32:00+02:00")
            )
        )
        assertEquals(Instant.parse("2026-11-16T09:32:00+02:00"), tracked.nextArrival)
    }

    @Test
    fun otherDirectionOfTheSameLineNumberIsNotNext() {
        val tracked = bus(
            listOf(marker("A", "2026-11-16T09:12:00+02:00")),
            listOf(
                LineArrival("B", "200", "2026-11-16T09:15:00+02:00"),
                LineArrival("C", "100", "2026-11-16T09:40:00+02:00")
            )
        )
        assertEquals(Instant.parse("2026-11-16T09:40:00+02:00"), tracked.nextArrival)
    }

    @Test
    fun followingTheSecondBusLooksPastIt() {
        val tracked = bus(
            listOf(marker("A", "2026-11-16T09:12:00+02:00"), marker("B", "2026-11-16T09:30:00+02:00")),
            listOf(
                LineArrival("A", "100", "2026-11-16T09:12:00+02:00"),
                LineArrival("B", "100", "2026-11-16T09:30:00+02:00")
            ),
            selected = 1
        )
        assertNull(tracked.nextArrival)
    }
}
