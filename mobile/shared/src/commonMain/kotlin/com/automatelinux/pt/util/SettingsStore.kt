package com.automatelinux.pt.util

import com.automatelinux.pt.data.model.GeocodeSuggestion
import com.russhwolf.settings.Settings
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.PairSerializer
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

// Multiplatform settings store. The platform supplies a `Settings` (SharedPreferences on
// Android, NSUserDefaults on iOS). JSON blobs use kotlinx-serialization.
class SettingsStore(private val prefs: Settings) {
    private val json = Json { ignoreUnknownKeys = true }

    var sheetOpacity: Float
        get() = prefs.getFloat("sheet_opacity", 0.50f)
        set(value) { prefs.putFloat("sheet_opacity", value) }

    var cardOpacity: Float
        get() = prefs.getFloat("card_opacity", 0.6f)
        set(value) { prefs.putFloat("card_opacity", value) }

    var debugAutoSearch: Boolean
        get() = prefs.getBoolean("debug_auto_search", true)
        set(value) { prefs.putBoolean("debug_auto_search", value) }

    var debugExpandSheet: Boolean
        get() = prefs.getBoolean("debug_expand_sheet", true)
        set(value) { prefs.putBoolean("debug_expand_sheet", value) }

    var debugFrom: GeocodeSuggestion
        get() = GeocodeSuggestion(
            name = prefs.getString("debug_from", "אלרום 6 רמת גן"),
            lat = prefs.getFloat("debug_from_lat", 32.0691133f).toDouble(),
            lon = prefs.getFloat("debug_from_lon", 34.8402174f).toDouble()
        )
        set(value) {
            prefs.putString("debug_from", value.name)
            prefs.putFloat("debug_from_lat", value.lat.toFloat())
            prefs.putFloat("debug_from_lon", value.lon.toFloat())
        }

    var debugTo: GeocodeSuggestion
        get() = GeocodeSuggestion(
            name = prefs.getString("debug_to", "המסגר 51 תל אביב"),
            lat = prefs.getFloat("debug_to_lat", 32.0636f).toDouble(),
            lon = prefs.getFloat("debug_to_lon", 34.7878f).toDouble()
        )
        set(value) {
            prefs.putString("debug_to", value.name)
            prefs.putFloat("debug_to_lat", value.lat.toFloat())
            prefs.putFloat("debug_to_lon", value.lon.toFloat())
        }

    var locationIconStyle: String
        get() = prefs.getString("location_icon_style", "dot")
        set(value) { prefs.putString("location_icon_style", value) }

    /** Base map style: "dark" (default, matches the app theme), "light", or "satellite". */
    var mapStyle: String
        get() = prefs.getString("map_style", "dark")
        set(value) { prefs.putString("map_style", value) }

    var language: String
        get() = prefs.getString("language", "en")
        set(value) { prefs.putString("language", value) }

    /** Persisted route-options mode filter as backend api keys ("bus,train,tram"). */
    var routeModes: Set<String>
        get() {
            val s = prefs.getStringOrNull("route_modes") ?: return emptySet()
            return s.split(",").filter { it.isNotBlank() }.toSet()
        }
        set(value) { prefs.putString("route_modes", value.joinToString(",")) }

    /** Persisted max-walk limit in minutes; 0 means no limit. */
    var maxWalkMinutes: Int
        get() = prefs.getInt("max_walk_minutes", 0)
        set(value) { prefs.putInt("max_walk_minutes", value) }

    private val suggestionListSerializer = ListSerializer(GeocodeSuggestion.serializer())

    fun getRecentSearches(): List<GeocodeSuggestion> {
        val s = prefs.getStringOrNull("recent_searches") ?: return emptyList()
        return try { json.decodeFromString(suggestionListSerializer, s) } catch (_: Exception) { emptyList() }
    }

    fun addRecentSearch(suggestion: GeocodeSuggestion) {
        val current = getRecentSearches().toMutableList()
        current.removeAll { it.name == suggestion.name && it.lat == suggestion.lat && it.lon == suggestion.lon }
        current.add(0, suggestion)
        prefs.putString("recent_searches", json.encodeToString(suggestionListSerializer, current.take(10)))
    }

    var homePlace: GeocodeSuggestion?
        get() = getSavedPlace("home")
        set(value) { setSavedPlace("home", value) }

    var workPlace: GeocodeSuggestion?
        get() = getSavedPlace("work")
        set(value) { setSavedPlace("work", value) }

    private fun getSavedPlace(key: String): GeocodeSuggestion? {
        val name = prefs.getStringOrNull("saved_${key}_name") ?: return null
        return GeocodeSuggestion(
            name = name,
            lat = prefs.getFloat("saved_${key}_lat", 0f).toDouble(),
            lon = prefs.getFloat("saved_${key}_lon", 0f).toDouble()
        )
    }

    private fun setSavedPlace(key: String, value: GeocodeSuggestion?) {
        if (value == null) {
            prefs.remove("saved_${key}_name")
            prefs.remove("saved_${key}_lat")
            prefs.remove("saved_${key}_lon")
        } else {
            prefs.putString("saved_${key}_name", value.name)
            prefs.putFloat("saved_${key}_lat", value.lat.toFloat())
            prefs.putFloat("saved_${key}_lon", value.lon.toFloat())
        }
    }

    private val lineSetSerializer = SetSerializer(String.serializer())

    fun getFavoriteLines(): Set<String> {
        val s = prefs.getStringOrNull("favorite_lines") ?: return emptySet()
        return try { json.decodeFromString(lineSetSerializer, s) } catch (_: Exception) { emptySet() }
    }

    fun toggleFavoriteLine(lineName: String): Boolean {
        val current = getFavoriteLines().toMutableSet()
        val added = if (current.contains(lineName)) { current.remove(lineName); false } else { current.add(lineName); true }
        prefs.putString("favorite_lines", json.encodeToString(lineSetSerializer, current))
        return added
    }

    private val stationListSerializer = ListSerializer(PairSerializer(String.serializer(), String.serializer()))

    fun getFavoriteStations(): List<Pair<String, String>> {
        val s = prefs.getStringOrNull("favorite_stations") ?: return emptyList()
        return try { json.decodeFromString(stationListSerializer, s) } catch (_: Exception) { emptyList() }
    }

    fun toggleFavoriteStation(code: String, name: String): Boolean {
        val current = getFavoriteStations().toMutableList()
        val existing = current.indexOfFirst { it.first == code }
        val added = if (existing >= 0) { current.removeAt(existing); false } else { current.add(Pair(code, name)); true }
        prefs.putString("favorite_stations", json.encodeToString(stationListSerializer, current))
        return added
    }

    fun isStationFavorite(code: String): Boolean = getFavoriteStations().any { it.first == code }

    // --- Anonymous analytics ---
    // installId is a random UUID generated on the device. It identifies an
    // install, not a person: no account, no device id, no ad id. Generation is
    // platform-side (java.util.UUID / NSUUID); only storage lives here.

    var installId: String?
        get() = prefs.getStringOrNull("install_id")
        set(value) { if (value != null) prefs.putString("install_id", value) }

    /** Local day ("yyyy-MM-dd") the app was last seen active, for active-day counting. */
    var lastActiveDay: String?
        get() = prefs.getStringOrNull("last_active_day")
        set(value) { if (value != null) prefs.putString("last_active_day", value) }

    /** Days the user actually opened the app — the honest measure of value delivered. */
    var activeDays: Int
        get() = prefs.getInt("active_days", 0)
        set(value) { prefs.putInt("active_days", value) }

    /**
     * Raw Play Install Referrer string, held until a ping successfully delivers it.
     * Play hands this over exactly once, so it must survive until the server has it.
     */
    var pendingReferrer: String?
        get() = prefs.getStringOrNull("pending_referrer")
        set(value) {
            if (value == null) prefs.remove("pending_referrer")
            else prefs.putString("pending_referrer", value)
        }

    /** True once the Play referrer has been read, so it is never queried twice. */
    var referrerChecked: Boolean
        get() = prefs.getBoolean("referrer_checked", false)
        set(value) { prefs.putBoolean("referrer_checked", value) }

    /** True once the user has seen and dismissed the pricing disclosure. */
    var pricingNoticeAcknowledged: Boolean
        get() = prefs.getBoolean("pricing_notice_ack", false)
        set(value) { prefs.putBoolean("pricing_notice_ack", value) }

    /** Set once registration has been accepted by the server. */
    var registeredEmail: String?
        get() = prefs.getStringOrNull("registered_email")
        set(value) {
            if (value == null) prefs.remove("registered_email")
            else prefs.putString("registered_email", value)
        }

    /** Server-confirmed date this account first registered — the founder date. */
    var founderSince: String?
        get() = prefs.getStringOrNull("founder_since")
        set(value) { if (value != null) prefs.putString("founder_since", value) }

    val isRegistered: Boolean
        get() = !registeredEmail.isNullOrBlank()

    // Home-screen departure widgets: each widget instance remembers its own station.
    fun getWidgetStation(widgetId: Int): Pair<String, String>? {
        val code = prefs.getStringOrNull("widget_${widgetId}_code") ?: return null
        return Pair(code, prefs.getString("widget_${widgetId}_name", ""))
    }

    fun setWidgetStation(widgetId: Int, code: String, name: String) {
        prefs.putString("widget_${widgetId}_code", code)
        prefs.putString("widget_${widgetId}_name", name)
    }

    fun removeWidgetStation(widgetId: Int) {
        prefs.remove("widget_${widgetId}_code")
        prefs.remove("widget_${widgetId}_name")
    }
}
