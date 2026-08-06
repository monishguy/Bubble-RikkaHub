package com.bubble.rikkahub.ui.screens.chat

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
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

    // ── Attachment pickers ──────────────────────────────────────
    val context = LocalContext.current
    // System photo picker (no permission needed, multi-select).
    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = 10)
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.addAttachments(uris.mapNotNull { buildPendingAttachment(context, it, isImage = true) })
        }
    }
    // SAF document picker (no permission needed, multi-select).
    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            uris.forEach { uri ->
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
            }
            viewModel.addAttachments(uris.mapNotNull { buildPendingAttachment(context, it, isImage = false) })
        }
    }

    // Conversation info screen state
    var showCustomize by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var avatarUri by remember { mutableStateOf<Uri?>(null) }

    // Remember coroutine scope for dialog actions
    val coroutineScope = rememberCoroutineScope()

    // Increment to force the message list to scroll to the bottom (input focused / send pressed).
    var scrollSignal by remember { mutableStateOf(0) }

    val displayName = state.conversation?.displayName ?: "加载中..."
    val conv = state.conversation

    // Save customization when the info screen's save button is pressed
    fun saveCustomization(nickname: String, emojiText: String, newAvatar: Uri?, bgUri: String?, bgColor: Long?) {
        if (newAvatar != null) avatarUri = newAvatar
        val convId = conversationId
        coroutineScope.launch {
            customizationRepository.setNickname(convId, nickname.ifBlank { null })
            customizationRepository.setEmoji(convId, emojiText.ifBlank { null })
            if (newAvatar != null) {
                customizationRepository.setAvatar(convId, newAvatar.toString())
            }
            customizationRepository.setChatBackground(convId, bgUri)
            customizationRepository.setChatBackgroundColor(convId, bgColor)
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
            customBgUri = conv?.chatBackgroundUri,
            customBgColor = conv?.chatBackgroundColor,
            onSave = { n, e, uri, bg, bc -> saveCustomization(n, e, uri, bg, bc) },
            onClose = { showCustomize = false }
        )
    }

    Scaffold(
        // The outer scaffold (MainNavGraph) already handles the navigation-bar inset for the
        // whole screen, so this nested scaffold must not add another one (that caused a blank
        // strip below the input bar). The top bar handles the status bar itself.
        contentWindowInsets = WindowInsets(0.dp),
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
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        // The list AND the input bar live in one imePadding()'ed column, so when the keyboard
        // opens the whole column shrinks: the message list rises together with the input bar
        // instead of being covered by it.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                // ime ∪ navigationBars = the keyboard when it's open (so the input bar sits
                // flush against it and the list shrinks), or just the navigation bar when closed.
                .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
        ) {
            Box(Modifier.weight(1f)) {
                when {
                    state.conversation == null && state.error == null -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    state.error != null && state.messages.isEmpty() -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
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
                        ChatBackground(
                            uri = conv?.chatBackgroundUri,
                            color = conv?.chatBackgroundColor
                        ) {
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
                                // Bubble split delimiters, used to split the text of attachment
                                // messages into separate bubbles at render time.
                                splitStart = state.splitStart,
                                splitEnd = state.splitEnd,
                                // When the input is focused (keyboard up) or a send is pressed,
                                // jump to the latest message so it's never hidden.
                                scrollToBottomSignal = scrollSignal,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
            ChatInputBar(
                inputText = state.currentInputText,
                onInputTextChanged = viewModel::onInputTextChanged,
                // Send also scrolls to the bottom so the latest user message is always visible.
                onSend = {
                    viewModel.commitBubble()
                    scrollSignal++
                },
                // Tapping the input (keyboard opens) jumps to the latest message.
                onInputFocused = { scrollSignal++ },
                onRemoveLast = viewModel::removeLastBubble,
                pendingBubbles = state.pendingBubbles,
                pendingAttachments = state.pendingAttachments,
                onRemoveAttachment = viewModel::removeAttachment,
                onPickImages = {
                    imagePicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onPickFiles = { filePicker.launch(arrayOf("*/*")) },
                sendMode = state.sendMode,
                timerSecondsRemaining = state.timerSecondsRemaining,
                isSending = state.isSending,
                queuedSends = state.queuedSends
            )
        }
    }
}

/** Renders a custom chat background (image or solid color) behind the bubbles. */
@Composable
private fun ChatBackground(uri: String?, color: Long?, content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        when {
            !uri.isNullOrBlank() -> AsyncImage(
                model = uri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            color != null -> Box(Modifier.fillMaxSize().background(Color(color)))
        }
        content()
    }
}

/** Builds a [PendingAttachment] for a picked content URI (queries the display name/mime). */
private fun buildPendingAttachment(context: Context, uri: Uri, isImage: Boolean): PendingAttachment? {
    val mime = context.contentResolver.getType(uri)
        ?: if (isImage) "image/*" else "application/octet-stream"
    val name = queryDisplayName(context, uri)
        ?: (if (isImage) "image_${System.currentTimeMillis()}.jpg" else "file")
    return PendingAttachment(uri = uri.toString(), fileName = name, mime = mime, isImage = isImage)
}

private fun queryDisplayName(context: Context, uri: Uri): String? {
    return try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
        }
    } catch (_: Exception) {
        null
    }
}
