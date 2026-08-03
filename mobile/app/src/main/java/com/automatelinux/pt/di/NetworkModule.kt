package com.automatelinux.pt.di

import com.automatelinux.pt.data.api.PtApi
import com.automatelinux.pt.data.api.PtJson
import com.automatelinux.pt.data.api.ptHttpClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import kotlinx.serialization.json.Json
import javax.inject.Singleton

/**
 * Hilt wiring for the HTTP stack.
 *
 * The stack itself — engine, timeouts, JSON leniency, logging, and cross-peer failover —
 * now lives in `:shared` so iOS gets exactly the same behaviour. This module only hands
 * those shared pieces to Hilt; there is deliberately no Android-specific HTTP
 * configuration left here to drift from the iOS build.
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideHttpClient(): HttpClient = ptHttpClient()

    /** Exposed for the callers that serialise payloads themselves (analytics, widget). */
    @Provides
    @Singleton
    fun provideJson(): Json = PtJson

    @Provides
    @Singleton
    fun providePtApi(client: HttpClient): PtApi = PtApi(client)
}
