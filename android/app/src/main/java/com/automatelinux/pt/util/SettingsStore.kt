package com.automatelinux.pt.util

import android.content.Context
import android.content.SharedPreferences
import com.automatelinux.pt.data.model.GeocodeSuggestion
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("pt_settings", Context.MODE_PRIVATE)

    var sheetOpacity: Float
        get() = prefs.getFloat("sheet_opacity", 0.50f)
        set(value) = prefs.edit().putFloat("sheet_opacity", value).apply()

    var cardOpacity: Float
        get() = prefs.getFloat("card_opacity", 0.6f)
        set(value) = prefs.edit().putFloat("card_opacity", value).apply()

    var debugAutoSearch: Boolean
        get() = prefs.getBoolean("debug_auto_search", true)
        set(value) = prefs.edit().putBoolean("debug_auto_search", value).apply()

    var debugExpandSheet: Boolean
        get() = prefs.getBoolean("debug_expand_sheet", true)
        set(value) = prefs.edit().putBoolean("debug_expand_sheet", value).apply()

    var debugFrom: GeocodeSuggestion
        get() = GeocodeSuggestion(
            name = prefs.getString("debug_from", "אלרום 6 רמת גן") ?: "אלרום 6 רמת גן",
            lat = prefs.getFloat("debug_from_lat", 32.0691133f).toDouble(),
            lon = prefs.getFloat("debug_from_lon", 34.8402174f).toDouble()
        )
        set(value) = prefs.edit()
            .putString("debug_from", value.name)
            .putFloat("debug_from_lat", value.lat.toFloat())
            .putFloat("debug_from_lon", value.lon.toFloat())
            .apply()

    var debugTo: GeocodeSuggestion
        get() = GeocodeSuggestion(
            name = prefs.getString("debug_to", "המסגר 51 תל אביב") ?: "המסגר 51 תל אביב",
            lat = prefs.getFloat("debug_to_lat", 32.0636f).toDouble(),
            lon = prefs.getFloat("debug_to_lon", 34.7878f).toDouble()
        )
        set(value) = prefs.edit()
            .putString("debug_to", value.name)
            .putFloat("debug_to_lat", value.lat.toFloat())
            .putFloat("debug_to_lon", value.lon.toFloat())
            .apply()

    var language: String
        get() = prefs.getString("language", "en") ?: "en"
        set(value) = prefs.edit().putString("language", value).apply()

    fun getRecentSearches(): List<GeocodeSuggestion> {
        val json = prefs.getString("recent_searches", null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                GeocodeSuggestion(
                    name = obj.getString("name"),
                    lat = obj.getDouble("lat"),
                    lon = obj.getDouble("lon")
                )
            }
        } catch (_: Exception) { emptyList() }
    }

    fun addRecentSearch(suggestion: GeocodeSuggestion) {
        val current = getRecentSearches().toMutableList()
        current.removeAll { it.name == suggestion.name && it.lat == suggestion.lat && it.lon == suggestion.lon }
        current.add(0, suggestion)
        val trimmed = current.take(10)
        val arr = JSONArray()
        trimmed.forEach { s ->
            arr.put(JSONObject().apply {
                put("name", s.name)
                put("lat", s.lat)
                put("lon", s.lon)
            })
        }
        prefs.edit().putString("recent_searches", arr.toString()).apply()
    }

    var homePlace: GeocodeSuggestion?
        get() = getSavedPlace("home")
        set(value) = setSavedPlace("home", value)

    var workPlace: GeocodeSuggestion?
        get() = getSavedPlace("work")
        set(value) = setSavedPlace("work", value)

    private fun getSavedPlace(key: String): GeocodeSuggestion? {
        val name = prefs.getString("saved_${key}_name", null) ?: return null
        val lat = prefs.getFloat("saved_${key}_lat", 0f).toDouble()
        val lon = prefs.getFloat("saved_${key}_lon", 0f).toDouble()
        return GeocodeSuggestion(name = name, lat = lat, lon = lon)
    }

    private fun setSavedPlace(key: String, value: GeocodeSuggestion?) {
        if (value == null) {
            prefs.edit()
                .remove("saved_${key}_name")
                .remove("saved_${key}_lat")
                .remove("saved_${key}_lon")
                .apply()
        } else {
            prefs.edit()
                .putString("saved_${key}_name", value.name)
                .putFloat("saved_${key}_lat", value.lat.toFloat())
                .putFloat("saved_${key}_lon", value.lon.toFloat())
                .apply()
        }
    }

    fun getFavoriteLines(): Set<String> {
        return prefs.getStringSet("favorite_lines", emptySet()) ?: emptySet()
    }

    fun toggleFavoriteLine(lineName: String): Boolean {
        val current = getFavoriteLines().toMutableSet()
        val added = if (current.contains(lineName)) {
            current.remove(lineName)
            false
        } else {
            current.add(lineName)
            true
        }
        prefs.edit().putStringSet("favorite_lines", current).apply()
        return added
    }

    fun getFavoriteStations(): List<Pair<String, String>> {
        val json = prefs.getString("favorite_stations", null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                Pair(obj.getString("code"), obj.getString("name"))
            }
        } catch (_: Exception) { emptyList() }
    }

    fun toggleFavoriteStation(code: String, name: String): Boolean {
        val current = getFavoriteStations().toMutableList()
        val existing = current.indexOfFirst { it.first == code }
        val added = if (existing >= 0) {
            current.removeAt(existing)
            false
        } else {
            current.add(Pair(code, name))
            true
        }
        val arr = JSONArray()
        current.forEach { (c, n) ->
            arr.put(JSONObject().apply {
                put("code", c)
                put("name", n)
            })
        }
        prefs.edit().putString("favorite_stations", arr.toString()).apply()
        return added
    }

    fun isStationFavorite(code: String): Boolean {
        return getFavoriteStations().any { it.first == code }
    }
}
