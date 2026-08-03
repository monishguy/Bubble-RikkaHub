package com.bubble.rikkahub.data.remote.dto

import kotlinx.serialization.Serializable

/** Body for POST /api/conversations/{id}/messages */
@Serializable
data class ChatStreamRequest(
    val parts: List<TextPart> = emptyList()
)

@Serializable
data class TextPart(
    val type: String = "text",
    val text: String
)
