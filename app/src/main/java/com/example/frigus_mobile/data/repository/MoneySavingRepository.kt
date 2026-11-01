package com.example.frigus_mobile.data.repository

import com.example.frigus_mobile.data.api.RetrofitClient
import com.example.frigus_mobile.data.api.requireBody
import com.example.frigus_mobile.data.model.MoneySavingEventPage
import com.example.frigus_mobile.data.model.MoneySavingSummary
import com.example.frigus_mobile.data.local.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

class MoneySavingRepository(private val session: SessionManager) {
    suspend fun groupId(): String = withContext(Dispatchers.IO) {
        val id = RetrofitClient.stockService.getInventoryHome(session.activeGroupId).requireBody().groupId
        require(!id.isNullOrBlank() && runCatching { UUID.fromString(id) }.isSuccess) {
            "Grupo ativo indisponível."
        }
        id
    }
    suspend fun summary(groupId: String, from: String, to: String): MoneySavingSummary =
        withContext(Dispatchers.IO) { RetrofitClient.moneySavingService.summary(groupId, from, to).requireBody() }
    suspend fun events(groupId: String, from: String, to: String, category: String?, kind: String?, page: Int): MoneySavingEventPage =
        withContext(Dispatchers.IO) { RetrofitClient.moneySavingService.events(groupId, from, to, category, kind, page).requireBody() }
}
