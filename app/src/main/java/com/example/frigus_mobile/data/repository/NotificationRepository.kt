package com.example.frigus_mobile.data.repository

import com.example.frigus_mobile.data.api.RetrofitClient
import com.example.frigus_mobile.data.model.NotificationResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

class NotificationRepository {

    private val api = RetrofitClient.notificationService

    suspend fun getNotifications(): List<NotificationResponse> = withContext(Dispatchers.IO) {
        val response = api.getNotifications().execute()
        if (response.isSuccessful) {
            response.body()?.content.orEmpty()
        } else {
            throw IOException("Falha ao carregar notificações do servidor (Código ${response.code()}).")
        }
    }
}

