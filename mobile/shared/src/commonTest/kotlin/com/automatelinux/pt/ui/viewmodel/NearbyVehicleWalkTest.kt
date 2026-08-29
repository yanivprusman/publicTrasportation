package com.automatelinux.pt.ui.viewmodel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The stop rule for the live-buses outward walk.
 *
 * Every case here is a place the search has actually been wrong: a village where the
 * nearest bus is tens of kilometres out, and a city centre where the budget ran out
 * inside the screen and the map reported on ground the user could not see the edges of.
 */
class NearbyVehicleWalkTest {

    // Neve Tzedek, 2026-08-29: 20 stops covered 560 m of an 840 m screen and the map
    // said "no live buses within 560 m" without asking the outer half of the view.
    @Test
    fun keepsWalkingWhileTheScreenShowsUnaskedGround() {
        assertFalse(
            nearbyWalkShouldStop(
                queried = 20, busesFound = 0, reachedMeters = 560, viewportRadiusMeters = 840
            )
        )
    }

    // Same screen, now actually covered: the ordinary budget is spent and the emptiness
    // is a real answer about the whole view.
    @Test
    fun stopsOnceTheScreenIsCoveredAndTheBudgetIsSpent() {
        assertTrue(
            nearbyWalkShouldStop(
                queried = 45, busesFound = 0, reachedMeters = 840, viewportRadiusMeters = 840
            )
        )
    }

    // A zoomed-out map has more stops inside it than any poll may spend. The walk gives
    // up rather than fanning out hundreds of requests; the UI then asks the user to zoom.
    @Test
    fun theWallWinsEvenWithTheScreenUncovered() {
        assertTrue(
            nearbyWalkShouldStop(
                queried = 60, busesFound = 0, reachedMeters = 900, viewportRadiusMeters = 5_000
            )
        )
    }

    // Midreshet Ben Gurion: buses far outside the screen are the point of walking out.
    @Test
    fun stopsWithEnoughBusesOnceTheScreenIsCovered() {
        assertTrue(
            nearbyWalkShouldStop(
                queried = 10, busesFound = 5, reachedMeters = 12_000, viewportRadiusMeters = 800
            )
        )
    }

    // Covered and far out, but only two buses in hand — worth the rest of the budget.
    @Test
    fun keepsWalkingForMoreBusesWithinTheOrdinaryBudget() {
        assertFalse(
            nearbyWalkShouldStop(
                queried = 10, busesFound = 2, reachedMeters = 12_000, viewportRadiusMeters = 800
            )
        )
    }

    // The dense case still stops early: five buses two rounds in, screen covered.
    @Test
    fun cityCentreStillStopsEarlyWhenBusesAreReporting() {
        assertTrue(
            nearbyWalkShouldStop(
                queried = 10, busesFound = 5, reachedMeters = 300, viewportRadiusMeters = 300
            )
        )
    }

    // Pacing: a poll that spent three budgets waits three intervals, so lifting the cap
    // raises the peak cost of a poll without raising the sustained request rate.
    @Test
    fun delayScalesWithWhatThePollCost() {
        assertEquals(15_000L, nearbyPollDelayMs(5))
        assertEquals(15_000L, nearbyPollDelayMs(20))
        assertEquals(30_000L, nearbyPollDelayMs(40))
        assertEquals(45_000L, nearbyPollDelayMs(60))
    }
}
