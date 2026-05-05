package com.automatelinux.pt.di

import android.os.Build
import com.automatelinux.feedbacklib.FeedbackConfig
import com.automatelinux.feedbacklib.data.api.FeedbackApi
import com.automatelinux.pt.BuildConfig
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
object FeedbackModule {

    @Provides
    @Singleton
    fun provideFeedbackApi(): FeedbackApi {
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

        val feedbackClient = OkHttpClient.Builder()
            .addInterceptor(baseUrlInterceptor)
            .addInterceptor(logging)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.MINUTES)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl("http://localhost/")
            .client(feedbackClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(FeedbackApi::class.java)
    }

    @Provides
    @Singleton
    fun provideFeedbackConfig(): FeedbackConfig =
        FeedbackConfig(
            appName = "pt",
            currentScreenProvider = { com.automatelinux.pt.util.ScreenTracker.currentScreen },
            platformContextProvider = {
                "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL}, v${BuildConfig.VERSION_NAME}"
            },
        )
}
