package com.automatelinux.pt.di

import android.content.Context
import com.russhwolf.settings.Settings
import com.russhwolf.settings.SharedPreferencesSettings
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * The Android half of the Koin graph: the one definition [sharedModule] cannot make
 * for itself, because persistence needs a platform handle. iOS supplies an
 * NSUserDefaults-backed `Settings` from its own module when that target is added.
 */
val androidPlatformModule = module {
    single<Settings> {
        SharedPreferencesSettings(
            androidContext().getSharedPreferences("pt_settings", Context.MODE_PRIVATE)
        )
    }
}
