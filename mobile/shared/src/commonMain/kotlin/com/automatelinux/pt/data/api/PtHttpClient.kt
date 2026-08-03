package com.automatelinux.pt.data.api

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * The HTTP engine for this platform: OkHttp on Android, Darwin on iOS.
 *
 * The engine is the only part of the stack that cannot be shared. Everything the app's
 * behaviour actually depends on — timeouts, JSON leniency, logging, peer failover — is
 * configured once in [ptHttpClient] / [PtApi] so the two platforms cannot drift.
 */
expect fun ptHttpEngine(): HttpClientEngineFactory<*>

/**
 * Tolerant JSON, matching the leniency the app had under Gson.
 *
 * The backend stitches together MOTIS and live SIRI feeds whose payloads carry fields
 * the app does not model and occasionally omit ones it does. Strict parsing would turn
 * a harmless extra key into a blank screen, so unknown keys are ignored and nulls are
 * coerced to defaults. `TransitMode` aliasing is handled by TransitModeSerializer.
 */
val PtJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
    explicitNulls = false
}

/**
 * Timeouts carried over verbatim from the OkHttp configuration this replaced.
 * Read is generous because a cold MOTIS routing query can legitimately take many seconds.
 */
private const val CONNECT_TIMEOUT_MS = 10_000L
private const val REQUEST_TIMEOUT_MS = 30_000L

fun ptHttpClient(): HttpClient = HttpClient(ptHttpEngine()) {
    // Failover inspects 502/503/504 responses itself (see PtApi.request), so a non-2xx
    // must come back as a response rather than being thrown as an exception here.
    expectSuccess = false

    install(ContentNegotiation) {
        json(PtJson)
    }
    install(HttpTimeout) {
        connectTimeoutMillis = CONNECT_TIMEOUT_MS
        requestTimeoutMillis = REQUEST_TIMEOUT_MS
    }
    install(Logging) {
        level = LogLevel.INFO
    }
}
