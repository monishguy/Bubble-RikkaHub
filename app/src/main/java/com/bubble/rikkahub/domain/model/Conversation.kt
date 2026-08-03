package com.bubble.rikkahub.domain.model

data class Conversation(
    val id: String,
    val title: String,
    val assistantId: String? = null,
    val avatarUrl: String? = null,
    val isPinned: Boolean = false,
    val updatedAt: Long = 0,
    val customAvatarUri: String? = null,
    val customEmoji: String? = null,
    val customNickname: String? = null,
    /** Last bubble message from the local cache, shown as a one-line preview in the list. */
    val lastMessagePreview: String? = null,
    /** Custom chat background: image URI or ARGB color (stored locally). */
    val chatBackgroundUri: String? = null,
    val chatBackgroundColor: Long? = null
) {
    /** Effective display name: custom override > API title */
    val displayName: String get() = customNickname ?: title

    /** First character fallback for avatar when no image/emoji is available */
    val avatarFallback: String get() = displayName.firstOrNull()?.toString() ?: "?"

    /** Effective avatar: first check for custom, then API, then fallback */
    val effectiveAvatarUrl: String?
        get() = avatarUrl
}
