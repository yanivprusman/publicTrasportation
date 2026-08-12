package com.automatelinux.pt.util

/**
 * Metres below a kilometre, one decimal of a kilometre above it.
 *
 * Shared rather than private so every distance the app shows reads the same way. This
 * lived inside TrackedBusCard until the live-buses hint needed the identical rule, which
 * is the moment a second, subtly different copy usually appears.
 */
fun formatDistance(meters: Int, strings: AppStrings): String =
    if (meters < 1000) {
        strings.distanceM(meters)
    } else {
        val tenths = (meters + 50) / 100
        strings.distanceKm("${tenths / 10}.${tenths % 10}")
    }
