package com.bubble.rikkahub.ui.screens.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bubble.rikkahub.data.ConversationVisibility
import com.bubble.rikkahub.data.preferences.AppPreferences
import com.bubble.rikkahub.data.remote.ConnectionMonitor
import com.bubble.rikkahub.data.remote.dto.ConversationNodeUpdateEvent
import com.bubble.rikkahub.data.remote.dto.ConversationSnapshotEvent
import com.bubble.rikkahub.data.remote.dto.ErrorEvent
import com.bubble.rikkahub.data.remote.dto.MessageDto
import com.bubble.rikkahub.data.remote.dto.MessageSendException
import com.bubble.rikkahub.data.remote.dto.SseFrame
import com.bubble.rikkahub.data.repository.ChatRepository
import com.bubble.rikkahub.data.repository.ConversationRepository
import com.bubble.rikkahub.data.repository.CustomizationRepository
import com.bubble.rikkahub.domain.model.AvatarMode
import com.bubble.rikkahub.domain.model.Conversation
import com.bubble.rikkahub.domain.model.Message
import com.bubble.rikkahub.domain.model.SendMode
import com.bubble.rikkahub.util.MessageSplitter
import com.bubble.rikkahub.util.TimestampParser
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
import java.util.UUID
import kotlin.random.Random

data class ChatUiState(
    val conversation: Conversation? = null,
    val messages: List<Message> = emptyList(),
    val pendingBubbles: List<String> = emptyList(),
    val currentInputText: String = "",
    val isStreaming: Boolean = false,
    val isSending: Boolean = false,
    val sendMode: SendMode = SendMode.TIMER,
    val avatarMode: AvatarMode = AvatarMode.EVERY_BUBBLE,
    val timerSecondsRemaining: Int? = null,
    val error: String? = null,
    /** Number of undelivered messages queued locally for this conversation. */
    val queuedSends: Int = 0,
    // Bubble pop-in animation params (from settings)
    val bubbleAnimScaleFrom: Float = AppPreferences.DEFAULT_BUBBLE_ANIM_SCALE,
    val bubbleAnimDurationMs: Int = AppPreferences.DEFAULT_BUBBLE_ANIM_DURATION,
    val bubbleAnimBounce: Boolean = AppPreferences.DEFAULT_BUBBLE_ANIM_BOUNCE,
    val bubbleAnimBounciness: Int = AppPreferences.DEFAULT_BUBBLE_ANIM_BOUNCINESS
)

class ChatViewModel(
    private val chatRepository: ChatRepository,
    private val conversationRepository: ConversationRepository,
    private val appPreferences: AppPreferences,
    private val connectionMonitor: ConnectionMonitor,
    private val customizationRepository: CustomizationRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var conversationId: String = ""
    private var timerJob: Job? = null
    private var streamJob: Job? = null
    private var refreshJob: Job? = null
    private var statusPollJob: Job? = null
    private var isRevealing = false
    /** True while the user has optimistic (not-yet-confirmed) messages on screen. */
    private var hasPendingSend = false
    /** True while the AI is known to be generating (set from stream events). */
    private var generationInProgress = false
    private var splitStart: String = AppPreferences.DEFAULT_SPLIT_START
    private var splitEnd: String = AppPreferences.DEFAULT_SPLIT_END
    private var bubbleDelayMinMs: Long = AppPreferences.DEFAULT_BUBBLE_DELAY_MIN.toLong()
    private var bubbleDelayMaxMs: Long = AppPreferences.DEFAULT_BUBBLE_DELAY_MAX.toLong()
    private var autoFormatPromptEnabled: Boolean = AppPreferences.DEFAULT_AUTO_FORMAT_PROMPT
    private var autoFormatPromptText: String = AppPreferences.DEFAULT_AUTO_FORMAT_PROMPT_TEXT
    /** True once this conversation has at least one user-sent message (AI messages don't count). */
    private var hasUserMessage = false

    private val json = Json { ignoreUnknownKeys = true }

    init {
        // When the connection comes back while this chat is open, reload the conversation
        // (this also flushes any queued sends and restarts the SSE stream).
        viewModelScope.launch {
            var wasOnline = connectionMonitor.isOnline.value
            connectionMonitor.isOnline.collect { online ->
                if (online && !wasOnline && conversationId.isNotBlank()) {
                    loadConversation(conversationId)
                }
                wasOnline = online
            }
        }
    }

    fun loadConversation(id: String) {
        conversationId = id
        ConversationVisibility.onOpen(id)
        viewModelScope.launch {
            splitStart = appPreferences.splitStartDelimiter.first()
            splitEnd = appPreferences.splitEndDelimiter.first()
            bubbleDelayMinMs = appPreferences.bubbleDelayMinMs.first().toLong()
            bubbleDelayMaxMs = appPreferences.bubbleDelayMaxMs.first().toLong()
            autoFormatPromptEnabled = appPreferences.autoFormatPrompt.first()
            autoFormatPromptText = appPreferences.autoFormatPromptText.first()
            val sendMode = appPreferences.sendMode.first()
            val avatarMode = appPreferences.avatarMode.first()
            val animScale = appPreferences.bubbleAnimScaleFrom.first()
            val animDuration = appPreferences.bubbleAnimDurationMs.first()
            val animBounce = appPreferences.bubbleAnimBounce.first()
            val animBounciness = appPreferences.bubbleAnimBounciness.first()
            _uiState.update {
                it.copy(
                    sendMode = sendMode,
                    avatarMode = avatarMode,
                    bubbleAnimScaleFrom = animScale,
                    bubbleAnimDurationMs = animDuration,
                    bubbleAnimBounce = animBounce,
                    bubbleAnimBounciness = animBounciness
                )
            }

            // 1. Show the locally cached copy instantly, so re-entering a conversation
            // doesn't reload from scratch with a spinner.
            val cached = conversationRepository.getCachedConversation(id)
            if (cached != null) {
                hasUserMessage = cached.messages.any { it.role == Message.ROLE_USER }
                val conv = enrichConversation(cached.conversation)
                _uiState.update {
                    it.copy(conversation = conv, messages = splitMessages(cached.messages), isStreaming = false)
                }
            }

            // 2. Fetch the fresh state and merge in any new messages.
            conversationRepository.getConversationWithMessages(id)
                .onSuccess { data ->
                    connectionMonitor.reportSuccess()
                    refreshQueuedCount()
                    hasPendingSend = false
                    hasUserMessage = data.messages.any { it.role == Message.ROLE_USER }
                    val conv = enrichConversation(data.conversation)
                    val serverSplit = splitMessages(data.messages)
                    _uiState.update { state ->
                        val current = state.messages
                        val currentIds = current.map { it.id }.toSet()
                        val hasOptimistic = current.any {
                            it.role == Message.ROLE_USER && it.id.startsWith("user-")
                        }
                        val toAdd = serverSplit.filter { m ->
                            m.id !in currentIds && !(m.role == Message.ROLE_USER && hasOptimistic)
                        }
                        state.copy(
                            conversation = conv,
                            messages = current + toAdd,
                            // If a generation is already running, show the typing indicator.
                            isStreaming = data.isGenerating
                        )
                    }
                    startConversationStream()
                }
                .onFailure { e ->
                    if (ConversationVisibility.isNewConversation(id)) {
                        // A conversation created by the app's "new chat" button — it doesn't
                        // exist on the server yet. Show an empty chat ready for the first message.
                        ConversationVisibility.clearNewConversation(id)
                        hasUserMessage = false
                        _uiState.update {
                            it.copy(
                                conversation = Conversation(id = id, title = "新会话"),
                                messages = emptyList(),
                                error = null
                            )
                        }
                    } else {
                        connectionMonitor.reportFailure()
                        if (_uiState.value.messages.isEmpty()) {
                            _uiState.update { it.copy(error = e.message ?: "加载对话失败") }
                        } else {
                            _uiState.update { it.copy(error = "服务器离线，已显示本地缓存") }
                        }
                    }
                }
        }
    }

    fun onInputTextChanged(text: String) {
        _uiState.update { it.copy(currentInputText = text) }
    }

    fun commitBubble() {
        val text = _uiState.value.currentInputText.trim()
        if (text.isBlank()) {
            if (_uiState.value.sendMode == SendMode.MANUAL && _uiState.value.pendingBubbles.isNotEmpty()) {
                triggerSend()
            }
            return
        }
        val newBubbles = _uiState.value.pendingBubbles + text
        _uiState.update { it.copy(pendingBubbles = newBubbles, currentInputText = "") }
        when (_uiState.value.sendMode) {
            SendMode.TIMER -> startTimer()
            SendMode.MANUAL -> {}
        }
    }

    fun removeLastBubble() {
        val bubbles = _uiState.value.pendingBubbles
        if (bubbles.isNotEmpty()) {
            _uiState.update { it.copy(pendingBubbles = bubbles.dropLast(1)) }
            if (_uiState.value.sendMode == SendMode.TIMER) startTimer()
        }
    }

    fun triggerSend() {
        val bubbles = _uiState.value.pendingBubbles
        if (bubbles.isEmpty()) return
        cancelTimer()

        val userMessages = bubbles.map { text ->
            Message(id = "user-${UUID.randomUUID()}", role = Message.ROLE_USER,
                content = text, timestamp = System.currentTimeMillis())
        }
        _uiState.update {
            it.copy(messages = it.messages + userMessages, pendingBubbles = emptyList(), isSending = true, error = null)
        }
        hasPendingSend = true

        val needsFormatPrompt = autoFormatPromptEnabled && !hasUserMessage
        val packedMessage = if (needsFormatPrompt) {
            // New conversation with no user message yet: teach the AI the bubble format.
            // {start}/{end} placeholders are substituted with the configured delimiters.
            val prompt = autoFormatPromptText
                .replace("{start}", splitStart)
                .replace("{end}", splitEnd)
            "$prompt\n${bubbles.joinToString("") { "$splitStart$it$splitEnd" }}"
        } else {
            bubbles.joinToString("") { "$splitStart$it$splitEnd" }
        }
        hasUserMessage = true
        viewModelScope.launch {
            try {
                // 1. Send message (202 Accepted)
                chatRepository.sendMessage(conversationId, packedMessage)
                connectionMonitor.reportSuccess()
                // 2. Start polling for the reply (works even if the SSE stream is down).
                ensureStatusPoll()
            } catch (e: CancellationException) {
                throw e
            } catch (e: java.io.IOException) {
                // Transient network failure: keep the optimistically-shown bubbles and queue
                // the message locally; it auto-sends when the connection returns.
                connectionMonitor.reportFailure()
                chatRepository.enqueuePending(conversationId, packedMessage)
                refreshQueuedCount()
                _uiState.update {
                    it.copy(isSending = false, error = "发送失败（网络问题），已排队，网络恢复后自动发送")
                }
            } catch (e: MessageSendException) {
                if (e.status.value >= 500) {
                    // Server-side failure — likely transient; queue it so the message isn't lost.
                    connectionMonitor.reportFailure()
                    chatRepository.enqueuePending(conversationId, packedMessage)
                    refreshQueuedCount()
                    _uiState.update {
                        it.copy(isSending = false, error = "服务器繁忙（HTTP ${e.status.value}），已排队，稍后自动重试")
                    }
                } else {
                    // Client error (4xx): surface the real error; don't queue.
                    _uiState.update {
                        it.copy(isSending = false, error = e.message ?: "发送失败")
                    }
                }
            } catch (e: Exception) {
                // Permanent failure (e.g. the server rejected the message): surface the real
                // error instead of silently queueing something that can never succeed.
                _uiState.update {
                    it.copy(isSending = false, error = "发送失败：${e.message ?: "未知错误"}")
                }
            }
        }
    }

    private fun startConversationStream() {
        streamJob?.cancel()
        streamJob = viewModelScope.launch {
            try {
                chatRepository.streamConversation(conversationId).collect { frame ->
                    handleStreamEvent(frame)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Never crash on a dropped/timeout stream — surface a recoverable state instead.
                connectionMonitor.reportFailure()
                _uiState.update {
                    it.copy(isStreaming = false, isSending = false, error = "连接中断：${e.message}")
                }
            }
        }
    }

    private fun handleStreamEvent(frame: SseFrame) {
        try {
            when (frame.event) {
                "snapshot" -> {
                    val snapshot = json.decodeFromString<ConversationSnapshotEvent>(frame.data ?: return)
                    val conv = snapshot.conversation
                    _uiState.update { it.copy(isSending = false) }
                    if (conv.isGenerating) {
                        generationInProgress = true
                        ensureStatusPoll()
                        _uiState.update { it.copy(isStreaming = true) }
                    } else {
                        generationInProgress = false
                        // Don't wholesale-replace while the user has optimistic messages on screen
                        // or while a staggered reveal is in progress.
                        if (!isRevealing && !hasPendingSend) {
                            // Incremental merge: keep what's shown (incl. optimistic sends and
                            // already-revealed bubbles) and append new server messages, so the
                            // list only grows instead of doing a jarring full reload.
                            val serverMessages = splitMessages(flattenConversationMessages(conv))
                            val current = _uiState.value.messages
                            val currentIds = current.map { it.id }.toSet()
                            val hasOptimisticUser = current.any {
                                it.role == Message.ROLE_USER && it.id.startsWith("user-")
                            }
                            val toAdd = serverMessages.filter { m ->
                                m.id !in currentIds && !(m.role == Message.ROLE_USER && hasOptimisticUser)
                            }
                            _uiState.update { it.copy(messages = current + toAdd, isStreaming = false) }
                        } else {
                            _uiState.update { it.copy(isStreaming = false) }
                        }
                    }
                }
                "node_update" -> {
                    val update = json.decodeFromString<ConversationNodeUpdateEvent>(frame.data ?: return)
                    if (update.isGenerating) {
                        generationInProgress = true
                        ensureStatusPoll()
                        _uiState.update { it.copy(isStreaming = true) }
                    } else if (generationInProgress) {
                        // The final node_update with isGenerating=false signals completion
                        // on servers that don't emit a separate "done" event.
                        generationInProgress = false
                        _uiState.update { it.copy(isStreaming = false, isSending = false) }
                        refreshAndReveal()
                    }
                    // Incremental content is intentionally ignored here: bubbles are only
                    // revealed once the whole generation finishes.
                }
                "done" -> {
                    generationInProgress = false
                    _uiState.update { it.copy(isStreaming = false, isSending = false) }
                    refreshAndReveal()
                }
                "error" -> {
                    val err = frame.data?.let { json.decodeFromString<ErrorEvent>(it) }
                    _uiState.update { it.copy(error = err?.message ?: "服务器错误", isSending = false, isStreaming = false) }
                }
            }
        } catch (_: Exception) {
            // Skip unparseable frames
        }
    }

    /**
     * Polls the conversation at random intervals after a send / during generation, so the AI
     * reply is reliably revealed even if the SSE stream is unavailable. Shows the typing
     * indicator while the server reports generating, and reveals new bubbles (each with a
     * random delay) once a complete new reply exists.
     */
    private fun ensureStatusPoll() {
        if (statusPollJob?.isActive == true) return
        statusPollJob = viewModelScope.launch {
            var polls = 0
            while (isActive && polls < MAX_POLLS) {
                polls++
                delay(Random.nextLong(3_000, 8_001))
                if (!hasPendingSend && !generationInProgress) break
                conversationRepository.getConversationWithMessages(conversationId)
                    .onSuccess { data ->
                        val serverMessages = splitMessages(data.messages)
                        val currentIds = _uiState.value.messages.map { it.id }.toSet()
                        val hasNewReply = serverMessages.any {
                            it.role == Message.ROLE_ASSISTANT && it.id !in currentIds
                        }
                        when {
                            data.isGenerating -> {
                                generationInProgress = true
                                _uiState.update { it.copy(isStreaming = true) }
                            }
                            hasNewReply -> {
                                generationInProgress = false
                                _uiState.update { it.copy(isStreaming = false, isSending = false) }
                                refreshAndReveal()
                            }
                            else -> {
                                // Send confirmed but reply not finished yet — keep waiting.
                                if (hasPendingSend) {
                                    _uiState.update { it.copy(isStreaming = true) }
                                }
                            }
                        }
                    }
            }
            // Safety: clear the flags so nothing stays stuck after the poll budget is used up.
            hasPendingSend = false
            generationInProgress = false
            _uiState.update { it.copy(isStreaming = false, isSending = false) }
        }
    }

    /** Re-fetches the authoritative final state after generation completes and reveals new bubbles. */
    private fun refreshAndReveal() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            conversationRepository.getConversationWithMessages(conversationId)
                .onSuccess { data ->
                    hasPendingSend = false
                    revealNewMessages(splitMessages(data.messages))
                }
                .onFailure { e ->
                    _uiState.update { it.copy(error = "刷新消息失败：${e.message}") }
                }
        }
    }

    /**
     * Reveals newly-arrived assistant bubbles one at a time with a random delay,
     * so a multi-bubble AI reply appears progressively instead of all at once.
     * User messages are shown optimistically on send, so their server echoes are skipped.
     */
    private suspend fun revealNewMessages(freshList: List<Message>) {
        val current = _uiState.value.messages
        val currentIds = current.map { it.id }.toSet()
        isRevealing = true
        try {
            var list = current
            val low = bubbleDelayMinMs
            val high = bubbleDelayMaxMs.coerceAtLeast(low + 1) + 1
            for (msg in freshList) {
                if (msg.id in currentIds) continue
                if (msg.role != Message.ROLE_ASSISTANT) continue
                delay(Random.nextLong(low, high))
                list = list + msg
                _uiState.update { it.copy(messages = list) }
            }
        } finally {
            isRevealing = false
        }
    }

    private fun flattenConversationMessages(conv: com.bubble.rikkahub.data.remote.dto.ConversationDetailDto): List<Message> {
        // When a generation is running, the last node is the still-in-progress reply;
        // drop it so we show only completed history + the typing indicator.
        val nodes = if (conv.isGenerating) conv.messages.dropLast(1) else conv.messages
        return nodes.flatMap { node ->
            val active = node.messages.getOrNull(node.selectIndex)
            if (active != null) {
                val text = partsToText(active)
                if (text.isNotBlank()) {
                    val role = when (active.role.uppercase()) {
                        "USER" -> Message.ROLE_USER
                        else -> Message.ROLE_ASSISTANT
                    }
                    listOf(
                        Message(
                            // CRITICAL: use the same id scheme as getConversationWithMessages
                            // (msg.id, the variant id) so snapshot merges don't treat every
                            // message as new and duplicate the whole history.
                            id = active.id.ifBlank { node.id },
                            role = role,
                            content = text,
                            timestamp = TimestampParser.parse(active.createdAt)
                        )
                    )
                } else emptyList()
            } else emptyList()
        }
    }

    /**
     * Splits messages into bubbles using the configured delimiters. Applied to BOTH user and
     * assistant messages: the app packs the user's bubbles with delimiters before sending, so
     * the server echoes them back packed and they must be split again on load/refresh.
     * Messages with no matching delimiters stay as a single bubble.
     */
    private fun splitMessages(messages: List<Message>): List<Message> {
        return messages.flatMap { msg ->
            val content = if (msg.role == Message.ROLE_USER) stripFormatPrompt(msg.content) else msg.content
            MessageSplitter.split(content, splitStart, splitEnd)
                .mapIndexed { i, c -> msg.copy(id = "${msg.id}-$i", content = c) }
        }
    }

    /**
     * The auto-format prompt is prepended to the first user message on the server, but it must
     * never be shown in the chat — strip it before splitting/displaying.
     */
    private fun stripFormatPrompt(text: String): String {
        if (autoFormatPromptText.isBlank()) return text
        val prompt = autoFormatPromptText.replace("{start}", splitStart).replace("{end}", splitEnd)
        return if (text.startsWith(prompt)) text.removePrefix(prompt).removePrefix("\n") else text
    }

    private fun partsToText(msg: MessageDto): String {
        return msg.parts.filter { it.type == "text" && !it.text.isNullOrBlank() }
            .joinToString("\n") { it.text!! }
    }

    private suspend fun refreshQueuedCount() {
        val n = chatRepository.getPendingForConversation(conversationId).size
        _uiState.update { it.copy(queuedSends = n) }
    }

    /** Merges the user's locally stored avatar/nickname customizations into the conversation so
     *  app-side info always takes priority over whatever the server provides. */
    private suspend fun enrichConversation(conv: Conversation): Conversation {
        val c = customizationRepository.getCustomization(conv.id) ?: return conv
        return conv.copy(
            customAvatarUri = c.avatarUri,
            customEmoji = c.avatarEmoji,
            customNickname = c.nickname,
            chatBackgroundUri = c.chatBackgroundUri,
            chatBackgroundColor = c.chatBackgroundColor
        )
    }

    private suspend fun getTimerDelay() = try { appPreferences.timerDelaySeconds.first() } catch (_: Exception) { 3 }

    private fun startTimer() {
        cancelTimer()
        viewModelScope.launch {
            val delaySeconds = getTimerDelay()
            var remaining = delaySeconds
            _uiState.update { it.copy(timerSecondsRemaining = remaining) }
            while (remaining > 0) {
                delay(1000); remaining--
                if (_uiState.value.timerSecondsRemaining == null) return@launch
                _uiState.update { it.copy(timerSecondsRemaining = remaining) }
            }
            triggerSend()
        }.also { timerJob = it }
    }

    private fun cancelTimer() { timerJob?.cancel(); timerJob = null; _uiState.update { it.copy(timerSecondsRemaining = null) } }

    fun clearError() { _uiState.update { it.copy(error = null) } }

    override fun onCleared() {
        super.onCleared()
        ConversationVisibility.onClose()
        cancelTimer()
        streamJob?.cancel()
        refreshJob?.cancel()
        statusPollJob?.cancel()
    }

    private companion object {
        /** Upper bound on poll iterations (~2-5 minutes) so a stuck conversation can't poll forever. */
        const val MAX_POLLS = 40
    }
}
