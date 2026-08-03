package com.bubble.rikkahub.ui.screens.chat.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.bubble.rikkahub.domain.model.Message
import com.bubble.rikkahub.ui.components.BubbleAvatar
import com.bubble.rikkahub.ui.components.MarkdownText
import com.bubble.rikkahub.ui.theme.BubbleShape

/**
 * Renders a single chat bubble with optional avatar.
 * - User bubbles: right-aligned, primaryContainer color, shows the user's own avatar
 * - AI bubbles: left-aligned, surfaceVariant color, shows the conversation/assistant avatar
 * Long-pressing a bubble copies its text to the clipboard.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: Message,
    showAvatar: Boolean,
    avatarUri: String? = null,
    emoji: String? = null,
    avatarUrl: String? = null,
    displayName: String = "",
    meAvatarUri: String? = null,
    meEmoji: String? = null,
    meDisplayName: String = "",
    modifier: Modifier = Modifier
) {
    if (message.isUser) {
        UserBubble(
            content = message.content,
            showAvatar = showAvatar,
            avatarUri = meAvatarUri,
            emoji = meEmoji,
            avatarUrl = null,
            displayName = meDisplayName.ifBlank { "我" },
            modifier = modifier
        )
    } else {
        AiBubble(
            content = message.content,
            showAvatar = showAvatar,
            avatarUri = avatarUri,
            emoji = emoji,
            avatarUrl = avatarUrl,
            displayName = displayName,
            modifier = modifier
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun UserBubble(
    content: String,
    showAvatar: Boolean,
    avatarUri: String?,
    emoji: String?,
    avatarUrl: String?,
    displayName: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.Bottom
    ) {
        Box {
            Surface(
                shape = BubbleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .combinedClickable(onClick = {}, onLongClick = { showMenu = true })
            ) {
                MarkdownText(
                    text = content,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                )
            }
            BubbleContextMenu(showMenu, { showMenu = false }) { copyToClipboard(context, content) }
        }

        if (showAvatar) {
            Spacer(Modifier.width(8.dp))
            BubbleAvatar(
                avatarUri = avatarUri,
                emoji = emoji,
                avatarUrl = avatarUrl,
                name = displayName,
                size = 32.dp
            )
        } else {
            // Reserve the space the avatar would occupy so consecutive bubbles from the
            // same sender stay right-aligned with each other.
            Spacer(Modifier.width(40.dp))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AiBubble(
    content: String,
    showAvatar: Boolean,
    avatarUri: String?,
    emoji: String?,
    avatarUrl: String?,
    displayName: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (showAvatar) {
            BubbleAvatar(
                avatarUri = avatarUri,
                emoji = emoji,
                avatarUrl = avatarUrl,
                name = displayName,
                size = 32.dp
            )
            Spacer(Modifier.width(8.dp))
        } else {
            Spacer(Modifier.width(40.dp)) // Reserve space where avatar would be
        }

        Box {
            Surface(
                shape = BubbleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .combinedClickable(onClick = {}, onLongClick = { showMenu = true })
            ) {
                MarkdownText(
                    text = content,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                )
            }
            BubbleContextMenu(showMenu, { showMenu = false }) { copyToClipboard(context, content) }
        }
    }
}

@Composable
private fun BubbleContextMenu(expanded: Boolean, onDismiss: () -> Unit, onCopy: () -> Unit) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text("复制") },
            onClick = { onDismiss(); onCopy() }
        )
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("气泡内容", text))
    Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
}
