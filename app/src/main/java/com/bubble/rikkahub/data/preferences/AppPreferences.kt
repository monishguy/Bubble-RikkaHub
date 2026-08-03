package com.bubble.rikkahub.data.preferences

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.bubble.rikkahub.domain.model.AvatarMode
import com.bubble.rikkahub.domain.model.ListTheme
import com.bubble.rikkahub.domain.model.SendMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AppPreferences(private val dataStore: DataStore<Preferences>) {

    companion object {
        private const val TAG = "AppPreferences"

        val KEY_SERVER_URL = stringPreferencesKey("server_url")
        val KEY_SPLIT_START = stringPreferencesKey("split_start")
        val KEY_SPLIT_END = stringPreferencesKey("split_end")
        val KEY_SEND_MODE = stringPreferencesKey("send_mode")
        val KEY_TIMER_DELAY = intPreferencesKey("timer_delay")
        val KEY_AVATAR_MODE = stringPreferencesKey("avatar_mode")
        val KEY_LIST_THEME = stringPreferencesKey("list_theme")
        val KEY_BUBBLE_DELAY_MIN = intPreferencesKey("bubble_delay_min")
        val KEY_BUBBLE_DELAY_MAX = intPreferencesKey("bubble_delay_max")
        val KEY_REFRESH_INTERVAL = intPreferencesKey("refresh_interval")
        val KEY_BUBBLE_ANIM_SCALE = floatPreferencesKey("bubble_anim_scale")
        val KEY_BUBBLE_ANIM_DURATION = intPreferencesKey("bubble_anim_duration")
        val KEY_BUBBLE_ANIM_BOUNCE = booleanPreferencesKey("bubble_anim_bounce")
        val KEY_BUBBLE_ANIM_BOUNCINESS = intPreferencesKey("bubble_anim_bounciness")
        val KEY_AUTO_FORMAT_PROMPT = booleanPreferencesKey("auto_format_prompt")
        val KEY_AUTO_FORMAT_PROMPT_TEXT = stringPreferencesKey("auto_format_prompt_text")

        const val DEFAULT_SERVER_URL = "http://localhost:8080"
        const val DEFAULT_SPLIT_START = "#"
        const val DEFAULT_SPLIT_END = "*"
        const val DEFAULT_SEND_MODE = "TIMER"
        const val DEFAULT_TIMER_DELAY = 3
        const val DEFAULT_AVATAR_MODE = "EVERY_BUBBLE"
        const val DEFAULT_LIST_THEME = "FLAT"
        const val DEFAULT_BUBBLE_DELAY_MIN = 200
        const val DEFAULT_BUBBLE_DELAY_MAX = 800
        const val DEFAULT_REFRESH_INTERVAL = 15
        const val DEFAULT_BUBBLE_ANIM_SCALE = 0.7f
        const val DEFAULT_BUBBLE_ANIM_DURATION = 400
        const val DEFAULT_BUBBLE_ANIM_BOUNCE = true
        const val DEFAULT_BUBBLE_ANIM_BOUNCINESS = 60
        const val DEFAULT_AUTO_FORMAT_PROMPT = true
        const val DEFAULT_AUTO_FORMAT_PROMPT_TEXT =
            "这是格式要求说明，请勿回复该说明本身：后续每次回复，你都必须用 {start}内容{end} 包裹每一条消息，每个 {start}...{end} 视为一条独立消息。请忽略本条说明，直接开始对话，回复用户的发言。"

        const val SERVER_URL_MIN_LENGTH = 5
    }

    val serverUrl: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_SERVER_URL] ?: DEFAULT_SERVER_URL
    }

    val splitStartDelimiter: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_SPLIT_START] ?: DEFAULT_SPLIT_START
    }

    val splitEndDelimiter: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_SPLIT_END] ?: DEFAULT_SPLIT_END
    }

    val sendMode: Flow<SendMode> = dataStore.data.map { prefs ->
        val mode = prefs[KEY_SEND_MODE] ?: DEFAULT_SEND_MODE
        try { SendMode.valueOf(mode) } catch (_: Exception) { SendMode.TIMER }
    }

    val timerDelaySeconds: Flow<Int> = dataStore.data.map { prefs ->
        prefs[KEY_TIMER_DELAY] ?: DEFAULT_TIMER_DELAY
    }

    val avatarMode: Flow<AvatarMode> = dataStore.data.map { prefs ->
        val mode = prefs[KEY_AVATAR_MODE] ?: DEFAULT_AVATAR_MODE
        try { AvatarMode.valueOf(mode) } catch (_: Exception) { AvatarMode.EVERY_BUBBLE }
    }

    val listTheme: Flow<ListTheme> = dataStore.data.map { prefs ->
        val theme = prefs[KEY_LIST_THEME] ?: DEFAULT_LIST_THEME
        try { ListTheme.valueOf(theme) } catch (_: Exception) { ListTheme.FLAT }
    }

    val bubbleDelayMinMs: Flow<Int> = dataStore.data.map { prefs ->
        prefs[KEY_BUBBLE_DELAY_MIN] ?: DEFAULT_BUBBLE_DELAY_MIN
    }

    val bubbleDelayMaxMs: Flow<Int> = dataStore.data.map { prefs ->
        prefs[KEY_BUBBLE_DELAY_MAX] ?: DEFAULT_BUBBLE_DELAY_MAX
    }

    val refreshIntervalSeconds: Flow<Int> = dataStore.data.map { prefs ->
        prefs[KEY_REFRESH_INTERVAL] ?: DEFAULT_REFRESH_INTERVAL
    }

    val bubbleAnimScaleFrom: Flow<Float> = dataStore.data.map { prefs ->
        prefs[KEY_BUBBLE_ANIM_SCALE] ?: DEFAULT_BUBBLE_ANIM_SCALE
    }

    val bubbleAnimDurationMs: Flow<Int> = dataStore.data.map { prefs ->
        prefs[KEY_BUBBLE_ANIM_DURATION] ?: DEFAULT_BUBBLE_ANIM_DURATION
    }

    val bubbleAnimBounce: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_BUBBLE_ANIM_BOUNCE] ?: DEFAULT_BUBBLE_ANIM_BOUNCE
    }

    val bubbleAnimBounciness: Flow<Int> = dataStore.data.map { prefs ->
        prefs[KEY_BUBBLE_ANIM_BOUNCINESS] ?: DEFAULT_BUBBLE_ANIM_BOUNCINESS
    }

    val autoFormatPrompt: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_AUTO_FORMAT_PROMPT] ?: DEFAULT_AUTO_FORMAT_PROMPT
    }

    val autoFormatPromptText: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_AUTO_FORMAT_PROMPT_TEXT] ?: DEFAULT_AUTO_FORMAT_PROMPT_TEXT
    }

    suspend fun setServerUrl(url: String) {
        Log.d(TAG, "setServerUrl: $url")
        dataStore.edit { it[KEY_SERVER_URL] = url }
    }

    suspend fun setSplitStartDelimiter(delim: String) {
        dataStore.edit { it[KEY_SPLIT_START] = delim.ifBlank { DEFAULT_SPLIT_START } }
    }

    suspend fun setSplitEndDelimiter(delim: String) {
        dataStore.edit { it[KEY_SPLIT_END] = delim.ifBlank { DEFAULT_SPLIT_END } }
    }

    suspend fun setSendMode(mode: SendMode) {
        dataStore.edit { it[KEY_SEND_MODE] = mode.name }
    }

    suspend fun setTimerDelaySeconds(seconds: Int) {
        dataStore.edit { it[KEY_TIMER_DELAY] = seconds.coerceIn(1, 60) }
    }

    suspend fun setAvatarMode(mode: AvatarMode) {
        dataStore.edit { it[KEY_AVATAR_MODE] = mode.name }
    }

    suspend fun setListTheme(theme: ListTheme) {
        dataStore.edit { it[KEY_LIST_THEME] = theme.name }
    }

    suspend fun setBubbleDelayMinMs(ms: Int) {
        dataStore.edit { it[KEY_BUBBLE_DELAY_MIN] = ms.coerceIn(0, 5000) }
    }

    suspend fun setBubbleDelayMaxMs(ms: Int) {
        dataStore.edit { it[KEY_BUBBLE_DELAY_MAX] = ms.coerceIn(0, 10_000) }
    }

    suspend fun setRefreshIntervalSeconds(seconds: Int) {
        dataStore.edit { it[KEY_REFRESH_INTERVAL] = seconds.coerceIn(5, 60) }
    }

    suspend fun setBubbleAnimScaleFrom(v: Float) {
        dataStore.edit { it[KEY_BUBBLE_ANIM_SCALE] = v.coerceIn(0.1f, 1f) }
    }

    suspend fun setBubbleAnimDurationMs(ms: Int) {
        dataStore.edit { it[KEY_BUBBLE_ANIM_DURATION] = ms.coerceIn(50, 3000) }
    }

    suspend fun setBubbleAnimBounce(enabled: Boolean) {
        dataStore.edit { it[KEY_BUBBLE_ANIM_BOUNCE] = enabled }
    }

    suspend fun setBubbleAnimBounciness(v: Int) {
        dataStore.edit { it[KEY_BUBBLE_ANIM_BOUNCINESS] = v.coerceIn(0, 100) }
    }

    suspend fun setAutoFormatPrompt(enabled: Boolean) {
        dataStore.edit { it[KEY_AUTO_FORMAT_PROMPT] = enabled }
    }

    suspend fun setAutoFormatPromptText(text: String) {
        dataStore.edit { it[KEY_AUTO_FORMAT_PROMPT_TEXT] = text }
    }

    suspend fun resetAutoFormatPromptText() {
        dataStore.edit { it[KEY_AUTO_FORMAT_PROMPT_TEXT] = DEFAULT_AUTO_FORMAT_PROMPT_TEXT }
    }

    fun validateServerUrl(url: String): Boolean {
        return url.length >= SERVER_URL_MIN_LENGTH &&
                (url.startsWith("http://") || url.startsWith("https://"))
    }
}
