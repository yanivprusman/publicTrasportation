package com.automatelinux.pt.data.api

import com.automatelinux.pt.data.model.JourneyLiveTokenResponse
import com.automatelinux.pt.data.model.JourneyLiveUpdateRequest
import com.automatelinux.pt.data.model.AppPingRequest
import com.automatelinux.pt.data.model.AppPingResponse
import com.automatelinux.pt.data.model.AppRegisterRequest
import com.automatelinux.pt.data.model.AppRegisterResponse
import com.automatelinux.pt.data.model.AppStateRequest
import com.automatelinux.pt.data.model.AppStateResponse
import com.automatelinux.pt.data.model.DayOverviewResult
import com.automatelinux.pt.data.model.GeocodeSuggestion
import com.automatelinux.pt.data.model.RouteResult
import com.automatelinux.pt.data.model.RouteStopsResponse
import com.automatelinux.pt.data.model.SiriResponse
import com.automatelinux.pt.data.model.StopResult
import com.automatelinux.pt.data.model.StoptimesResponse
import com.automatelinux.pt.data.model.TripShapeResponse
import com.automatelinux.pt.util.ServerConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.parameter
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import kotlinx.serialization.encodeToString

/**
 * The PT backend, over Ktor.
 *
 * Method signatures are deliberately identical to the Retrofit interface this replaced,
 * so every caller (view models, the analytics repository, the home-screen widget) is
 * unchanged by the swap.
 */
class PtApi(private val client: HttpClient) {

    suspend fun searchRoute(
        from: String,
        to: String,
        via: String? = null,
        time: String? = null,
        arriveBy: Boolean? = null,
        modes: String? = null,
        maxWalk: Int? = null
    ): RouteResult = fetch(
        "/api/route",
        params = mapOf(
            "from" to from, "to" to to, "via" to via, "time" to time,
            "arriveBy" to arriveBy, "modes" to modes, "maxWalk" to maxWalk
        )
    )

    suspend fun dayOverview(
        from: String,
        to: String,
        start: String,
        end: String
    ): DayOverviewResult = fetch(
        "/api/day-overview",
        params = mapOf("from" to from, "to" to to, "start" to start, "end" to end)
    )

    suspend fun geocode(text: String, near: String? = null): List<GeocodeSuggestion> =
        fetch(
            "/api/geocode",
            params = if (near != null) mapOf("text" to text, "near" to near)
                     else mapOf("text" to text)
        )

    suspend fun reverseGeocode(lat: Double, lon: Double): List<GeocodeSuggestion> =
        fetch("/api/reverse-geocode", params = mapOf("lat" to lat, "lon" to lon))

    suspend fun searchStops(query: String): List<StopResult> =
        fetch("/api/stops", params = mapOf("q" to query))

    suspend fun nearbyStops(lat: Double, lon: Double, radius: Int = 500): List<StopResult> =
        fetch("/api/stops", params = mapOf("lat" to lat, "lon" to lon, "radius" to radius))

    suspend fun getStoptimes(stopId: String, n: Int = 30): StoptimesResponse =
        fetch("/api/stoptimes", params = mapOf("stopId" to stopId, "n" to n))

    suspend fun getTransport(
        station: String,
        detail: String = "calls",
        line: String? = null
    ): SiriResponse = fetch(
        "/api/transport",
        params = mapOf("station" to station, "detail" to detail, "line" to line)
    )

    suspend fun getLineShape(line: String): Map<String, List<List<Double>>> =
        fetch("/api/line-shape", params = mapOf("line" to line))

    /** The ordered stop list of one route; routeId is SIRI's LineRef. */
    suspend fun getRouteStops(routeId: String): RouteStopsResponse =
        fetch("/api/route-stops", params = mapOf("routeId" to routeId))

    /**
     * Geometry of one specific trip. Preferred over [getLineShape] wherever the
     * trip is known: line numbers repeat across the country, trip ids do not.
     */
    suspend fun getTripShape(tripId: String): TripShapeResponse =
        fetch("/api/trip-shape", params = mapOf("tripId" to tripId))

    /**
     * Geometry for a route, for callers holding a live arrival rather than a
     * planned leg: SIRI's LineRef is the GTFS route_id.
     */
    suspend fun getRouteShape(routeId: String): TripShapeResponse =
        fetch("/api/trip-shape", params = mapOf("routeId" to routeId))

    suspend fun journeyLivePost(body: JourneyLiveUpdateRequest): JourneyLiveTokenResponse =
        fetch("/api/journey-live", HttpMethod.Post, PtJson.encodeToString(body))

    suspend fun appPing(body: AppPingRequest): AppPingResponse =
        fetch("/api/app/ping", HttpMethod.Post, PtJson.encodeToString(body))

    suspend fun appRegister(body: AppRegisterRequest): AppRegisterResponse =
        fetch("/api/app/register", HttpMethod.Post, PtJson.encodeToString(body))

    suspend fun appGetState(installId: String): AppStateResponse =
        fetch("/api/app/state", params = mapOf("installId" to installId))

    suspend fun appPutState(body: AppStateRequest): AppStateResponse =
        fetch("/api/app/state", HttpMethod.Post, PtJson.encodeToString(body))

    private suspend inline fun <reified T> fetch(
        path: String,
        method: HttpMethod = HttpMethod.Get,
        body: String? = null,
        params: Map<String, Any?> = emptyMap()
    ): T = execute(path, method, body, params).body()

    /**
     * Sends the request to the active server, falling across to another peer when that
     * one is down.
     *
     * This reproduces the OkHttp interceptor it replaced: a failed connection or a
     * gateway-class status means *this server* is unhealthy rather than the request
     * being bad, so the same request is retried once against the next reachable peer.
     * Unlike the interceptor it suspends instead of blocking a thread, which is why
     * `findReachableServerBlocking` no longer needs to exist.
     *
     * Bodies arrive pre-encoded as JSON strings: passing an `Any` to Ktor's `setBody`
     * would lose the type information kotlinx-serialization needs, and the alternative
     * (making this function reified) would force the whole failover path to be inlined
     * into all thirteen call sites.
     */
    private suspend fun execute(
        path: String,
        method: HttpMethod,
        body: String?,
        params: Map<String, Any?>
    ): HttpResponse {
        suspend fun sendTo(server: String): HttpResponse =
            client.request("$server$path") {
                this.method = method
                // A null parameter means "not supplied" and is omitted, matching
                // Retrofit's treatment of a null @Query.
                params.forEach { (key, value) -> if (value != null) parameter(key, value) }
                if (body != null) {
                    contentType(ContentType.Application.Json)
                    setBody(body)
                }
            }

        val attempted = ServerConfig.activeServer

        val response = try {
            sendTo(attempted)
        } catch (e: Exception) {
            // Could not complete the exchange at all. If another peer answers, use it;
            // otherwise the original failure is the honest thing to report.
            val next = ServerConfig.findReachableServer(exclude = attempted) ?: throw e
            return sendTo(next)
        }

        // A server is unhealthy in two ways: a gateway-class status, or a body that is
        // not JSON at all. The second is how the dev-auth sign-in wall presents — the
        // client follows its redirect and receives a perfectly successful HTML page —
        // and without this check that server passes every health test the failover
        // runs on, so the app stays wedged on it until the process dies.
        val unhealthy = response.status.value in GATEWAY_FAILURE_CODES ||
            response.contentType()?.match(ContentType.Application.Json) != true
        if (unhealthy) {
            val next = ServerConfig.findReachableServer(exclude = attempted)
            if (next != null) return sendTo(next)
            // Nothing healthier exists — return the response rather than pretending,
            // so the caller surfaces a real failure instead of an empty result.
        }
        return response
    }

    private companion object {
        /** Statuses that indicate the *server* is unhealthy, not that the request was wrong. */
        val GATEWAY_FAILURE_CODES = setOf(502, 503, 504)
    }
}
