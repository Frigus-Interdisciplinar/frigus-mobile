package com.example.frigus_mobile.ui.profile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.frigus_mobile.data.local.SessionManager
import com.example.frigus_mobile.data.model.ApiError
import com.example.frigus_mobile.data.model.UserResponse
import com.example.frigus_mobile.data.repository.ProfileRepository
import retrofit2.Call

data class ProfileUiState(
    val loading: Boolean = false,
    val user: UserResponse? = null,
    val error: ApiError? = null
)

class ProfileViewModel(application: Application) : AndroidViewModel(application) {
    private val session = SessionManager(application)
    private val repository = ProfileRepository()
    private val mutableState = MutableLiveData<ProfileUiState>()
    val state: LiveData<ProfileUiState> = mutableState
    private var pending: Call<UserResponse>? = null

    fun loadIfNeeded() {
        if (mutableState.value == null) reload()
    }

    fun reload() {
        if (!session.isLoggedIn || mutableState.value?.loading == true) return
        pending?.cancel()
        val credential = session.state.credential() ?: return
        mutableState.value = ProfileUiState(loading = true)
        pending = repository.load(session.state) { user, error ->
            if (session.isLoggedIn && session.state.credential() == credential) {
                mutableState.value = ProfileUiState(user = user, error = error)
            }
        }
    }

    override fun onCleared() {
        pending?.cancel()
    }
}
