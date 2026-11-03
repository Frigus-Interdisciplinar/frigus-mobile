package com.example.frigus_mobile.data.repository

import com.example.frigus_mobile.data.api.RetrofitClient
import com.example.frigus_mobile.data.api.NotificationApiService
import com.example.frigus_mobile.data.api.requireBody
import com.example.frigus_mobile.data.api.requireNoContent
import com.example.frigus_mobile.data.model.NotificationPageResponse
import com.example.frigus_mobile.data.model.NotificationResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class NotificationRepository(
    private val api: NotificationApiService = RetrofitClient.notificationService
) {

    suspend fun getNotifications(page: Int, size: Int, unreadOnly: Boolean? = null): NotificationPageResponse = withContext(Dispatchers.IO) {
        api.getNotifications(page, size, unreadOnly).requireBody()
    }

    suspend fun markAsRead(id: String): NotificationResponse = withContext(Dispatchers.IO) {
        api.markAsRead(id).requireBody()
    }

    suspend fun markAllAsRead() = withContext(Dispatchers.IO) {
        api.markAllAsRead().requireNoContent()
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        api.clearAll().requireNoContent()
    }
}

