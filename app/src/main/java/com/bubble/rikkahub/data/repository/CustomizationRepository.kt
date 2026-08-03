package com.bubble.rikkahub.data.repository

import com.bubble.rikkahub.data.local.dao.CustomizationDao
import com.bubble.rikkahub.data.local.entity.CustomizationEntity

class CustomizationRepository(private val dao: CustomizationDao) {

    companion object {
        /** Reserved key under which the user's own profile (avatar/nickname) is stored. */
        const val SELF_ID = "__self__"
    }

    suspend fun getCustomization(conversationId: String): CustomizationEntity? {
        return dao.get(conversationId)
    }

    // ── "Me" profile (user's own avatar/nickname, shown on right-side bubbles) ──

    suspend fun getSelfProfile(): CustomizationEntity? = dao.get(SELF_ID)

    suspend fun setSelfAvatar(uri: String?) = setAvatar(SELF_ID, uri)

    suspend fun setSelfEmoji(emoji: String?) = setEmoji(SELF_ID, emoji)

    suspend fun setSelfNickname(nickname: String?) = setNickname(SELF_ID, nickname)

    suspend fun setAvatar(conversationId: String, uri: String?) {
        val existing = dao.get(conversationId)
        dao.upsert(
            (existing ?: CustomizationEntity(conversationId)).copy(avatarUri = uri)
        )
    }

    suspend fun setEmoji(conversationId: String, emoji: String?) {
        val existing = dao.get(conversationId)
        dao.upsert(
            (existing ?: CustomizationEntity(conversationId)).copy(avatarEmoji = emoji)
        )
    }

    suspend fun setNickname(conversationId: String, nickname: String?) {
        val existing = dao.get(conversationId)
        dao.upsert(
            (existing ?: CustomizationEntity(conversationId)).copy(nickname = nickname)
        )
    }

    suspend fun removeCustomization(conversationId: String) {
        dao.delete(conversationId)
    }
}
