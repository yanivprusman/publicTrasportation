package com.automatelinux.pt.di

import android.content.Context
import com.automatelinux.pt.util.SettingsStore
import com.russhwolf.settings.Settings
import com.russhwolf.settings.SharedPreferencesSettings
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

// Android wiring for the commonMain SettingsStore. Hilt provides the platform Settings
// (SharedPreferences-backed) and the store; iOS will provide an NSUserDefaults-backed
// Settings when its target is added.
@Module
@InstallIn(SingletonComponent::class)
object SettingsModule {
    @Provides
    @Singleton
    fun provideSettings(@ApplicationContext context: Context): Settings =
        SharedPreferencesSettings(context.getSharedPreferences("pt_settings", Context.MODE_PRIVATE))

    @Provides
    @Singleton
    fun provideSettingsStore(settings: Settings): SettingsStore = SettingsStore(settings)
}
