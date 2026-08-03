package com.automatelinux.pt.util

import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Formats to a fixed number of decimal places.
 *
 * Kotlin has no common `String.format` — that is a JVM API. Code using it compiles happily
 * for Android and then fails to resolve on iOS, which is exactly how it got into shared
 * code here unnoticed: the Android build cannot catch it.
 *
 * Deliberately does its own rounding rather than leaning on `toString()`, whose output for
 * a Double is not fixed-width ("2.0", "2.5", "2.4999999999999996") and would show the user
 * a distance like "2.4999999999999996 km".
 */
fun Double.toFixed(decimals: Int): String {
    require(decimals >= 0) { "decimals must not be negative" }

    var factor = 1L
    repeat(decimals) { factor *= 10 }

    val scaled = (this * factor).roundToLong()
    val whole = scaled / factor
    if (decimals == 0) return whole.toString()

    val fraction = abs(scaled % factor).toString().padStart(decimals, '0')
    // A negative value between -1 and 0 rounds to a whole part of 0, which would print as
    // "0.5" instead of "-0.5"; carry the sign explicitly.
    val sign = if (scaled < 0 && whole == 0L) "-" else ""
    return "$sign$whole.$fraction"
}
