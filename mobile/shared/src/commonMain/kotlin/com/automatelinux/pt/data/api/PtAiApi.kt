package com.automatelinux.pt.data.api

import com.automatelinux.pt.data.model.Itinerary
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

/**
 * The AI navigator (server: `lib/ai/navigator.ts`, `/api/ai`).
 *
 * Deliberately NOT routed through [PtApi]'s peer failover: a conversation is a Claude
 * session that lives on the one server that started it, and only that server holds
 * the token and the Claude account. So the app talks to [server] alone, and a server
 * that cannot be reached is an error the screen shows, never a quiet switch to a peer
 * that knows nothing about the conversation.
 */
class PtAiApi(private val client: HttpClient, private val server: String, private val token: String) {

    /** Sends a message; the answer arrives later — poll [conversation]. Returns the conversation id. */
    suspend fun send(conversationId: String?, text: String, lat: Double?, lon: Double?): String {
        val body = PtJson.encodeToString(AiSendRequest(conversationId, text, lat, lon))
        val r = call(HttpMethod.Post, body = body)
        return PtJson.decodeFromString<AiSendResponse>(r.bodyAsText()).conversationId
    }

    suspend fun conversation(id: String): AiConversation {
        val r = call(HttpMethod.Get, params = mapOf("id" to id))
        return PtJson.decodeFromString(r.bodyAsText())
    }

    private suspend fun call(method: HttpMethod, body: String? = null, params: Map<String, String> = emptyMap()): HttpResponse {
        val r = client.request("$server/api/ai") {
            this.method = method
            header(HttpHeaders.Authorization, "Bearer $token")
            params.forEach { (k, v) -> parameter(k, v) }
            if (body != null) {
                contentType(ContentType.Application.Json)
                setBody(body)
            }
        }
        if (!r.status.isSuccess()) {
            val error = runCatching { PtJson.decodeFromString<AiError>(r.bodyAsText()).error }.getOrNull()
            throw AiApiException(r.status.value, error ?: "HTTP ${r.status.value}")
        }
        return r
    }
}

/** [error] is the server's word: "ended", "busy", "unauthorized", "unavailable", ... */
class AiApiException(val statusCode: Int, val error: String) : Exception("AI navigator: $error")

@Serializable
private data class AiSendRequest(val conversationId: String?, val text: String, val lat: Double?, val lon: Double?)

@Serializable
private data class AiSendResponse(val conversationId: String)

@Serializable
private data class AiError(val error: String)

@Serializable
data class AiConversation(
    val id: String,
    val ended: Boolean = false,
    val busy: Boolean = false,
    val messages: List<AiMessage> = emptyList()
)

@Serializable
data class AiMessage(
    val role: String,
    val text: String,
    val at: String,
    val options: List<AiOption> = emptyList(),
    /** The option the rider chose in words — the app starts following it. */
    val start: String? = null,
    val error: Boolean? = null
)

@Serializable
data class AiOption(val id: String, val line: String, val itinerary: Itinerary)
