package com.example.frigus_mobile.ui.login

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import com.example.frigus_mobile.data.model.AuthResult
import com.example.frigus_mobile.data.repository.AuthRepository

class LoginViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = AuthRepository(application)
    val result = MutableLiveData<AuthResult?>()
    val hasStoredSession: Boolean get() = repository.session.accessToken != null
    val isAuthenticated: Boolean get() = repository.session.validated
    init {
        if (isAuthenticated) result.value = AuthResult.Authenticated
    }
    fun login(email: String, password: String) {
        if (result.value != AuthResult.Loading) repository.login(email, password) { result.value = it }
    }
    fun loginWithGoogle(idToken: String) {
        if (result.value != AuthResult.Loading) repository.loginWithGoogle(idToken) { result.value = it }
    }
    fun restore() {
        if (result.value != AuthResult.Loading) repository.restore { result.value = it }
    }
    fun signOut() {
        repository.cancel()
        repository.session.clear()
        result.value = null
    }
    override fun onCleared() { repository.cancel() }
}
