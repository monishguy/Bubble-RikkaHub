package com.bubble.rikkahub.ui.screens.conversations

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bubble.rikkahub.data.ConversationVisibility
import com.bubble.rikkahub.domain.model.AssistantInfo
import com.bubble.rikkahub.domain.model.Conversation
import com.bubble.rikkahub.domain.model.ListTheme
import com.bubble.rikkahub.ui.components.BubbleAvatar
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ConversationListScreen(
    viewModel: ConversationListViewModel,
    listTheme: ListTheme,
    onConversationClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val currentAssistant by viewModel.currentAssistant.collectAsStateWithLifecycle()
    val assistants by viewModel.assistants.collectAsStateWithLifecycle()
    val isSwitchingAssistant by viewModel.isSwitchingAssistant.collectAsStateWithLifecycle()
    val isOffline by viewModel.isOffline.collectAsStateWithLifecycle()
    val unreadCounts by viewModel.unreadCounts.collectAsStateWithLifecycle()
    val assistantError by viewModel.assistantError.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(assistantError) {
        assistantError?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearAssistantError()
        }
    }
    var deleteTarget by remember { mutableStateOf<Conversation?>(null) }

    // Assistant customization dialog state
    val context = LocalContext.current
    var customizingAssistant by remember { mutableStateOf<AssistantInfo?>(null) }
    var editAsstNickname by remember { mutableStateOf("") }
    var editAsstEmoji by remember { mutableStateOf("") }
    var editAsstAvatar by remember { mutableStateOf<Uri?>(null) }
    val asstImagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            editAsstAvatar = uri
        }
    }

    customizingAssistant?.let { asst ->
        AlertDialog(
            onDismissRequest = { customizingAssistant = null },
            title = { Text("自定义助手「${asst.displayName}」") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = editAsstNickname,
                        onValueChange = { editAsstNickname = it },
                        label = { Text("名称") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editAsstEmoji,
                        onValueChange = { editAsstEmoji = it.take(2) },
                        label = { Text("Emoji 头像") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(onClick = { asstImagePicker.launch("image/*") }) {
                        Text(if (editAsstAvatar != null) "已选择图片 ✓" else "选择头像图片")
                    }
                    Text(
                        "自定义信息保存在本机，按助手 ID 关联，不会被服务器刷新覆盖。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.saveAssistantCustomization(
                        asst.id, editAsstNickname, editAsstEmoji, editAsstAvatar?.toString()
                    )
                    customizingAssistant = null
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { customizingAssistant = null }) { Text("取消") }
            }
        )
    }

    deleteTarget?.let { conv ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除对话") },
            text = { Text("确定要删除「${conv.displayName}」吗？") },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteConversation(conv.id); deleteTarget = null }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("取消") } }
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("聊天") },
            actions = {
                AssistantSwitcher(
                    currentAssistant = currentAssistant,
                    assistants = assistants,
                    isSwitching = isSwitchingAssistant,
                    onSwitch = viewModel::switchAssistant,
                    onRefresh = viewModel::load,
                    onCustomizeAssistant = { asst ->
                        customizingAssistant = asst
                        editAsstNickname = asst.name
                        editAsstEmoji = asst.avatarEmoji ?: ""
                        editAsstAvatar = null
                    }
                )
            }
        )

        // Offline banner: shown at the top of the main screen when the server is unreachable.
        if (isOffline) {
            Surface(color = MaterialTheme.colorScheme.errorContainer) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Filled.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        "服务器离线，已显示本地缓存",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = viewModel::load) { Text("重试") }
                }
            }
        }

        when (val s = state) {
            is ConversationListUiState.Loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is ConversationListUiState.Error -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(s.message, color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = viewModel::load) { Text("重试") }
                    }
                }
            }
            is ConversationListUiState.Success -> {
                PullToRefreshBox(
                    isRefreshing = s.isRefreshing,
                    onRefresh = viewModel::refresh,
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (s.conversations.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("暂无对话\n下拉刷新",
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(vertical = 4.dp)
                        ) {
                            items(s.conversations, key = { it.id }) { conv ->
                                ConversationItem(
                                    conversation = conv,
                                    listTheme = listTheme,
                                    onClick = {
                                        viewModel.readConversation(conv.id)
                                        onConversationClick(conv.id)
                                    },
                                    onDelete = { deleteTarget = conv },
                                    onTogglePin = { viewModel.togglePin(conv.id) },
                                    unreadCount = unreadCounts[conv.id] ?: 0
                                )
                            }
                        }
                    }
                }
            }
        }
        }
        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
        // New conversation: opens an empty chat whose first message creates it on the server.
        FloatingActionButton(
            onClick = {
                val newId = UUID.randomUUID().toString()
                ConversationVisibility.markAsNewConversation(newId)
                onConversationClick(newId)
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Icon(Icons.Filled.Add, contentDescription = "新建会话")
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConversationItem(
    conversation: Conversation,
    listTheme: ListTheme,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onTogglePin: () -> Unit,
    unreadCount: Int = 0
) {
    var showMenu by remember { mutableStateOf(false) }

    val itemContent: @Composable (Modifier) -> Unit = { modifier ->
        Row(
            modifier = modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (conversation.isPinned) {
                Icon(Icons.Filled.PushPin, "已置顶",
                    modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            }
            BubbleAvatar(
                avatarUri = conversation.customAvatarUri,
                emoji = conversation.customEmoji,
                avatarUrl = conversation.avatarUrl,
                name = conversation.displayName,
                size = 48.dp
            )
            val preview = conversation.lastMessagePreview
            Column(modifier = Modifier.weight(1f)) {
                Text(conversation.displayName, style = MaterialTheme.typography.titleSmall,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!preview.isNullOrBlank()) {
                    Text(preview,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (unreadCount > 0) {
                Badge { Text("$unreadCount") }
            }
        }
    }

    // Long-press context menu
    DropdownMenu(showMenu, { showMenu = false }) {
        DropdownMenuItem(
            text = { Text(if (conversation.isPinned) "取消置顶" else "置顶") },
            onClick = { onTogglePin(); showMenu = false },
            leadingIcon = { Icon(Icons.Filled.PushPin, null) }
        )
        DropdownMenuItem(
            text = { Text("删除", color = MaterialTheme.colorScheme.error) },
            onClick = { onDelete(); showMenu = false },
            leadingIcon = { Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error) }
        )
    }

    when (listTheme) {
        ListTheme.FLAT -> {
            Box(Modifier.combinedClickable(onClick = onClick, onLongClick = { showMenu = true })) {
                itemContent(Modifier.fillMaxWidth())
            }
            HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
        }
        ListTheme.CARDS -> {
            Card(
                modifier = Modifier
                    .fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
                    .combinedClickable(onClick = onClick, onLongClick = { showMenu = true }),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
            ) { itemContent(Modifier.fillMaxWidth()) }
        }
    }
}
