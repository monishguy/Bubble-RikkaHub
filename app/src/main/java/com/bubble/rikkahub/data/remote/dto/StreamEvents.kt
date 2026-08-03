package com.bubble.rikkahub.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** Events from GET /api/events (multiplexed SSE) */
@Serializable
data class SettingsEvent(
    val settings: JsonElement  // Full settings object - contains assistants list
)

/** Events from GET /api/conversations/{id}/stream */
@Serializable
data class ConversationSnapshotEvent(
    val seq: Long = 0,
    val conversation: ConversationDetailDto
)

@Serializable
data class ConversationNodeUpdateEvent(
    val seq: Long = 0,
    @SerialName("conversationId") val conversationId: String,
    @SerialName("nodeId") val nodeId: String,
    @SerialName("nodeIndex") val nodeIndex: Int,
    val node: MessageNodeDto,
    @SerialName("updateAt") val updateAt: Long = 0,
    @SerialName("isGenerating") val isGenerating: Boolean = false
)

@Serializable
data class GenerationDoneEvent(
    @SerialName("conversationId") val conversationId: String
)

@Serializable
data class ErrorEvent(
    val message: String
)
