package com.bubble.rikkahub.domain.model

enum class SendMode {
    /** Auto-send after N seconds of inactivity */
    TIMER,
    /** User sends an empty message to manually trigger the batch */
    MANUAL
}
