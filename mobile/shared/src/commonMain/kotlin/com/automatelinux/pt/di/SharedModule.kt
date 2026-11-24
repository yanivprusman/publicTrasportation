package com.automatelinux.pt.di

import com.automatelinux.pt.data.api.PtApi
import com.automatelinux.pt.data.api.ptHttpClient
import com.automatelinux.pt.ui.viewmodel.ArrivalsViewModel
import com.automatelinux.pt.ui.viewmodel.RoutingViewModel
import com.automatelinux.pt.util.LocalPlaces
import com.automatelinux.pt.util.SettingsStore
import org.koin.core.qualifier.named
import io.ktor.client.HttpClient
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * The object graph both platforms share.
 *
 * Koin owns these definitions outright. On Android, Hilt is still present for the
 * Android-only pieces (feedback-lib, the Activity, the widget) and *resolves* these
 * objects from Koin rather than constructing its own — otherwise each framework would
 * build a separate SettingsStore and HttpClient, and two stores writing the same
 * preferences is the kind of bug that only shows up as settings mysteriously reverting.
 *
 * iOS uses this module with no Hilt in the picture at all.
 *
 * The platform `Settings` is deliberately absent: it needs a Context on Android and
 * NSUserDefaults on iOS, so each platform contributes it via its own module.
 */
val sharedModule: Module = module {
    single<HttpClient> { ptHttpClient() }
    single { PtApi(get()) }
    single { SettingsStore(get()) }
    // Its own preferences file, supplied by the platform under this name, so it can
    // be kept off backups and out of account sync. See LocalPlaces.
    single { LocalPlaces(get(named(LOCAL_ONLY_SETTINGS))) }

    viewModel { RoutingViewModel(get(), get(), get()) }
    viewModel { ArrivalsViewModel(get()) }
}

/** Koin name of the platform `Settings` whose contents must never leave the device. */
const val LOCAL_ONLY_SETTINGS = "localOnlySettings"
