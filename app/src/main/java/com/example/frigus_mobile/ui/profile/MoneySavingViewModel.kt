package com.example.frigus_mobile.ui.profile

import androidx.lifecycle.MutableLiveData
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.frigus_mobile.data.model.MoneySavingEvent
import com.example.frigus_mobile.data.model.MoneySavingSummary
import com.example.frigus_mobile.data.repository.MoneySavingRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class MoneySavingState(
    val summary: MoneySavingSummary? = null,
    val events: List<MoneySavingEvent> = emptyList(),
    val month: Calendar = Calendar.getInstance(),
    val category: String? = null,
    val kind: String? = null,
    val page: Int = -1,
    val hasMore: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null
)

class MoneySavingViewModel(private val repository: MoneySavingRepository) : ViewModel() {
    val state = MutableLiveData(MoneySavingState())
    private var groupId: String? = null
    private var busy = false

    fun load() {
        if (busy) return
        busy = true
        state.value = state.value!!.copy(loading = true, error = null)
        viewModelScope.launch {
            try {
                val group = repository.groupId().also { groupId = it }
                val (from, to) = period()
                val summary = repository.summary(group, from, to)
                val page = repository.events(group, from, to, state.value?.category, state.value?.kind, 0)
                require(page.content != null && page.number == 0)
                state.value = state.value!!.copy(
                    summary = summary, events = page.content, page = 0,
                    hasMore = page.totalPages > 1
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                state.value = state.value!!.copy(error = e.message ?: "Não foi possível carregar as métricas.")
            } finally {
                busy = false
                state.value = state.value!!.copy(loading = false)
            }
        }
    }

    fun nextPage() {
        val current = state.value ?: return
        val group = groupId ?: return
        if (busy || !current.hasMore) return
        busy = true
        state.value = current.copy(loading = true, error = null)
        viewModelScope.launch {
            try {
                val (from, to) = period()
                val result = repository.events(group, from, to, current.category, current.kind, current.page + 1)
                require(result.content != null && result.number == current.page + 1)
                state.value = state.value!!.copy(
                    events = current.events + result.content, page = result.number,
                    hasMore = result.number + 1 < result.totalPages
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                state.value = state.value!!.copy(error = e.message ?: "Não foi possível carregar mais eventos.")
            } finally {
                busy = false
                state.value = state.value!!.copy(loading = false)
            }
        }
    }

    fun changeMonth(delta: Int) {
        if (busy) return
        val month = (state.value?.month?.clone() as? Calendar) ?: Calendar.getInstance()
        month.add(Calendar.MONTH, delta)
        state.value = state.value!!.copy(month = month, summary = null, events = emptyList(), page = -1, hasMore = false)
        load()
    }

    fun filter(category: String?, kind: String?) {
        if (busy) return
        state.value = state.value!!.copy(category = category, kind = kind, events = emptyList(), page = -1, hasMore = false)
        load()
    }

    private fun period(): Pair<String, String> {
        val start = (state.value!!.month.clone() as Calendar).apply {
            set(Calendar.DAY_OF_MONTH, 1)
        }
        val end = (start.clone() as Calendar).apply { add(Calendar.MONTH, 1) }
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return format.format(start.time) to format.format(end.time)
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(MoneySavingViewModel::class.java))
            @Suppress("UNCHECKED_CAST")
            return MoneySavingViewModel(MoneySavingRepository(com.example.frigus_mobile.data.local.SessionManager(context))) as T
        }
    }
}
