package com.example.frigus_mobile.data.repository

import com.example.frigus_mobile.data.api.RetrofitClient
import com.example.frigus_mobile.data.api.requireBody
import com.example.frigus_mobile.data.api.requireCreatedBody
import com.example.frigus_mobile.data.api.requireNoContent
import com.example.frigus_mobile.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

class SubscriptionRepository {
    private val api get() = RetrofitClient.subscriptionService
    suspend fun plans() = withContext(Dispatchers.IO) { api.plans().requireBody() }
    suspend fun subscription() = withContext(Dispatchers.IO) { api.subscription().requireBody() }
    suspend fun limits() = withContext(Dispatchers.IO) { api.limits().requireBody() }
    suspend fun transactions(page: Int) = withContext(Dispatchers.IO) { api.transactions(page).requireBody() }
    suspend fun cancel() = withContext(Dispatchers.IO) { api.cancel().requireBody() }
    suspend fun reactivate() = withContext(Dispatchers.IO) { api.reactivate().requireBody() }
    suspend fun freeCheckout(planCode: String) = withContext(Dispatchers.IO) {
        api.checkout(UUID.randomUUID().toString(), CheckoutRequest(planCode)).requireBody()
    }
    suspend fun groups() = withContext(Dispatchers.IO) { api.groups().requireBody() }
    suspend fun createGroup(name: String) = withContext(Dispatchers.IO) {
        api.createGroup(GroupCreateRequest(name)).requireCreatedBody()
    }
    suspend fun createStock(groupId: String, name: String) = withContext(Dispatchers.IO) {
        api.createStock(StockCreateRequest(groupId, name)).requireCreatedBody()
    }
    suspend fun inventory(groupId: String) = withContext(Dispatchers.IO) {
        RetrofitClient.stockService.getInventoryHome(groupId).requireBody()
    }
    suspend fun group(id: String) = withContext(Dispatchers.IO) { api.group(id).requireBody() }
    suspend fun invitations(id: String) = withContext(Dispatchers.IO) { api.invitations(id).requireBody() }
    suspend fun invite(id: String, email: String, role: String) = withContext(Dispatchers.IO) {
        api.invite(id, InviteMemberRequest(email, role)).requireBody()
    }
    suspend fun revoke(id: String, invitationId: String) = withContext(Dispatchers.IO) {
        api.revoke(id, invitationId).requireNoContent()
    }
}
