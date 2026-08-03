package com.bubble.rikkahub.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.bubble.rikkahub.data.local.entity.CachedConversationEntity

@Dao
interface CachedConversationDao {
    @Query("SELECT * FROM cached_conversations WHERE conversationId = :id")
    suspend fun get(id: String): CachedConversationEntity?

    @Query("SELECT * FROM cached_conversations")
    suspend fun getAll(): List<CachedConversationEntity>

    @Upsert
    suspend fun upsert(entity: CachedConversationEntity)

    @Query("DELETE FROM cached_conversations WHERE conversationId = :id")
    suspend fun delete(id: String)

    @Query("UPDATE cached_conversations SET lastReadAt = :ts WHERE conversationId = :id")
    suspend fun updateLastReadAt(id: String, ts: Long)
}
