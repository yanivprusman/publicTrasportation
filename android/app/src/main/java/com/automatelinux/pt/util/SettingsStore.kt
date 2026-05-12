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
        get() = prefs.getFloat("sheet_opacity", 0.82f)
        set(value) = prefs.edit().putFloat("sheet_opacity", value).apply()
}
