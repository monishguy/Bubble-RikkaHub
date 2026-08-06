package com.bubble.rikkahub.ui.screens.chat.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.bubble.rikkahub.domain.model.Message
import com.bubble.rikkahub.domain.model.MessagePart
import com.bubble.rikkahub.ui.components.BubbleAvatar
import com.bubble.rikkahub.ui.components.MarkdownText
import com.bubble.rikkahub.ui.theme.BubbleShape
import com.bubble.rikkahub.util.MessageSplitter

/**
 * Renders a single chat bubble with optional avatar.
 * - User bubbles: right-aligned, primaryContainer color, shows the user's own avatar
 * - AI bubbles: left-aligned, surfaceVariant color, shows the conversation/assistant avatar
 *
 * Messages with attachments show the images/files as cards WITHOUT a bubble background
 * (each counts as its own bubble), followed by the text bubbles (split by the configured
 * delimiters). Pure-text messages render as the usual single bubble.
 *
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
    splitStart: String = "#",
    splitEnd: String = "*",
    modifier: Modifier = Modifier
) {
    if (message.isUser) {
        UserBubble(
            message = message,
            showAvatar = showAvatar,
            avatarUri = meAvatarUri,
            emoji = meEmoji,
            avatarUrl = null,
            displayName = meDisplayName.ifBlank { "我" },
            splitStart = splitStart,
            splitEnd = splitEnd,
            modifier = modifier
        )
    } else {
        AiBubble(
            message = message,
            showAvatar = showAvatar,
            avatarUri = avatarUri,
            emoji = emoji,
            avatarUrl = avatarUrl,
            displayName = displayName,
            splitStart = splitStart,
            splitEnd = splitEnd,
            modifier = modifier
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun UserBubble(
    message: Message,
    showAvatar: Boolean,
    avatarUri: String?,
    emoji: String?,
    avatarUrl: String?,
    displayName: String,
    splitStart: String,
    splitEnd: String,
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
            MessageContent(message, splitStart, splitEnd, isUser = true) { showMenu = true }
            BubbleContextMenu(showMenu, { showMenu = false }) { copyToClipboard(context, message.content) }
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
    message: Message,
    showAvatar: Boolean,
    avatarUri: String?,
    emoji: String?,
    avatarUrl: String?,
    displayName: String,
    splitStart: String,
    splitEnd: String,
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
            MessageContent(message, splitStart, splitEnd, isUser = false) { showMenu = true }
            BubbleContextMenu(showMenu, { showMenu = false }) { copyToClipboard(context, message.content) }
        }
    }
}

/**
 * The message body: attachment cards (no bubble background) + text bubbles.
 * Pure-text messages render as a single bubble.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageContent(
    message: Message,
    splitStart: String,
    splitEnd: String,
    isUser: Boolean,
    onLongPress: () -> Unit
) {
    Box(
        modifier = Modifier
            .widthIn(max = 300.dp)
            .combinedClickable(onClick = {}, onLongClick = onLongPress)
    ) {
        if (!message.hasAttachments) {
            TextBubble(text = message.content, isUser = isUser)
        } else {
            // Memoize the text-bubble split so a bubble recomposing doesn't re-parse unchanged text.
            val textBubbles = remember(message.content, splitStart, splitEnd) {
                if (message.content.isBlank()) emptyList()
                else MessageSplitter.split(message.content, splitStart, splitEnd)
            }
            Column(horizontalAlignment = if (isUser) Alignment.End else Alignment.Start) {
                message.attachments.forEach { part ->
                    AttachmentCard(part)
                    Spacer(Modifier.height(6.dp))
                }
                textBubbles.forEachIndexed { index, text ->
                    if (index > 0) Spacer(Modifier.height(4.dp))
                    TextBubble(text = text, isUser = isUser)
                }
            }
        }
    }
}

@Composable
private fun TextBubble(text: String, isUser: Boolean) {
    Surface(
        shape = BubbleShape,
        color = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
    ) {
        MarkdownText(
            text = text,
            color = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
        )
    }
}

/** A single attachment (image card / file card), shown WITHOUT a bubble background. */
@Composable
private fun AttachmentCard(part: MessagePart) {
    when {
        part.isImage -> AsyncImage(
            model = part.url,
            contentDescription = part.fileName ?: "图片",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .widthIn(max = 240.dp)
                .heightIn(max = 240.dp)
                .clip(RoundedCornerShape(12.dp))
        )
        else -> Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.width(220.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.InsertDriveFile,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = part.fileName ?: "文件",
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!part.mime.isNullOrBlank()) {
                        Text(
                            text = part.mime,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
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
