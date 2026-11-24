package com.automatelinux.pt.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GeoUriTest {
    @Test
    fun plainPoint() {
        assertEquals(GeoUri.Point(32.141579, 34.800092, null), GeoUri.parse("geo:32.141579,34.800092"))
    }

    @Test
    fun uncertaintyAndZoomAreIgnored() {
        assertEquals(GeoUri.Point(32.14, 34.8, null), GeoUri.parse("geo:32.14,34.8;u=35?z=16"))
    }

    @Test
    fun markerInQueryWinsOverMapCentre() {
        assertEquals(
            GeoUri.Point(32.14, 34.8, "Cafe Nimrod"),
            GeoUri.parse("geo:0,0?q=32.14,34.8(Cafe%20Nimrod)")
        )
    }

    @Test
    fun hebrewLabelIsDecoded() {
        assertEquals(
            GeoUri.Point(32.14, 34.8, "תחנה מרכזית"),
            GeoUri.parse("geo:32.14,34.8?q=32.14,34.8(%D7%AA%D7%97%D7%A0%D7%94+%D7%9E%D7%A8%D7%9B%D7%96%D7%99%D7%AA)")
        )
    }

    @Test
    fun textQueryNamesThePathPoint() {
        assertEquals(GeoUri.Point(32.14, 34.8, "Cafe Nimrod"), GeoUri.parse("geo:32.14,34.8?q=Cafe+Nimrod"))
    }

    @Test
    fun addressOnlyHasNoPoint() {
        assertNull(GeoUri.parse("geo:0,0?q=Herzl+1+Tel+Aviv"))
    }

    @Test
    fun nullIslandIsNotADestination() {
        assertNull(GeoUri.parse("geo:0,0"))
    }

    @Test
    fun outOfRangeAndOtherSchemesAreRejected() {
        assertNull(GeoUri.parse("geo:95,34"))
        assertNull(GeoUri.parse("https://pt.dev.ya-niv.com/?to=32,34"))
    }
}
