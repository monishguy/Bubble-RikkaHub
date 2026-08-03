package com.bubble.rikkahub.domain.model

/**
 * A RikkaHub assistant (a profile that owns its own set of conversations).
 * Derived from the Settings object's assistants[] array.
 */
data class AssistantInfo(
    val id: String,
    val name: String = "",
    val avatarUrl: String? = null,
    val avatarEmoji: String? = null
) {
    val displayName: String get() = name.ifBlank { "未命名助手" }
}
