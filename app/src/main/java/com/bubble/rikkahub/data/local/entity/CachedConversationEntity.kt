package com.bubble.rikkahub.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Last-known metadata + message history for a conversation, cached locally so the
 * app still works when the RikkaHub server is unreachable.
 * [messagesJson] is a kotlinx.serialization JSON array of [com.bubble.rikkahub.domain.model.Message].
 */
@Entity(tableName = "cached_conversations")
data class CachedConversationEntity(
    @PrimaryKey
    val conversationId: String,
    val title: String = "",
    val assistantId: String? = null,
    val isPinned: Boolean = false,
    val updatedAt: Long = 0,
    val messagesJson: String? = null,
    /** Server time (epoch ms) when the user last read this conversation; used for unread counts. */
    val lastReadAt: Long = 0
)
