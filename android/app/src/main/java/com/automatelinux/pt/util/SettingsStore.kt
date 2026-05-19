package com.automatelinux.pt.util

import android.content.Context
import android.content.SharedPreferences
import com.automatelinux.pt.data.model.GeocodeSuggestion
import dagger.hilt.android.qualifiers.ApplicationContext
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
}
