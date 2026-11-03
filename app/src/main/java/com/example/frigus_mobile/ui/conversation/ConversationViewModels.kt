package com.example.frigus_mobile.ui.conversation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.frigus_mobile.R
import com.example.frigus_mobile.data.api.BffException
import com.example.frigus_mobile.data.local.SessionManager
import com.example.frigus_mobile.data.model.*
import com.example.frigus_mobile.data.repository.ConversationRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

abstract class HumanConversationViewModel(application: Application) : AndroidViewModel(application) {
    protected val session = SessionManager(application)
    protected val owner = session.userId
    private val credential = session.state.credential()
    protected val repository = ConversationRepository(application)
    protected fun active() = session.isLoggedIn && session.userId == owner && session.state.credential() == credential
    fun hasCurrentSession(): Boolean = active()
    protected fun guard() { if (!active()) throw CancellationException() }
    protected fun error(failure: Exception): ApiError = if (failure is BffException) failure.error else ApiError(code = "NETWORK_ERROR")
    protected fun text(id: Int) = getApplication<Application>().getString(id)
    protected fun row(conversation: HumanConversation): ConversationItem {
        val members = conversation.participants.orEmpty().filter { it.leftAt == null }
        val group = conversation.conversationType == "GROUP"
        val title = if (group) conversation.name?.takeIf(String::isNotBlank)
            ?: conversation.groupName?.takeIf(String::isNotBlank) else members.firstOrNull { it.userId != owner }?.name?.takeIf(String::isNotBlank)
            ?: conversation.name?.takeIf(String::isNotBlank)
        return ConversationItem(conversation.id!!, title ?: text(R.string.conversation_generic_title), group,
            members.size, members.mapNotNull { it.name?.takeIf(String::isNotBlank) },
            conversation.latestMessage?.let { body(it) } ?: text(R.string.conversation_no_messages),
            time(conversation.latestMessage?.createdAt))
    }
    protected fun message(item: HumanMessage) = ConversationMessage(item.id.toString(),
        item.senderName?.takeIf(String::isNotBlank) ?: text(R.string.conversation_generic_participant),
        body(item), time(item.createdAt), item.senderId == owner)
    private fun body(item: HumanMessage) = if (item.messageType == "TEXT") item.content.orEmpty()
        else text(R.string.conversation_other_message)
    private fun time(value: String?): String = runCatching {
        DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault()).format(Instant.parse(value))
    }.getOrDefault("")
}

data class ConversationsState(val items: List<ConversationItem> = emptyList(), val loading: Boolean = false,
    val error: ApiError? = null, val loaded: Boolean = false)
class ConversationsViewModel(application: Application) : HumanConversationViewModel(application) {
    private val mutableState = MutableLiveData(ConversationsState())
    val state: LiveData<ConversationsState> = mutableState
    var query: String = ""
        private set
    fun setQuery(value: String) { query = value }
    fun load(explicit: Boolean = false) {
        val old = mutableState.value!!
        if (!active() || old.loading || (!explicit && old.loaded)) return
        mutableState.value = old.copy(loading = true, error = null)
        viewModelScope.launch {
            try {
                val entries = repository.conversations(::guard)
                guard()
                mutableState.value = ConversationsState(entries.map(::row), loaded = true)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { if (active()) mutableState.value = mutableState.value!!.copy(error = error(failure)) }
            finally { if (active()) mutableState.value = mutableState.value!!.copy(loading = false) }
        }
    }
}

data class ConversationState(val conversation: ConversationItem? = null,
    val messages: List<ConversationMessage> = emptyList(), val loading: Boolean = false,
    val sending: Boolean = false, val uncertain: Boolean = false, val error: ApiError? = null,
    val page: Int = -1, val totalPages: Int = 0, val sentRevision: Long = 0L,
    val readConfirmed: Boolean = false)
class ConversationViewModel(application: Application) : HumanConversationViewModel(application) {
    private val mutableState = MutableLiveData(ConversationState())
    val state: LiveData<ConversationState> = mutableState
    private var id: String? = null
    private var latestReceived: Int? = null
    private var sentRevision = 0L
    private val composer = ConversationComposer()
    val draft: String get() = composer.text
    fun setDraft(value: String) { composer.edit(value) }
    fun open(conversationId: String) {
        if (id != null) return
        id = conversationId
        val pending = runCatching { repository.pending(owner.orEmpty(), conversationId, "send") ||
            repository.pending(owner.orEmpty(), conversationId, "read") }.getOrDefault(true)
        mutableState.value = mutableState.value!!.copy(uncertain = pending)
        load()
    }
    fun load(explicit: Boolean = false, more: Boolean = false) {
        val conversationId = id ?: return
        val old = mutableState.value!!
        if (!active() || old.loading || old.sending || (more && (old.page < 0 || old.page + 1 >= old.totalPages))) return
        if (!explicit && !more && old.page >= 0) return
        mutableState.value = old.copy(loading = true, error = null)
        viewModelScope.launch {
            try {
                val info = if (more) null else repository.conversation(conversationId, ::guard)
                val page = repository.messages(conversationId, if (more) old.page + 1 else 0, ::guard)
                guard()
                if (explicit) {
                    repository.setPending(owner!!, conversationId, "send", false)
                    repository.setPending(owner!!, conversationId, "read", false)
                }
                val raw = page.content
                val receivedOnPage = raw.filter { it.senderId != owner }.maxByOrNull { it.id ?: 0 }?.id
                latestReceived = if (more) listOfNotNull(latestReceived, receivedOnPage).maxOrNull() else receivedOnPage
                val messages = ((if (more) old.messages else emptyList()) + raw.map(::message))
                    .distinctBy { it.id }.sortedBy { it.id.toIntOrNull() ?: 0 }
                mutableState.value = mutableState.value!!.copy(conversation = info?.let(::row) ?: old.conversation,
                    messages = messages, page = page.number, totalPages = page.totalPages,
                    uncertain = if (explicit) false else old.uncertain, readConfirmed = false)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { if (active()) mutableState.value = mutableState.value!!.copy(error = error(failure)) }
            finally { if (active()) mutableState.value = mutableState.value!!.copy(loading = false) }
        }
    }
    fun send() {
        val conversationId = id ?: return
        val old = mutableState.value!!
        val submitted = composer.snapshot()
        val content = submitted.text.trim()
        if (!active() || old.loading || old.sending || old.uncertain || content.isBlank()) return
        mutate("send") {
            val posted = repository.send(conversationId, content, ::guard)
            guard()
            if (posted.senderId != owner) throw BffException(ApiError(code = "INVALID_RESPONSE"), "")
            composer.confirm(submitted)
            mutableState.value = mutableState.value!!.copy(messages = (mutableState.value!!.messages + message(posted)).distinctBy { it.id },
                sentRevision = ++sentRevision)
        }
    }
    fun markRead() {
        val messageId = latestReceived ?: return
        val old = mutableState.value!!
        if (!active() || old.loading || old.sending || old.uncertain || old.readConfirmed) return
        mutate("read") {
            repository.read(id!!, messageId, ::guard)
            guard()
            mutableState.value = mutableState.value!!.copy(readConfirmed = true)
        }
    }
    fun canMarkRead() = latestReceived != null
    private fun mutate(action: String, command: suspend () -> Unit) {
        val conversationId = id ?: return
        try { repository.setPending(owner!!, conversationId, action, true) }
        catch (failure: Exception) { mutableState.value = mutableState.value!!.copy(error = error(failure)); return }
        mutableState.value = mutableState.value!!.copy(sending = true, error = null)
        viewModelScope.launch {
            try {
                command(); guard()
                repository.setPending(owner!!, conversationId, action, false)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                if (active()) {
                    val apiError = error(failure)
                    val uncertain = conversationMutationUncertain(apiError.status)
                    if (!uncertain) runCatching { repository.setPending(owner!!, conversationId, action, false) }
                    mutableState.value = mutableState.value!!.copy(error = apiError, uncertain = uncertain)
                }
            } finally { if (active()) mutableState.value = mutableState.value!!.copy(sending = false) }
        }
    }
}
