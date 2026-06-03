package com.automatelinux.pt.di

import com.automatelinux.pt.data.api.PtApi
import com.automatelinux.pt.util.ServerConfig
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val serverInterceptor = Interceptor { chain ->
            val original = chain.request()

            fun rebuildWith(server: String): okhttp3.Request {
                val parsed = server.toHttpUrlOrNull() ?: return original
                val newUrl = original.url.newBuilder()
                    .scheme(parsed.scheme)
                    .host(parsed.host)
                    .port(parsed.port)
                    .build()
                return original.newBuilder().url(newUrl).build()
            }

            val response = try {
                chain.proceed(rebuildWith(ServerConfig.activeServer))
            } catch (e: java.io.IOException) {
                val newServer = ServerConfig.findReachableServerBlocking(exclude = ServerConfig.activeServer)
                if (newServer != null) {
                    return@Interceptor chain.proceed(rebuildWith(newServer))
                }
                throw e
            }

            if (response.code in listOf(502, 503, 504)) {
                val failedServer = ServerConfig.activeServer
                response.close()
                val newServer = ServerConfig.findReachableServerBlocking(exclude = failedServer)
                if (newServer != null) {
                    return@Interceptor chain.proceed(rebuildWith(newServer))
                }
                return@Interceptor chain.proceed(rebuildWith(failedServer))
            }

            response
        }

        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        return OkHttpClient.Builder()
            .addInterceptor(serverInterceptor)
            .addInterceptor(logging)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    // Tolerant config mirrors Gson's old leniency (ignore unknown/extra fields, coerce
    // nulls into defaults). TransitMode aliasing is handled by TransitModeSerializer.
    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
    }

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, json: Json): Retrofit =
        Retrofit.Builder()
            .baseUrl("http://localhost/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides
    @Singleton
    fun providePtApi(retrofit: Retrofit): PtApi =
        retrofit.create(PtApi::class.java)
}
