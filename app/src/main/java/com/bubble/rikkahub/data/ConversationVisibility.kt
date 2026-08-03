package com.bubble.rikkahub.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Tracks which conversation is currently open in the chat screen, so the conversation
 * list can compute unread counts (messages in conversations the user is NOT viewing).
 */
object ConversationVisibility {

    private val _openConversationId = MutableStateFlow<String?>(null)
    val openConversationId: StateFlow<String?> = _openConversationId.asStateFlow()

    fun onOpen(conversationId: String) {
        _openConversationId.value = conversationId
    }

    fun onClose() {
        _openConversationId.value = null
    }
}
