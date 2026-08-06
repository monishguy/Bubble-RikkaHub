package com.bubble.rikkahub.data.remote.dto

import kotlinx.serialization.Serializable

/** Body for POST /api/conversations/{id}/messages */
@Serializable
data class ChatStreamRequest(
    val parts: List<UIMessagePartDto> = emptyList()
)
