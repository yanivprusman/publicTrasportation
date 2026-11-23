package com.automatelinux.pt.util

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt

/**
 * Which way a rider on a bus is moving, when the GPS will not say.
 *
 * The phone only reports a bearing while moving, and a bus spends much of its time
 * stopped — at the stop where the rider says "I'm on the 73", and at every light. With
 * no heading the server cannot tell the line's two directions apart where they share a
 * street, and on 2026-11-23 it picked the 73 back to Tel Aviv for a rider riding it to
 * Holon, then told him to get off at once. The road behind the rider says which way
 * the bus is going just as well: this keeps the last few minutes of fixes and, when the
 * GPS gives no bearing, takes the direction from where the rider was to where they are.
 */
class RiderHeading(
    /** Fixes older than this say nothing about the bus now (it may have turned). */
    private val maxAgeMs: Long = 3 * 60_000L,
    /** Closer than this, GPS scatter is bigger than the movement. */
    private val minDistanceM: Double = 60.0,
) {
    private data class Fix(val lat: Double, val lon: Double, val atMs: Long)

    private val trail = ArrayDeque<Fix>()
    private var lastBearing: Pair<Int, Long>? = null

    fun clear() {
        trail.clear()
        lastBearing = null
    }

    /** Records a fix and returns the rider's heading in degrees, or null if unknown. */
    fun update(lat: Double, lon: Double, gpsBearing: Int?, nowMs: Long): Int? {
        trail.addLast(Fix(lat, lon, nowMs))
        while (trail.isNotEmpty() && nowMs - trail.first().atMs > maxAgeMs) trail.removeFirst()
        if (gpsBearing != null) {
            lastBearing = gpsBearing to nowMs
            return gpsBearing
        }
        // The most recent earlier fix far enough away to be movement, not scatter.
        val from = trail.lastOrNull { metersBetween(it.lat, it.lon, lat, lon) >= minDistanceM }
        if (from != null) return bearing(from.lat, from.lon, lat, lon)
        // Stopped, and nothing in the trail is far enough: the way it was last moving.
        return lastBearing?.takeIf { nowMs - it.second <= maxAgeMs }?.first
    }

    private fun bearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Int {
        val dy = lat2 - lat1
        val dx = (lon2 - lon1) * cos(lat1 * PI / 180.0)
        val deg = atan2(dx, dy) * 180.0 / PI
        return ((deg + 360.0).roundToInt()) % 360
    }
}
