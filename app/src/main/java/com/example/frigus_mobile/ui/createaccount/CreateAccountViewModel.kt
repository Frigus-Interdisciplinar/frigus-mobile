package com.example.frigus_mobile.ui.createaccount

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import com.example.frigus_mobile.data.model.AuthResult
import com.example.frigus_mobile.data.repository.AuthRepository

class CreateAccountViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = AuthRepository(application)
    val result = MutableLiveData<AuthResult>()
    fun register(name: String, birthDate: String, email: String, password: String) {
        if (result.value == AuthResult.Loading || result.value is AuthResult.CreatedNeedsLogin ||
            (result.value as? AuthResult.Failure)?.error?.code == "REGISTRATION_UNCERTAIN") return
        repository.register(name, birthDate, email, password) { result.value = it }
    }
    override fun onCleared() { repository.cancel() }
}
