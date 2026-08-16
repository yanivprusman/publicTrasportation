package com.automatelinux.pt.util

import kotlinx.datetime.toLocalDateTime

/**
 * An ISO instant as the wall clock the rider reads, in their own zone.
 *
 * Shared rather than owned by the results card, for the same reason [formatDistance]
 * is: the step list, the itinerary timeline, the journey panel and the lock-screen
 * notification all name the same instants, and two subtly different copies of this
 * rule is how one screen ends up disagreeing with another about what time the bus is.
 *
 * A string that will not parse is shown as the clock part of the ISO text rather than
 * hidden — a visibly odd time is a bug report; a silently missing one is not.
 */
fun formatTime(isoString: String): String {
    return try {
        // Instant.parse accepts ISO-8601 with offset (both ZonedDateTime and OffsetDateTime forms).
        val local = kotlinx.datetime.Instant.parse(isoString)
            .toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault())
        val h = local.hour.toString().padStart(2, '0')
        val m = local.minute.toString().padStart(2, '0')
        "$h:$m"
    } catch (_: Exception) {
        isoString.substringAfter("T").take(5)
    }
}
