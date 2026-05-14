package com.automatelinux.pt.util

import android.content.Context
import android.content.SharedPreferences
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
}
