package com.bubble.rikkahub.data.repository

import com.bubble.rikkahub.data.local.dao.PendingMessageDao
import com.bubble.rikkahub.data.local.entity.PendingMessageEntity
import com.bubble.rikkahub.data.remote.RikkaHubApi
import com.bubble.rikkahub.data.remote.dto.SseFrame
import com.bubble.rikkahub.data.remote.dto.UIMessagePartDto
import com.bubble.rikkahub.data.remote.dto.UploadFileData
import com.bubble.rikkahub.data.remote.dto.UploadedFileDto
import kotlinx.coroutines.flow.Flow

class ChatRepository(
    private val api: RikkaHubApi,
    private val pendingMessageDao: PendingMessageDao
) {

    /** Send a text message to the conversation (returns immediately, 202 Accepted) */
    suspend fun sendMessage(conversationId: String, text: String) {
        api.sendMessage(conversationId, listOf(UIMessagePartDto(type = "text", text = text)))
    }

    /** Send a message with explicit parts (text + attachments). */
    suspend fun sendMessageWithParts(conversationId: String, parts: List<UIMessagePartDto>) {
        api.sendMessage(conversationId, parts)
    }

    /** Upload files and return their descriptors (for building attachment parts). */
    suspend fun uploadFiles(files: List<UploadFileData>): List<UploadedFileDto> {
        return api.uploadFiles(files)
    }

    /** Converts a stored attachment reference to a loadable HTTP URL. */
    fun resolveFileUrl(url: String): String? = api.resolveFileUrl(url)

    /** Open SSE stream for conversation updates at /api/conversations/{id}/stream */
    fun streamConversation(conversationId: String): Flow<SseFrame> {
        return api.streamConversation(conversationId)
    }

    // ── Offline send queue ──────────────────────────────────────

    suspend fun enqueuePending(conversationId: String, text: String) {
        pendingMessageDao.insert(
            PendingMessageEntity(
                conversationId = conversationId,
                text = text,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun getPendingForConversation(conversationId: String): List<PendingMessageEntity> {
        return pendingMessageDao.getForConversation(conversationId)
    }

    /**
     * Sends every queued message in order (oldest first), deleting each on success.
     * Stops at the first failure (still offline). Returns the number sent.
     */
    suspend fun flushPending(): Int {
        var sent = 0
        for (pending in pendingMessageDao.getAll()) {
            try {
                api.sendMessage(pending.conversationId, listOf(UIMessagePartDto(type = "text", text = pending.text)))
                pendingMessageDao.delete(pending.id)
                sent++
            } catch (e: Exception) {
                break
            }
        }
        return sent
    }
}
