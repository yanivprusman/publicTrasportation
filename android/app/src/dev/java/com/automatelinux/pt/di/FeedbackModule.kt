package com.automatelinux.pt.di

import com.automatelinux.feedbacklib.FeedbackConfig
import com.automatelinux.feedbacklib.data.api.FeedbackApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object FeedbackModule {

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
