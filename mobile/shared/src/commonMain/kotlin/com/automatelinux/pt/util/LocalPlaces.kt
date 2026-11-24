package com.automatelinux.pt.util

import com.automatelinux.pt.data.model.GeocodeSuggestion
import com.russhwolf.settings.Settings
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Names the rider gave to coordinates ("Grandma", "the gate by the field") —
 * kept on this device ONLY.
 *
 * The store is a preferences file of its own so it can be kept out of everything
 * that leaves the phone: it is not part of [com.automatelinux.pt.data.model.SyncedState]
 * (never uploaded to the account) and the Android app excludes its file from cloud
 * backup and device transfer. Do not move these into [SettingsStore].
 *
 * A name belongs to a point, not to an exact pair of doubles: the same spot comes
 * back from WhatsApp, a map tap or GPS a few metres apart, so anything within
 * [MATCH_RADIUS_M] of a named point carries its name.
 */
class LocalPlaces(private val prefs: Settings) {
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(GeocodeSuggestion.serializer())

    fun all(): List<GeocodeSuggestion> {
        val s = prefs.getStringOrNull(KEY) ?: return emptyList()
        return try { json.decodeFromString(serializer, s) } catch (_: Exception) { emptyList() }
    }

    /** The rider's name for this point, if they gave one to a point this close. */
    fun nameAt(lat: Double, lon: Double): String? = nearest(lat, lon)?.name

    /** Names this point, replacing any name already given to the same spot. */
    fun save(name: String, lat: Double, lon: Double) {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "A place name cannot be empty" }
        val kept = all().filterNot { distanceM(it.lat, it.lon, lat, lon) <= MATCH_RADIUS_M }
        write(listOf(GeocodeSuggestion(name = trimmed, lat = lat, lon = lon)) + kept)
    }

    /** Forgets the name given to this spot. */
    fun remove(lat: Double, lon: Double) {
        write(all().filterNot { distanceM(it.lat, it.lon, lat, lon) <= MATCH_RADIUS_M })
    }

    private fun nearest(lat: Double, lon: Double): GeocodeSuggestion? =
        all().map { it to distanceM(it.lat, it.lon, lat, lon) }
            .filter { it.second <= MATCH_RADIUS_M }
            .minByOrNull { it.second }
            ?.first

    private fun write(places: List<GeocodeSuggestion>) {
        prefs.putString(KEY, json.encodeToString(serializer, places))
    }

    companion object {
        private const val KEY = "named_places"
        const val MATCH_RADIUS_M = 50.0

        /** Equirectangular distance: exact enough at 50 m, and needs no trig library. */
        fun distanceM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val rad = kotlin.math.PI / 180
            val x = (lon2 - lon1) * rad * kotlin.math.cos((lat1 + lat2) / 2 * rad)
            val y = (lat2 - lat1) * rad
            return kotlin.math.sqrt(x * x + y * y) * 6_371_000.0
        }
    }
}
