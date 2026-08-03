package com.bubble.rikkahub.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** List item returned by GET /api/conversations */
@Serializable
data class ConversationListDto(
    val id: String,
    @SerialName("assistantId") val assistantId: String? = null,
    val title: String,
    @SerialName("isPinned") val isPinned: Boolean = false,
    @SerialName("folderId") val folderId: String? = null,
    @SerialName("createAt") val createAt: Long = 0,
    @SerialName("updateAt") val updateAt: Long = 0,
    @SerialName("isGenerating") val isGenerating: Boolean = false
)
