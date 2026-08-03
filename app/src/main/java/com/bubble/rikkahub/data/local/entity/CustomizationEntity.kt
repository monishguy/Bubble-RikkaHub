package com.bubble.rikkahub.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "customizations")
data class CustomizationEntity(
    @PrimaryKey
    val conversationId: String,
    val avatarUri: String? = null,
    val avatarEmoji: String? = null,
    val nickname: String? = null,
    val chatBackgroundUri: String? = null,
    val chatBackgroundColor: Long? = null
)
