package com.bubble.rikkahub.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Full detail returned by GET /api/conversations/{id} */
@Serializable
data class ConversationDetailDto(
    val id: String,
    @SerialName("assistantId") val assistantId: String? = null,
    val title: String,
    /** CRITICAL: The JSON field is "messages", NOT "messageNodes" */
    val messages: List<MessageNodeDto> = emptyList(),
    @SerialName("chatSuggestions") val chatSuggestions: List<String> = emptyList(),
    @SerialName("isPinned") val isPinned: Boolean = false,
    @SerialName("createAt") val createAt: Long = 0,
    @SerialName("updateAt") val updateAt: Long = 0,
    @SerialName("isGenerating") val isGenerating: Boolean = false
)

@Serializable
data class MessageNodeDto(
    val id: String,
    val messages: List<MessageDto> = emptyList(),
    @SerialName("selectIndex") val selectIndex: Int = 0
)

@Serializable
data class MessageDto(
    val id: String,
    val role: String = "",
    val parts: List<UIMessagePartDto> = emptyList(),
    @SerialName("createdAt") val createdAt: String? = null,
    @SerialName("finishedAt") val finishedAt: String? = null
)

@Serializable
data class UIMessagePartDto(
    val type: String = "text",
    val text: String? = null
)
