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
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** What makes a started trip come back: still on the timetable, never ended by hand. */
class StartedTripTest {

    private val t0 = Instant.parse("2026-10-12T09:00:00Z").toEpochMilliseconds()
    private fun min(m: Int) = t0 + m * 60_000L
    private fun iso(m: Int) = Instant.fromEpochMilliseconds(min(m)).toString()

    private fun itinerary(at: Int, fromLat: Double, toLat: Double = 31.1) = Itinerary(
        duration = 1800,
        startTime = iso(at + 0),
        endTime = iso(at + 30),
        transfers = 0,
        legs = listOf(
            RouteLeg(
                mode = TransitMode.BUS,
                from = Place("A", fromLat, 34.8),
                to = Place("B", toLat, 34.8),
                startTime = iso(at + 0),
                endTime = iso(at + 30),
                duration = 1800,
                routeShortName = "64"
            )
        )
    )

    private fun trip(startedMin: Int, ended: Boolean = false, fromLat: Double = 31.0) =
        StartedTrip(
            startedAtMs = min(startedMin),
            itinerary = itinerary(startedMin, fromLat),
            endedAtMs = if (ended) min(startedMin + 1) else null
        )

    @Test
    fun resumableUntilGraceAfterTimetabledEnd() {
        val t = trip(0)
        assertTrue(t.resumable(min(10)))
        assertTrue(t.resumable(min(30 + 29)))
        assertFalse(t.resumable(min(30 + 31)))
    }

    @Test
    fun arrivedIsNeverResumable() {
        val t = trip(0).copy(cursor = JourneyCursor(legIndex = 1, arrived = true))
        assertFalse(t.resumable(min(5)))
    }

    @Test
    fun onlyAnInterruptedTripResumesByItself() {
        assertTrue(trip(0).interrupted(min(5)))
        assertFalse(trip(0, ended = true).interrupted(min(5)))
        assertTrue(trip(0, ended = true).resumable(min(5)))
    }

    @Test
    fun startingATripClosesTheOpenOne() {
        val open = trip(0, fromLat = 32.0)
        val list = recordStartedTrip(listOf(open), trip(5), min(5))
        assertEquals(listOf(min(5), min(0)), list.map { it.startedAtMs })
        assertNull(list[0].endedAtMs)
        assertEquals(min(5), list[1].endedAtMs)
    }

    @Test
    fun resumingKeepsOneEntryAndReopensIt() {
        val ended = trip(0, ended = true)
        val list = recordStartedTrip(listOf(ended), ended, min(5))
        assertEquals(1, list.size)
        assertNull(list[0].endedAtMs)
    }

    @Test
    fun aFinishedRunOfTheSameEndsMakesWay() {
        val yesterday = trip(-24 * 60, ended = true)
        val list = recordStartedTrip(listOf(yesterday), trip(0), min(0))
        assertEquals(listOf(min(0)), list.map { it.startedAtMs })
    }

    @Test
    fun keepsAtMostTen() {
        val many = (1..15).map { trip(-it * 24 * 60, ended = true, fromLat = 30.0 + it) }
        assertEquals(StartedTrip.MAX_KEPT, recordStartedTrip(many, trip(0), min(0)).size)
    }

    @Test
    fun survivesARoundTripThroughStorage() {
        val t = trip(0).copy(cursor = JourneyCursor(legIndex = 0, stopIndex = 2, alightAlerted = setOf(0)))
        val json = Json { ignoreUnknownKeys = true }
        val s = json.encodeToString(ListSerializer(StartedTrip.serializer()), listOf(t))
        assertEquals(listOf(t), json.decodeFromString(ListSerializer(StartedTrip.serializer()), s))
    }
}
