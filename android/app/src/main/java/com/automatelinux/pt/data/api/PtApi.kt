package com.automatelinux.pt.data.api

import com.automatelinux.pt.data.model.GeocodeSuggestion
import com.automatelinux.pt.data.model.RouteResult
import com.automatelinux.pt.data.model.SiriResponse
import com.automatelinux.pt.data.model.StopResult
import retrofit2.http.GET
import retrofit2.http.Query

interface PtApi {

    @GET("/api/route")
    suspend fun searchRoute(
        @Query("from") from: String,
        @Query("to") to: String,
        @Query("time") time: String? = null,
        @Query("arriveBy") arriveBy: Boolean? = null
    ): RouteResult

    @GET("/api/geocode")
    suspend fun geocode(
        @Query("text") text: String
    ): List<GeocodeSuggestion>

    @GET("/api/stops")
    suspend fun searchStops(
        @Query("q") query: String
    ): List<StopResult>

    @GET("/api/transport")
    suspend fun getTransport(
        @Query("station") station: String,
        @Query("detail") detail: String = "calls",
        @Query("line") line: String? = null
    ): SiriResponse

    @GET("/api/stoptimes")
    suspend fun getStoptimes(
        @Query("stopId") stopId: String,
        @Query("n") n: Int = 20
    ): Any

    @GET("/api/health")
    suspend fun health(): Any
}
