package com.bubble.rikkahub.ui.screens.chat.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bubble.rikkahub.ui.components.BubbleAvatar
import com.bubble.rikkahub.ui.theme.BubbleShape
import kotlinx.coroutines.delay

/**
 * A live-updating bubble shown while the AI response is streaming in via SSE.
 * Includes a blinking cursor to indicate active generation.
 */
@Composable
fun StreamingBubble(
    content: String,
    showAvatar: Boolean,
    avatarUri: String? = null,
    emoji: String? = null,
    avatarUrl: String? = null,
    displayName: String = "",
    modifier: Modifier = Modifier
) {
    var cursorVisible by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        while (true) {
            cursorVisible = !cursorVisible
            delay(530)
        }
    }

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
            Spacer(Modifier.width(40.dp))
        }

        Surface(
            shape = BubbleShape,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text(
                    text = content.ifBlank { "..." },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge
                )
                AnimatedVisibility(
                    visible = cursorVisible,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Text(
                        text = "|",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    }
}
