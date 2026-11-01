package com.example.frigus_mobile.data.repository

import android.content.Context
import com.example.frigus_mobile.data.api.*
import com.example.frigus_mobile.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/** Human chat uses published BFF contracts; no sample people, local replies or global transcript. */
class ConversationRepository(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("conversation_pending_v1", Context.MODE_PRIVATE)
    private val api get() = RetrofitClient.conversationService

    fun pending(owner: String, id: String, action: String): Boolean = preferences.getBoolean(key(owner, id, action), false)
    fun setPending(owner: String, id: String, action: String, value: Boolean) {
        val key = key(owner, id, action)
        val before = preferences.getBoolean(key, false)
        if (!preferences.edit().putBoolean(key, value).commit()) {
            preferences.edit().putBoolean(key, before).commit()
            throw BffException(ApiError(code = "CONVERSATION_LOCAL_WRITE_FAILED"), "")
        }
    }
    private fun key(owner: String, id: String, action: String): String {
        valid(uuid(owner) && uuid(id) && action in setOf("send", "read"))
        return "$owner/$id/$action"
    }

    suspend fun conversations(guard: () -> Unit): List<HumanConversation> = withContext(Dispatchers.IO) {
        guard()
        val response = api.conversations().requireBody()
        guard()
        valid(response.all { validConversation(it) } && response.map { it.id }.distinct().size == response.size)
        response
    }
    suspend fun conversation(id: String, guard: () -> Unit): HumanConversation = withContext(Dispatchers.IO) {
        valid(uuid(id)); guard()
        val response = api.conversation(id).requireBody()
        guard(); valid(response.id == id && validConversation(response)); response
    }
    suspend fun messages(id: String, page: Int, guard: () -> Unit): PageResponse<HumanMessage> = withContext(Dispatchers.IO) {
        guard()
        val response = api.messages(id, page).requireBody()
        guard()
        valid(response.number == page && response.size > 0 && response.totalPages >= 0 &&
            response.totalElements >= response.content.size && response.content.size <= response.size &&
            response.content.all { validMessage(it, id) } &&
            (if (response.totalPages == 0) response.content.isEmpty() else page < response.totalPages))
        response
    }
    suspend fun send(id: String, content: String, guard: () -> Unit): HumanMessage = withContext(Dispatchers.IO) {
        guard()
        val response = api.send(id, ConversationSendRequest(content = content)).requireCreatedBody()
        guard(); valid(validMessage(response, id) && response.messageType == "TEXT" && response.content == content)
        response
    }
    suspend fun read(id: String, messageId: Int, guard: () -> Unit) = withContext(Dispatchers.IO) {
        guard(); api.read(id, messageId).requireNoContent(); guard()
    }
    private fun validConversation(item: HumanConversation): Boolean = uuid(item.id) &&
        item.conversationType in setOf("GROUP", "PRIVATE") && item.participants != null &&
        item.participants.all { uuid(it.userId) } &&
        (item.latestMessage == null || validMessage(item.latestMessage, item.id!!))
    private fun validMessage(item: HumanMessage, id: String): Boolean = (item.id ?: 0) > 0 &&
        item.conversationId == id && uuid(item.senderId) && !item.messageType.isNullOrBlank()
    private fun valid(condition: Boolean) { if (!condition) throw BffException(ApiError(code = "INVALID_RESPONSE"), "") }
    private fun uuid(value: String?): Boolean = value != null &&
        runCatching { UUID.fromString(value).toString().equals(value, true) }.getOrDefault(false)
}
