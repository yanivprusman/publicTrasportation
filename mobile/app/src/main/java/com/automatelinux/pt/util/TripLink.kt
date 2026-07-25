package com.automatelinux.pt.util

import android.net.Uri
import com.automatelinux.pt.BuildConfig
import com.automatelinux.pt.data.model.GeocodeSuggestion
import kotlinx.datetime.Instant
import java.util.Locale

/**
 * Trip links use the public web app's URL format (from/fromName/to/toName/time/arriveBy),
 * so a shared link opens the full journey in the web app for anyone, and in this app
 * when it handles the link. A missing time means "leave now", resolved when opened.
 */
object TripLink {

    data class SharedTrip(
        val origin: GeocodeSuggestion,
        val destination: GeocodeSuggestion,
        val departureTime: Instant?,
        val arriveBy: Boolean
    )

    private val baseUrl =
        if (BuildConfig.FEEDBACK_ENABLED) "https://pt.dev.ya-niv.com" else "https://pt.prod.ya-niv.com"

    fun build(
        origin: GeocodeSuggestion,
        destination: GeocodeSuggestion,
        departureTime: Instant?,
        arriveBy: Boolean,
        /**
         * Sharer's anonymous install id, carried so an install that starts from
         * this link can be credited back. Attribution only — [parse] ignores it,
         * so it can never affect which journey the recipient sees.
         */
        referrerInstallId: String? = null
    ): String {
        val builder = Uri.parse(baseUrl).buildUpon()
            .appendQueryParameter("from", formatCoords(origin.lat, origin.lon))
            .appendQueryParameter("fromName", origin.name)
            .appendQueryParameter("to", formatCoords(destination.lat, destination.lon))
            .appendQueryParameter("toName", destination.name)
        if (departureTime != null) builder.appendQueryParameter("time", departureTime.toString())
        if (arriveBy) builder.appendQueryParameter("arriveBy", "1")
        if (!referrerInstallId.isNullOrBlank()) builder.appendQueryParameter("ref", referrerInstallId)
        return builder.build().toString()
    }

    fun parse(uri: Uri?): SharedTrip? {
        if (uri == null) return null
        val from = parseCoords(uri.getQueryParameter("from")) ?: return null
        val to = parseCoords(uri.getQueryParameter("to")) ?: return null

        val originName = uri.getQueryParameter("fromName")?.trim().takeUnless { it.isNullOrEmpty() }
            ?: formatCoords(from.first, from.second)
        val destinationName = uri.getQueryParameter("toName")?.trim().takeUnless { it.isNullOrEmpty() }
            ?: formatCoords(to.first, to.second)

        // An unparseable time can only be read as "leave now" — same rule as the web app.
        val departureTime = uri.getQueryParameter("time")?.let {
            try { Instant.parse(it) } catch (_: Exception) { null }
        }

        return SharedTrip(
            origin = GeocodeSuggestion(name = originName, lat = from.first, lon = from.second),
            destination = GeocodeSuggestion(name = destinationName, lat = to.first, lon = to.second),
            departureTime = departureTime,
            arriveBy = uri.getQueryParameter("arriveBy") == "1"
        )
    }

    private fun formatCoords(lat: Double, lon: Double): String =
        String.format(Locale.US, "%.5f,%.5f", lat, lon)

    private fun parseCoords(value: String?): Pair<Double, Double>? {
        if (value == null) return null
        val parts = value.split(",")
        if (parts.size != 2) return null
        val lat = parts[0].toDoubleOrNull() ?: return null
        val lon = parts[1].toDoubleOrNull() ?: return null
        if (lat < -90 || lat > 90 || lon < -180 || lon > 180) return null
        return Pair(lat, lon)
    }
}
