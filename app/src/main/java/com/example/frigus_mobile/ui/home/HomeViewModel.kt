package com.example.frigus_mobile.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.frigus_mobile.data.local.SessionManager
import com.example.frigus_mobile.data.model.ApiError
import com.example.frigus_mobile.data.model.InventoryHomeResponse
import com.example.frigus_mobile.data.repository.InventoryHomeRepository
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
    private var needsRefresh = false

    fun loadIfNeeded() {
        if (mutableState.value == null || needsRefresh) reload()
    }

    fun invalidate() {
        pending?.cancel()
        needsRefresh = true
        mutableState.value = HomeUiState()
    }

    fun reload() {
        if (!session.isLoggedIn || mutableState.value?.loading == true) return
        needsRefresh = false
        val generation = session.state.generation
        mutableState.value = HomeUiState(loading = true)
        pending = repository.load { home, error ->
            if (session.isLoggedIn && session.state.generation == generation) {
                mutableState.value = HomeUiState(home = home, error = error)
            }
        }
    }

    override fun onCleared() { pending?.cancel() }
}
