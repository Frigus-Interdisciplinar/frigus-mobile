package com.example.frigus_mobile.ui.forgotpassword

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.frigus_mobile.data.model.Resource
import com.example.frigus_mobile.data.repository.AuthRepository

class ForgotPasswordViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: AuthRepository = AuthRepository(application)
    val resetResult: MutableLiveData<Resource<Void>> = MutableLiveData()

    fun getResetResult(): LiveData<Resource<Void>> = resetResult

    fun sendPasswordReset(email: String) {
        repository.sendPasswordReset(email, resetResult)
    }
}
