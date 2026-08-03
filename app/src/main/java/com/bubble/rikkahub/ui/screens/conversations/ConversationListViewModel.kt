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

    // ── Offline state (drives the red banner) ──
    private val _isOffline = MutableStateFlow(false)
    val isOffline: StateFlow<Boolean> = _isOffline.asStateFlow()

    private val json = Json { ignoreUnknownKeys = true }
    private var refreshJob: Job? = null

    companion object {
        private const val TAG = "ConversationListVM"
    }

    init {
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

    private fun handleSettingsFrame(data: String?) {
        if (data.isNullOrBlank()) return
        runCatching { json.decodeFromString<SettingsDto>(data) }
            .onSuccess { applySettings(it) }
            .onFailure { Log.w(TAG, "解析 settings 事件失败: ${it.message}") }
    }

    private fun applySettings(settings: SettingsDto) {
        _assistants.value = settings.assistants.mapNotNull { a ->
            val id = a.id ?: return@mapNotNull null
            AssistantInfo(
                id = id,
                name = a.name ?: "",
                // The upstream avatar URL is a file:// URI inside the RikkaHub app's sandbox,
                // which this app cannot read — only accept http(s) URLs, else fall back.
                avatarUrl = a.avatar?.url?.takeIf { it.startsWith("http", ignoreCase = true) },
                avatarEmoji = a.avatar?.content?.takeIf { it.isNotBlank() }
            )
        }
        _currentAssistant.value = _assistants.value.firstOrNull { it.id == settings.assistantId }
        Log.d(TAG, "助手列表已更新: ${_assistants.value.size} 个，当前=${_currentAssistant.value?.name}")
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
            conversationRepository.switchAssistant(id)
                .onSuccess {
                    loadConversations()
                }
                .onFailure {
                    _isSwitchingAssistant.value = false
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
                val cached = conversationRepository.getCachedConversations()
                if (cached.isNotEmpty()) {
                    val enriched = cached.map { enrich(it) }
                    _uiState.update { ConversationListUiState.Success(enriched, false) }
                } else {
                    _uiState.update { ConversationListUiState.Error(e.message ?: "加载失败") }
                }
            }

        // Fallback: if the live /api/events connection hasn't populated the assistant list yet,
        // fetch the settings directly once.
        if (_assistants.value.isEmpty()) {
            conversationRepository.getSettings()
                .onSuccess { applySettings(it) }
                .onFailure { }
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
