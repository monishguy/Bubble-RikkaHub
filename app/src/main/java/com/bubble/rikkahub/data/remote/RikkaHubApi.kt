package com.bubble.rikkahub.data.remote

import android.util.Log
import com.bubble.rikkahub.data.remote.dto.*
import com.bubble.rikkahub.util.SseLineParser
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeoutConfig
import io.ktor.client.plugins.timeout
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class RikkaHubApi(private val client: HttpClient) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    companion object {
        private const val TAG = "RikkaHubApi"
    }

    // ── Conversations ───────────────────────────────────────────

    suspend fun getConversations(): List<ConversationListDto> {
        return client.get("/api/conversations").body()
    }

    suspend fun getConversationDetail(id: String): ConversationDetailDto {
        return client.get("/api/conversations/$id").body()
    }

    suspend fun deleteConversation(id: String) {
        client.delete("/api/conversations/$id")
    }

    suspend fun togglePin(id: String) {
        client.post("/api/conversations/$id/pin")
    }

    // ── Messages ────────────────────────────────────────────────

    /**
     * Sends a message. Returns 202 Accepted immediately.
     * The actual response comes via the conversation SSE stream.
     * Transient server errors (5xx) are retried twice; if it still fails it throws
     * [MessageSendException] so silent send failures are surfaced.
     */
    suspend fun sendMessage(conversationId: String, text: String) {
        val request = ChatStreamRequest(parts = listOf(TextPart(text = text)))
        val url = "/api/conversations/$conversationId/messages"
        Log.d(TAG, "sendMessage to $url body=${json.encodeToString(request)}")

        suspend fun doPost(): HttpResponse = client.post(url) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }

        var response = doPost()
        var attempt = 0
        while (response.status.value >= 500 && attempt < 2) {
            attempt++
            Log.w(TAG, "sendMessage got HTTP ${response.status.value}, retrying ($attempt/2)")
            delay(1_000)
            response = doPost()
        }

        if (!response.status.isSuccess()) {
            Log.e(TAG, "sendMessage FAILED: HTTP ${response.status.value} to $url")
            throw MessageSendException(
                response.status,
                "发送失败：服务器返回 HTTP ${response.status.value} ${response.status.description}"
            )
        }
        Log.d(TAG, "sendMessage OK: HTTP ${response.status.value} to $url")
    }

    // ── Streaming ───────────────────────────────────────────────

    /**
     * Opens SSE stream at GET /api/conversations/{id}/stream.
     * Events:
     *   - "snapshot"     data = ConversationSnapshotEvent (full state)
     *   - "node_update"  data = ConversationNodeUpdateEvent (partial update)
     *   - "done"         data = GenerationDoneEvent
     *   - "error"        data = ErrorEvent
     */
    fun streamConversation(conversationId: String): Flow<SseFrame> = flow {
        client.get("/api/conversations/$conversationId/stream") {
            // SSE is a long-lived connection; the CIO engine's default request timeout
            // (15s when HttpTimeout is unconfigured) must not kill it.
            timeout {
                requestTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
                socketTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
            }
        }.let { response ->
            val channel = response.bodyAsChannel()
            val buffer = mutableListOf<String>()
            while (!channel.isClosedForRead) {
                val line = channel.readUTF8Line() ?: break
                if (line.isBlank()) {
                    SseLineParser.parse(buffer.toList())?.let { emit(it) }
                    buffer.clear()
                } else {
                    buffer.add(line)
                }
            }
            if (buffer.isNotEmpty()) {
                SseLineParser.parse(buffer.toList())?.let { emit(it) }
            }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Opens multiplexed SSE stream at GET /api/events.
     * Event "settings" carries the full Settings JSON including the assistants list.
     */
    fun streamEvents(): Flow<SseFrame> = flow {
        client.get("/api/events") {
            // Same long-lived SSE connection: never let the request timeout kill it.
            timeout {
                requestTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
                socketTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
            }
        }.let { response ->
            val channel = response.bodyAsChannel()
            val buffer = mutableListOf<String>()
            while (!channel.isClosedForRead) {
                val line = channel.readUTF8Line() ?: break
                if (line.isBlank()) {
                    SseLineParser.parse(buffer.toList())?.let { emit(it) }
                    buffer.clear()
                } else {
                    buffer.add(line)
                }
            }
            if (buffer.isNotEmpty()) {
                SseLineParser.parse(buffer.toList())?.let { emit(it) }
            }
        }
    }.flowOn(Dispatchers.IO)

    // ── Settings / Assistant ────────────────────────────────────

    suspend fun switchAssistant(assistantId: String) {
        client.post("/api/settings/assistant") {
            contentType(ContentType.Application.Json)
            setBody(mapOf("assistantId" to assistantId))
        }
    }

    /**
     * Fetches the current Settings object (which contains the assistant list and the
     * active assistant id). There is no plain GET for settings — they are delivered as
     * the first `settings` SSE event on GET /api/events, so we collect it and disconnect.
     */
    suspend fun getSettings(): SettingsDto {
        return withTimeout(10_000) {
            streamEvents()
                .mapNotNull { frame ->
                    if (frame.event == "settings" && frame.data != null) {
                        runCatching { json.decodeFromString<SettingsDto>(frame.data) }.getOrNull()
                    } else null
                }
                .firstOrNull()
        } ?: throw IllegalStateException("获取服务器设置超时")
    }
}
