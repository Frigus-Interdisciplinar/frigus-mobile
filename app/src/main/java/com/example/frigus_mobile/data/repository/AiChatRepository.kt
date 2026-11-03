package com.example.frigus_mobile.data.repository

import android.content.Context
import android.util.Log
import com.example.frigus_mobile.BuildConfig
import com.example.frigus_mobile.data.api.BffException
import com.example.frigus_mobile.data.api.InvalidBffResponseException
import com.example.frigus_mobile.data.api.RetrofitClient
import com.example.frigus_mobile.data.local.SessionCredential
import com.example.frigus_mobile.data.local.SessionManager
import com.example.frigus_mobile.data.model.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal data class AiChatContext(
    val stock: StockResponse, val groupId: String, val userId: String,
    val credential: SessionCredential, val selectedGroup: String?, val selectedStock: Int?
)
internal class AiChatContextException(val code: String) : IllegalStateException()
internal class AiChatStorageException : IllegalStateException()
internal class AiChatInputException : IllegalArgumentException()

/** Only the currently selected stock, or the unambiguous stock of the confirmed group. */
internal fun selectAiChatStock(home: InventoryHomeResponse, groupId: String?, stockId: Int?): StockResponse? {
    val confirmedGroup = home.groupId
    if (confirmedGroup == null || !runCatching { UUID.fromString(confirmedGroup).toString().equals(confirmedGroup, true) }.getOrDefault(false) ||
        (groupId != null && groupId != confirmedGroup) || home.stocks == null ||
        home.stocks.any { it == null || it.id <= 0 || (it.groupId != null && it.groupId != confirmedGroup) })
        throw InvalidBffResponseException()
    val stocks = home.stocks.filterNotNull()
    if (stockId != null) return stocks.firstOrNull { it.id == stockId }
        ?: throw AiChatContextException("AI_STOCK_CHANGED")
    if (stocks.size > 1) throw AiChatContextException("AI_STOCK_AMBIGUOUS")
    return stocks.singleOrNull()
}

internal fun validAiChatReply(reply: AiRecipeChatResponse): Boolean =
    (reply.recipeIds == null || (reply.recipeIds.size <= 10 && reply.recipeIds.all { it > 0 })) &&
    !reply.sessionId.isNullOrBlank() && (reply.sessionId.length <= 500) &&
        (!reply.chatMessage.isNullOrBlank() || ((reply.recipeId ?: 0) > 0 && !reply.recipeName.isNullOrBlank() &&
            (!reply.recipeDescription.isNullOrBlank() || !reply.recipeInstructions.isNullOrBlank())))

/** The only mutation is M19, sent once through the authenticated mobile client. */
class AiChatRepository(context: Context) {
    private val session = SessionManager(context)
    private val preferences = context.applicationContext.getSharedPreferences("ai_chat_send_state", Context.MODE_PRIVATE)
    private val foodPreferences = FoodPreferencesRepository(context.applicationContext)

    internal fun isCurrent(context: AiChatContext): Boolean = session.isLoggedIn && session.userId == context.userId &&
        session.state.credential() == context.credential && session.activeGroupId == context.selectedGroup &&
        session.activeStockId == context.selectedStock

    internal fun ensureCurrent(context: AiChatContext) { if (!isCurrent(context)) throw CancellationException() }

    internal suspend fun resolveContext(): AiChatContext? {
        if (!session.isLoggedIn) throw CancellationException()
        val credential = session.state.credential() ?: throw CancellationException()
        val userId = session.userId ?: throw CancellationException()
        val selectedGroup = session.activeGroupId
        val selectedStock = session.activeStockId
        val home = RetrofitClient.stockService.getInventoryHome(selectedGroup).read()
        if (!session.isLoggedIn || session.state.credential() != credential || session.userId != userId ||
            session.activeGroupId != selectedGroup || session.activeStockId != selectedStock) throw CancellationException()
        val stock = selectAiChatStock(home, selectedGroup, selectedStock) ?: return null
        return AiChatContext(stock, home.groupId!!, userId, credential, selectedGroup, selectedStock)
    }

    internal fun hasPending(context: AiChatContext): Boolean = preferences.getBoolean(pendingKey(context), false)
    internal fun markPending(context: AiChatContext) {
        ensureCurrent(context)
        if (hasPending(context)) throw AiChatContextException("AI_SEND_UNCERTAIN")
        if (!preferences.edit().putBoolean(pendingKey(context), true).commit()) throw AiChatStorageException()
        ensureCurrent(context)
    }
    internal fun clearPending(context: AiChatContext) {
        ensureCurrent(context)
        if (!preferences.edit().remove(pendingKey(context)).commit()) throw AiChatStorageException()
        ensureCurrent(context)
    }
    private fun pendingKey(context: AiChatContext) = "pending_${context.userId}_${context.groupId}_${context.stock.id}"

    internal suspend fun send(context: AiChatContext, message: String, conversationId: String?): AiRecipeChatResponse {
        ensureCurrent(context)
        val dietaryContext = foodPreferences.promptContext()
        val choices = foodPreferences.snapshot()?.enabled.orEmpty().map { it.name }.sorted()
        ensureCurrent(context)
        val payload = composeAiChatMessage(message, dietaryContext)
        val started = System.nanoTime()
        val resumed = !conversationId.isNullOrBlank()
        diagnostic(context.stock.id, "prepared", null, null, started, resumed)
        var status: Int? = null
        try {
            val reply = RetrofitClient.aiRecipeService.chat(AiRecipeChatRequest(payload, context.stock.id, conversationId, choices)).read()
            status = 200
            ensureCurrent(context)
            if (!validAiChatReply(reply)) throw InvalidBffResponseException()
            diagnostic(context.stock.id, "confirmed", status, null, started, resumed)
            AiRecipeUpdates.confirmed()
            return reply
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) {
            val error = (failure as? BffException)?.error
            diagnostic(context.stock.id, "failed", error?.status ?: status,
                error?.code ?: if (failure is InvalidBffResponseException) "INVALID_RESPONSE" else "TRANSPORT_ERROR",
                started, resumed)
            throw failure
        }
    }

    /** Debug metadata only: never record prompts, tokens, session IDs, preferences or response bodies. */
    private fun diagnostic(stockId: Int, phase: String, status: Int?, code: String?, started: Long, resumed: Boolean) {
        if (!BuildConfig.DEBUG) return
        val safeCode = code?.takeIf { it.matches(Regex("[A-Z][A-Z0-9_]{0,63}")) } ?: "NONE"
        val duration = (System.nanoTime() - started) / 1_000_000
        runCatching { Log.i("FrigusAi", "M19 phase=$phase stockId=$stockId status=${status ?: "unknown"} " +
            "code=$safeCode elapsedMs=$duration reusedSession=$resumed") }
    }

    /** M18 can reveal persisted recipe side effects, but cannot recover the chat transcript. */
    internal suspend fun savedSuggestions(context: AiChatContext): List<RecipeResponse> {
        ensureCurrent(context)
        val recipes = RetrofitClient.recipeService.getSuggestionsByStock(context.stock.id).read()
        ensureCurrent(context)
        val nullable: List<RecipeResponse?> = recipes
        if (nullable.any { it == null || (it.recipeId ?: 0) <= 0 }) throw InvalidBffResponseException()
        return recipes
    }

    private suspend fun <T> Call<T>.read(): T = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(object : Callback<T> {
            override fun onResponse(call: Call<T>, response: Response<T>) {
                if (!continuation.isActive) return
                when {
                    !response.isSuccessful -> continuation.resumeWithException(BffException(ApiError.parse(response), ""))
                    response.code() != 200 || response.body() == null -> continuation.resumeWithException(InvalidBffResponseException())
                    else -> continuation.resume(response.body()!!)
                }
            }
            override fun onFailure(call: Call<T>, failure: Throwable) {
                if (continuation.isActive) continuation.resumeWithException(failure)
            }
        })
    }
}

internal fun composeAiChatMessage(message: String, dietaryContext: String): String {
    val trimmed = message.trim()
    val payload = if (dietaryContext.isBlank()) trimmed else "$trimmed\n\n${dietaryContext.trim()}"
    if (trimmed.isBlank() || trimmed.length > 3000 || payload.length > 4000) throw AiChatInputException()
    return payload
}
