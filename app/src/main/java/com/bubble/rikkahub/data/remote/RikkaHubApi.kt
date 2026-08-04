package com.bubble.rikkahub.data.remote

import android.util.Log
import com.bubble.rikkahub.data.remote.dto.*
import com.bubble.rikkahub.util.SseLineParser
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
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
import java.net.HttpURLConnection
import java.net.URL

class RikkaHubApi(
    private val client: HttpClient,
    private val baseUrl: String
) {

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
     * Shared robust SSE reader: connects with a connect-timeout guard (so a stale/pooled
     * connection fails fast instead of hanging forever on the infinite request timeout),
     * checks the response status, and parses frames line by line. Each received frame is
     * logged so a broken stream is easy to diagnose.
     */
    /**
     * Reads an SSE stream using a plain HttpURLConnection. Ktor's CIO client was found to hang
     * on streaming responses in this app, so the stream is read manually here — this is
     * reliable regardless of the Ktor engine.
     */
    private fun sseFlow(path: String, tag: String): Flow<SseFrame> = flow {
        Log.d(tag, "SSE 开始连接 $path")
        val url = URL(baseUrl.trimEnd('/') + path)
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 5_000
            conn.readTimeout = 0 // no read timeout: stream stays open
            conn.setRequestProperty("Accept", "text/event-stream")
            conn.instanceFollowRedirects = false
            val status = conn.responseCode
            Log.d(tag, "SSE 连接 $path → HTTP $status")
            if (status !in 200..299) {
                Log.w(tag, "SSE 连接失败 HTTP $status")
                return@flow
            }
            val buffer = mutableListOf<String>()
            conn.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isBlank()) {
                        SseLineParser.parse(buffer.toList())?.let { frame ->
                            // node_update fires very frequently during generation — don't log every frame.
                            if (frame.event != "node_update") {
                                Log.d(tag, "SSE 收到事件: ${frame.event}")
                            }
                            emit(frame)
                        }
                        buffer.clear()
                    } else {
                        buffer.add(line)
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
        Log.w(tag, "SSE 流结束 ($path)")
    }.flowOn(Dispatchers.IO)

    fun streamConversation(conversationId: String): Flow<SseFrame> =
        sseFlow("/api/conversations/$conversationId/stream", TAG)

    fun streamEvents(): Flow<SseFrame> = sseFlow("/api/events", TAG)

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
     * the first `settings` SSE event on GET /api/events. Retries up to 3 times.
     */
    suspend fun getSettings(): SettingsDto {
        var lastError: Throwable? = null
        repeat(3) { attempt ->
            try {
                val settings = withTimeout(8_000) {
                    streamEvents()
                        .mapNotNull { frame ->
                            if (frame.event == "settings" && frame.data != null) {
                                runCatching { json.decodeFromString<SettingsDto>(frame.data) }
                                    .onFailure { e -> Log.w(TAG, "解析 settings 失败: ${e.message}") }
                                    .getOrNull()
                            } else null
                        }
                        .firstOrNull()
                }
                if (settings != null) {
                    Log.d(TAG, "getSettings 成功（第 ${attempt + 1} 次尝试）")
                    return settings
                }
                lastError = IllegalStateException("未收到 settings 事件")
            } catch (e: Exception) {
                lastError = e
            }
            Log.w(TAG, "getSettings 第 ${attempt + 1} 次尝试失败: ${lastError?.message}")
        }
        throw IllegalStateException("获取服务器设置失败: ${lastError?.message}")
    }
}
