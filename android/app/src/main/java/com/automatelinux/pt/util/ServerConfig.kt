package com.automatelinux.pt.util

import com.automatelinux.pt.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

object ServerConfig {
    private val port = BuildConfig.SERVER_PORT

    val servers = listOf(
        "http://10.7.0.2:$port",
        "http://10.7.0.1:$port",
        "http://10.7.0.4:$port",
        "http://10.7.0.6:$port",
        if (BuildConfig.FEEDBACK_ENABLED) "https://pt.dev.ya-niv.com" else "https://pt.prod.ya-niv.com"
    )

    val serverLabels = mapOf(
        "http://10.7.0.2:$port" to "Desktop",
        "http://10.7.0.1:$port" to "NUC (Leader)",
        "http://10.7.0.4:$port" to "Ubuntu Levtov",
        "http://10.7.0.6:$port" to "RPi Ubuntu",
        (if (BuildConfig.FEEDBACK_ENABLED) "https://pt.dev.ya-niv.com" else "https://pt.prod.ya-niv.com") to "Public"
    )

    @Volatile
    var activeServer: String = servers.first()
        private set

    fun setServer(url: String) {
        activeServer = url
    }

    suspend fun isReachable(url: String, timeoutMs: Int = 3000): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val connection = URL("$url/api/health").openConnection() as HttpURLConnection
                connection.connectTimeout = timeoutMs
                connection.readTimeout = timeoutMs
                connection.requestMethod = "GET"
                val code = connection.responseCode
                connection.disconnect()
                code in 200..299
            } catch (_: Exception) {
                false
            }
        }

    suspend fun findReachableServer(): String? =
        withContext(Dispatchers.IO) {
            for (server in servers) {
                if (isReachable(server)) {
                    activeServer = server
                    return@withContext server
                }
            }
            null
        }
}
