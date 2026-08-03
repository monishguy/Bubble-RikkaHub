package com.bubble.rikkahub

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.bubble.rikkahub.ui.navigation.MainNavGraph
import com.bubble.rikkahub.ui.theme.Bubble_RikkahubTheme

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    /** Conversation to open when launched from a notification, if any. */
    private var pendingConversationId by mutableStateOf<String?>(null)
    private var syncServiceStarted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        createNotificationChannel()
        requestNotificationPermission()

        handleIntent(intent)

        setContent {
            Bubble_RikkahubTheme {
                MainNavGraph(
                    appContainer = (application as BubbleRhApp).container,
                    initialConversationId = pendingConversationId
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Foreground services must be started while the activity is actually in the
        // foreground. Starting them too early (onCreate) is rejected on Android 12+ and
        // crashes the app with ForegroundServiceStartNotAllowedException.
        if (!syncServiceStarted) {
            syncServiceStarted = true
            try {
                ContextCompat.startForegroundService(
                    this, Intent(this, BackgroundSyncService::class.java)
                )
            } catch (e: Exception) {
                Log.w(TAG, "无法启动后台同步服务", e)
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: android.content.Intent?) {
        val id = intent?.getStringExtra(EXTRA_CONVERSATION_ID)
        if (!id.isNullOrBlank()) {
            pendingConversationId = id
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                UNREAD_CHANNEL_ID,
                "新消息",
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "收到未读消息时通知" }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    companion object {
        private const val TAG = "MainActivity"
        const val UNREAD_CHANNEL_ID = "unread_messages"
        const val EXTRA_CONVERSATION_ID = "conversation_id"
    }
}
