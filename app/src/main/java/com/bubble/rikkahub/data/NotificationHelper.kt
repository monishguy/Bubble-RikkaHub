package com.bubble.rikkahub.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.bubble.rikkahub.MainActivity
import com.bubble.rikkahub.domain.model.Conversation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Builds and posts unread-message notifications using the app's custom nickname/avatar. */
object NotificationHelper {

    suspend fun notifyUnread(context: Context, conv: Conversation) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val builder = NotificationCompat.Builder(context, MainActivity.UNREAD_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(conv.displayName) // custom nickname if set, else server title
            .setContentText("有新消息未读")
            .setAutoCancel(true)

        conv.customAvatarUri?.let { uri ->
            val bitmap = loadBitmap(context, uri)
            if (bitmap != null) {
                builder.setLargeIcon(bitmap)
            }
        }

        NotificationManagerCompat.from(context).notify(conv.id.hashCode(), builder.build())
    }

    private suspend fun loadBitmap(context: Context, uriString: String): Bitmap? {
        return withContext(Dispatchers.IO) {
            runCatching {
                val uri = Uri.parse(uriString)
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
            }.getOrNull()
        }
    }
}
