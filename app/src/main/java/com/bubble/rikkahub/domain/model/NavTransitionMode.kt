package com.bubble.rikkahub.domain.model

/** How the app animates switching bottom-nav tabs and entering/exiting secondary screens. */
enum class NavTransitionMode {
    /** Crossfade (the default Compose animation). */
    FADE,
    /** Stacked: new screen slides in from the right, pops out to the right. */
    SLIDE,
    /** No transition animation. */
    NONE
}
