package com.automatelinux.pt.util

import io.ktor.http.decodeURLQueryComponent

/**
 * Reads a `geo:` URI (RFC 5870 plus Android's `?q=` extension) — what WhatsApp,
 * Google Maps and most apps hand out for a map pin — as one point.
 *
 * Forms seen in the wild:
 *  - `geo:32.14,34.80`                     the point itself
 *  - `geo:32.14,34.80;u=35?z=16`           with uncertainty / zoom, ignored
 *  - `geo:0,0?q=32.14,34.80(Cafe)`         the marker is in `q`, the path is only the map centre
 *  - `geo:32.14,34.80?q=Cafe+Nimrod`       a named point at the path's coordinates
 *
 * A pin carrying only an address (`geo:0,0?q=Herzl+1+Tel+Aviv`) has no point,
 * and is returned as null: this app does not guess a place from text it was
 * handed by another app.
 */
object GeoUri {

    data class Point(val lat: Double, val lon: Double, val label: String?)

    fun parse(uri: String): Point? {
        if (!uri.startsWith("geo:", ignoreCase = true)) return null
        val rest = uri.substring(4)
        val queryStart = rest.indexOf('?')
        val path = if (queryStart >= 0) rest.substring(0, queryStart) else rest
        val query = if (queryStart >= 0) rest.substring(queryStart + 1) else ""

        val pathCoords = coords(path.substringBefore(';'))
        val q = query.split('&')
            .firstOrNull { it.startsWith("q=") }
            ?.substring(2)
            ?.let { runCatching { it.decodeURLQueryComponent(plusIsSpace = true) }.getOrNull() }
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

        if (q != null) {
            // `lat,lon(Label)` — the marker, whatever the path says.
            val parenStart = q.indexOf('(')
            val qPoint = if (parenStart >= 0) q.substring(0, parenStart) else q
            val qCoords = coords(qPoint)
            if (qCoords != null) {
                val label = if (parenStart >= 0) q.substring(parenStart + 1).removeSuffix(")").trim() else ""
                return Point(qCoords.first, qCoords.second, label.ifEmpty { null })
            }
            // Text in q: a name for the path's point — but only if the path IS a point.
            return pathCoords?.takeUnless { isNullIsland(it) }?.let { Point(it.first, it.second, q) }
        }
        return pathCoords?.takeUnless { isNullIsland(it) }?.let { Point(it.first, it.second, null) }
    }

    /** `geo:0,0` is the "no centre given" placeholder, never a real destination. */
    private fun isNullIsland(c: Pair<Double, Double>) = c.first == 0.0 && c.second == 0.0

    private fun coords(value: String): Pair<Double, Double>? {
        val parts = value.split(',')
        if (parts.size < 2 || parts.size > 3) return null // 3 = altitude, allowed by RFC 5870
        val lat = parts[0].trim().toDoubleOrNull() ?: return null
        val lon = parts[1].trim().toDoubleOrNull() ?: return null
        if (lat < -90 || lat > 90 || lon < -180 || lon > 180) return null
        return Pair(lat, lon)
    }
}
