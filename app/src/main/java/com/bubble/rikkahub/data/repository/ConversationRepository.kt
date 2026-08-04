package com.bubble.rikkahub.data.repository

import com.bubble.rikkahub.data.local.dao.CachedConversationDao
import com.bubble.rikkahub.data.local.entity.CachedConversationEntity
import com.bubble.rikkahub.data.remote.RikkaHubApi
import com.bubble.rikkahub.data.remote.dto.ConversationDetailDto
import com.bubble.rikkahub.data.remote.dto.ConversationListDto
import com.bubble.rikkahub.data.remote.dto.MessageDto
import com.bubble.rikkahub.data.remote.dto.SettingsDto
import com.bubble.rikkahub.data.remote.dto.SseFrame
import com.bubble.rikkahub.domain.model.Conversation
import com.bubble.rikkahub.domain.model.Message
import com.bubble.rikkahub.util.MessageSplitter
import com.bubble.rikkahub.util.TimestampParser
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json

class ConversationRepository(
    private val api: RikkaHubApi,
    private val cachedConversationDao: CachedConversationDao
) {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun getConversations(): Result<List<Conversation>> = runCatching {
        val list = api.getConversations().map { it.toDomain() }
            .sortedWith(compareByDescending<Conversation> { it.isPinned }.thenByDescending { it.updatedAt })
        cacheListEntries(list)
        list
    }

    suspend fun getConversationDetail(id: String): Result<Conversation> = runCatching {
        api.getConversationDetail(id).toDomain()
    }

    suspend fun deleteConversation(id: String): Result<Unit> = runCatching {
        api.deleteConversation(id)
        cachedConversationDao.delete(id)
    }

    suspend fun togglePin(id: String): Result<Unit> = runCatching {
        api.togglePin(id)
    }

    suspend fun getSettings(): Result<SettingsDto> = runCatching {
        api.getSettings()
    }

    suspend fun switchAssistant(assistantId: String): Result<Unit> = runCatching {
        api.switchAssistant(assistantId)
    }

    /** Long-lived SSE stream of server events (settings, conversation_list_invalidate, …). */
    fun streamConversationListEvents(): Flow<SseFrame> = api.streamEvents()

    suspend fun getConversationWithMessages(id: String): Result<ConversationWithMessages> = runCatching {
        val dto = api.getConversationDetail(id)
        cacheConversation(dto)
        ConversationWithMessages(dto.toDomain(), flattenMessages(dto, dto.isGenerating), dto.isGenerating)
    }

    // ── Offline cache ───────────────────────────────────────────

    /** Returns the last-known conversation + messages from local cache, or null if never opened. */
    suspend fun getCachedConversation(id: String): ConversationWithMessages? {
        val cached = cachedConversationDao.get(id) ?: return null
        val messages = cached.messagesJson?.let {
            runCatching { json.decodeFromString<List<Message>>(it) }.getOrNull()
        } ?: emptyList()
        return ConversationWithMessages(
            conversation = cached.toConversation(),
            messages = messages
        )
    }

    /**
     * Returns cached conversation list entries for one assistant, or all of them when
     * [assistantId] is null. Conversations whose assistant is unknown (null) are included
     * in every assistant's offline list so nothing cached becomes invisible offline.
     */
    suspend fun getCachedConversations(assistantId: String? = null): List<Conversation> {
        val all = cachedConversationDao.getAll()
        val filtered = if (assistantId == null) {
            all
        } else {
            all.filter { it.assistantId == assistantId || it.assistantId == null }
        }
        return filtered.map { it.toConversation() }
            .sortedWith(compareByDescending<Conversation> { it.isPinned }.thenByDescending { it.updatedAt })
    }

    /** Extracts the last bubble of the most recent cached message as a one-line preview. */
    suspend fun lastMessagePreview(conversationId: String, splitStart: String, splitEnd: String): String? {
        val cached = cachedConversationDao.get(conversationId) ?: return null
        val messages = cached.messagesJson?.let {
            runCatching { json.decodeFromString<List<Message>>(it) }.getOrNull()
        } ?: return null
        val last = messages.lastOrNull() ?: return null
        val bubbles = MessageSplitter.split(last.content, splitStart, splitEnd)
        return bubbles.lastOrNull()?.takeIf { it.isNotBlank() }
    }

    private suspend fun cacheConversation(dto: ConversationDetailDto) {
        // Only cache completed messages — drop the still-in-progress node while generating.
        val messages = flattenMessages(dto, isGenerating = dto.isGenerating)
        val existing = cachedConversationDao.get(dto.id)
        cachedConversationDao.upsert(
            CachedConversationEntity(
                conversationId = dto.id,
                title = dto.title,
                assistantId = dto.assistantId,
                isPinned = dto.isPinned,
                updatedAt = dto.updateAt,
                messagesJson = json.encodeToString(messages),
                lastReadAt = existing?.lastReadAt ?: 0
            )
        )
    }

    /** Marks a conversation as read (stores the current time as its last-read timestamp). */
    suspend fun markRead(conversationId: String) {
        cachedConversationDao.updateLastReadAt(conversationId, System.currentTimeMillis())
    }

    suspend fun getLastReadAt(conversationId: String): Long {
        return cachedConversationDao.get(conversationId)?.lastReadAt ?: 0
    }

    /** Counts the number of NEW assistant bubbles created after [since] (unread per bubble). */
    suspend fun countNewBubbles(conversationId: String, since: Long, splitStart: String, splitEnd: String): Int {
        return runCatching {
            val dto = api.getConversationDetail(conversationId)
            var count = 0
            for (node in dto.messages) {
                val active = node.messages.getOrNull(node.selectIndex) ?: continue
                if (!active.role.equals("ASSISTANT", ignoreCase = true)) continue
                if (TimestampParser.parse(active.createdAt) > since) {
                    val text = active.parts
                        .filter { it.type == "text" && !it.text.isNullOrBlank() }
                        .joinToString("\n") { it.text!! }
                    if (text.isNotBlank()) {
                        count += MessageSplitter.split(text, splitStart, splitEnd).size
                    }
                }
            }
            count
        }.getOrDefault(0)
    }

    /**
     * Refreshes metadata for all list items and prunes cached entries that no longer exist.
     * The list is assistant-scoped, so only conversations of the CURRENT assistant that are
     * missing are deleted — other assistants' cached conversations are kept so switching back
     * doesn't lose their cached messages.
     */
    private suspend fun cacheListEntries(list: List<Conversation>) {
        val existing = cachedConversationDao.getAll()
        val currentAssistantId = list.firstOrNull()?.assistantId
        val ids = list.map { it.id }.toSet()
        existing.filter { it.assistantId == currentAssistantId && it.conversationId !in ids }
            .forEach { cachedConversationDao.delete(it.conversationId) }
        list.forEach { conv ->
            val cached = existing.firstOrNull { it.conversationId == conv.id }
            cachedConversationDao.upsert(
                (cached ?: CachedConversationEntity(conversationId = conv.id)).copy(
                    title = conv.title,
                    assistantId = conv.assistantId,
                    isPinned = conv.isPinned,
                    updatedAt = conv.updatedAt
                )
            )
        }
    }

    private fun CachedConversationEntity.toConversation() = Conversation(
        id = conversationId,
        title = title,
        assistantId = assistantId,
        isPinned = isPinned,
        updatedAt = updatedAt
    )

    private fun flattenMessages(dto: ConversationDetailDto, isGenerating: Boolean): List<Message> {
        val nodes = if (isGenerating) dto.messages.dropLast(1) else dto.messages
        return nodes.flatMap { node ->
            val active = node.messages.getOrNull(node.selectIndex)
            if (active != null) messagesFromVariant(active, node.id) else emptyList()
        }
    }

    private fun messagesFromVariant(msg: MessageDto, nodeId: String): List<Message> {
        val textContent = msg.parts
            .filter { it.type == "text" && !it.text.isNullOrBlank() }
            .joinToString("\n") { it.text!! }
        if (textContent.isBlank()) return emptyList()
        val role = when (msg.role.uppercase()) {
            "USER" -> Message.ROLE_USER
            "ASSISTANT" -> Message.ROLE_ASSISTANT
            "SYSTEM" -> Message.ROLE_SYSTEM
            else -> Message.ROLE_ASSISTANT
        }
        return listOf(
            Message(
                id = msg.id.ifBlank { nodeId },
                role = role,
                content = textContent,
                timestamp = TimestampParser.parse(msg.createdAt)
            )
        )
    }

    private fun ConversationListDto.toDomain() = Conversation(
        id = id, title = title, assistantId = assistantId,
        isPinned = isPinned, updatedAt = updateAt
    )

    private fun ConversationDetailDto.toDomain() = Conversation(
        id = id, title = title, assistantId = assistantId,
        isPinned = isPinned, updatedAt = updateAt
    )
}
