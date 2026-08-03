package com.bubble.rikkahub.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Message(
    val id: String,
    val role: String,
    val content: String,
    val timestamp: Long = 0,
    val isStreaming: Boolean = false
) {
    val isUser: Boolean get() = role == "user"
    val isAssistant: Boolean get() = role == "assistant"

    companion object {
        const val ROLE_USER = "user"
        const val ROLE_ASSISTANT = "assistant"
        const val ROLE_SYSTEM = "system"
    }
}
