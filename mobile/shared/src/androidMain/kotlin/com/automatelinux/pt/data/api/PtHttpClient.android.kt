package com.automatelinux.pt.data.api

import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.okhttp.OkHttp

actual fun ptHttpEngine(): HttpClientEngineFactory<*> = OkHttp
