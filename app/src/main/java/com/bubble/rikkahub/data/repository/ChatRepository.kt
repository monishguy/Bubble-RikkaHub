package com.bubble.rikkahub.data.repository

import com.bubble.rikkahub.data.local.dao.PendingMessageDao
import com.bubble.rikkahub.data.local.entity.PendingMessageEntity
import com.bubble.rikkahub.data.remote.RikkaHubApi
import com.bubble.rikkahub.data.remote.dto.SseFrame
import kotlinx.coroutines.flow.Flow

class ChatRepository(
    private val api: RikkaHubApi,
    private val pendingMessageDao: PendingMessageDao
) {

    /** Send a text message to the conversation (returns immediately, 202 Accepted) */
    suspend fun sendMessage(conversationId: String, text: String) {
        api.sendMessage(conversationId, text)
    }

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
                api.sendMessage(pending.conversationId, pending.text)
                pendingMessageDao.delete(pending.id)
                sent++
            } catch (e: Exception) {
                break
            }
        }
        return sent
    }
}
