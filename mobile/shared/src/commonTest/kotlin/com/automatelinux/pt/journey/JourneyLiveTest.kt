package com.automatelinux.pt.journey

import com.automatelinux.pt.data.model.Itinerary
import com.automatelinux.pt.data.model.Place
import com.automatelinux.pt.data.model.RouteLeg
import com.automatelinux.pt.data.model.TransitMode
import com.automatelinux.pt.data.model.VehicleMarker
import com.automatelinux.pt.util.EnStrings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.datetime.Instant

/**
 * The live layer's promises: it watches exactly the bus that can still be missed,
 * it never confuses the 64 going our way with the 64 going back, and a report is
 * shown fresh or not at all.
 */
class JourneyLiveTest {

    private val t0 = Instant.parse("2026-08-15T09:00:00Z").toEpochMilliseconds()
    private fun iso(minutes: Int) =
        Instant.fromEpochMilliseconds(t0 + minutes * 60_000L).toString()

    private val walk = RouteLeg(
        mode = TransitMode.WALK,
        from = Place("home", 31.0, 34.8),
        to = Place("stop A", 31.005, 34.8),
        startTime = iso(0),
        endTime = iso(8),
        duration = 480
    )
    private val ride = RouteLeg(
        mode = TransitMode.BUS,
        from = Place("stop A", 31.005, 34.8),
        to = Place("stop E", 31.03, 34.8),
        startTime = iso(10),
        endTime = iso(30),
        duration = 1200,
        routeShortName = "64",
        routeId = "11057",
        fromStopCode = "13868"
    )
    private val trip = Itinerary(
        duration = 1800, startTime = iso(0), endTime = iso(30),
        transfers = 0, legs = listOf(walk, ride)
    )

    private fun progressAt(phase: JourneyPhase, legIndex: Int) = JourneyProgress(
        phase = phase, legIndex = legIndex, totalLegs = 2,
        leg = trip.legs.getOrNull(legIndex), nextLeg = trip.legs.getOrNull(legIndex + 1),
        stopsRemaining = null, nextStopName = null, targetName = null,
        metersToTarget = null, secondsToTarget = null, secondsToDeparture = null,
        alightImminent = false, positionKnown = true
    )

    @Test
    fun `walking to a stop watches the ride at the end of the walk`() {
        assertEquals(1, JourneyLive.watchLegIndex(trip, progressAt(JourneyPhase.WALKING, 0)))
    }

    @Test
    fun `waiting at the pole watches the ride being waited for`() {
        assertEquals(1, JourneyLive.watchLegIndex(trip, progressAt(JourneyPhase.WAITING, 1)))
    }

    @Test
    fun `on board there is no boarding stop left to watch`() {
        assertNull(JourneyLive.watchLegIndex(trip, progressAt(JourneyPhase.RIDING, 1)))
    }

    private fun marker(
        arrivalMinutes: Int,
        lineRef: String? = "11057",
        lineNumber: String = "64",
        vehicleRef: String = "v1"
    ) = VehicleMarker(
        lat = 31.01, lon = 34.8,
        vehicleRef = vehicleRef,
        lineNumber = lineNumber,
        expectedArrival = iso(arrivalMinutes),
        lineRef = lineRef
    )

    @Test
    fun `the 64 going the other way is not our 64`() {
        // Same published name, different route id — the classic wrong-direction trap.
        val info = JourneyLive.infoFrom(
            legIndex = 1, leg = ride,
            markers = listOf(marker(3, lineRef = "99999"), marker(9)),
            nowMs = t0
        )!!
        assertEquals(t0 + 9 * 60_000L, info.arrivalMs)
    }

    @Test
    fun `the soonest bus leads and the one behind it is the 'then'`() {
        val info = JourneyLive.infoFrom(
            legIndex = 1, leg = ride,
            markers = listOf(marker(41, vehicleRef = "v2"), marker(6)),
            nowMs = t0
        )!!
        assertEquals(t0 + 6 * 60_000L, info.arrivalMs)
        assertEquals(t0 + 41 * 60_000L, info.nextArrivalMs)
        assertEquals("v1", info.vehicle?.vehicleRef)
    }

    @Test
    fun `no sighting of our line is no report, not a guess`() {
        assertNull(
            JourneyLive.infoFrom(
                legIndex = 1, leg = ride,
                markers = listOf(marker(5, lineRef = "99999", lineNumber = "60")),
                nowMs = t0
            )
        )
    }

    @Test
    fun `a banner goes stale rather than counting down a ghost`() {
        val live = JourneyLiveInfo(
            legIndex = 1, arrivalMs = t0 + 6 * 60_000L, fetchedAtMs = t0
        )
        assertEquals(
            "64 arrives in 6 min",
            JourneyText.liveBanner(live, trip, t0, EnStrings)
        )
        assertNull(
            JourneyText.liveBanner(live, trip, t0 + JourneyLive.STALE_MS + 1_000L, EnStrings)
        )
    }

    @Test
    fun `a bus under a minute out is arriving, not 'in 0 min'`() {
        val live = JourneyLiveInfo(
            legIndex = 1, arrivalMs = t0 + 30_000L, fetchedAtMs = t0
        )
        assertEquals("64 is arriving", JourneyText.liveBanner(live, trip, t0, EnStrings))
    }

    @Test
    fun `the bus behind rides along in the banner`() {
        val live = JourneyLiveInfo(
            legIndex = 1,
            arrivalMs = t0 + 6 * 60_000L,
            nextArrivalMs = t0 + 41 * 60_000L,
            fetchedAtMs = t0
        )
        assertEquals(
            "64 arrives in 6 min · then 41 min",
            JourneyText.liveBanner(live, trip, t0, EnStrings)
        )
    }
}
