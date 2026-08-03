package com.automatelinux.pt.di

import com.automatelinux.pt.util.SettingsStore
import com.russhwolf.settings.Settings
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.koin.core.context.GlobalContext
import javax.inject.Singleton

/**
 * Bridges persistence from Koin into Hilt.
 *
 * Both definitions resolve rather than construct. SettingsStore reads and writes straight
 * through to the platform store on every access, so a duplicate would not corrupt anything
 * today — but the moment anyone adds caching to it, two instances would start disagreeing,
 * and that failure presents as settings randomly reverting rather than as a DI mistake.
 *
 * The Android `Settings` itself is defined in [androidPlatformModule]; iOS supplies an
 * NSUserDefaults-backed one when that target is added.
 */
@Module
@InstallIn(SingletonComponent::class)
object SettingsModule {

    @Provides
    @Singleton
    fun provideSettings(): Settings = GlobalContext.get().get()

    @Provides
    @Singleton
    fun provideSettingsStore(): SettingsStore = GlobalContext.get().get()
}
