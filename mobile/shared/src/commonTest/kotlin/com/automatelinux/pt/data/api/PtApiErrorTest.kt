package com.automatelinux.pt.data.api

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A failed request must reach the screen as the server's own explanation. The
 * regression this guards: an `{error, message}` body force-parsed as the payload
 * type put a raw kotlinx-serialization crash ("Field 'itineraries' is required for
 * type ... RouteResult, but it was missing at path: $") in front of the user.
 */
class PtApiErrorTest {

    @Test
    fun errorAndMessageAreBothSpoken() {
        assertEquals(
            "Failed to fetch route: MOTIS returned 502",
            apiErrorMessage(502, """{"error":"Failed to fetch route","message":"MOTIS returned 502"}""")
        )
    }

    @Test
    fun errorAloneIsEnough() {
        assertEquals(
            "Missing required parameters: from, to (format: lat,lon)",
            apiErrorMessage(400, """{"error":"Missing required parameters: from, to (format: lat,lon)"}""")
        )
    }

    @Test
    fun htmlBodyFallsBackToTheStatus() {
        assertEquals(
            "Server error (HTTP 502)",
            apiErrorMessage(502, "<html><body>Bad Gateway</body></html>")
        )
    }

    @Test
    fun emptyBodyFallsBackToTheStatus() {
        assertEquals("Server error (HTTP 503)", apiErrorMessage(503, ""))
    }

    @Test
    fun jsonOfSomeOtherShapeFallsBackToTheStatus() {
        assertEquals("Server error (HTTP 500)", apiErrorMessage(500, """{"ok":true}"""))
    }
}
