package com.example.frigus_mobile.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.frigus_mobile.R
import com.example.frigus_mobile.data.api.BffException
import com.example.frigus_mobile.data.local.SessionManager
import com.example.frigus_mobile.data.model.ApiError
import com.example.frigus_mobile.data.model.InventoryHomeResponse
import com.example.frigus_mobile.data.repository.InventoryHomeRepository
import com.example.frigus_mobile.data.repository.SubscriptionRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import retrofit2.Call

data class HomeUiState(
    val loading: Boolean = false,
    val home: InventoryHomeResponse? = null,
    val error: ApiError? = null
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val session = SessionManager(application)
    private val repository = InventoryHomeRepository()
    private val mutableState = MutableLiveData<HomeUiState>()
    val state: LiveData<HomeUiState> = mutableState
    private var pending: Call<InventoryHomeResponse>? = null
    private var bootstrap: Job? = null
    private var needsRefresh = false

    fun loadIfNeeded() {
        if (mutableState.value == null || needsRefresh) reload()
    }

    fun invalidate() {
        pending?.cancel()
        bootstrap?.cancel()
        needsRefresh = true
        mutableState.value = HomeUiState()
    }

    fun reload() {
        if (!session.isLoggedIn || mutableState.value?.loading == true) return
        needsRefresh = false
        val generation = session.state.generation
        val groupId = session.activeGroupId
        mutableState.value = HomeUiState(loading = true)
        if (groupId != null) {
            loadHome(groupId, generation)
            return
        }

        bootstrap = viewModelScope.launch {
            try {
                val subscriptions = SubscriptionRepository()
                val groups = subscriptions.groups()
                ensureSession(generation)
                val group = groups.firstOrNull() ?: try {
                    subscriptions.createGroup(getApplication<Application>().getString(R.string.home_default_group_name))
                } catch (creationFailure: Exception) {
                    if (creationFailure is CancellationException) throw creationFailure
                    // The create may have reached the server before its response was lost.
                    ensureSession(generation)
                    subscriptions.groups().firstOrNull() ?: throw creationFailure
                }
                val resolvedId = group.id?.takeIf {
                    runCatching { java.util.UUID.fromString(it) }.isSuccess
                } ?: throw IllegalStateException()
                ensureSession(generation)
                session.saveActiveGroupAndStock(resolvedId, null)
                loadHome(resolvedId, generation)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (session.isLoggedIn && session.state.generation == generation) {
                    mutableState.value = HomeUiState(error = (failure as? BffException)?.error
                        ?: ApiError(code = "NETWORK_ERROR"))
                }
            }
        }
    }

    private fun loadHome(groupId: String, generation: Long) {
        pending = repository.load(groupId) { home, error ->
            if (session.isLoggedIn && session.state.generation == generation && session.activeGroupId == groupId) {
                if (home != null) session.saveActiveGroupAndStock(groupId, home.stocks?.firstOrNull()?.id)
                mutableState.value = HomeUiState(home = home, error = error)
            }
        }
    }

    private fun ensureSession(generation: Long) {
        if (!session.isLoggedIn || session.state.generation != generation) throw CancellationException()
    }

    override fun onCleared() { pending?.cancel(); bootstrap?.cancel() }
}
