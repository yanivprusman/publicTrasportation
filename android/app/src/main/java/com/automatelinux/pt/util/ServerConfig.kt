package com.automatelinux.pt.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

object ServerConfig {
    val servers = listOf(
        "http://10.0.0.2:3003",
        "http://10.0.0.1:3003",
        "http://10.0.0.4:3003",
        "http://10.0.0.6:3003",
        "https://pt.dev.ya-niv.com"
    )

    val serverLabels = mapOf(
        "http://10.0.0.2:3003" to "Desktop",
        "http://10.0.0.1:3003" to "NUC (Leader)",
        "http://10.0.0.4:3003" to "Ubuntu Levtov",
        "http://10.0.0.6:3003" to "RPi Ubuntu",
        "https://pt.dev.ya-niv.com" to "Public"
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
