package com.example.frigus_mobile.ui.ai

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.frigus_mobile.R
import com.example.frigus_mobile.data.api.BffException
import com.example.frigus_mobile.data.api.InvalidBffResponseException
import com.example.frigus_mobile.data.local.SessionManager
import com.example.frigus_mobile.data.model.*
import com.example.frigus_mobile.data.repository.*
import com.google.gson.JsonParseException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

data class AiChatState(
    val messages: List<ChatUiMessage> = emptyList(), val draft: String = "",
    val loadingContext: Boolean = false, val contextReady: Boolean = false,
    val stockName: String? = null, val sending: Boolean = false,
    val checkingSaved: Boolean = false, val saved: List<RecipeResponse> = emptyList(),
    val savedVersion: Int = 0, val error: ApiError? = null, val errorVersion: Int = 0,
    val uncertain: Boolean = false, val reviewedUncertain: Boolean = false
)

class AiChatViewModel(application: Application) : AndroidViewModel(application) {
    private val session = SessionManager(application)
    private val repository = AiChatRepository(application)
    private val mutableState = MutableLiveData(AiChatState())
    val state: LiveData<AiChatState> = mutableState
    private var context: AiChatContext? = null
    private var contextStamp: Triple<com.example.frigus_mobile.data.local.SessionCredential?, String?, Int?>? = null
    private var contextJob: Job? = null
    private var sendJob: Job? = null
    private var savedJob: Job? = null
    private var conversationId: String? = null

    private fun stamp() = Triple(session.state.credential(), session.activeGroupId, session.activeStockId)
    private fun update(action: (AiChatState) -> AiChatState) { mutableState.value = action(mutableState.value ?: AiChatState()) }
    private fun greeting(): ChatUiMessage {
        val firstName = session.userName?.trim()?.split(Regex("\\s+"))?.firstOrNull()?.takeIf(String::isNotBlank)
        val text = if (firstName == null) getApplication<Application>().getString(R.string.ai_chat_greeting)
            else getApplication<Application>().getString(R.string.ai_chat_named_greeting, firstName)
        return ChatUiMessage(text = text, isUser = false)
    }

    fun loadIfNeeded() {
        if (contextStamp != stamp() || mutableState.value?.messages?.isEmpty() == true) reloadContext()
    }

    fun reloadContext() {
        if (!session.isLoggedIn) return
        val snapshot = stamp()
        if ((mutableState.value?.sending == true || mutableState.value?.checkingSaved == true) && contextStamp == snapshot) return
        if (mutableState.value?.loadingContext == true && contextStamp == snapshot) return
        contextJob?.cancel()
        if (contextStamp != snapshot) {
            sendJob?.cancel(); savedJob?.cancel()
            context = null; conversationId = null
            val previous = mutableState.value ?: AiChatState()
            mutableState.value = AiChatState(messages = listOf(greeting()), errorVersion = previous.errorVersion,
                savedVersion = previous.savedVersion)
        }
        contextStamp = snapshot
        update { it.copy(loadingContext = true, error = null) }
        contextJob = viewModelScope.launch {
            try {
                val resolved = repository.resolveContext()
                if (!session.isLoggedIn || stamp() != snapshot) return@launch
                context = resolved
                val pending = resolved?.let(repository::hasPending) == true
                update { it.copy(loadingContext = false, contextReady = resolved != null,
                    stockName = resolved?.stock?.name, uncertain = pending, reviewedUncertain = false) }
                if (resolved == null) fail(ApiError(code = "AI_NO_STOCK"))
                else if (pending) fail(ApiError(code = "AI_SEND_UNCERTAIN"))
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                if (session.isLoggedIn && stamp() == snapshot) {
                    context = null
                    update { it.copy(loadingContext = false, contextReady = false) }
                    fail(chatError(failure))
                }
            }
        }
    }

    fun setDraft(value: String) = update { it.copy(draft = value.take(3000)) }

    /** User action only; neither a read nor an error dialog invokes this method again. */
    fun send(text: String, fromDraft: Boolean = true): Boolean {
        val current = mutableState.value ?: return false
        val scope = context ?: return false
        val message = text.trim()
        if (!repository.isCurrent(scope) || current.sending || current.loadingContext || current.checkingSaved ||
            current.uncertain || message.isBlank() || message.length > 3000) return false
        try { repository.markPending(scope) }
        catch (cancelled: CancellationException) { return false }
        catch (failure: Exception) {
            val error = chatError(failure)
            if (error.code == "AI_SEND_UNCERTAIN") update { it.copy(uncertain = true, reviewedUncertain = false) }
            fail(error)
            return false
        }
        val user = ChatUiMessage(text = message, isUser = true, delivery = AiMessageDelivery.SENDING)
        val thinking = ChatUiMessage(text = getApplication<Application>().getString(R.string.ai_chat_thinking),
            isUser = false, isLoading = true)
        update { it.copy(messages = (it.messages.takeLast(198) + user + thinking), draft = if (fromDraft) "" else it.draft, sending = true,
            error = null, reviewedUncertain = false) }
        sendJob = viewModelScope.launch {
            try {
                val reply = repository.send(scope, message, conversationId)
                repository.ensureCurrent(scope)
                conversationId = reply.sessionId
                var storageError: ApiError? = null
                try { repository.clearPending(scope) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { storageError = ApiError(code = "AI_STATE_STORAGE_ERROR") }
                update { it.copy(messages = it.messages.map { row -> when (row.id) {
                    user.id -> row.copy(delivery = AiMessageDelivery.CONFIRMED)
                    thinking.id -> row.copy(text = reply.chatMessage.orEmpty(), reply = reply, isLoading = false)
                    else -> row
                } }, sending = false, uncertain = storageError != null) }
                storageError?.let(::fail)
            } catch (cancelled: CancellationException) {
                // Cancellation cannot establish whether the server processed the message; the marker remains.
                throw cancelled
            } catch (failure: Exception) {
                if (repository.isCurrent(scope)) {
                    val error = chatError(failure)
                    var uncertain = aiChatMutationUncertain(error)
                    if (!uncertain) {
                        try { repository.clearPending(scope) }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { uncertain = true }
                    }
                    update { it.copy(messages = it.messages.filterNot { row -> row.id == thinking.id }.map { row ->
                        if (row.id == user.id) row.copy(delivery = if (uncertain) AiMessageDelivery.UNCERTAIN else AiMessageDelivery.REJECTED) else row
                    }, sending = false, uncertain = uncertain) }
                    fail(error)
                }
            }
        }
        return true
    }

    fun loadSavedSuggestions() {
        val scope = context ?: return
        val current = mutableState.value ?: return
        if (!repository.isCurrent(scope) || current.sending || current.checkingSaved) return
        update { it.copy(checkingSaved = true, error = null) }
        savedJob = viewModelScope.launch {
            try {
                val recipes = repository.savedSuggestions(scope)
                repository.ensureCurrent(scope)
                update { it.copy(checkingSaved = false, saved = recipes, savedVersion = it.savedVersion + 1,
                    reviewedUncertain = it.uncertain) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                if (repository.isCurrent(scope)) {
                    update { it.copy(checkingSaved = false) }
                    fail(chatError(failure))
                }
            }
        }
    }

    /** Explicitly abandon the unresolved thread only after a user-requested M18 read. Never resend. */
    fun startNewConversationAfterReview() {
        val scope = context ?: return
        val current = mutableState.value ?: return
        if (!repository.isCurrent(scope) || !current.uncertain || !current.reviewedUncertain ||
            current.sending || current.checkingSaved) return
        try {
            repository.clearPending(scope)
            conversationId = null
            update { it.copy(messages = listOf(greeting()), uncertain = false, reviewedUncertain = false,
                error = null, saved = emptyList()) }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { fail(chatError(failure)) }
    }

    private fun fail(error: ApiError) = update { it.copy(error = error, errorVersion = it.errorVersion + 1) }
    private fun chatError(failure: Exception): ApiError = when (failure) {
        is BffException -> failure.error
        is AiChatContextException -> ApiError(code = failure.code)
        is AiChatStorageException -> ApiError(code = "AI_STATE_STORAGE_ERROR")
        is AiChatInputException -> ApiError(code = "AI_INPUT_TOO_LONG")
        is InvalidBffResponseException, is JsonParseException -> ApiError(code = "INVALID_RESPONSE")
        else -> ApiError(code = "NETWORK_ERROR")
    }
}

internal fun aiChatMutationUncertain(error: ApiError): Boolean =
    error.code !in setOf("AI_INPUT_TOO_LONG", "AI_STATE_STORAGE_ERROR") &&
        (error.status == null || error.status == 408 || (error.status ?: 0) >= 500)
