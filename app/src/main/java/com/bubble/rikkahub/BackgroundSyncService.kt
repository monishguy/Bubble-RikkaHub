package com.bubble.rikkahub

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.bubble.rikkahub.data.NotificationHelper
import com.bubble.rikkahub.data.repository.CustomizationRepository
import com.bubble.rikkahub.di.AppContainer
import com.bubble.rikkahub.domain.model.Conversation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps the process alive with a silent persistent notification and
 * polls the RikkaHub server in the background, posting unread-message notifications. This is
 * the closest we can get to push-based chat updates (like WeChat) without a push channel.
 */
class BackgroundSyncService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var pollJob: Job? = null
    private val lastSeenUpdateAt = mutableMapOf<String, Long>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        try {
            startForeground(NOTIFICATION_ID, buildKeepAliveNotification())
            startPolling()
        } catch (e: Exception) {
            // Foreground start can be rejected (Android 12+ restriction). Stop gracefully
            // instead of crashing the whole app; background sync simply won't run.
            Log.w(TAG, "startForeground 被拒绝，后台同步不运行", e)
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        pollJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private fun buildKeepAliveNotification(): Notification {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(KEEPALIVE_CHANNEL_ID, "后台同步", NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false)
                description = "保持应用在后台运行，自动检测新消息"
            }
        )
        return NotificationCompat.Builder(this, KEEPALIVE_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("气泡RH")
            .setContentText("正在后台自动检测新消息")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = scope.launch {
            while (isActive) {
                val container = (application as BubbleRhApp).container
                val intervalMs = (container.appPreferences.refreshIntervalSeconds.first() * 1000L)
                    .coerceAtLeast(5_000L)
                try {
                    pollOnce(container)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Transient network error — keep the loop alive.
                }
                delay(intervalMs)
            }
        }
    }

    private suspend fun pollOnce(container: AppContainer) {
        val repo = container.conversationRepository
        val prefs = container.appPreferences
        val customRepo = container.customizationRepository
        val splitStart = prefs.splitStartDelimiter.first()
        val splitEnd = prefs.splitEndDelimiter.first()

        val conversations = repo.getConversations().getOrNull() ?: return
        for (conv in conversations) {
            val lastRead = repo.getLastReadAt(conv.id)
            if (lastRead <= 0) {
                // Never read — establish a baseline so existing history isn't "unread".
                repo.markRead(conv.id)
                lastSeenUpdateAt[conv.id] = conv.updatedAt
                continue
            }
            val prevUpdateAt = lastSeenUpdateAt[conv.id]
            val changed = prevUpdateAt != null && conv.updatedAt > prevUpdateAt
            lastSeenUpdateAt[conv.id] = conv.updatedAt
            if (changed) {
                val count = repo.countNewBubbles(conv.id, lastRead, splitStart, splitEnd)
                if (count > 0) {
                    NotificationHelper.notifyUnread(this, enrich(conv, customRepo))
                }
            }
        }
    }

    private suspend fun enrich(conv: Conversation, customRepo: CustomizationRepository): Conversation {
        val c = customRepo.getCustomization(conv.id) ?: return conv
        return conv.copy(
            customAvatarUri = c.avatarUri,
            customEmoji = c.avatarEmoji,
            customNickname = c.nickname
        )
    }

    private companion object {
        const val KEEPALIVE_CHANNEL_ID = "background_sync"
        const val NOTIFICATION_ID = 2001
        const val TAG = "BackgroundSyncService"
    }
}
