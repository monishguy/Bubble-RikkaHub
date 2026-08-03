package com.bubble.rikkahub.data.preferences

import kotlinx.serialization.Serializable

/** Serializable snapshot of the app's configuration, used for export/import. */
@Serializable
data class AppConfig(
    val serverUrl: String = "",
    val splitStart: String = AppPreferences.DEFAULT_SPLIT_START,
    val splitEnd: String = AppPreferences.DEFAULT_SPLIT_END,
    val sendMode: String = AppPreferences.DEFAULT_SEND_MODE,
    val timerDelay: Int = AppPreferences.DEFAULT_TIMER_DELAY,
    val avatarMode: String = AppPreferences.DEFAULT_AVATAR_MODE,
    val listTheme: String = AppPreferences.DEFAULT_LIST_THEME,
    val bubbleDelayMinMs: Int = AppPreferences.DEFAULT_BUBBLE_DELAY_MIN,
    val bubbleDelayMaxMs: Int = AppPreferences.DEFAULT_BUBBLE_DELAY_MAX,
    val refreshIntervalSeconds: Int = AppPreferences.DEFAULT_REFRESH_INTERVAL,
    val bubbleAnimScaleFrom: Float = AppPreferences.DEFAULT_BUBBLE_ANIM_SCALE,
    val bubbleAnimDurationMs: Int = AppPreferences.DEFAULT_BUBBLE_ANIM_DURATION,
    val bubbleAnimBounce: Boolean = AppPreferences.DEFAULT_BUBBLE_ANIM_BOUNCE,
    val bubbleAnimBounciness: Int = AppPreferences.DEFAULT_BUBBLE_ANIM_BOUNCINESS,
    // "Me" profile
    val meNickname: String = "",
    val meEmoji: String = "",
    val meAvatarUri: String? = null
)
