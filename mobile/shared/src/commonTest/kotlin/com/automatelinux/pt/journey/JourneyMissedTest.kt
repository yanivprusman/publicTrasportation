package com.automatelinux.pt.journey

import com.automatelinux.pt.data.model.Itinerary
import com.automatelinux.pt.data.model.Place
import com.automatelinux.pt.data.model.RouteLeg
import com.automatelinux.pt.data.model.TransitMode
import com.automatelinux.pt.util.EnStrings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.Instant

/**
 * A missed bus is said to be missed, and the next one of the line takes its place.
 * Owner, 2026-11-25: "Board 370 now" at 10:39 for the 10:00 from Tel Aviv central.
 */
class JourneyMissedTest {

    private val lon = 34.8
    private fun at(index: Int) = 31.0 + index * 0.0054
    private fun place(name: String, index: Int) = Place(name, at(index), lon)

    private val t0 = Instant.parse("2026-11-25T08:00:00Z").toEpochMilliseconds()
    private fun ms(minutes: Int) = t0 + minutes * 60_000L
    private fun iso(minutes: Int) = Instant.fromEpochMilliseconds(ms(minutes)).toString()

    private fun bus(start: Int, end: Int, line: String = "370", routeId: String? = "r370", from: Int = 1, to: Int = 4) = RouteLeg(
        mode = TransitMode.BUS,
        from = place("central", from),
        to = place("beer sheva", to),
        startTime = iso(start),
        endTime = iso(end),
        duration = (end - start) * 60L,
        routeShortName = line,
        routeId = routeId,
        intermediateStops = listOf(place("b", 2), place("c", 3))
    )

    private fun walk(from: Int, to: Int, start: Int, end: Int) = RouteLeg(
        mode = TransitMode.WALK,
        from = place("p$from", from),
        to = place("p$to", to),
        startTime = iso(start),
        endTime = iso(end),
        duration = (end - start) * 60L
    )

    /** walk to the pole → the 10:00 370 → walk to the door. */
    private val trip = Itinerary(
        duration = 100 * 60L,
        startTime = iso(-5),
        endTime = iso(95),
        transfers = 0,
        legs = listOf(walk(0, 1, -5, 0), bus(0, 88), walk(4, 5, 88, 95))
    )

    private fun atPole(minutes: Int) = GeoFix(at(1), lon, accuracyMeters = 8.0, atMs = ms(minutes))

    private fun step(minutes: Int, fix: GeoFix? = atPole(minutes), live: Long? = null) =
        stepJourney(trip, JourneyCursor(legIndex = 1), fix, ms(minutes), liveBoardingArrivalMs = live, gps = GpsState.ON)

    @Test
    fun `still at the pole long after the bus was due is a missed bus, not board now`() {
        val p = step(39).progress
        assertEquals(JourneyPhase.WAITING, p.phase)
        assertTrue(p.boardingMissed)
        assertEquals("370 has left · finding the next one", JourneyText.headline(p, EnStrings))
    }

    @Test
    fun `a few minutes late is still a bus about to come`() {
        val p = step(5).progress
        assertFalse(p.boardingMissed)
        assertEquals("Board 370 now", JourneyText.headline(p, EnStrings))
    }

    @Test
    fun `a bus the feed still sees coming is not missed however late`() {
        assertFalse(step(39, live = ms(42)).progress.boardingMissed)
    }

    @Test
    fun `with no position the rider may be on the bus, so nothing is called missed`() {
        assertFalse(step(39, fix = null).progress.boardingMissed)
    }

    @Test
    fun `the next trip of the same line from the same pole is picked, earliest first`() {
        fun only(leg: RouteLeg) = Itinerary(0, leg.startTime, leg.endTime, 0, listOf(leg))
        val options = listOf(
            only(bus(60, 150)),
            only(bus(45, 133, line = "369", routeId = "r369")), // another line
            only(bus(30, 125, from = 3)), // same line, another pole
            only(bus(45, 133)),
            Itinerary(0, iso(40), iso(140), 1, listOf(bus(40, 90, to = 3), bus(95, 140, line = "4", routeId = "r4")))
        )
        assertEquals(iso(45), laterRideFor(trip.legs[1], options)?.startTime)
    }

    @Test
    fun `no later trip of the line leaves the journey alone`() {
        val options = listOf(Itinerary(0, iso(45), iso(133), 0, listOf(bus(45, 133, line = "369", routeId = "r369"))))
        assertNull(laterRideFor(trip.legs[1], options))
    }

    @Test
    fun `the next trip replaces the ride in place and moves what follows by its delay`() {
        val next = laterRideFor(trip.legs[1], listOf(Itinerary(0, iso(45), iso(133), 0, listOf(bus(45, 133)))))!!
        val spliced = trip.withRideReplaced(1, next)
        assertEquals(3, spliced.legs.size)
        assertEquals(trip.legs[0], spliced.legs[0])
        assertEquals(iso(45), spliced.legs[1].startTime)
        assertEquals(iso(133), spliced.legs[2].startTime)
        assertEquals(iso(140), spliced.legs[2].endTime)
        assertEquals(iso(140), spliced.endTime)

        // And the journey on it counts down to the new bus, with its board call re-armed.
        val cursor = JourneyCursor(legIndex = 1, boardAlerted = setOf(1)).rearmedFor(1)
        val update = stepJourney(spliced, cursor, atPole(43), ms(43), gps = GpsState.ON)
        assertFalse(update.progress.boardingMissed)
        assertEquals(120L, update.progress.secondsToDeparture)
        assertTrue(JourneyAlert.BOARD_NOW in update.alerts)
    }
}
