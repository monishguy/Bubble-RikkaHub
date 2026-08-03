package com.bubble.rikkahub.ui.screens.chat.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bubble.rikkahub.domain.model.SendMode

@Composable
fun ChatInputBar(
    inputText: String,
    onInputTextChanged: (String) -> Unit,
    onSend: () -> Unit,
    onRemoveLast: () -> Unit,
    pendingBubbles: List<String>,
    sendMode: SendMode,
    timerSecondsRemaining: Int?,
    isSending: Boolean,
    queuedSends: Int = 0,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // Pending bubbles preview
        if (pendingBubbles.isNotEmpty()) {
            PendingBubblesPreview(
                bubbles = pendingBubbles,
                onRemoveLast = onRemoveLast,
                sendMode = sendMode,
                timerSecondsRemaining = timerSecondsRemaining
            )
        }

        // Input row
        Surface(
            tonalElevation = 3.dp,
            shadowElevation = 8.dp
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = onInputTextChanged,
                        placeholder = { Text("消息") },
                        modifier = Modifier.weight(1f),
                        maxLines = 4,
                        shape = RoundedCornerShape(20.dp),
                        enabled = !isSending
                    )

                    SendButton(
                        onClick = onSend,
                        enabled = !isSending,
                        hasContent = inputText.isNotBlank() || pendingBubbles.isNotEmpty()
                    )
                }

                // Timer / manual mode indicator
                if (pendingBubbles.isNotEmpty()) {
                    ModeIndicator(
                        sendMode = sendMode,
                        timerSecondsRemaining = timerSecondsRemaining,
                        bubbleCount = pendingBubbles.size
                    )
                }
            }
        }

        // Offline-queued sends indicator
        if (queuedSends > 0) {
            Text(
                text = "⏳ $queuedSends 条消息排队，网络恢复后自动发送",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun PendingBubblesPreview(
    bubbles: List<String>,
    onRemoveLast: () -> Unit,
    sendMode: SendMode,
    timerSecondsRemaining: Int?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (sendMode == SendMode.TIMER && timerSecondsRemaining != null) {
            Text(
                text = "${timerSecondsRemaining}s",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(8.dp))
        }

        Text(
            text = "${bubbles.size} 个气泡待发送",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.width(4.dp))
        IconButton(
            onClick = onRemoveLast,
            modifier = Modifier.size(24.dp)
        ) {
            Icon(
                Icons.Filled.Close,
                contentDescription = "撤销最后一个气泡",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SendButton(
    onClick: () -> Unit,
    enabled: Boolean,
    hasContent: Boolean
) {
    FilledIconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(40.dp)
    ) {
        Icon(
            Icons.AutoMirrored.Filled.Send,
            contentDescription = "发送",
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun ModeIndicator(
    sendMode: SendMode,
    timerSecondsRemaining: Int?,
    bubbleCount: Int
) {
    Text(
        text = when (sendMode) {
            SendMode.TIMER ->
                if (timerSecondsRemaining != null)
                    "已排队 $bubbleCount 个气泡 · ${timerSecondsRemaining}秒后自动发送"
                else
                    "正在发送…"
            SendMode.MANUAL ->
                "已排队 $bubbleCount 个气泡 · 清空输入框后再点发送即可提交"
        },
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
    )
}
