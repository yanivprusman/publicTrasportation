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
    /** Fixes older than this say nothing about the bus's direction now (it may have turned). */
    private val maxAgeMs: Long = 3 * 60_000L,
    /** Closer than this, GPS scatter is bigger than the movement. */
    private val minDistanceM: Double = 60.0,
    /** How long the trail is kept for [earlierFix]; the server accepts up to 15 minutes. */
    private val trailAgeMs: Long = 10 * 60_000L,
    /** An earlier fix closer than this is the same stretch of road, which proves nothing. */
    private val earlierFixMinDistanceM: Double = 300.0,
) {
    /** Where the rider was [ageSeconds] ago. */
    data class EarlierFix(val lat: Double, val lon: Double, val ageSeconds: Int)

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
        while (trail.isNotEmpty() && nowMs - trail.first().atMs > trailAgeMs) trail.removeFirst()
        if (gpsBearing != null) {
            lastBearing = gpsBearing to nowMs
            return gpsBearing
        }
        // The most recent earlier fix far enough away to be movement, not scatter.
        val from = trail.lastOrNull {
            nowMs - it.atMs <= maxAgeMs && metersBetween(it.lat, it.lon, lat, lon) >= minDistanceM
        }
        if (from != null) return bearing(from.lat, from.lon, lat, lon)
        // Stopped, and nothing in the trail is far enough: the way it was last moving.
        return lastBearing?.takeIf { nowMs - it.second <= maxAgeMs }?.first
    }

    /**
     * Where the rider was a few minutes ago, far enough back to be a different stretch
     * of road. Heading alone cannot tell a line's two directions apart where a loop runs
     * them the same way down one street (the 73 in Holon, 2026-11-23); the trip that
     * passed this earlier point first is the one carrying the rider.
     */
    fun earlierFix(nowMs: Long): EarlierFix? {
        val here = trail.lastOrNull() ?: return null
        val then = trail.lastOrNull {
            metersBetween(it.lat, it.lon, here.lat, here.lon) >= earlierFixMinDistanceM
        } ?: return null
        return EarlierFix(then.lat, then.lon, ((nowMs - then.atMs) / 1000).toInt())
    }

    private fun bearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Int {
        val dy = lat2 - lat1
        val dx = (lon2 - lon1) * cos(lat1 * PI / 180.0)
        val deg = atan2(dx, dy) * 180.0 / PI
        return ((deg + 360.0).roundToInt()) % 360
    }
}
