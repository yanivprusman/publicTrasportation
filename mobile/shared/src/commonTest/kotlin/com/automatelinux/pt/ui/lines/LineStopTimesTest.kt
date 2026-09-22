package com.automatelinux.pt.ui.lines

import com.automatelinux.pt.data.model.RouteStopItem
import com.automatelinux.pt.data.model.UpcomingCall
import kotlin.test.Test
import kotlin.test.assertEquals

class LineStopTimesTest {

    private fun stops(vararg codes: String) = codes.map { RouteStopItem(stopCode = it) }
    private fun call(code: String, time: String) =
        UpcomingCall(stopCode = code, expectedArrival = "2026-09-22T$time:00+03:00")

    // Line 60 toward Be'er Sheva, 2026-09-22: the bus was polled at מדרשת בן גוריון
    // (13868), so the feed reports it and every stop after — nothing before.
    @Test
    fun stopsBeforeThePolledOneHaveNoTime() {
        val times = stopArrivalTimes(
            stops("13841", "13868", "13850", "13891"),
            listOf(call("13868", "09:14"), call("13850", "09:16"), call("13891", "09:18"))
        )
        assertEquals(
            listOf(
                null,
                "2026-09-22T09:14:00+03:00",
                "2026-09-22T09:16:00+03:00",
                "2026-09-22T09:18:00+03:00"
            ),
            times
        )
    }

    // The bus runs a variant that skips a stop the representative trip makes: the
    // skipped row stays blank, and every row after it still gets its own stop's time.
    @Test
    fun aVariantThatSkipsAStopDoesNotShiftTheTimes() {
        val times = stopArrivalTimes(
            stops("A", "B", "C", "D"),
            listOf(call("A", "10:00"), call("C", "10:05"), call("D", "10:07"))
        )
        assertEquals(
            listOf(
                "2026-09-22T10:00:00+03:00",
                null,
                "2026-09-22T10:05:00+03:00",
                "2026-09-22T10:07:00+03:00"
            ),
            times
        )
    }

    // A loop starting and ending at T. With one lap done, T's only remaining call is the
    // final one — it belongs on the last row, not on the start the bus already left.
    @Test
    fun aLoopStopsRemainingVisitGoesToItsLastRow() {
        val times = stopArrivalTimes(
            stops("T", "X", "Y", "T"),
            listOf(call("Y", "11:00"), call("T", "11:04"))
        )
        assertEquals(
            listOf(null, null, "2026-09-22T11:00:00+03:00", "2026-09-22T11:04:00+03:00"),
            times
        )
    }

    @Test
    fun aLoopPolledAtItsStartTimesBothVisits() {
        val times = stopArrivalTimes(
            stops("T", "X", "T"),
            listOf(call("T", "12:00"), call("X", "12:10"), call("T", "12:20"))
        )
        assertEquals(
            listOf(
                "2026-09-22T12:00:00+03:00",
                "2026-09-22T12:10:00+03:00",
                "2026-09-22T12:20:00+03:00"
            ),
            times
        )
    }
}
