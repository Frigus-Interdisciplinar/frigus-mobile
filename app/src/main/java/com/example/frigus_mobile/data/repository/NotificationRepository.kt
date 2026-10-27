package com.example.frigus_mobile.data.repository

import com.example.frigus_mobile.data.api.RetrofitClient
import com.example.frigus_mobile.data.api.NotificationApiService
import com.example.frigus_mobile.data.model.NotificationResponse
import com.example.frigus_mobile.data.model.PageResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

class NotificationRepository(
    private val api: NotificationApiService = RetrofitClient.notificationService
) {

    suspend fun getNotifications(page: Int, size: Int): PageResponse<NotificationResponse> = withContext(Dispatchers.IO) {
        val response = api.getNotifications(page, size).execute()
        if (!response.isSuccessful) throw IOException("Falha ao carregar notificações (HTTP " + response.code() + ").")
        response.body() ?: throw IOException("Resposta de notificações vazia.")
    }

    suspend fun markAsRead(id: String): NotificationResponse = withContext(Dispatchers.IO) {
        val response = api.markAsRead(id).execute()
        if (!response.isSuccessful) throw IOException("Falha ao marcar notificação como lida (HTTP " + response.code() + ").")
        response.body() ?: throw IOException("Resposta de notificação vazia.")
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        val response = api.clearAll().execute()
        if (!response.isSuccessful) throw IOException("Falha ao limpar notificações (HTTP " + response.code() + ").")
    }
}

