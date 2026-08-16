package com.automatelinux.pt.journey

import com.automatelinux.pt.data.model.Itinerary
import com.automatelinux.pt.data.model.Place
import com.automatelinux.pt.data.model.RouteLeg
import com.automatelinux.pt.data.model.TransitMode
import com.automatelinux.pt.util.EnStrings
import com.automatelinux.pt.util.formatTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.datetime.Instant

/**
 * What the panel says out loud.
 *
 * The trip's own two ends are pins the rider dropped, so they reach the app without
 * names — the server clears the "START"/"END" the router calls them by. The rule
 * this file holds is that a nameless target is described, never printed: a walk to
 * nowhere-named is a walk to *your destination*, and no router vocabulary ever
 * reaches the screen.
 */
class JourneyTextTest {

    private val t0 = Instant.parse("2026-08-15T20:16:00Z").toEpochMilliseconds()
    private fun iso(minutes: Int) =
        Instant.fromEpochMilliseconds(t0 + minutes * 60_000L).toString()

    /**
     * The single-walk itinerary: what "walk 286 m to the pin" actually looks like.
     *
     * Its two ends are ~290 m apart in a straight line, so [distanceMeters] can be
     * raised to describe a path that bends — which is the only way to tell the two
     * measures apart in a test.
     */
    private fun walkTo(name: String, distanceMeters: Int = 286) = Itinerary(
        duration = 300,
        startTime = iso(0),
        endTime = iso(5),
        transfers = 0,
        legs = listOf(
            RouteLeg(
                mode = TransitMode.WALK,
                from = Place("", 30.8536, 34.7822),
                to = Place(name, 30.8562, 34.7818),
                startTime = iso(0),
                endTime = iso(5),
                duration = 300,
                distanceMeters = distanceMeters
            )
        )
    )

    private fun headlineFor(destinationName: String): String =
        JourneyText.headline(
            stepJourney(walkTo(destinationName), JourneyCursor(), fix = null, nowMs = t0).progress,
            EnStrings
        )

    @Test
    fun `a nameless destination is described, not named`() {
        assertEquals("Walk to your destination", headlineFor(""))
    }

    @Test
    fun `the router's own word for the end of a trip never reaches the rider`() {
        // The regression this file exists for: MOTIS calls the trip's end "END", and
        // it once went on screen verbatim. The server strips it; if it ever stops
        // doing so, this is the line that notices.
        assertFalse(headlineFor("").contains("END"))
    }

    @Test
    fun `a walk that ends somewhere real still names it`() {
        assertEquals("Walk to stop A", headlineFor("stop A"))
    }

    private fun walkDetailAt(nowMinutes: Int): String? =
        JourneyText.detail(
            stepJourney(
                walkTo("stop A"),
                JourneyCursor(),
                fix = null,
                nowMs = t0 + nowMinutes * 60_000L
            ).progress,
            EnStrings
        )

    @Test
    fun `a walk card says how long the walk has, not only how far`() {
        // The bug this covers: the card read "286 m" and nothing else, so the one
        // question a rider on foot has — do I have time — was answered nowhere on it.
        val detail = walkDetailAt(0)
        assertEquals("286 m · 5 min on foot", detail)
    }

    @Test
    fun `the walk time is the walk, not the wait for the bus at the end of it`() {
        // The regression: a walk to a stop is timetabled to end when the bus leaves,
        // so the gap to the leg's end is the WAIT, not the walk. Reading that out
        // loud told a rider who opened the journey an hour early that a five-minute
        // stroll would take an hour and five.
        val anHourEarly = t0 - 60 * 60_000L
        val detail = JourneyText.detail(
            stepJourney(walkTo("stop A"), JourneyCursor(), fix = null, nowMs = anHourEarly).progress,
            EnStrings
        )
        assertEquals("286 m · 5 min on foot", detail)
    }

    @Test
    fun `the metres are the metres you walk, not the crow's flight`() {
        // The other half of "150 m · 7 min on foot": 150 m was the straight line to
        // the pole, while the 7 min covered the pavement that bends around the
        // buildings to reach it. Standing at the start of a 600 m path whose ends are
        // 290 m apart, the card owes the rider the 600.
        val atStart = GeoFix(lat = 30.8536, lon = 34.7822, atMs = t0)
        val detail = JourneyText.detail(
            stepJourney(
                walkTo("stop A", distanceMeters = 600),
                JourneyCursor(),
                fix = atStart,
                nowMs = t0
            ).progress,
            EnStrings
        )
        assertEquals("600 m · 5 min on foot", detail)
    }

    @Test
    fun `the metres and the minutes describe the same walk`() {
        // The one the rider caught: "150 m · 7 min on foot". The metres were what was
        // left to walk; the minutes were the whole 385 m leg. Standing halfway along,
        // both halves have to have halved — a line whose two numbers are measured
        // from different points is worse than a line with one number.
        val halfway = GeoFix(lat = 30.8549, lon = 34.7820, atMs = t0)
        val detail = JourneyText.detail(
            stepJourney(walkTo("stop A"), JourneyCursor(), fix = halfway, nowMs = t0).progress,
            EnStrings
        )!!
        assertTrue(
            detail.endsWith("2 min on foot"),
            "half the walk left should read as half the time, got: $detail"
        )
        val meters = detail.substringBefore(" m").toInt()
        assertTrue(meters in 130..160, "expected roughly half of 286 m, got $meters in: $detail")
    }

    @Test
    fun `a walk shorter than a minute rounds up to one, and does not throw`() {
        // The 35-second hop across an interchange: the minute floor exceeded the
        // leg's own total, and coerceIn(60, 35) is an empty range — the panel
        // crashed on every tick of exactly the legs too short to matter.
        val shortHop = Itinerary(
            duration = 35,
            startTime = iso(0),
            endTime = iso(1),
            transfers = 0,
            legs = listOf(
                RouteLeg(
                    mode = TransitMode.WALK,
                    from = Place("platform 1", 30.8536, 34.7822),
                    to = Place("platform 3", 30.8538, 34.7822),
                    startTime = iso(0),
                    endTime = Instant.fromEpochMilliseconds(t0 + 35_000L).toString(),
                    duration = 35,
                    distanceMeters = 40
                )
            )
        )
        val detail = JourneyText.detail(
            stepJourney(shortHop, JourneyCursor(), fix = null, nowMs = t0).progress,
            EnStrings
        )
        assertEquals("40 m · 1 min on foot", detail)
    }

    /** Walk to a pole, then a bus off it — the shape almost every real trip starts with. */
    private fun walkThenRide() = Itinerary(
        duration = 1800,
        startTime = iso(0),
        endTime = iso(30),
        transfers = 0,
        legs = listOf(
            RouteLeg(
                mode = TransitMode.WALK,
                from = Place("", 30.8536, 34.7822),
                to = Place("stop A", 30.8562, 34.7818),
                startTime = iso(0),
                endTime = iso(5),
                duration = 300,
                distanceMeters = 286
            ),
            RouteLeg(
                mode = TransitMode.BUS,
                from = Place("stop A", 30.8562, 34.7818),
                to = Place("stop B", 31.2500, 34.7900),
                startTime = iso(7),
                endTime = iso(30),
                duration = 1380,
                routeShortName = "60"
            )
        )
    )

    @Test
    fun `a walk to a stop says when the bus it is for leaves`() {
        // The deadline the minutes are measured against. It names the RIDE's
        // departure, not the walk's scheduled end: a router that grants slack ends
        // the walk first, and the earlier of the two is not the one you can miss.
        val detail = JourneyText.detail(
            stepJourney(walkThenRide(), JourneyCursor(), fix = null, nowMs = t0).progress,
            EnStrings
        )!!
        assertEquals("286 m · 5 min on foot · by ${formatTime(iso(7))}", detail)
    }

    @Test
    fun `the last walk of a trip has no deadline to give`() {
        // It ends at the rider's own pin, at the time the header already calls the ETA.
        assertEquals("286 m · 5 min on foot", walkDetailAt(0))
    }

    /** Standing at the pole, then rolling: one ride, looked at twice. */
    private fun rideOnly() = Itinerary(
        duration = 1500,
        startTime = iso(5),
        endTime = iso(30),
        transfers = 0,
        legs = listOf(
            RouteLeg(
                mode = TransitMode.BUS,
                from = Place("stop A", 30.8562, 34.7818),
                to = Place("stop B", 31.2500, 34.7900),
                startTime = iso(5),
                endTime = iso(30),
                duration = 1500,
                routeShortName = "60"
            )
        )
    )

    @Test
    fun `waiting at the pole says when the ride leaves, not only how soon`() {
        // The headline counts down ("60 leaves in 5 min"); a countdown alone cannot be
        // checked against a timetable, a screenshot, or the sign on the pole.
        val detail = JourneyText.detail(
            stepJourney(rideOnly(), JourneyCursor(), fix = null, nowMs = t0).progress,
            EnStrings
        )
        assertEquals("Board at stop A · ${formatTime(iso(5))}", detail)
    }

    @Test
    fun `riding says when you get off, next to where`() {
        // "Get off at stop B" and no clock was the panel's largest silence: the rider
        // had to open the step list to learn when their own stop comes.
        val detail = JourneyText.detail(
            stepJourney(rideOnly(), JourneyCursor(), fix = null, nowMs = t0 + 10 * 60_000L).progress,
            EnStrings
        )
        assertEquals("Get off at stop B · ${formatTime(iso(30))}", detail)
    }

    @Test
    fun `the walk time never counts below zero`() {
        // Two minutes past the schedule and still short of the stop — a state only a
        // live position can reach, since without one the schedule closes the leg 30s
        // after its end. A remaining-time reading would go negative here.
        val late = t0 + 7 * 60_000L
        val detail = JourneyText.detail(
            stepJourney(
                walkTo("stop A"),
                JourneyCursor(),
                fix = GeoFix(lat = 30.8536, lon = 34.7822, atMs = late),
                nowMs = late
            ).progress,
            EnStrings
        )
        assertFalse(detail!!.contains("-"), "a late walk must not print a negative duration: $detail")
        assertTrue(
            detail.endsWith("5 min on foot"),
            "a late walk still takes as long as it takes, got: $detail"
        )
    }
}
