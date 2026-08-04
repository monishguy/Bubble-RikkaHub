package com.bubble.rikkahub.ui.screens.conversations

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bubble.rikkahub.data.ConversationVisibility
import com.bubble.rikkahub.data.NotificationHelper
import com.bubble.rikkahub.data.preferences.AppPreferences
import com.bubble.rikkahub.data.remote.ConnectionMonitor
import com.bubble.rikkahub.data.remote.dto.SettingsDto
import com.bubble.rikkahub.data.repository.ConversationRepository
import com.bubble.rikkahub.data.repository.CustomizationRepository
import com.bubble.rikkahub.domain.model.AssistantInfo
import com.bubble.rikkahub.domain.model.Conversation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

sealed interface ConversationListUiState {
    data object Loading : ConversationListUiState
    data class Success(
        val conversations: List<Conversation> = emptyList(),
        val isRefreshing: Boolean = false
    ) : ConversationListUiState
    data class Error(val message: String) : ConversationListUiState
}

class ConversationListViewModel(
    private val conversationRepository: ConversationRepository,
    private val customizationRepository: CustomizationRepository,
    private val connectionMonitor: ConnectionMonitor,
    private val appContext: Context,
    private val appPreferences: AppPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow<ConversationListUiState>(ConversationListUiState.Loading)
    val uiState: StateFlow<ConversationListUiState> = _uiState.asStateFlow()

    // ── Unread counts (per conversation) ──
    private val _unreadCounts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val unreadCounts: StateFlow<Map<String, Int>> = _unreadCounts.asStateFlow()

    /** updateAt value the last time the list was observed, used to detect new activity. */
    private val lastObservedUpdateAt = mutableMapOf<String, Long>()

    // ── Assistant state (used by the top-bar switcher) ──
    private val _currentAssistant = MutableStateFlow<AssistantInfo?>(null)
    val currentAssistant: StateFlow<AssistantInfo?> = _currentAssistant.asStateFlow()

    private val _assistants = MutableStateFlow<List<AssistantInfo>>(emptyList())
    val assistants: StateFlow<List<AssistantInfo>> = _assistants.asStateFlow()

    private val _isSwitchingAssistant = MutableStateFlow(false)
    val isSwitchingAssistant: StateFlow<Boolean> = _isSwitchingAssistant.asStateFlow()

    private val _assistantError = MutableStateFlow<String?>(null)
    val assistantError: StateFlow<String?> = _assistantError.asStateFlow()

    fun clearAssistantError() { _assistantError.value = null }

    // ── Offline state (drives the red banner) ──
    private val _isOffline = MutableStateFlow(false)
    val isOffline: StateFlow<Boolean> = _isOffline.asStateFlow()

    private val json = Json { ignoreUnknownKeys = true }
    private var refreshJob: Job? = null

    companion object {
        private const val TAG = "ConversationListVM"
    }

    init {
        // Restore the last-known assistant list + active assistant so the top-bar switcher
        // and the per-assistant conversation filter work immediately, even offline.
        viewModelScope.launch {
            val cached = appPreferences.getCachedAssistants()
            if (cached.isNotEmpty() && _assistants.value.isEmpty()) {
                _assistants.value = cached
                val currentId = appPreferences.getCachedCurrentAssistantId()
                _currentAssistant.value = cached.firstOrNull { it.id == currentId } ?: cached.firstOrNull()
                Log.d(TAG, "已从本地缓存恢复助手列表: ${cached.size} 个")
            }
        }
        load()
        // Auto-reload when the connection comes back.
        viewModelScope.launch {
            var wasOnline = connectionMonitor.isOnline.value
            connectionMonitor.isOnline.collect { online ->
                if (online && !wasOnline) {
                    _isOffline.value = false
                    load()
                }
                wasOnline = online
            }
        }
        observeServerEvents()
        // When a conversation is opened, clear its unread count immediately and persist the read time.
        viewModelScope.launch {
            ConversationVisibility.openConversationId.collect { openId ->
                if (openId != null) {
                    _unreadCounts.update { it - openId }
                    conversationRepository.markRead(openId)
                }
            }
        }
        // Periodic silent refresh: keeps new messages/conversations flowing even if the
        // SSE /api/events connection drops or is unreachable in the background.
        viewModelScope.launch {
            while (isActive) {
                val intervalMs = (appPreferences.refreshIntervalSeconds.first() * 1000L).coerceAtLeast(1_000L)
                delay(intervalMs)
                loadConversations()
            }
        }
    }

    /** Marks a conversation as read (clears its unread badge and persists the read time). */
    fun readConversation(id: String) {
        _unreadCounts.update { it - id }
        viewModelScope.launch { conversationRepository.markRead(id) }
    }

    /**
     * Maintains a long-lived /api/events connection: it delivers the current Settings
     * object (assistant list + active assistant) on connect, and a
     * `conversation_list_invalidate` event whenever conversations change on the server,
     * which keeps the list up to date in real time. Reconnects automatically.
     */
    private fun observeServerEvents() {
        viewModelScope.launch {
            while (isActive) {
                try {
                    conversationRepository.streamConversationListEvents().collect { frame ->
                        when (frame.event) {
                            "settings" -> handleSettingsFrame(frame.data)
                            "conversation_list_invalidate" -> debounceRefresh()
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Connection dropped — wait a moment and reconnect.
                    delay(5_000)
                }
            }
        }
    }

    private suspend fun handleSettingsFrame(data: String?) {
        if (data.isNullOrBlank()) return
        val settings = runCatching { json.decodeFromString<SettingsDto>(data) }.getOrNull()
        if (settings != null) {
            applySettings(settings)
        } else {
            Log.w(TAG, "解析 settings 事件失败")
        }
    }

    private suspend fun applySettings(settings: SettingsDto) {
        val resolved = settings.assistants.mapNotNull { a ->
            val id = a.id ?: return@mapNotNull null
            // Local customization (set by the user, keyed by assistant id) overrides the server.
            val custom = customizationRepository.getAssistantCustomization(id)
            AssistantInfo(
                id = id,
                name = custom?.nickname ?: a.name ?: "",
                avatarUrl = custom?.avatarUri
                    ?: a.avatar?.url?.takeIf { it.startsWith("http", ignoreCase = true) },
                avatarEmoji = custom?.avatarEmoji ?: a.avatar?.content?.takeIf { it.isNotBlank() }
            )
        }
        _assistants.value = resolved
        _currentAssistant.value = resolved.firstOrNull { it.id == settings.assistantId }
        // Persist so the switcher + per-assistant filter still work when offline.
        appPreferences.saveCachedAssistants(resolved)
        appPreferences.saveCachedCurrentAssistantId(settings.assistantId)
        Log.d(TAG, "助手列表已更新: ${resolved.size} 个，当前=${_currentAssistant.value?.name}")
    }

    /** Saves the user's custom avatar/name for an assistant (stored locally by assistant id). */
    fun saveAssistantCustomization(id: String, nickname: String, emoji: String, avatarUri: String?) {
        viewModelScope.launch {
            customizationRepository.setAssistantNickname(id, nickname.ifBlank { null })
            customizationRepository.setAssistantEmoji(id, emoji.ifBlank { null })
            customizationRepository.setAssistantAvatar(id, avatarUri)
            // Refresh the assistant list to reflect the new customization.
            if (_assistants.value.isEmpty()) {
                refreshAssistantSettingsFromServer()
            } else {
                // Re-derive from the last settings if available; otherwise just re-map.
                val cur = _assistants.value
                _assistants.value = cur.map {
                    if (it.id == id) {
                        it.copy(
                            name = nickname.ifBlank { it.name },
                            avatarUrl = avatarUri ?: it.avatarUrl,
                            avatarEmoji = emoji.ifBlank { it.avatarEmoji }
                        )
                    } else it
                }
                _currentAssistant.value = _assistants.value.firstOrNull { it.id == _currentAssistant.value?.id }
                // Keep the offline cache in sync with the local customization.
                appPreferences.saveCachedAssistants(_assistants.value)
            }
        }
    }

    private suspend fun refreshAssistantSettingsFromServer() {
        conversationRepository.getSettings()
            .onSuccess { applySettings(it) }
            .onFailure { Log.w(TAG, "兜底拉取助手列表失败: ${it.message}") }
    }

    private fun debounceRefresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            delay(500)
            refresh()
        }
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { ConversationListUiState.Loading }
            loadConversations()
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val cur = _uiState.value as? ConversationListUiState.Success
            if (cur != null) _uiState.update { cur.copy(isRefreshing = true) }
            loadConversations()
        }
    }

    fun deleteConversation(id: String) {
        viewModelScope.launch {
            conversationRepository.deleteConversation(id)
            refresh()
        }
    }

    fun togglePin(id: String) {
        viewModelScope.launch {
            conversationRepository.togglePin(id)
            refresh()
        }
    }

    fun switchAssistant(id: String) {
        viewModelScope.launch {
            _isSwitchingAssistant.value = true
            _assistantError.value = null
            // Offline: there's no server to switch on, so just switch the local view to that
            // assistant's cached conversations (matched against the cached assistant list).
            if (_isOffline.value) {
                val target = _assistants.value.firstOrNull { it.id == id }
                if (target != null) {
                    _currentAssistant.value = target
                    appPreferences.saveCachedCurrentAssistantId(id)
                    lastObservedUpdateAt.clear()
                    loadConversations()
                } else {
                    _assistantError.value = "没有找到助手「$id」的缓存信息"
                }
                _isSwitchingAssistant.value = false
                return@launch
            }
            conversationRepository.switchAssistant(id)
                .onSuccess {
                    _isSwitchingAssistant.value = false
                    // Reflect the new assistant in the top bar immediately, before the
                    // settings SSE event arrives.
                    _currentAssistant.value = _assistants.value.firstOrNull { it.id == id }
                    appPreferences.saveCachedCurrentAssistantId(id)
                    // The conversation set changed — reset unread baselines.
                    lastObservedUpdateAt.clear()
                    loadConversations()
                }
                .onFailure { e ->
                    _isSwitchingAssistant.value = false
                    _assistantError.value = e.message ?: "切换助手失败"
                }
        }
    }

    private suspend fun loadConversations() {
        // Conversation list and assistant settings are fetched independently so that a
        // failure in one does not hide the other.
        conversationRepository.getConversations()
            .onSuccess { list ->
                connectionMonitor.reportSuccess()
                _isOffline.value = false
                // Enrich first so notifications/UI use the user's custom nickname & avatar.
                val enriched = list.map { enrich(it) }
                updateUnreadCounts(enriched)
                _uiState.update { ConversationListUiState.Success(enriched, false) }
            }
            .onFailure { e ->
                connectionMonitor.reportFailure()
                _isOffline.value = true
                // Offline cache is assistant-scoped, matching the online list: only show the
                // current assistant's cached conversations (plus any with unknown assistant).
                val currentId = _currentAssistant.value?.id ?: appPreferences.getCachedCurrentAssistantId()
                val cached = conversationRepository.getCachedConversations(currentId)
                if (cached.isNotEmpty()) {
                    val enriched = cached.map { enrich(it) }
                    _uiState.update { ConversationListUiState.Success(enriched, false) }
                } else if (_assistants.value.isNotEmpty()) {
                    // We know the assistants but this one has no cached conversations yet.
                    _uiState.update { ConversationListUiState.Success(emptyList(), false) }
                } else {
                    _uiState.update { ConversationListUiState.Error(e.message ?: "加载失败") }
                }
            }

        // Fallback: if the live /api/events connection hasn't populated the assistant list yet,
        // fetch the settings directly once.
        if (_assistants.value.isEmpty()) {
            refreshAssistantSettingsFromServer()
        }

        _isSwitchingAssistant.value = false
    }

    private suspend fun enrich(conv: Conversation): Conversation {
        val c = customizationRepository.getCustomization(conv.id)
        val customized = if (c != null) conv.copy(
            customAvatarUri = c.avatarUri, customEmoji = c.avatarEmoji, customNickname = c.nickname
        ) else conv
        val splitStart = appPreferences.splitStartDelimiter.first()
        val splitEnd = appPreferences.splitEndDelimiter.first()
        return customized.copy(
            lastMessagePreview = conversationRepository.lastMessagePreview(conv.id, splitStart, splitEnd)
        )
    }

    // ── Unread tracking ─────────────────────────────────────────

    /**
     * Detects conversations that changed while NOT being viewed, then counts their unread
     * messages per BUBBLE (fetching the detail and splitting new assistant messages since the
     * last-read time). Fires a notification when a conversation gets its first new unread.
     */
    private fun updateUnreadCounts(list: List<Conversation>) {
        val openId = ConversationVisibility.openConversationId.value
        list.forEach { conv ->
            val prevUpdateAt = lastObservedUpdateAt[conv.id]
            val changed = prevUpdateAt != null && conv.updatedAt > prevUpdateAt
            val isOpen = conv.id == openId
            lastObservedUpdateAt[conv.id] = conv.updatedAt

            if (isOpen) {
                _unreadCounts.update { it - conv.id }
            } else if (changed) {
                viewModelScope.launch {
                    val lastRead = conversationRepository.getLastReadAt(conv.id)
                    if (lastRead <= 0) {
                        // Never read — establish a baseline so existing history isn't "unread".
                        conversationRepository.markRead(conv.id)
                        return@launch
                    }
                    val splitStart = appPreferences.splitStartDelimiter.first()
                    val splitEnd = appPreferences.splitEndDelimiter.first()
                    val count = conversationRepository.countNewBubbles(conv.id, lastRead, splitStart, splitEnd)
                    val prev = _unreadCounts.value[conv.id] ?: 0
                    _unreadCounts.update { it + (conv.id to count) }
                    if (count > 0 && prev == 0) NotificationHelper.notifyUnread(appContext, conv)
                }
            }
        }
    }
}
