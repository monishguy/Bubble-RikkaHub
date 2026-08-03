package com.bubble.rikkahub.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A message that could not be delivered while the server was offline.
 * [text] is the fully packed message (with split delimiters already applied),
 * so it can be sent verbatim once the connection returns.
 */
@Entity(tableName = "pending_messages")
data class PendingMessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val conversationId: String,
    val text: String,
    val createdAt: Long
)
