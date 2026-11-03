package com.example.frigus_mobile.push

import android.content.Intent

object PushNotifications {
    const val CHANNEL_ID = "frigus_reminders"
    const val EXTRA_OPEN_NOTIFICATIONS = "open_notifications"

    fun supported(type: String?): Boolean =
        type == "PRODUCT_NEAR_EXPIRATION" || type == "SHOPPING_LIST_REMINDER"

    fun shouldOpenCenter(intent: Intent): Boolean =
        intent.getBooleanExtra(EXTRA_OPEN_NOTIFICATIONS, false) ||
            supported(intent.getStringExtra("type"))
}
