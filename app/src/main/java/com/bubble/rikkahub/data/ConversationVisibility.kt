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

    /** Conversation ids that were just created by the app and don't exist on the server yet. */
    private val newConversationIds = mutableSetOf<String>()

    fun onOpen(conversationId: String) {
        _openConversationId.value = conversationId
    }

    fun onClose() {
        _openConversationId.value = null
    }

    fun markAsNewConversation(conversationId: String) {
        newConversationIds.add(conversationId)
    }

    fun isNewConversation(conversationId: String): Boolean = conversationId in newConversationIds

    fun clearNewConversation(conversationId: String) {
        newConversationIds.remove(conversationId)
    }
}
