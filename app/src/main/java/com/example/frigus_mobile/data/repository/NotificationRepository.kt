package com.example.frigus_mobile.data.repository

import com.example.frigus_mobile.data.api.RetrofitClient
import com.example.frigus_mobile.data.model.NotificationResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class NotificationRepository {

    private val api = RetrofitClient.notificationService

    private val localNotifications = mutableListOf(
        NotificationResponse(
            id = "n1",
            title = "Peito de frango vence amanhã!",
            body = "Confira uma receita para aproveitá-lo.",
            createdAt = "Hoje, 09:20",
            type = "EXPIRATION"
        ),
        NotificationResponse(
            id = "n2",
            title = "Leite Integral está acabando",
            body = "Você usou 75% do produto.",
            createdAt = "Hoje, 08:15",
            type = "STOCK"
        ),
        NotificationResponse(
            id = "n3",
            title = "Receita sugerida para você",
            body = "Temos uma receita deliciosa.",
            createdAt = "Hoje, 07:45",
            type = "RECIPE"
        ),
        NotificationResponse(
            id = "n4",
            title = "Iogurte natural vence em 2 dias",
            body = "Não esqueça de consumi-lo.",
            createdAt = "Ontem, 21:30",
            type = "EXPIRATION"
        ),
        NotificationResponse(
            id = "n5",
            title = "Lista de compras atualizada",
            body = "3 itens foram adicionados.",
            createdAt = "Ontem, 18:20",
            type = "LIST"
        ),
        NotificationResponse(
            id = "n6",
            title = "Resumo semanal disponível",
            body = "Veja como foi sua semana.",
            createdAt = "Este mês, 12/08",
            type = "SUMMARY"
        )
    )

    suspend fun getNotifications(): List<NotificationResponse> = withContext(Dispatchers.IO) {
        try {
            val response = api.getNotifications().execute()
            if (response.isSuccessful && response.body() != null && response.body()!!.content.isNotEmpty()) {
                val list = response.body()!!.content
                localNotifications.clear()
                localNotifications.addAll(list)
                list
            } else {
                localNotifications.toList()
            }
        } catch (_: Exception) {
            localNotifications.toList()
        }
    }

    suspend fun clearAll(): Boolean = withContext(Dispatchers.IO) {
        localNotifications.clear()
        true
    }
}
