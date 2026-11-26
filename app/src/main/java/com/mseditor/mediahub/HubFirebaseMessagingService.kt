package com.mseditor.mediahub

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import java.net.HttpURLConnection
import java.net.URL

class HubFirebaseMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        PushTokenRegistrar.register(applicationContext, token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        if (data["type"] != "message") return

        val username = data["sender_username"].orEmpty().ifBlank { "user" }
        val senderId = data["sender_id"].orEmpty()
        val avatarUrl = data["sender_avatar_url"].orEmpty()
        val messageId = data["message_id"].orEmpty()
        val body = data["body"].orEmpty().ifBlank { "Aapko naya message mila hai." }
        val recipientId = data["recipient_id"].orEmpty()

        createNotificationChannel()
        val avatar = downloadAvatar(avatarUrl)
        val openChat = Intent(this, MainActivity::class.java).apply {
            action = "com.mseditor.mediahub.OPEN_MESSAGE_CHAT"
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("open_chat_user_id", senderId)
            putExtra("open_chat_username", username)
            putExtra("open_chat_avatar_url", avatarUrl)
            putExtra("open_chat_recipient_id", recipientId)
            putExtra("open_chat_message_id", messageId)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            messageId.hashCode(),
            openChat,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("@$username")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setLargeIcon(avatar)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        try {
            NotificationManagerCompat.from(this).notify(messageId.hashCode(), notification)
        } catch (_: SecurityException) {
            // Android 13+ notification permission has not been granted.
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Direct messages",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "New messages from MS Editor Media Hub users"
            }
            manager.createNotificationChannel(channel)
        }
    }

    private fun downloadAvatar(url: String): Bitmap? {
        if (url.isBlank()) return null
        return runCatching {
            val connection = URL(url).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 7000
                connection.readTimeout = 7000
                connection.inputStream.use { BitmapFactory.decodeStream(it) }
            } finally {
                connection.disconnect()
            }
        }.getOrNull()
    }

    companion object {
        private const val CHANNEL_ID = "ms_editor_direct_messages"
    }
}
