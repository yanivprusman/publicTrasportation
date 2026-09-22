package com.automatelinux.pt.ui.routing

import com.automatelinux.pt.data.model.LineSuggestion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RidingSuggestionsTest {

    // שוק עירוני, Be'er Sheva, 2026-09-22 10:31, heading west: the server's order, best
    // timetable fit first. Line 33 was running ten minutes late and came far down.
    private val passingHere = listOf(
        "4", "6", "48", "397", "9", "450", "5", "7", "370", "348", "130", "20",
        "27א", "3", "22", "33", "34", "330", "333", "32"
    ).map { LineSuggestion(line = it) }

    private fun lines(typed: String) = matchingSuggestions(passingHere, typed).map { it.line }

    @Test
    fun nothingTypedShowsTheBestGuesses() {
        assertEquals(listOf("4", "6", "48", "397", "9", "450"), lines(""))
    }

    @Test
    fun theTypedLineComesFirstHoweverLateItRuns() {
        assertEquals(listOf("33", "330", "333"), lines("33"))
    }

    @Test
    fun aPrefixKeepsTheServersOrder() {
        assertEquals(listOf("3", "397", "370", "348", "33", "34"), lines("3"))
    }

    @Test
    fun digitsFindALineWithALetter() {
        assertEquals(listOf("27א"), lines("27"))
    }

    @Test
    fun spacesAroundTheNumberAreIgnored() {
        assertEquals(listOf("33", "330", "333"), lines(" 33 "))
    }

    @Test
    fun aLineNotPassingHereMatchesNothing() {
        assertTrue(lines("99").isEmpty())
    }
}
