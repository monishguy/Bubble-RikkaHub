package com.bubble.rikkahub.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.bubble.rikkahub.data.local.entity.PendingMessageEntity

@Dao
interface PendingMessageDao {
    @Query("SELECT * FROM pending_messages ORDER BY createdAt ASC")
    suspend fun getAll(): List<PendingMessageEntity>

    @Query("SELECT * FROM pending_messages WHERE conversationId = :conversationId ORDER BY createdAt ASC")
    suspend fun getForConversation(conversationId: String): List<PendingMessageEntity>

    @Insert
    suspend fun insert(entity: PendingMessageEntity): Long

    @Query("DELETE FROM pending_messages WHERE id = :id")
    suspend fun delete(id: Long)
}
