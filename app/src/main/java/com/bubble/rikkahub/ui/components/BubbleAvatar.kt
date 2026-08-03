package com.bubble.rikkahub.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage

/**
 * Avatar component with fallback chain:
 * 1. Custom image URI (content:// or file://)
 * 2. Custom emoji (displayed as text)
 * 3. API avatar URL (loaded via Coil)
 * 4. First character of display name in a colored circle
 */
@Composable
fun BubbleAvatar(
    avatarUri: String? = null,
    emoji: String? = null,
    avatarUrl: String? = null,
    name: String,
    size: Dp = 40.dp,
    modifier: Modifier = Modifier
) {
    val fallbackChar = name.firstOrNull()?.uppercase() ?: "?"

    when {
        // Priority 1: Custom local avatar image
        !avatarUri.isNullOrBlank() -> {
            AsyncImage(
                model = avatarUri,
                contentDescription = "$name 的头像",
                contentScale = ContentScale.Crop,
                modifier = modifier
                    .size(size)
                    .clip(CircleShape)
            )
        }

        // Priority 2: Custom emoji
        !emoji.isNullOrBlank() -> {
            Box(
                modifier = modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = emoji,
                    fontSize = (size.value * 0.5f).sp,
                    textAlign = TextAlign.Center
                )
            }
        }

        // Priority 3: API avatar URL
        !avatarUrl.isNullOrBlank() -> {
            AsyncImage(
                model = avatarUrl,
                contentDescription = "$name 的头像",
                contentScale = ContentScale.Crop,
                modifier = modifier
                    .size(size)
                    .clip(CircleShape)
            )
        }

        // Priority 4: First character fallback
        else -> {
            Box(
                modifier = modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = fallbackChar,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Medium,
                    fontSize = (size.value * 0.4f).sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
