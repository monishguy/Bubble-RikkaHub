package com.bubble.rikkahub.ui.screens.chat

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage

private val BackgroundColors = listOf(
    0xFFF5F5F5L, 0xFFFFE4E1L, 0xFFE3F2FDL, 0xFFE8F5E9L,
    0xFFFFF8E1L, 0xFFF3E5F5L, 0xFFE0F7FAL, 0xFFFAFAFAL
)

/**
 * Full-screen conversation info / edit screen: large square avatar, centered name,
 * and controls to edit the nickname / emoji / avatar image.
 */
@Composable
fun ChatInfoScreen(
    displayName: String,
    customAvatarUri: String?,
    customEmoji: String?,
    customNickname: String?,
    customBgUri: String? = null,
    customBgColor: Long? = null,
    onSave: (nickname: String, emoji: String, avatarUri: Uri?, bgUri: String?, bgColor: Long?) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var nickname by remember { mutableStateOf(customNickname ?: "") }
    var emoji by remember { mutableStateOf(customEmoji ?: "") }
    var pickedAvatarUri by remember { mutableStateOf<Uri?>(null) }
    var bgUri by remember { mutableStateOf(customBgUri) }
    var bgColor by remember { mutableStateOf(customBgColor) }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            pickedAvatarUri = uri
        }
    }

    val bgImagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            bgUri = uri.toString()
            bgColor = null
        }
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                // Top bar
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                    Text(
                        "会话信息",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { onSave(nickname.trim(), emoji.trim(), pickedAvatarUri, bgUri, bgColor) }) {
                        Text("保存")
                    }
                }

                // Large square avatar (fills the width)
                val effectiveUri = pickedAvatarUri?.toString() ?: customAvatarUri
                val effectiveEmoji = emoji.takeIf { it.isNotBlank() }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 48.dp)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        !effectiveUri.isNullOrBlank() -> AsyncImage(
                            model = effectiveUri,
                            contentDescription = displayName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        !effectiveEmoji.isNullOrBlank() -> Text(
                            effectiveEmoji, fontSize = 64.sp, textAlign = TextAlign.Center
                        )
                        else -> Text(
                            displayName.firstOrNull()?.toString() ?: "?",
                            fontSize = 64.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // Name centered below the avatar
                Text(
                    text = nickname.ifBlank { displayName },
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    textAlign = TextAlign.Center
                )

                // Edit fields
                Column(
                    modifier = Modifier.padding(horizontal = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = nickname,
                        onValueChange = { nickname = it },
                        label = { Text("昵称") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = emoji,
                        onValueChange = { emoji = it.take(2) },
                        label = { Text("Emoji 头像") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = { imagePicker.launch("image/*") },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (pickedAvatarUri != null) "已选择图片 ✓" else "选择头像图片")
                    }
                }

                Spacer(Modifier.height(24.dp))

                // --- Chat Background ---
                Text("聊天背景", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { bgImagePicker.launch("image/*") }) {
                        Text(if (bgUri != null) "已选图片" else "选择背景图片")
                    }
                    OutlinedButton(onClick = { bgUri = null; bgColor = null }) {
                        Text("清除背景")
                    }
                }
                Text(
                    "背景颜色",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BackgroundColors.forEach { c ->
                        val selected = bgColor == c && bgUri == null
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(c))
                                .border(
                                    width = if (selected) 3.dp else 1.dp,
                                    color = if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outlineVariant,
                                    shape = CircleShape
                                )
                                .clickable { bgColor = c; bgUri = null }
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
