package com.bubble.rikkahub.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The RikkaHub Settings object, delivered as the `data` of the `settings` SSE event
 * on GET /api/events (there is no plain GET for it). Only the fields the app needs
 * are declared; the rest is ignored via ignoreUnknownKeys.
 */
@Serializable
data class SettingsDto(
    @SerialName("assistantId") val assistantId: String? = null,
    val assistants: List<AssistantDto> = emptyList()
)

@Serializable
data class AssistantDto(
    val id: String? = null,
    val name: String? = null,
    val avatar: AssistantAvatarDto? = null
)

@Serializable
data class AssistantAvatarDto(
    val type: String? = null,
    val content: String? = null,
    val url: String? = null
)
