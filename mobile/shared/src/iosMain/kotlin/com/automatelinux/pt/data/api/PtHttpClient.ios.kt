package com.automatelinux.pt.data.api

import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.darwin.Darwin

/**
 * iOS uses Ktor's Darwin engine, which is backed by NSURLSession.
 *
 * This is the whole of the platform-specific HTTP layer. Timeouts, JSON leniency, logging
 * and cross-peer failover are configured once in commonMain, so the two platforms cannot
 * disagree about how the app talks to the backend.
 */
actual fun ptHttpEngine(): HttpClientEngineFactory<*> = Darwin
