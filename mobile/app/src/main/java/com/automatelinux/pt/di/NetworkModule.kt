package com.automatelinux.pt.di

import com.automatelinux.pt.data.api.PtApi
import com.automatelinux.pt.data.api.PtJson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import kotlinx.serialization.json.Json
import org.koin.core.context.GlobalContext
import javax.inject.Singleton

/**
 * Bridges the HTTP stack from Koin into Hilt.
 *
 * The stack itself — engine, timeouts, JSON leniency, logging and cross-peer failover —
 * lives in `:shared` so iOS gets identical behaviour. These `@Provides` deliberately
 * *resolve* rather than construct: Hilt building its own client and PtApi alongside
 * Koin's would give the app two HTTP stacks with independent failover state, so the
 * server one half believed was active would not be the one the other half was using.
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideHttpClient(): HttpClient = GlobalContext.get().get()

    @Provides
    @Singleton
    fun providePtApi(): PtApi = GlobalContext.get().get()

    /** A plain value, not a graph object — no bridging needed. */
    @Provides
    @Singleton
    fun provideJson(): Json = PtJson
}
