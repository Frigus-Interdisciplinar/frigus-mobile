package com.example.frigus_mobile.push

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.frigus_mobile.R
import com.example.frigus_mobile.data.local.SessionManager
import com.example.frigus_mobile.ui.login.LoginActivity
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class FrigusMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        PushRegistration.sync(this, token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        if (!PushNotifications.supported(message.data["type"]) || !SessionManager(this).isLoggedIn) return
        val manager = NotificationManagerCompat.from(this)
        if (!manager.areNotificationsEnabled()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val id = message.data["notificationId"] ?: message.messageId ?: return
        val intent = Intent(this, LoginActivity::class.java).apply {
            putExtra(PushNotifications.EXTRA_OPEN_NOTIFICATIONS, true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this, id.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, PushNotifications.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_status)
            .setContentTitle(message.notification?.title ?: getString(R.string.notifications_title))
            .setContentText(message.notification?.body.orEmpty())
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .build()
        try {
            manager.notify(id.hashCode(), notification)
        } catch (_: SecurityException) {
            // Permission can be revoked after the check above.
        }
    }
}
