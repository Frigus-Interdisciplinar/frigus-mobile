package com.example.frigus_mobile.data.repository

import com.example.frigus_mobile.data.api.AuthApiService
import com.example.frigus_mobile.data.api.RetrofitClient
import com.example.frigus_mobile.data.api.requireNoContent
import com.example.frigus_mobile.data.model.PasswordCodeRequest
import com.example.frigus_mobile.data.model.PasswordRecoveryRequest
import com.example.frigus_mobile.data.model.PasswordResetRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Public BFF operations. One attempt only; success requires HTTP 204. */
class PasswordRecoveryRepository(private val api: AuthApiService = RetrofitClient.authService) {
    suspend fun request(email: String) = withContext(Dispatchers.IO) {
        api.requestPasswordRecovery(PasswordRecoveryRequest(email)).requireNoContent()
    }
    suspend fun verify(email: String, code: String) = withContext(Dispatchers.IO) {
        api.verifyPasswordCode(PasswordCodeRequest(email, code)).requireNoContent()
    }
    suspend fun reset(email: String, code: String, password: String) = withContext(Dispatchers.IO) {
        api.resetPassword(PasswordResetRequest(email, code, password)).requireNoContent()
    }
}
