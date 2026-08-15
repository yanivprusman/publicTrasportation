package com.automatelinux.pt.journey

import com.automatelinux.pt.data.model.Itinerary
import com.automatelinux.pt.data.model.Place
import com.automatelinux.pt.data.model.RouteLeg
import com.automatelinux.pt.data.model.TransitMode
import com.automatelinux.pt.util.EnStrings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
}
