package com.bubble.rikkahub.ui.screens.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bubble.rikkahub.domain.model.AvatarMode
import com.bubble.rikkahub.domain.model.ListTheme
import com.bubble.rikkahub.domain.model.SendMode
import com.bubble.rikkahub.ui.components.BubbleAvatar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: (() -> Unit)?
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            viewModel.onMeAvatarChanged(uri.toString())
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(viewModel.buildConfigJson().toByteArray(Charsets.UTF_8))
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val json = context.contentResolver.openInputStream(uri)
                ?.bufferedReader()?.use { it.readText() }
            if (!json.isNullOrBlank()) viewModel.applyConfigJson(json)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // --- "Me" Profile ---
            Text("我的资料", style = MaterialTheme.typography.titleMedium)
            Text(
                "显示在你发出的聊天气泡旁",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BubbleAvatar(
                    avatarUri = state.meAvatarUri,
                    emoji = state.meEmoji.takeIf { it.isNotBlank() },
                    name = state.meNickname.ifBlank { "我" },
                    size = 56.dp
                )
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = state.meNickname,
                        onValueChange = viewModel::onMeNicknameChanged,
                        label = { Text("我的昵称") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = state.meEmoji,
                        onValueChange = viewModel::onMeEmojiChanged,
                        label = { Text("Emoji 头像") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { imagePicker.launch("image/*") }) {
                    Text(if (state.meAvatarUri != null) "更换头像图片" else "选择头像图片")
                }
                Button(onClick = viewModel::saveMeProfile, enabled = !state.isSavingProfile) {
                    if (state.isSavingProfile) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("保存")
                }
            }

            HorizontalDivider()

            // --- Server Settings ---
            Text("服务器连接", style = MaterialTheme.typography.titleMedium)

            OutlinedTextField(
                value = state.serverUrl,
                onValueChange = viewModel::onServerUrlChanged,
                label = { Text("RikkaHub 服务器地址") },
                placeholder = { Text("http://192.168.1.100:8080") },
                supportingText = state.serverUrlError?.let { error ->
                    { Text(error, color = MaterialTheme.colorScheme.error) }
                },
                isError = state.serverUrlError != null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = viewModel::saveServerUrl,
                enabled = !state.isSaving,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text("保存并连接")
            }

            HorizontalDivider()

            // --- Message Split Settings ---
            Text("消息拆分", style = MaterialTheme.typography.titleMedium)
            Text(
                "AI 回复中使用分隔符标记的内容将被拆分为多个聊天气泡",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = state.splitStart,
                    onValueChange = viewModel::onSplitStartChanged,
                    label = { Text("开始分隔符") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = state.splitEnd,
                    onValueChange = viewModel::onSplitEndChanged,
                    label = { Text("结束分隔符") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            HorizontalDivider()

            // --- Send Mode ---
            Text("发送模式", style = MaterialTheme.typography.titleMedium)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.sendMode == SendMode.TIMER,
                    onClick = { viewModel.onSendModeChanged(SendMode.TIMER) },
                    label = { Text("定时发送") }
                )
                FilterChip(
                    selected = state.sendMode == SendMode.MANUAL,
                    onClick = { viewModel.onSendModeChanged(SendMode.MANUAL) },
                    label = { Text("手动发送") }
                )
            }

            if (state.sendMode == SendMode.TIMER) {
                SliderWithIntValue(
                    label = "自动发送倒计时",
                    value = state.timerDelay,
                    min = 1,
                    max = 60,
                    suffix = "秒",
                    onValueChange = { viewModel.onTimerDelayChanged(it) }
                )
                Text(
                    "添加气泡后如无操作，将在 ${state.timerDelay} 秒后自动发送",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    "添加完所有气泡后，发送空消息即可提交",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HorizontalDivider()

            // --- Avatar Mode ---
            Text("头像显示", style = MaterialTheme.typography.titleMedium)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.avatarMode == AvatarMode.EVERY_BUBBLE,
                    onClick = { viewModel.onAvatarModeChanged(AvatarMode.EVERY_BUBBLE) },
                    label = { Text("每条都显示") }
                )
                FilterChip(
                    selected = state.avatarMode == AvatarMode.FIRST_ONLY,
                    onClick = { viewModel.onAvatarModeChanged(AvatarMode.FIRST_ONLY) },
                    label = { Text("仅首条显示") }
                )
                FilterChip(
                    selected = state.avatarMode == AvatarMode.LAST_ONLY,
                    onClick = { viewModel.onAvatarModeChanged(AvatarMode.LAST_ONLY) },
                    label = { Text("仅末条显示") }
                )
            }

            Text(
                when (state.avatarMode) {
                    AvatarMode.FIRST_ONLY -> "同一发送者连续消息中，头像仅在第一条（组顶）显示"
                    AvatarMode.LAST_ONLY -> "同一发送者连续消息中，头像仅在最后一条（组底）显示"
                    else -> "每条消息气泡旁都显示头像"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider()

            // --- List Theme ---
            Text("对话列表样式", style = MaterialTheme.typography.titleMedium)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.listTheme == ListTheme.FLAT,
                    onClick = { viewModel.onListThemeChanged(ListTheme.FLAT) },
                    label = { Text("平铺列表") }
                )
                FilterChip(
                    selected = state.listTheme == ListTheme.CARDS,
                    onClick = { viewModel.onListThemeChanged(ListTheme.CARDS) },
                    label = { Text("圆角卡片") }
                )
            }

            HorizontalDivider()

            // --- Bubble Pop-in Delay ---
            Text("气泡弹出延迟", style = MaterialTheme.typography.titleMedium)
            Text(
                "AI 回复拆分出的气泡逐个弹出的随机延迟范围（毫秒）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            SliderWithIntValue(
                label = "最小延迟",
                value = state.bubbleDelayMinMs,
                min = 0,
                max = 2000,
                suffix = "ms",
                onValueChange = { viewModel.onBubbleDelayMinChanged(it) }
            )
            SliderWithIntValue(
                label = "最大延迟",
                value = state.bubbleDelayMaxMs,
                min = 0,
                max = 4000,
                suffix = "ms",
                onValueChange = { viewModel.onBubbleDelayMaxChanged(it) }
            )

            HorizontalDivider()

            // --- Auto Refresh Interval ---
            Text("自动刷新间隔", style = MaterialTheme.typography.titleMedium)
            Text(
                "应用在前台或后台时自动检测新消息的频率",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            SliderWithIntValue(
                label = "刷新间隔",
                value = state.refreshIntervalSeconds,
                min = 5,
                max = 60,
                suffix = "秒",
                onValueChange = { viewModel.onRefreshIntervalChanged(it) }
            )

            HorizontalDivider()

            // --- Bubble Pop Animation ---
            Text("气泡动画", style = MaterialTheme.typography.titleMedium)
            Text(
                "新气泡弹出动画的自定义参数",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            SliderWithIntValue(
                label = "起始缩放",
                value = (state.bubbleAnimScaleFrom * 100).toInt(),
                min = 10,
                max = 100,
                suffix = "%",
                onValueChange = { viewModel.onBubbleAnimScaleFromChanged(it / 100f) }
            )
            SliderWithIntValue(
                label = "动画时长",
                value = state.bubbleAnimDurationMs,
                min = 50,
                max = 3000,
                suffix = "ms",
                onValueChange = { viewModel.onBubbleAnimDurationChanged(it) }
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("回弹效果", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                Switch(
                    checked = state.bubbleAnimBounce,
                    onCheckedChange = { viewModel.onBubbleAnimBounceChanged(it) }
                )
            }
            if (state.bubbleAnimBounce) {
                SliderWithIntValue(
                    label = "回弹强度",
                    value = state.bubbleAnimBounciness,
                    min = 0,
                    max = 100,
                    suffix = "%",
                    onValueChange = { viewModel.onBubbleAnimBouncinessChanged(it) }
                )
            }

            HorizontalDivider()

            // --- Config export / import ---
            Text("配置管理", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { exportLauncher.launch("bubble-rh-config.json") }) {
                    Text("导出配置")
                }
                OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json")) }) {
                    Text("导入配置")
                }
            }
            Text(
                "导出为 JSON 文件，包含服务器地址、分隔符、发送/头像模式、气泡延迟和我的资料",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(32.dp))
        }
    }
}

/** A slider paired with a numeric input box. Both edit the same value. */
@Composable
private fun SliderWithIntValue(
    label: String,
    value: Int,
    min: Int,
    max: Int,
    suffix: String = "",
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            OutlinedTextField(
                value = value.toString(),
                onValueChange = { input ->
                    input.toIntOrNull()?.let { onValueChange(it.coerceIn(min, max)) }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(88.dp)
            )
            if (suffix.isNotBlank()) {
                Text(suffix, style = MaterialTheme.typography.bodySmall)
            }
        }
        Slider(
            value = value.toFloat().coerceIn(min.toFloat(), max.toFloat()),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = min.toFloat()..max.toFloat(),
            steps = 0,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
