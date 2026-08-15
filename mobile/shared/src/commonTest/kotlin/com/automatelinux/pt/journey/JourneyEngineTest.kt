package com.automatelinux.pt.journey

import com.automatelinux.pt.data.model.Itinerary
import com.automatelinux.pt.data.model.Place
import com.automatelinux.pt.data.model.RouteLeg
import com.automatelinux.pt.data.model.TransitMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.Instant

/**
 * The rules a rider actually depends on: the stop count only falls, the "get off next"
 * warning arrives once and not twice, and a wobbling fix never rewinds the trip.
 */
class JourneyEngineTest {

    // A straight line of stops ~600 m apart, running north.
    private val originLat = 31.0000
    private val lon = 34.8000
    private fun at(index: Int) = originLat + index * 0.0054

    private fun place(name: String, index: Int) = Place(name, at(index), lon)

    private val t0 = Instant.parse("2026-08-15T09:00:00Z").toEpochMilliseconds()
    private fun iso(minutes: Int) =
        Instant.fromEpochMilliseconds(t0 + minutes * 60_000L).toString()

    /** walk → bus calling at five stops → walk. */
    private val trip = Itinerary(
        duration = 2400,
        startTime = iso(0),
        endTime = iso(40),
        transfers = 0,
        legs = listOf(
            RouteLeg(
                mode = TransitMode.WALK,
                from = place("home", 0),
                to = place("stop A", 1),
                startTime = iso(0),
                endTime = iso(8),
                duration = 480,
                distanceMeters = 600
            ),
            RouteLeg(
                mode = TransitMode.BUS,
                from = place("stop A", 1),
                to = place("stop E", 5),
                startTime = iso(10),
                endTime = iso(30),
                duration = 1200,
                routeShortName = "64",
                intermediateStops = listOf(
                    place("stop B", 2),
                    place("stop C", 3),
                    place("stop D", 4)
                )
            ),
            RouteLeg(
                mode = TransitMode.WALK,
                from = place("stop E", 5),
                to = place("work", 6),
                startTime = iso(30),
                endTime = iso(40),
                duration = 600,
                distanceMeters = 600
            )
        )
    )

    private fun fixAt(index: Int, atMinutes: Int) =
        GeoFix(lat = at(index), lon = lon, accuracyMeters = 8.0, atMs = t0 + atMinutes * 60_000L)

    @Test
    fun `without a fix the journey follows the timetable and says so`() {
        val update = stepJourney(trip, JourneyCursor(), fix = null, nowMs = t0 + 60_000)
        assertEquals(JourneyPhase.WALKING, update.progress.phase)
        assertEquals(0, update.progress.legIndex)
        assertFalse(update.progress.positionKnown)
        assertNull(update.progress.metersToTarget)
    }

    @Test
    fun `a stale fix is not a position`() {
        val stale = GeoFix(at(3), lon, 8.0, atMs = t0 - FIX_STALE_MS - 1)
        val update = stepJourney(trip, JourneyCursor(), stale, nowMs = t0)
        assertFalse(update.progress.positionKnown)
    }

    @Test
    fun `standing at the pole before departure is waiting, with a countdown`() {
        val update = stepJourney(
            trip,
            JourneyCursor(legIndex = 1),
            fix = fixAt(1, 9),
            nowMs = t0 + 9 * 60_000
        )
        assertEquals(JourneyPhase.WAITING, update.progress.phase)
        assertEquals(60L, update.progress.secondsToDeparture)
        assertTrue(JourneyAlert.BOARD_NOW in update.alerts)
    }

    @Test
    fun `stops remaining counts down as the bus calls at each stop`() {
        var cursor = JourneyCursor(legIndex = 1)
        val seen = mutableListOf<Int>()
        for (stop in 1..4) {
            val update = stepJourney(trip, cursor, fixAt(stop, 10 + stop * 3), t0 + (10 + stop * 3) * 60_000L)
            cursor = update.cursor
            update.progress.stopsRemaining?.let { seen += it }
        }
        // Boarding stop, then B, C, D: four stops to go, then three, two, one.
        assertEquals(listOf(4, 3, 2, 1), seen)
    }

    @Test
    fun `a fix that wobbles backwards does not rewind the trip`() {
        val forward = stepJourney(trip, JourneyCursor(legIndex = 1), fixAt(3, 18), t0 + 18 * 60_000)
        assertEquals(2, forward.progress.stopsRemaining)

        val backwards = stepJourney(trip, forward.cursor, fixAt(1, 19), t0 + 19 * 60_000)
        assertEquals(
            2,
            backwards.progress.stopsRemaining,
            "a GPS wobble must not un-pass a stop the rider has already gone by"
        )
    }

    @Test
    fun `the alight warning fires once and only once`() {
        val first = stepJourney(trip, JourneyCursor(legIndex = 1), fixAt(4, 24), t0 + 24 * 60_000)
        assertTrue(first.progress.alightImminent)
        assertEquals(listOf(JourneyAlert.PREPARE_TO_ALIGHT), first.alerts)

        val second = stepJourney(trip, first.cursor, fixAt(4, 25), t0 + 25 * 60_000)
        assertTrue(second.progress.alightImminent)
        assertTrue(second.alerts.isEmpty(), "a rider does not need to be buzzed every second")
    }

    @Test
    fun `reaching the alighting stop moves on to the walk, not past it`() {
        val update = stepJourney(trip, JourneyCursor(legIndex = 1, stopIndex = 3), fixAt(5, 30), t0 + 30 * 60_000)
        assertEquals(2, update.progress.legIndex)
        assertEquals(JourneyPhase.WALKING, update.progress.phase)
    }

    @Test
    fun `arriving ends the journey once`() {
        val arrive = stepJourney(trip, JourneyCursor(legIndex = 2), fixAt(6, 40), t0 + 40 * 60_000)
        assertEquals(JourneyPhase.ARRIVED, arrive.progress.phase)
        assertTrue(JourneyAlert.ARRIVED in arrive.alerts)

        val after = stepJourney(trip, arrive.cursor, fixAt(6, 41), t0 + 41 * 60_000)
        assertTrue(after.alerts.isEmpty(), "arrival is announced once, not on every tick")
    }

    @Test
    fun `a late bus does not get the rider off early`() {
        // Timetabled to have finished the ride at minute 30; the rider is still two
        // stops short at minute 35. GPS wins: the leg is not over.
        val update = stepJourney(trip, JourneyCursor(legIndex = 1, stopIndex = 2), fixAt(3, 35), t0 + 35 * 60_000)
        assertEquals(1, update.progress.legIndex)
        assertEquals(JourneyPhase.RIDING, update.progress.phase)
    }

    @Test
    fun `distance to the alight stop warns even when no intermediate stops are known`() {
        val sparse = trip.copy(
            legs = trip.legs.mapIndexed { i, leg ->
                if (i == 1) leg.copy(intermediateStops = null) else leg
            }
        )
        // Between stop D and E: no stop list to count, but within the warning radius.
        val near = GeoFix(at(5) - 0.003, lon, 8.0, atMs = t0 + 28 * 60_000)
        val update = stepJourney(sparse, JourneyCursor(legIndex = 1), near, t0 + 28 * 60_000)
        assertTrue(update.progress.alightImminent)
    }

    @Test
    fun `the bar for a leg fills by what was actually travelled`() {
        // Measured against the leg's own straight line: nothing at the doorstep,
        // four fifths of the way along four fifths of the walk. (Standing *at* the
        // stop is not tested here — by then the engine has moved on to the bus.)
        val start = stepJourney(trip, JourneyCursor(), fixAt(0, 1), t0 + 60_000)
        assertEquals(0f, start.progress.legFraction(), 0.05f)

        val nearlyThere = GeoFix(at(0) + 0.8 * 0.0054, lon, 8.0, atMs = t0 + 6 * 60_000)
        val most = stepJourney(trip, JourneyCursor(), nearlyThere, t0 + 6 * 60_000)
        assertEquals(0.8f, most.progress.legFraction(), 0.05f)
    }

    @Test
    fun `waiting for a bus is no progress through the ride`() {
        val waiting = stepJourney(trip, JourneyCursor(legIndex = 1), fixAt(1, 9), t0 + 9 * 60_000)
        assertEquals(JourneyPhase.WAITING, waiting.progress.phase)
        assertEquals(0f, waiting.progress.legFraction())
    }

    @Test
    fun `a ride fills by stops called at, not by the clock`() {
        // Two of the four stops passed, but running seven minutes behind: the bar
        // follows the bus, not the timetable it is failing to keep.
        val late = stepJourney(trip, JourneyCursor(legIndex = 1, stopIndex = 2), fixAt(3, 32), t0 + 32 * 60_000)
        assertEquals(0.5f, late.progress.legFraction(), 0.01f)
    }

    @Test
    fun `a journey with no fix claims no ground`() {
        val blind = stepJourney(trip, JourneyCursor(), fix = null, nowMs = t0)
        assertEquals(0f, blind.progress.legFraction())
    }

    @Test
    fun `haversine matches a known distance`() {
        val meters = haversineMeters(at(0), lon, at(1), lon)
        assertTrue(meters in 580.0..620.0, "expected ~600 m, got $meters")
    }
}
