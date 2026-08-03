package com.bubble.rikkahub.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bubble.rikkahub.data.preferences.AppConfig
import com.bubble.rikkahub.data.preferences.AppPreferences
import com.bubble.rikkahub.data.repository.CustomizationRepository
import com.bubble.rikkahub.domain.model.AvatarMode
import com.bubble.rikkahub.domain.model.ListTheme
import com.bubble.rikkahub.domain.model.SendMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class SettingsUiState(
    val serverUrl: String = AppPreferences.DEFAULT_SERVER_URL,
    val splitStart: String = AppPreferences.DEFAULT_SPLIT_START,
    val splitEnd: String = AppPreferences.DEFAULT_SPLIT_END,
    val sendMode: SendMode = SendMode.TIMER,
    val timerDelay: Int = AppPreferences.DEFAULT_TIMER_DELAY,
    val avatarMode: AvatarMode = AvatarMode.EVERY_BUBBLE,
    val listTheme: ListTheme = ListTheme.FLAT,
    val bubbleDelayMinMs: Int = AppPreferences.DEFAULT_BUBBLE_DELAY_MIN,
    val bubbleDelayMaxMs: Int = AppPreferences.DEFAULT_BUBBLE_DELAY_MAX,
    val refreshIntervalSeconds: Int = AppPreferences.DEFAULT_REFRESH_INTERVAL,
    val bubbleAnimScaleFrom: Float = AppPreferences.DEFAULT_BUBBLE_ANIM_SCALE,
    val bubbleAnimDurationMs: Int = AppPreferences.DEFAULT_BUBBLE_ANIM_DURATION,
    val bubbleAnimBounce: Boolean = AppPreferences.DEFAULT_BUBBLE_ANIM_BOUNCE,
    val bubbleAnimBounciness: Int = AppPreferences.DEFAULT_BUBBLE_ANIM_BOUNCINESS,
    val isSaving: Boolean = false,
    val serverUrlError: String? = null,
    // "Me" profile (user's own avatar/nickname)
    val meNickname: String = "",
    val meEmoji: String = "",
    val meAvatarUri: String? = null,
    val isSavingProfile: Boolean = false
)

class SettingsViewModel(
    private val preferences: AppPreferences,
    private val customizationRepository: CustomizationRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            // Load the user's own profile (avatar/nickname) from local storage.
            val profile = customizationRepository.getSelfProfile()
            _uiState.update {
                it.copy(
                    meNickname = profile?.nickname ?: "",
                    meEmoji = profile?.avatarEmoji ?: "",
                    meAvatarUri = profile?.avatarUri
                )
            }
        }
        viewModelScope.launch {
            combine(
                preferences.serverUrl,
                preferences.splitStartDelimiter,
                preferences.splitEndDelimiter,
                preferences.sendMode,
                preferences.timerDelaySeconds,
                preferences.avatarMode,
                preferences.listTheme,
                preferences.bubbleDelayMinMs,
                preferences.bubbleDelayMaxMs,
                preferences.refreshIntervalSeconds,
                preferences.bubbleAnimScaleFrom,
                preferences.bubbleAnimDurationMs,
                preferences.bubbleAnimBounce,
                preferences.bubbleAnimBounciness
            ) { values ->
                SettingsUiState(
                    serverUrl = values[0] as String,
                    splitStart = values[1] as String,
                    splitEnd = values[2] as String,
                    sendMode = values[3] as SendMode,
                    timerDelay = values[4] as Int,
                    avatarMode = values[5] as AvatarMode,
                    listTheme = values[6] as ListTheme,
                    bubbleDelayMinMs = values[7] as Int,
                    bubbleDelayMaxMs = values[8] as Int,
                    refreshIntervalSeconds = values[9] as Int,
                    bubbleAnimScaleFrom = values[10] as Float,
                    bubbleAnimDurationMs = values[11] as Int,
                    bubbleAnimBounce = values[12] as Boolean,
                    bubbleAnimBounciness = values[13] as Int
                )
            }.collect { state ->
                // Preserve the "me" profile fields set by the local load / user edits.
                _uiState.update { current ->
                    state.copy(
                        meNickname = current.meNickname,
                        meEmoji = current.meEmoji,
                        meAvatarUri = current.meAvatarUri,
                        isSavingProfile = current.isSavingProfile
                    )
                }
            }
        }
    }

    fun onServerUrlChanged(url: String) {
        _uiState.update {
            it.copy(
                serverUrl = url,
                serverUrlError = when {
                    url.isNotBlank() && !preferences.validateServerUrl(url) ->
                        "请输入有效的 HTTP(S) 地址"
                    else -> null
                }
            )
        }
    }

    fun onSplitStartChanged(delim: String) {
        _uiState.update { it.copy(splitStart = delim) }
        viewModelScope.launch { preferences.setSplitStartDelimiter(delim) }
    }

    fun onSplitEndChanged(delim: String) {
        _uiState.update { it.copy(splitEnd = delim) }
        viewModelScope.launch { preferences.setSplitEndDelimiter(delim) }
    }

    fun onSendModeChanged(mode: SendMode) {
        _uiState.update { it.copy(sendMode = mode) }
        viewModelScope.launch { preferences.setSendMode(mode) }
    }

    fun onTimerDelayChanged(seconds: Int) {
        _uiState.update { it.copy(timerDelay = seconds) }
        viewModelScope.launch { preferences.setTimerDelaySeconds(seconds) }
    }

    fun onAvatarModeChanged(mode: AvatarMode) {
        _uiState.update { it.copy(avatarMode = mode) }
        viewModelScope.launch { preferences.setAvatarMode(mode) }
    }

    fun onListThemeChanged(theme: ListTheme) {
        _uiState.update { it.copy(listTheme = theme) }
        viewModelScope.launch { preferences.setListTheme(theme) }
    }

    fun onBubbleDelayMinChanged(ms: Int) {
        _uiState.update { it.copy(bubbleDelayMinMs = ms) }
        viewModelScope.launch { preferences.setBubbleDelayMinMs(ms) }
    }

    fun onBubbleDelayMaxChanged(ms: Int) {
        _uiState.update { it.copy(bubbleDelayMaxMs = ms) }
        viewModelScope.launch { preferences.setBubbleDelayMaxMs(ms) }
    }

    fun onRefreshIntervalChanged(seconds: Int) {
        _uiState.update { it.copy(refreshIntervalSeconds = seconds) }
        viewModelScope.launch { preferences.setRefreshIntervalSeconds(seconds) }
    }

    fun onBubbleAnimScaleFromChanged(v: Float) {
        _uiState.update { it.copy(bubbleAnimScaleFrom = v) }
        viewModelScope.launch { preferences.setBubbleAnimScaleFrom(v) }
    }

    fun onBubbleAnimDurationChanged(ms: Int) {
        _uiState.update { it.copy(bubbleAnimDurationMs = ms) }
        viewModelScope.launch { preferences.setBubbleAnimDurationMs(ms) }
    }

    fun onBubbleAnimBounceChanged(enabled: Boolean) {
        _uiState.update { it.copy(bubbleAnimBounce = enabled) }
        viewModelScope.launch { preferences.setBubbleAnimBounce(enabled) }
    }

    fun onBubbleAnimBouncinessChanged(v: Int) {
        _uiState.update { it.copy(bubbleAnimBounciness = v) }
        viewModelScope.launch { preferences.setBubbleAnimBounciness(v) }
    }

    fun onMeNicknameChanged(value: String) {
        _uiState.update { it.copy(meNickname = value) }
    }

    fun onMeEmojiChanged(value: String) {
        _uiState.update { it.copy(meEmoji = value.take(2)) }
    }

    fun onMeAvatarChanged(uri: String?) {
        _uiState.update { it.copy(meAvatarUri = uri) }
    }

    fun saveMeProfile() {
        val s = _uiState.value
        _uiState.update { it.copy(isSavingProfile = true) }
        viewModelScope.launch {
            customizationRepository.setSelfNickname(s.meNickname.trim().ifBlank { null })
            customizationRepository.setSelfEmoji(s.meEmoji.trim().ifBlank { null })
            customizationRepository.setSelfAvatar(s.meAvatarUri)
            _uiState.update { it.copy(isSavingProfile = false) }
        }
    }

    private val json = Json { ignoreUnknownKeys = true }

    /** Serializes the current configuration (settings + "me" profile) to JSON for export. */
    fun buildConfigJson(): String {
        val s = _uiState.value
        return json.encodeToString(
            AppConfig(
                serverUrl = s.serverUrl,
                splitStart = s.splitStart,
                splitEnd = s.splitEnd,
                sendMode = s.sendMode.name,
                timerDelay = s.timerDelay,
                avatarMode = s.avatarMode.name,
                listTheme = s.listTheme.name,
                bubbleDelayMinMs = s.bubbleDelayMinMs,
                bubbleDelayMaxMs = s.bubbleDelayMaxMs,
                refreshIntervalSeconds = s.refreshIntervalSeconds,
                bubbleAnimScaleFrom = s.bubbleAnimScaleFrom,
                bubbleAnimDurationMs = s.bubbleAnimDurationMs,
                bubbleAnimBounce = s.bubbleAnimBounce,
                bubbleAnimBounciness = s.bubbleAnimBounciness,
                meNickname = s.meNickname,
                meEmoji = s.meEmoji,
                meAvatarUri = s.meAvatarUri
            )
        )
    }

    /** Parses an exported config JSON and applies it to preferences + the "me" profile. */
    fun applyConfigJson(raw: String) {
        val config = runCatching { json.decodeFromString<AppConfig>(raw) }.getOrNull()
            ?: return
        viewModelScope.launch {
            preferences.setServerUrl(config.serverUrl)
            preferences.setSplitStartDelimiter(config.splitStart)
            preferences.setSplitEndDelimiter(config.splitEnd)
            preferences.setSendMode(
                runCatching { SendMode.valueOf(config.sendMode) }.getOrDefault(SendMode.TIMER)
            )
            preferences.setTimerDelaySeconds(config.timerDelay)
            preferences.setAvatarMode(
                runCatching { AvatarMode.valueOf(config.avatarMode) }.getOrDefault(AvatarMode.EVERY_BUBBLE)
            )
            preferences.setListTheme(
                runCatching { ListTheme.valueOf(config.listTheme) }.getOrDefault(ListTheme.FLAT)
            )
            preferences.setBubbleDelayMinMs(config.bubbleDelayMinMs)
            preferences.setBubbleDelayMaxMs(config.bubbleDelayMaxMs)
            preferences.setRefreshIntervalSeconds(config.refreshIntervalSeconds)
            preferences.setBubbleAnimScaleFrom(config.bubbleAnimScaleFrom)
            preferences.setBubbleAnimDurationMs(config.bubbleAnimDurationMs)
            preferences.setBubbleAnimBounce(config.bubbleAnimBounce)
            preferences.setBubbleAnimBounciness(config.bubbleAnimBounciness)
            customizationRepository.setSelfNickname(config.meNickname.ifBlank { null })
            customizationRepository.setSelfEmoji(config.meEmoji.ifBlank { null })
            customizationRepository.setSelfAvatar(config.meAvatarUri)
            reloadProfile()
        }
    }

    private suspend fun reloadProfile() {
        val profile = customizationRepository.getSelfProfile()
        _uiState.update {
            it.copy(
                meNickname = profile?.nickname ?: "",
                meEmoji = profile?.avatarEmoji ?: "",
                meAvatarUri = profile?.avatarUri
            )
        }
    }

    fun saveServerUrl() {
        val url = _uiState.value.serverUrl.trim()
        if (url.isBlank() || !preferences.validateServerUrl(url)) {
            _uiState.update { it.copy(serverUrlError = "请输入有效的服务器地址") }
            return
        }
        _uiState.update { it.copy(isSaving = true, serverUrlError = null) }
        viewModelScope.launch {
            preferences.setServerUrl(url)
            _uiState.update { it.copy(isSaving = false) }
        }
    }
}
