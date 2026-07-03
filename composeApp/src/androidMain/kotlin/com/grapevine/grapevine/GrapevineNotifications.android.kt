package com.grapevine.grapevine

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

actual object GrapevineNotifications {
    const val CHANNEL_ID = "grapevine_messages"

    actual fun showMessageNotification(
        roomId: String,
        roomName: String,
        senderName: String,
        preview: String,
    ) {
        val context = AppContext.get()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = android.app.Notification.Builder(context, CHANNEL_ID)
            .setContentTitle(if (senderName.isNotEmpty()) "$senderName in $roomName" else roomName)
            .setContentText(preview)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setAutoCancel(true)
            .build()
        manager.notify(roomId.hashCode(), notification)
    }

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Messages",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = "New message notifications" }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }
}
