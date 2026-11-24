package com.automatelinux.pt.di

import android.content.Context
import com.russhwolf.settings.Settings
import com.russhwolf.settings.SharedPreferencesSettings
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
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
    // Excluded from cloud backup and device transfer (res/xml/backup_rules.xml,
    // data_extraction_rules.xml): what is in here stays on this phone.
    single<Settings>(named(LOCAL_ONLY_SETTINGS)) {
        SharedPreferencesSettings(
            androidContext().getSharedPreferences(LOCAL_ONLY_PREFS_FILE, Context.MODE_PRIVATE)
        )
    }
}

/** Must match the file name excluded in res/xml/backup_rules.xml and data_extraction_rules.xml. */
const val LOCAL_ONLY_PREFS_FILE = "pt_local_only"

