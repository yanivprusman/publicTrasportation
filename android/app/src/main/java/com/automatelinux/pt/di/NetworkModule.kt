package com.automatelinux.pt.di

import com.automatelinux.pt.data.api.PtApi
import com.automatelinux.pt.data.model.TransitMode
import com.automatelinux.pt.util.ServerConfig
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
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
    fun provideGson(): Gson = GsonBuilder()
        .registerTypeAdapter(TransitMode::class.java, object : TypeAdapter<TransitMode>() {
            override fun write(out: JsonWriter, value: TransitMode?) {
                out.value(value?.name ?: "WALK")
            }
            override fun read(`in`: JsonReader): TransitMode {
                return TransitMode.fromString(`in`.nextString())
            }
        })
        .create()

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, gson: Gson): Retrofit =
        Retrofit.Builder()
            .baseUrl("http://localhost/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()

    @Provides
    @Singleton
    fun providePtApi(retrofit: Retrofit): PtApi =
        retrofit.create(PtApi::class.java)
}
