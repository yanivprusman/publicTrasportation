package com.automatelinux.pt.util

import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class LocalPlacesTest {
    private val pin = Pair(32.141579, 34.800092)

    @Test
    fun aNamedPointIsFoundAgain() {
        val places = LocalPlaces(MapSettings())
        places.save("  Grandma  ", pin.first, pin.second)
        assertEquals("Grandma", places.nameAt(pin.first, pin.second))
    }

    @Test
    fun theSameSpotAFewMetresOffCarriesTheName() {
        val places = LocalPlaces(MapSettings())
        places.save("Grandma", pin.first, pin.second)
        // ~20 m north
        assertEquals("Grandma", places.nameAt(pin.first + 0.00018, pin.second))
    }

    @Test
    fun aDifferentPlaceDoesNot() {
        val places = LocalPlaces(MapSettings())
        places.save("Grandma", pin.first, pin.second)
        // ~110 m north
        assertNull(places.nameAt(pin.first + 0.001, pin.second))
    }

    @Test
    fun renamingReplacesInsteadOfDuplicating() {
        val places = LocalPlaces(MapSettings())
        places.save("Grandma", pin.first, pin.second)
        places.save("Savta", pin.first + 0.0001, pin.second)
        assertEquals(1, places.all().size)
        assertEquals("Savta", places.nameAt(pin.first, pin.second))
    }

    @Test
    fun nearestNameWins() {
        val places = LocalPlaces(MapSettings())
        places.save("A", pin.first, pin.second)
        places.save("B", pin.first + 0.0008, pin.second) // ~90 m away: both kept
        assertEquals(2, places.all().size)
        assertEquals("B", places.nameAt(pin.first + 0.0006, pin.second))
    }

    @Test
    fun forgetting() {
        val places = LocalPlaces(MapSettings())
        places.save("Grandma", pin.first, pin.second)
        places.remove(pin.first, pin.second)
        assertNull(places.nameAt(pin.first, pin.second))
    }

    @Test
    fun emptyNameIsRefused() {
        assertFailsWith<IllegalArgumentException> { LocalPlaces(MapSettings()).save("  ", 1.0, 1.0) }
    }
}
