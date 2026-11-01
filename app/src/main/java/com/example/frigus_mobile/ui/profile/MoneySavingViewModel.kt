package com.example.frigus_mobile.ui.profile

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.frigus_mobile.data.api.BffException
import com.example.frigus_mobile.data.api.InvalidBffResponseException
import com.example.frigus_mobile.data.local.SessionManager
import com.example.frigus_mobile.data.model.ApiError
import com.example.frigus_mobile.data.model.MoneySavingEvent
import com.example.frigus_mobile.data.model.MoneySavingSummary
import com.example.frigus_mobile.data.repository.MoneySavingRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.IOException
import java.time.YearMonth

data class MoneySavingState(
    val summary: MoneySavingSummary? = null,
    val events: List<MoneySavingEvent> = emptyList(),
    val month: YearMonth = YearMonth.now(),
    val category: String? = null,
    val kind: String? = null,
    val page: Int = -1,
    val hasMore: Boolean = false,
    val loading: Boolean = false,
    val error: ApiError? = null
)

class MoneySavingViewModel(
    private val repository: MoneySavingRepository,
    private val session: SessionManager
) : ViewModel() {
    private val mutableState = MutableLiveData(MoneySavingState())
    val state: LiveData<MoneySavingState> = mutableState
    private var groupId: String? = null
    private var selectedGroupId: String? = null
    private var generation: Long? = null
    private var pending: Job? = null
    private var requestId = 0L

    fun loadIfNeeded() {
        if (generation != session.state.generation || selectedGroupId != session.activeGroupId ||
            (state.value?.summary == null && state.value?.loading != true)) load()
    }

    fun load() = request(false)
    fun nextPage() {
        if (state.value?.hasMore == true && state.value?.error == null) request(true)
    }

    fun changeMonth(delta: Int) {
        val current = state.value ?: return
        if (current.loading) return
        mutableState.value = current.copy(month = current.month.plusMonths(delta.toLong()), summary = null,
            events = emptyList(), page = -1, hasMore = false)
        load()
    }

    fun filter(category: String?, kind: String?) {
        val current = state.value ?: return
        if (current.loading || (category == current.category && kind == current.kind)) return
        if (category !in CATEGORIES || kind !in KINDS) return
        mutableState.value = current.copy(category = category, kind = kind, events = emptyList(), page = -1, hasMore = false)
        load()
    }

    private fun request(append: Boolean) {
        if (!session.isLoggedIn) return
        val currentGeneration = session.state.generation
        val currentGroup = session.activeGroupId
        val sameSession = generation == currentGeneration && selectedGroupId == currentGroup
        if (append && !sameSession) { load(); return }
        if (sameSession && state.value?.loading == true) return
        pending?.cancel()
        generation = currentGeneration
        selectedGroupId = currentGroup
        val snapshot = if (sameSession) state.value!! else MoneySavingState()
        val id = ++requestId
        mutableState.value = snapshot.copy(loading = true, error = null)
        fun active() = session.isLoggedIn && session.state.generation == currentGeneration &&
            session.activeGroupId == currentGroup && requestId == id
        pending = viewModelScope.launch {
            try {
                val group = if (append && sameSession) groupId ?: repository.groupId(currentGroup)
                    else repository.groupId(currentGroup)
                if (!active()) return@launch
                groupId = group
                val from = snapshot.month.atDay(1).toString()
                val to = snapshot.month.plusMonths(1).atDay(1).toString()
                if (!append) {
                    val summary = repository.summary(group, from, to)
                    if (!active()) return@launch
                    mutableState.value = mutableState.value!!.copy(summary = summary)
                }
                val page = repository.events(group, from, to, snapshot.category, snapshot.kind,
                    if (append) snapshot.page + 1 else 0)
                if (!active()) return@launch
                mutableState.value = mutableState.value!!.copy(
                    events = (if (append) snapshot.events else emptyList()) + page.content.orEmpty(),
                    page = page.number, hasMore = page.number + 1 < page.totalPages
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (active()) {
                    val failure = when (error) {
                    is BffException -> error.error
                    is InvalidBffResponseException -> ApiError(code = "INVALID_RESPONSE")
                    is IOException -> ApiError(code = "NETWORK_ERROR")
                    else -> ApiError(code = "INVALID_RESPONSE")
                    }
                    val current = mutableState.value!!
                    mutableState.value = if (failure.status == 403) current.copy(error = failure,
                        summary = null, events = emptyList(), page = -1, hasMore = false)
                        else current.copy(error = failure)
                }
            } finally {
                if (active()) mutableState.value = mutableState.value!!.copy(loading = false)
            }
        }
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(MoneySavingViewModel::class.java))
            @Suppress("UNCHECKED_CAST")
            return MoneySavingViewModel(MoneySavingRepository(), SessionManager(context.applicationContext)) as T
        }
    }

    companion object {
        val CATEGORIES = listOf(null, "HORTIFRUTI", "MEAT", "DAIRY", "GRAIN", "BEVERAGE")
        val KINDS = listOf(null, "PURCHASE", "CONSUMED", "DISCARDED")
    }
}
