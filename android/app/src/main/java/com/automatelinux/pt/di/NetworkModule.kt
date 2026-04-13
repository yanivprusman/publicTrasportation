package com.automatelinux.pt.di

import com.automatelinux.feedbacklib.FeedbackConfig
import com.automatelinux.feedbacklib.data.api.FeedbackApi
import com.automatelinux.pt.data.api.PtApi
import com.automatelinux.pt.util.ServerConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val baseUrlInterceptor = Interceptor { chain ->
            val original = chain.request()
            val activeUrl = ServerConfig.activeServer.toHttpUrlOrNull()
                ?: return@Interceptor chain.proceed(original)

            val newUrl = original.url.newBuilder()
                .scheme(activeUrl.scheme)
                .host(activeUrl.host)
                .port(activeUrl.port)
                .build()

            chain.proceed(original.newBuilder().url(newUrl).build())
        }

        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        return OkHttpClient.Builder()
            .addInterceptor(baseUrlInterceptor)
            .addInterceptor(logging)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl("http://localhost/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

    @Provides
    @Singleton
    fun providePtApi(retrofit: Retrofit): PtApi =
        retrofit.create(PtApi::class.java)

    @Provides
    @Singleton
    fun provideFeedbackApi(client: OkHttpClient): FeedbackApi =
        Retrofit.Builder()
            .baseUrl("http://localhost/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(FeedbackApi::class.java)

    @Provides
    @Singleton
    fun provideFeedbackConfig(): FeedbackConfig =
        FeedbackConfig(appName = "pt")
}
