package com.bubble.rikkahub.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.bubble.rikkahub.data.local.entity.CustomizationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomizationDao {

    @Query("SELECT * FROM customizations WHERE conversationId = :id")
    suspend fun get(id: String): CustomizationEntity?

    @Upsert
    suspend fun upsert(entity: CustomizationEntity)

    @Query("DELETE FROM customizations WHERE conversationId = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM customizations")
    fun getAllFlow(): Flow<List<CustomizationEntity>>
}
