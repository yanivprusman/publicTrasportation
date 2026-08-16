package com.automatelinux.pt.journey

import com.automatelinux.pt.data.model.Itinerary
import com.automatelinux.pt.data.model.Place
import com.automatelinux.pt.data.model.RouteLeg
import com.automatelinux.pt.data.model.TransitMode
import com.automatelinux.pt.ui.routing.formatTime
import com.automatelinux.pt.util.EnStrings
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

    /** The single-walk itinerary: what "walk 286 m to the pin" actually looks like. */
    private fun walkTo(name: String) = Itinerary(
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
                distanceMeters = 286
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
    fun `the walk countdown falls with the clock`() {
        assertEquals("286 m · 2 min on foot", walkDetailAt(3))
    }

    @Test
    fun `a walk past its scheduled end names the time instead of counting below zero`() {
        // Two minutes late and still short of the stop. Without a fix the schedule
        // would have closed this leg 30s after its end, so being late on foot is a
        // state only a live position can reach — and it is the state a countdown
        // would render as a negative or a flat "0 min".
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
            detail.endsWith("Scheduled ${formatTime(iso(5))}"),
            "a late walk should name the instant it was due, got: $detail"
        )
    }
}
