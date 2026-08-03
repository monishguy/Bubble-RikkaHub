package com.bubble.rikkahub.domain.model

enum class AvatarMode {
    /** Show avatar on every bubble */
    EVERY_BUBBLE,
    /** Show avatar only on the first bubble of a consecutive sequence from same sender */
    FIRST_ONLY,
    /** Show avatar only on the last bubble of a consecutive sequence from same sender */
    LAST_ONLY
}
