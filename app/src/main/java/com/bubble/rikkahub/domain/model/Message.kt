package com.bubble.rikkahub.domain.model

import kotlinx.serialization.Serializable

/** A single part of a message: text, or an attachment (image / document / …). */
@Serializable
data class MessagePart(
    val type: String = "text",
    val text: String? = null,
    /** HTTP URL of the attachment, resolved against the server (for display). */
    val url: String? = null,
    val fileName: String? = null,
    val mime: String? = null
) {
    val isText: Boolean get() = type == "text"
    val isImage: Boolean get() = type == "image"
    val isDocument: Boolean get() = type == "document"
}

@Serializable
data class Message(
    val id: String,
    val role: String,
    val content: String,
    val timestamp: Long = 0,
    val isStreaming: Boolean = false,
    /** All parts of the message; [content] is the concatenated text part(s). */
    val parts: List<MessagePart> = emptyList()
) {
    val isUser: Boolean get() = role == "user"
    val isAssistant: Boolean get() = role == "assistant"

    /** Non-text parts (images / files / …), shown without a bubble background. */
    val attachments: List<MessagePart> get() = parts.filter { !it.isText }
    val hasAttachments: Boolean get() = parts.any { !it.isText }

    companion object {
        const val ROLE_USER = "user"
        const val ROLE_ASSISTANT = "assistant"
        const val ROLE_SYSTEM = "system"
    }
}
