package com.bubble.rikkahub.ui.screens.chat.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bubble.rikkahub.domain.model.AvatarMode
import com.bubble.rikkahub.domain.model.Message
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val TIME_GAP_MS = 5 * 60 * 1000L

@Composable
fun BubbleList(
    messages: List<Message>,
    avatarMode: AvatarMode,
    avatarUri: String? = null,
    emoji: String? = null,
    avatarUrl: String? = null,
    displayName: String = "",
    meAvatarUri: String? = null,
    meEmoji: String? = null,
    meDisplayName: String = "",
    isStreaming: Boolean = false,
    streamingMessageId: String? = null,
    bubbleAnimScaleFrom: Float = 0.7f,
    bubbleAnimDurationMs: Int = 400,
    bubbleAnimBounce: Boolean = true,
    bubbleAnimBounciness: Int = 60,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    var hasScrolledToBottom by remember { mutableStateOf(false) }
    var showJumpButton by remember { mutableStateOf(false) }

    // Build the pop-in animation from the customizable settings.
    val enterTransition = buildBubbleEnterTransition(
        scaleFrom = bubbleAnimScaleFrom,
        durationMs = bubbleAnimDurationMs,
        bounce = bubbleAnimBounce,
        bounciness = bubbleAnimBounciness
    )

    // Messages present on the very first composition are shown without animation;
    // messages added later (new replies) pop in with the configured transition.
    val initialIds = remember { messages.map { it.id }.toSet() }

    // Show a "jump to bottom" button whenever the user scrolls up from the latest message.
    // Keyed on listState only so it doesn't restart on every message addition.
    LaunchedEffect(listState) {
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: Int.MAX_VALUE
        }.collect { lastVisible ->
            showJumpButton = lastVisible < (messages.size - 1)
        }
    }

    // Auto-scroll to bottom. The first display jumps instantly (so re-entering lands on the
    // latest message); later additions only auto-scroll if the user is already at the bottom.
    LaunchedEffect(messages.size, isStreaming) {
        if (messages.isNotEmpty()) {
            if (!hasScrolledToBottom) {
                hasScrolledToBottom = true
                listState.scrollToItem(messages.size - 1)
            } else {
                val atBottom = (listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= messages.size - 1
                if (atBottom) {
                    listState.animateScrollToItem(messages.size - 1)
                }
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp),
        reverseLayout = false
    ) {
        itemsIndexed(
            items = messages,
            key = { _, msg -> msg.id }
        ) { index, message ->
            val showAvatar = shouldShowAvatar(index, message, messages, avatarMode)

            val (previousRole, nextRole) = getAdjacentRoles(index, messages)
            val topSpacing = if (index == 0 || previousRole != message.role) 12.dp else 2.dp
            val bottomSpacing = if (index == messages.lastIndex || nextRole != message.role) 12.dp else 2.dp

            Column(modifier = Modifier.padding(top = topSpacing, bottom = bottomSpacing)) {
                if (shouldShowTimeSeparator(index, message, messages)) {
                    TimeSeparator(timeText = formatTime(message.timestamp))
                    Spacer(Modifier.height(8.dp))
                }
                // AnimatedVisibility only runs its enter transition when `visible` flips
                // false -> true, so new bubbles start hidden and flip in to pop.
                var appeared by remember(message.id) { mutableStateOf(message.id in initialIds) }
                LaunchedEffect(message.id) { appeared = true }
                AnimatedVisibility(
                    visible = appeared,
                    enter = enterTransition
                ) {
                    MessageBubble(
                        message = message,
                        showAvatar = showAvatar,
                        avatarUri = avatarUri,
                        emoji = emoji,
                        avatarUrl = avatarUrl,
                        displayName = displayName,
                        meAvatarUri = meAvatarUri,
                        meEmoji = meEmoji,
                        meDisplayName = meDisplayName
                    )
                }
            }
        }

        if (isStreaming) {
            item(key = "typing-indicator") {
                TypingIndicator(
                    avatarUri = avatarUri,
                    emoji = emoji,
                    avatarUrl = avatarUrl,
                    displayName = displayName
                )
            }
        }
    }

    // Jump-to-bottom button, shown while the user has scrolled up from the latest message.
    if (showJumpButton) {
        FloatingActionButton(
            onClick = {
                coroutineScope.launch { listState.animateScrollToItem(messages.size - 1) }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(12.dp)
                .size(44.dp)
        ) {
            Icon(Icons.Filled.ArrowDownward, contentDescription = "跳到底部")
        }
    }
    }
}

/** Centered timestamp shown when there's a large gap between consecutive messages. */
@Composable
private fun TimeSeparator(timeText: String) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(
            text = timeText,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
        )
    }
}

/** Shows a time separator for the first message or when the gap to the previous exceeds 5 minutes. */
private fun shouldShowTimeSeparator(index: Int, current: Message, all: List<Message>): Boolean {
    if (current.timestamp <= 0) return false
    if (index == 0) return true
    val previous = all.getOrNull(index - 1) ?: return false
    if (previous.timestamp <= 0) return true
    return current.timestamp - previous.timestamp >= TIME_GAP_MS
}

/**
 * Formats a message timestamp: today → "HH:mm", yesterday → "昨天 HH:mm",
 * same year → "M月d日", older years → "yyyy年M月d日".
 */
private fun formatTime(timestamp: Long): String {
    if (timestamp <= 0) return ""
    val dt = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault())
    val date = dt.toLocalDate()
    val today = LocalDate.now()
    return when {
        date == today -> DateTimeFormatter.ofPattern("HH:mm").format(dt)
        date == today.minusDays(1) -> "昨天 " + DateTimeFormatter.ofPattern("HH:mm").format(dt)
        date.year == today.year -> DateTimeFormatter.ofPattern("M月d日").format(dt)
        else -> DateTimeFormatter.ofPattern("yyyy年M月d日").format(dt)
    }
}

/**
 * Builds the bubble pop-in transition from the customizable settings.
 * - [durationMs] controls the fade (and the spring's effective speed)
 * - [scaleFrom] is the initial scale factor
 * - when [bounce] is on, the scale uses a spring whose bounciness is controlled by [bounciness]
 */
private fun buildBubbleEnterTransition(
    scaleFrom: Float,
    durationMs: Int,
    bounce: Boolean,
    bounciness: Int
): EnterTransition {
    val duration = durationMs.coerceIn(50, 3000)
    val fade = fadeIn(tween(duration))
    val scaleSpec = if (bounce) {
        val damping = (1f - (bounciness.coerceIn(0, 100) / 100f) * 0.85f).coerceIn(0.1f, 1f)
        val stiffness = (400f / duration) * 400f
        spring<Float>(dampingRatio = damping, stiffness = stiffness)
    } else {
        tween<Float>(duration)
    }
    return fade + scaleIn(
        initialScale = scaleFrom.coerceIn(0.1f, 1f),
        animationSpec = scaleSpec
    )
}

/**
 * Determines if the avatar should be shown for this message based on the avatar mode.
 *
 * EVERY_BUBBLE: always show
 * FIRST_ONLY: show only on the first bubble of the sender's consecutive group
 * LAST_ONLY: show only on the last bubble of the sender's consecutive group
 */
private fun shouldShowAvatar(
    index: Int,
    current: Message,
    allMessages: List<Message>,
    avatarMode: AvatarMode
): Boolean {
    return when (avatarMode) {
        AvatarMode.EVERY_BUBBLE -> true
        AvatarMode.FIRST_ONLY -> {
            val previous = allMessages.getOrNull(index - 1)
            previous == null || previous.role != current.role
        }
        AvatarMode.LAST_ONLY -> {
            val next = allMessages.getOrNull(index + 1)
            next == null || next.role != current.role
        }
    }
}

private fun getAdjacentRoles(
    index: Int,
    messages: List<Message>
): Pair<String?, String?> {
    val previous = messages.getOrNull(index - 1)?.role
    val next = messages.getOrNull(index + 1)?.role
    return previous to next
}
