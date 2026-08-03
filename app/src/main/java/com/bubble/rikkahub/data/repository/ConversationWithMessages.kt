package com.bubble.rikkahub.data.repository

import com.bubble.rikkahub.domain.model.Conversation
import com.bubble.rikkahub.domain.model.Message

data class ConversationWithMessages(
    val conversation: Conversation,
    val messages: List<Message>,
    /** True when the AI is currently generating a reply in this conversation. */
    val isGenerating: Boolean = false
)
