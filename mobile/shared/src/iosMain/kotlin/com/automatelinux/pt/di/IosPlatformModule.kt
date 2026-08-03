package com.automatelinux.pt.di

import com.russhwolf.settings.NSUserDefaultsSettings
import com.russhwolf.settings.Settings
import org.koin.dsl.module
import platform.Foundation.NSUserDefaults

/**
 * The iOS half of the Koin graph — the mirror of `androidPlatformModule`.
 *
 * Persistence is the only thing [sharedModule] cannot construct for itself: Android needs a
 * Context for SharedPreferences, iOS needs NSUserDefaults. Everything else — the HTTP
 * client, PtApi, SettingsStore and both view models — is defined once in common and is
 * identical here.
 *
 * There is no Hilt on this side. The Android app keeps it for feedback-lib and its entry
 * points and bridges into Koin; iOS just uses the graph directly.
 */
val iosPlatformModule = module {
    single<Settings> { NSUserDefaultsSettings(NSUserDefaults.standardUserDefaults) }
}
