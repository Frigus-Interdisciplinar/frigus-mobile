package com.example.frigus_mobile.ui.login

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.frigus_mobile.data.model.Resource
import com.example.frigus_mobile.data.model.UserLoginResponse
import com.example.frigus_mobile.data.repository.AuthRepository

class LoginViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: AuthRepository = AuthRepository(application)
    val loginResult: MutableLiveData<Resource<UserLoginResponse>> = MutableLiveData()

    fun getLoginResult(): LiveData<Resource<UserLoginResponse>> = loginResult

    fun login(email: String, password: String) {
        repository.login(email, password, loginResult)
    }
}
