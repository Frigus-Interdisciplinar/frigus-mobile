package com.example.frigus_mobile.ui.notifications

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.frigus_mobile.data.model.NotificationResponse
import com.example.frigus_mobile.data.repository.NotificationRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

data class NotificationUiState(
    val items: List<NotificationResponse> = emptyList(),
    val page: Int = -1,
    val hasMore: Boolean = false,
    val loading: Boolean = false,
    val error: Boolean = false
)

class NotificacoesViewModel(private val repository: NotificationRepository) : ViewModel() {
    val state = MutableLiveData(NotificationUiState())
    private var inFlight = false

    fun loadFirst() = runRequest {
        val result = repository.getNotifications(0, PAGE_SIZE)
        state.value = state.value!!.copy(
            items = result.content,
            page = result.number,
            hasMore = result.number + 1 < result.totalPages
        )
    }

    fun loadNext() {
        val current = state.value ?: return
        if (!current.hasMore) return
        runRequest {
            val result = repository.getNotifications(current.page + 1, PAGE_SIZE)
            state.value = state.value!!.copy(
                items = current.items + result.content,
                page = result.number,
                hasMore = result.number + 1 < result.totalPages
            )
        }
    }

    fun markAsRead(item: NotificationResponse) {
        val id = item.id ?: return
        if (item.readAt != null) return
        runRequest {
            val updated = repository.markAsRead(id)
            state.value = state.value!!.copy(
                items = state.value!!.items.map { if (it.id == id) updated else it }
            )
        }
    }

    fun clearAll() {
        if (state.value?.items.isNullOrEmpty()) return
        runRequest {
            repository.clearAll()
            state.value = state.value!!.copy(items = emptyList(), page = -1, hasMore = false)
        }
    }

    private fun runRequest(action: suspend () -> Unit) {
        if (inFlight) return
        inFlight = true
        state.value = state.value!!.copy(loading = true, error = false)
        viewModelScope.launch {
            try {
                action()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                state.value = state.value!!.copy(error = true)
            } finally {
                inFlight = false
                state.value = state.value!!.copy(loading = false)
            }
        }
    }

    class Factory(private val repository: NotificationRepository = NotificationRepository()) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(NotificacoesViewModel::class.java))
            @Suppress("UNCHECKED_CAST")
            return NotificacoesViewModel(repository) as T
        }
    }

    companion object {
        private const val PAGE_SIZE = 20
    }
}
