package com.bubble.rikkahub.ui.screens.chat

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bubble.rikkahub.data.repository.CustomizationRepository
import com.bubble.rikkahub.ui.screens.chat.components.BubbleList
import com.bubble.rikkahub.ui.screens.chat.components.ChatInputBar
import com.bubble.rikkahub.ui.screens.chat.components.ChatTopBar
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    conversationId: String,
    customizationRepository: CustomizationRepository,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(conversationId) { viewModel.loadConversation(conversationId) }

    // Load the user's own avatar/nickname (shown on the right-side bubbles)
    var meProfile by remember { mutableStateOf<com.bubble.rikkahub.data.local.entity.CustomizationEntity?>(null) }
    LaunchedEffect(Unit) { meProfile = customizationRepository.getSelfProfile() }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.error) {
        state.error?.let { snackbarHostState.showSnackbar(it); viewModel.clearError() }
    }

    // Conversation info screen state
    var showCustomize by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var avatarUri by remember { mutableStateOf<Uri?>(null) }

    // Remember coroutine scope for dialog actions
    val coroutineScope = rememberCoroutineScope()

    val displayName = state.conversation?.displayName ?: "加载中..."
    val conv = state.conversation

    // Save customization when the info screen's save button is pressed
    fun saveCustomization(nickname: String, emojiText: String, newAvatar: Uri?) {
        if (newAvatar != null) avatarUri = newAvatar
        val convId = conversationId
        coroutineScope.launch {
            customizationRepository.setNickname(convId, nickname.ifBlank { null })
            customizationRepository.setEmoji(convId, emojiText.ifBlank { null })
            if (newAvatar != null) {
                customizationRepository.setAvatar(convId, newAvatar.toString())
            }
            showCustomize = false
            viewModel.loadConversation(convId)
        }
    }

    // Full-screen conversation info / edit screen
    if (showCustomize) {
        ChatInfoScreen(
            displayName = displayName,
            customAvatarUri = avatarUri?.toString() ?: conv?.customAvatarUri,
            customEmoji = conv?.customEmoji,
            customNickname = conv?.customNickname,
            onSave = { n, e, uri -> saveCustomization(n, e, uri) },
            onClose = { showCustomize = false }
        )
    }

    Scaffold(
        topBar = {
            ChatTopBar(
                title = displayName,
                avatarUri = avatarUri?.toString() ?: conv?.customAvatarUri,
                emoji = conv?.customEmoji,
                avatarUrl = conv?.avatarUrl,
                onBack = onBack,
                actions = {
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Filled.MoreVert, "更多")
                        }
                        DropdownMenu(showMenu, { showMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("会话信息") },
                                onClick = {
                                    showMenu = false
                                    showCustomize = true
                                }
                            )
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            ChatInputBar(
                inputText = state.currentInputText,
                onInputTextChanged = viewModel::onInputTextChanged,
                onSend = viewModel::commitBubble,
                onRemoveLast = viewModel::removeLastBubble,
                pendingBubbles = state.pendingBubbles,
                sendMode = state.sendMode,
                timerSecondsRemaining = state.timerSecondsRemaining,
                isSending = state.isSending,
                queuedSends = state.queuedSends,
                modifier = Modifier.imePadding()
            )
        }
    ) { padding ->
        when {
            state.conversation == null && state.error == null -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            state.error != null && state.messages.isEmpty() -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(state.error ?: "", color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { viewModel.loadConversation(conversationId) }) {
                            Text("重试")
                        }
                    }
                }
            }
            else -> {
                BubbleList(
                    messages = state.messages,
                    avatarMode = state.avatarMode,
                    avatarUri = avatarUri?.toString() ?: conv?.customAvatarUri,
                    emoji = conv?.customEmoji,
                    avatarUrl = conv?.avatarUrl,
                    displayName = displayName,
                    meAvatarUri = meProfile?.avatarUri,
                    meEmoji = meProfile?.avatarEmoji,
                    meDisplayName = meProfile?.nickname ?: "我",
                    isStreaming = state.isStreaming,
                    streamingMessageId = null,
                    bubbleAnimScaleFrom = state.bubbleAnimScaleFrom,
                    bubbleAnimDurationMs = state.bubbleAnimDurationMs,
                    bubbleAnimBounce = state.bubbleAnimBounce,
                    bubbleAnimBounciness = state.bubbleAnimBounciness,
                    // No imePadding here: the ChatInputBar already lifts above the keyboard,
                    // and the Scaffold shrinks the list accordingly. Double imePadding caused
                    // a big black/blank gap when the keyboard opened.
                    modifier = Modifier.fillMaxSize().padding(padding)
                )
            }
        }
    }
}
