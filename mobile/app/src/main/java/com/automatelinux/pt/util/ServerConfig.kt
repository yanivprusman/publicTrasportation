package com.automatelinux.pt.util

import com.automatelinux.pt.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

object ServerConfig {
    private val port = BuildConfig.SERVER_PORT

    private val publicServer =
        if (BuildConfig.FEEDBACK_ENABLED) "https://pt.dev.ya-niv.com" else "https://pt.prod.ya-niv.com"

    // Private WireGuard peers are a developer convenience and must never ship.
    // On a real user's phone every one of these is unreachable, so probing them
    // would stall startup for seconds behind connect timeouts before the public
    // URL is ever tried — and would advertise the internal topology besides.
    private val peerServers =
        if (BuildConfig.PEER_SERVERS_ENABLED) listOf(
            "http://10.7.0.2:$port",
            "http://10.7.0.1:$port",
            "http://10.7.0.4:$port",
            "http://10.7.0.6:$port"
        ) else emptyList()

    val servers = peerServers + publicServer

    val serverLabels = mapOf(
        "http://10.7.0.2:$port" to "Desktop",
        "http://10.7.0.1:$port" to "NUC (Leader)",
        "http://10.7.0.4:$port" to "Ubuntu Levtov",
        "http://10.7.0.6:$port" to "RPi Ubuntu",
        publicServer to "Public"
    ).filterKeys { it in servers }

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

    fun findReachableServerBlocking(exclude: String? = null): String? {
        for (server in servers) {
            if (server == exclude) continue
            try {
                val connection = URL("$server/api/health").openConnection() as HttpURLConnection
                connection.connectTimeout = 3000
                connection.readTimeout = 3000
                connection.requestMethod = "GET"
                val code = connection.responseCode
                connection.disconnect()
                if (code in 200..299) {
                    activeServer = server
                    return server
                }
            } catch (_: Exception) { }
        }
        return null
    }
}
