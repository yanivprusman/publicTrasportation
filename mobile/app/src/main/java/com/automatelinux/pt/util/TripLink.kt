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
        /** null = "from wherever I am when I tap it": the link carried no `from`. */
        val origin: GeocodeSuggestion?,
        val destination: GeocodeSuggestion,
        /** false = the link only had coordinates; [destination]'s name is those numbers. */
        val destinationNamed: Boolean,
        val departureTime: Instant?,
        val arriveBy: Boolean
    )

    private val baseUrl =
        if (BuildConfig.FEEDBACK_ENABLED) "https://pt.dev.ya-niv.com" else "https://pt.prod.ya-niv.com"

    /** The page a live-journey share opens: the rider's position, watched live. */
    fun liveJourneyUrl(token: String): String = "$baseUrl/journey/$token"

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
        // A map pin from another app (WhatsApp location, Google Maps share):
        // the pin is the destination, the start is wherever the rider stands.
        if (uri.scheme.equals("geo", ignoreCase = true)) {
            val pin = GeoUri.parse(uri.toString()) ?: return null
            return SharedTrip(
                origin = null,
                destination = GeocodeSuggestion(
                    name = pin.label ?: formatCoords(pin.lat, pin.lon),
                    lat = pin.lat,
                    lon = pin.lon
                ),
                destinationNamed = pin.label != null,
                departureTime = null,
                arriveBy = false
            )
        }
        // No `from` at all means "start where I'm standing" — a link saved once and
        // tapped from anywhere. A `from` that is present but malformed is still a
        // broken link, not a request for GPS.
        val fromRaw = uri.getQueryParameter("from")
        val from = if (fromRaw == null) null else parseCoords(fromRaw) ?: return null
        val to = parseCoords(uri.getQueryParameter("to")) ?: return null

        val origin = from?.let {
            val name = uri.getQueryParameter("fromName")?.trim().takeUnless { n -> n.isNullOrEmpty() }
                ?: formatCoords(it.first, it.second)
            GeocodeSuggestion(name = name, lat = it.first, lon = it.second)
        }
        val toName = uri.getQueryParameter("toName")?.trim().takeUnless { it.isNullOrEmpty() }
        val destinationName = toName ?: formatCoords(to.first, to.second)

        // An unparseable time can only be read as "leave now" — same rule as the web app.
        val departureTime = uri.getQueryParameter("time")?.let {
            try { Instant.parse(it) } catch (_: Exception) { null }
        }

        return SharedTrip(
            origin = origin,
            destination = GeocodeSuggestion(name = destinationName, lat = to.first, lon = to.second),
            destinationNamed = toName != null,
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
