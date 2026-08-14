package com.automatelinux.pt.ui.routing

import com.automatelinux.pt.util.AppStrings
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.toLocalDateTime

/**
 * Which day a printed time belongs to.
 *
 * Every time in this app is printed as "HH:mm", so a search made at 21:58 shows
 * "06:35" for a bus that leaves the next morning with nothing to say so — and the
 * Fastest sort orders by trip length, not by clock, so that card can sit ABOVE
 * one leaving tonight. The times are correct and the day is missing, which is the
 * one failure mode that makes a user miss a bus by a whole day.
 *
 * Days are counted as CALENDAR days in the device's zone, not 24-hour blocks:
 * a 23:50 search returning 00:10 is "tomorrow" twenty minutes later, because that
 * is how a person reads a date.
 */

private val sysTz get() = TimeZone.currentSystemDefault()

/** The local calendar date of an ISO-8601 instant, or null if it cannot be parsed. */
fun localDateOf(isoString: String): LocalDate? = try {
    Instant.parse(isoString).toLocalDateTime(sysTz).date
} catch (_: Exception) {
    null
}

/**
 * Calendar days from [now] to [isoString]: 0 today, 1 tomorrow, -1 yesterday.
 * Null when the timestamp is unparsable — an unknown day is left unlabelled rather
 * than guessed, the same rule the boarding line already follows for unknown times.
 */
fun dayOffsetFromNow(isoString: String, now: Instant): Int? {
    val date = localDateOf(isoString) ?: return null
    return now.toLocalDateTime(sysTz).date.daysUntil(date)
}

/** Calendar days between two ISO timestamps — how many midnights a trip crosses. */
fun dayOffsetBetween(fromIso: String, toIso: String): Int? {
    val from = localDateOf(fromIso) ?: return null
    val to = localDateOf(toIso) ?: return null
    return from.daysUntil(to)
}

/**
 * The day badge for a departure: null for today (the common case stays clean),
 * "Tomorrow" for the next day, and a real date past that — a search for next week
 * must not read as "tomorrow".
 */
fun departureDayLabel(isoString: String, now: Instant, strings: AppStrings): String? {
    val offset = dayOffsetFromNow(isoString, now) ?: return null
    val date = localDateOf(isoString) ?: return null
    return when (offset) {
        0 -> null
        1 -> strings.dayTomorrow
        -1 -> strings.dayYesterday
        else -> strings.formatShortDate(
            date.dayOfMonth,
            date.monthNumber,
            date.dayOfWeek.isoDayNumber
        )
    }
}

/**
 * "+1" for a trip that lands after midnight, as its own string so it can be drawn
 * as a separate mark: appending it inside the "21:55 - 23:47" text would put a
 * Latin-digit suffix inside a bidi run and let Hebrew layout reorder it.
 */
fun nextDayMarker(fromIso: String, toIso: String): String? {
    val days = dayOffsetBetween(fromIso, toIso) ?: return null
    return if (days > 0) "+$days" else null
}
