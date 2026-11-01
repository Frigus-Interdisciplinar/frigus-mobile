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

    fun login(email: String, password: String) {
        if (result.value != AuthResult.Loading) repository.login(email, password) { result.value = it }
    }
    fun loginWithGoogle(idToken: String, photoUrl: String? = null, googleEmail: String? = null) {
        if (result.value != AuthResult.Loading) repository.loginWithGoogle(idToken) { response ->
            if (response == AuthResult.Authenticated) {
                val session = com.example.frigus_mobile.data.local.SessionManager(getApplication())
                if (!googleEmail.isNullOrBlank() && session.userEmail.equals(googleEmail, ignoreCase = true))
                    session.saveGooglePhotoUrl(photoUrl)
            }
            result.value = response
        }
    }
    fun restore() {
        if (result.value != AuthResult.Loading) repository.restore { result.value = it }
    }
    fun signOut() {
        repository.cancel()
        repository.session.clear()
        result.value = null
    }
    fun pauseForOfflineShopping() {
        repository.cancel()
        result.value = null
    }
    override fun onCleared() { repository.cancel() }
}
