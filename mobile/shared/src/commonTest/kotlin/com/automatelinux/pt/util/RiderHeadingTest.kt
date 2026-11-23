package com.automatelinux.pt.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RiderHeadingTest {
    @Test
    fun gpsBearingWinsWhenThereIsOne() {
        val h = RiderHeading()
        assertEquals(90, h.update(32.0, 34.0, 90, 0))
    }

    @Test
    fun noBearingAndNoHistoryIsUnknown() {
        assertNull(RiderHeading().update(32.0, 34.0, null, 0))
    }

    @Test
    fun theRoadBehindGivesTheDirection() {
        // 2026-11-23: the rider's 73 went south through Jaffa, the GPS gave no bearing.
        val h = RiderHeading()
        h.update(32.0560, 34.7640, null, 0)
        val heading = h.update(32.0530706, 34.7653712, null, 60_000)!!
        assertTrue(heading in 150..200, "south-ish, was $heading")
    }

    @Test
    fun gpsScatterAtAStopIsNotMovement() {
        val h = RiderHeading()
        h.update(32.05300, 34.76530, null, 0)
        assertNull(h.update(32.05310, 34.76540, null, 10_000))
    }

    @Test
    fun stoppedAtALightKeepsTheLastBearing() {
        val h = RiderHeading()
        h.update(32.0530, 34.7653, 180, 0)
        assertEquals(180, h.update(32.0530, 34.7653, null, 30_000))
    }

    @Test
    fun oldHistoryIsForgotten() {
        val h = RiderHeading()
        h.update(32.0600, 34.7640, 10, 0)
        assertNull(h.update(32.0530, 34.7653, null, 10 * 60_000))
    }
}
