package com.automatelinux.pt.util

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * Straight-line metres between two coordinates.
 *
 * Equirectangular rather than haversine: over the distances this app deals in — a bus and
 * the person waiting for it — the error is centimetres, and the result is only ever shown
 * rounded to a tenth of a kilometre.
 *
 * Shared because "how far apart are these two points" now has several callers, and the
 * alternative to one function is the same three lines copied with a different sign
 * convention in each.
 */
fun metersBetween(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val dLat = (lat1 - lat2) * 111_320.0
    val dLon = (lon1 - lon2) * 111_320.0 * cos(lat2 * PI / 180.0)
    return sqrt(dLat * dLat + dLon * dLon)
}
