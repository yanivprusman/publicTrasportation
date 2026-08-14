package com.automatelinux.pt.util

import com.automatelinux.pt.data.api.ptHttpEngine
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.http.isSuccess
import kotlin.concurrent.Volatile

/**
 * The list of PT backends and which one is currently in use.
 *
 * `:shared` cannot read `:app`'s generated `BuildConfig`, so the host app supplies the
 * flavour-dependent values once at startup via [configure] instead. Reading the server
 * list before that has happened is a wiring bug rather than a recoverable state, so the
 * accessors throw instead of returning an empty list — a silent empty list would look
 * like "no servers reachable" and send someone hunting a network fault that isn't there.
 */
object ServerConfig {

    private var configured = false
    private var _servers: List<String> = emptyList()
    private var _serverLabels: Map<String, String> = emptyMap()

    /**
     * A bare client owned by ServerConfig purely for `/api/health` probes.
     *
     * It deliberately does NOT reuse the app's main client: that one resolves its host
     * *through* ServerConfig, so sharing it would make server discovery depend on the
     * very thing it is trying to discover. Health checks need no JSON handling — only a
     * status code — so the cost of a second, tiny client is a few bytes.
     */
    private val healthClient: HttpClient by lazy {
        HttpClient(ptHttpEngine()) {
            expectSuccess = false
            install(HttpTimeout) {
                connectTimeoutMillis = REACHABILITY_TIMEOUT_MS
                requestTimeoutMillis = REACHABILITY_TIMEOUT_MS
            }
        }
    }

    val servers: List<String>
        get() {
            check(configured) { "ServerConfig.configure() must be called before reading servers" }
            return _servers
        }

    val serverLabels: Map<String, String>
        get() {
            check(configured) { "ServerConfig.configure() must be called before reading serverLabels" }
            return _serverLabels
        }

    @Volatile
    var activeServer: String = ""
        private set

    /**
     * @param port the backend port for this flavour (dev 3003 / prod 3002).
     * @param publicServer the public HTTPS origin for this flavour.
     * @param peerServersEnabled whether to probe the USB tunnel and private WireGuard peers first.
     *   These are a developer convenience and must never ship: on a real user's phone
     *   every one is unreachable, so probing them would stall startup behind connect
     *   timeouts before the public URL is tried, and would advertise the internal
     *   topology besides.
     */
    fun configure(port: Int, publicServer: String, peerServersEnabled: Boolean) {
        val peerServers = if (peerServersEnabled) listOf(
            // A phone plugged into a dev machine reaches its server through
            // `adb reverse tcp:<port> tcp:<port>` — no VPN, no WiFi, no public
            // host. It is first because a test phone that HAS the tunnel should
            // use it, and one that has not is refused instantly by its own
            // loopback rather than waiting out a connect timeout.
            "http://127.0.0.1:$port",
            "http://10.7.0.2:$port",
            "http://10.7.0.1:$port",
            "http://10.7.0.4:$port",
            "http://10.7.0.6:$port"
        ) else emptyList()

        _servers = peerServers + publicServer
        _serverLabels = mapOf(
            "http://127.0.0.1:$port" to "USB (adb reverse)",
            "http://10.7.0.2:$port" to "Desktop",
            "http://10.7.0.1:$port" to "NUC (Leader)",
            "http://10.7.0.4:$port" to "Ubuntu Levtov",
            "http://10.7.0.6:$port" to "RPi Ubuntu",
            publicServer to "Public"
        ).filterKeys { it in _servers }

        activeServer = _servers.first()
        configured = true
    }

    fun setServer(url: String) {
        activeServer = url
    }

    suspend fun isReachable(url: String): Boolean =
        try {
            healthClient.get("$url/api/health").status.isSuccess()
        } catch (_: Exception) {
            // Any failure to complete the probe — DNS, connect, timeout, TLS — means
            // "not reachable". The specific cause is not actionable here; the caller
            // only needs to know whether to move on to the next candidate.
            false
        }

    /** First reachable server, in preference order, or null if none answer. */
    suspend fun findReachableServer(exclude: String? = null): String? {
        for (server in servers) {
            if (server == exclude) continue
            if (isReachable(server)) {
                activeServer = server
                return server
            }
        }
        return null
    }

    private const val REACHABILITY_TIMEOUT_MS = 3000L
}
