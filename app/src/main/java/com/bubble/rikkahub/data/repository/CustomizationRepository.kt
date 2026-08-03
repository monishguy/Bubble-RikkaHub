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

    // ── Assistant customization (keyed by assistant id, prefixed to avoid collision) ──

    private fun assistantKey(assistantId: String) = "assistant_$assistantId"

    suspend fun getAssistantCustomization(assistantId: String): CustomizationEntity? =
        dao.get(assistantKey(assistantId))

    suspend fun setAssistantAvatar(assistantId: String, uri: String?) =
        setAvatar(assistantKey(assistantId), uri)

    suspend fun setAssistantEmoji(assistantId: String, emoji: String?) =
        setEmoji(assistantKey(assistantId), emoji)

    suspend fun setAssistantNickname(assistantId: String, nickname: String?) =
        setNickname(assistantKey(assistantId), nickname)

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

    suspend fun setChatBackground(conversationId: String, uri: String?) {
        val existing = dao.get(conversationId)
        dao.upsert(
            (existing ?: CustomizationEntity(conversationId)).copy(chatBackgroundUri = uri)
        )
    }

    suspend fun setChatBackgroundColor(conversationId: String, color: Long?) {
        val existing = dao.get(conversationId)
        dao.upsert(
            (existing ?: CustomizationEntity(conversationId)).copy(chatBackgroundColor = color)
        )
    }

    suspend fun removeCustomization(conversationId: String) {
        dao.delete(conversationId)
    }
}
