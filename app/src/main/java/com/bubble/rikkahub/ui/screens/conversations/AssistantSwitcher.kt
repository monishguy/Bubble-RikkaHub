package com.bubble.rikkahub.ui.screens.conversations

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bubble.rikkahub.domain.model.AssistantInfo
import com.bubble.rikkahub.ui.components.BubbleAvatar

/**
 * Top-bar control showing the current assistant's name; tapping it opens a dialog
 * listing all assistants. Selecting one switches the active assistant on the server.
 */
@Composable
fun AssistantSwitcher(
    currentAssistant: AssistantInfo?,
    assistants: List<AssistantInfo>,
    isSwitching: Boolean,
    onSwitch: (String) -> Unit,
    onRefresh: () -> Unit,
    onCustomizeAssistant: (AssistantInfo) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showDialog by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .clickable(enabled = !isSwitching) { showDialog = true }
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = currentAssistant?.displayName ?: "助手",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Icon(
            imageVector = Icons.Filled.ArrowDropDown,
            contentDescription = "切换助手",
            tint = MaterialTheme.colorScheme.primary
        )
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("切换助手") },
            text = {
                if (assistants.isEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("暂时没有获取到助手列表", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = { onRefresh() }) { Text("重新加载") }
                    }
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                        items(assistants, key = { it.id }) { assistant ->
                            val isCurrent = assistant.id == currentAssistant?.id
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onSwitch(assistant.id)
                                        showDialog = false
                                    }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                BubbleAvatar(
                                    avatarUrl = assistant.avatarUrl,
                                    emoji = assistant.avatarEmoji,
                                    name = assistant.displayName,
                                    size = 40.dp
                                )
                                Text(
                                    text = assistant.displayName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f)
                                )
                                if (isCurrent) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = "当前助手",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                IconButton(onClick = { onCustomizeAssistant(assistant) }) {
                                    Icon(
                                        imageVector = Icons.Filled.Edit,
                                        contentDescription = "自定义助手",
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) { Text("关闭") }
            }
        )
    }
}
