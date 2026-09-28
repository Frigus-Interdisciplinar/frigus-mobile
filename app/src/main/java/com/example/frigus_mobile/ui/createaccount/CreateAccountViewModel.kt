package com.example.frigus_mobile.ui.createaccount

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.frigus_mobile.data.model.Resource
import com.example.frigus_mobile.data.model.UserResponse
import com.example.frigus_mobile.data.repository.AuthRepository

class CreateAccountViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: AuthRepository = AuthRepository(application)
    val registerResult: MutableLiveData<Resource<UserResponse>> = MutableLiveData()

    fun getRegisterResult(): LiveData<Resource<UserResponse>> = registerResult

    fun register(name: String, birthDate: String, email: String, password: String) {
        repository.register(name, birthDate, email, password, registerResult)
    }
}
