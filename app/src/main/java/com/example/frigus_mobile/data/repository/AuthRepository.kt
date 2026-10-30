package com.example.frigus_mobile.data.repository

import android.content.Context
import androidx.lifecycle.MutableLiveData
import com.example.frigus_mobile.R
import com.example.frigus_mobile.data.api.AuthApiService
import com.example.frigus_mobile.data.api.RetrofitClient
import com.example.frigus_mobile.data.local.SessionManager
import com.example.frigus_mobile.data.local.SessionCredential
import com.example.frigus_mobile.data.local.SessionState
import com.example.frigus_mobile.data.model.*
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class AuthRepository(
    private val api: AuthApiService,
    val session: SessionState,
    private val text: (Int) -> String
) {
    constructor(context: Context) : this(RetrofitClient.authService, SessionManager(context).state,
        { context.applicationContext.getString(it) })

    private var pending: Call<*>? = null

    fun login(email: String, password: String, result: (AuthResult) -> Unit) {
        cancel()
        val generation = session.beginAuthentication()
        result(AuthResult.Loading)
        signIn(email, password, generation, result)
    }

    fun loginWithGoogle(idToken: String, result: (AuthResult) -> Unit) {
        cancel()
        val generation = session.beginAuthentication()
        result(AuthResult.Loading)
        val call = api.loginComGoogle(GoogleLoginRequest(idToken))
        pending = call
        call.enqueue(object : Callback<UserLoginResponse> {
            override fun onResponse(call: Call<UserLoginResponse>, response: Response<UserLoginResponse>) {
                if (call.isCanceled || generation != session.generation) return
                val body = response.body()
                try {
                    if (response.code() == 200 && validUser(body?.user) && session.authenticate(generation, body?.accessToken, body?.user)) {
                        result(AuthResult.Authenticated)
                    } else {
                        val err = if (response.isSuccessful) ApiError(displayMessage = text(R.string.auth_invalid_response))
                            else ApiError.parse(response)
                        result(AuthResult.Failure(err))
                    }
                } catch (_: Exception) {
                    session.clear()
                    result(AuthResult.Failure(ApiError(displayMessage = text(R.string.auth_storage_error))))
                }
            }
            override fun onFailure(call: Call<UserLoginResponse>, t: Throwable) {
                if (!call.isCanceled && generation == session.generation) result(AuthResult.Failure(networkError()))
            }
        })
    }

    private fun signIn(email: String, password: String, generation: Long, result: (AuthResult) -> Unit,
                       accountCreated: Boolean = false) {
        val call = api.login(UserLoginRequest(email, password))
        pending = call
        call.enqueue(object : Callback<UserLoginResponse> {
            override fun onResponse(call: Call<UserLoginResponse>, response: Response<UserLoginResponse>) {
                if (call.isCanceled || generation != session.generation) return
                val body = response.body()
                try {
                    if (response.code() == 200 && validUser(body?.user) && session.authenticate(generation, body?.accessToken, body?.user)) {
                        result(AuthResult.Authenticated)
                    } else {
                        finishError(if (response.isSuccessful) ApiError(displayMessage = text(R.string.auth_invalid_response))
                            else ApiError.parse(response))
                    }
                } catch (_: Exception) {
                    session.clear()
                    finishError(ApiError(displayMessage = text(R.string.auth_storage_error)))
                }
            }
            override fun onFailure(call: Call<UserLoginResponse>, t: Throwable) {
                if (!call.isCanceled && generation == session.generation) finishError(networkError())
            }
            private fun finishError(error: ApiError) {
                result(if (accountCreated) AuthResult.CreatedNeedsLogin(error) else AuthResult.Failure(error))
            }
        })
    }

    fun register(name: String, birthDate: String, email: String, password: String, result: (AuthResult) -> Unit) {
        cancel()
        val generation = session.beginAuthentication()
        result(AuthResult.Loading)
        val call = api.cadastrar(UserRegisterRequest(name, birthDate, email, password))
        pending = call
        call.enqueue(object : Callback<UserResponse> {
            override fun onResponse(call: Call<UserResponse>, response: Response<UserResponse>) {
                if (call.isCanceled || generation != session.generation) return
                if (response.code() == 201) {
                    signIn(email, password, generation, result, accountCreated = true)
                } else result(AuthResult.Failure(ApiError.parse(response)))
            }
            override fun onFailure(call: Call<UserResponse>, t: Throwable) {
                if (!call.isCanceled && generation == session.generation) result(AuthResult.Failure(
                    ApiError(code = "REGISTRATION_UNCERTAIN", displayMessage = text(R.string.auth_registration_uncertain))))
            }
        })
    }

    fun restore(result: (AuthResult) -> Unit) {
        val token = session.accessToken ?: return
        val generation = session.generation
        result(AuthResult.Loading)
        val call = api.getProfile()
        pending = call
        call.enqueue(object : Callback<UserResponse> {
            override fun onResponse(call: Call<UserResponse>, response: Response<UserResponse>) {
                if (call.isCanceled) return
                if (generation != session.generation) {
                    // The interceptor may already have cleared this very session for a 401 or 403.
                    if ((response.code() == 401 || response.code() == 403) && session.generation == generation + 1 && session.accessToken == null)
                        result(AuthResult.Failure(ApiError.parse(response)))
                    return
                }
                val user = response.body()
                try {
                    if (response.code() == 200 && user != null && validUser(user) && session.validate(generation, token, user)) {
                        result(AuthResult.Authenticated)
                    } else {
                        if (response.code() == 401 || response.code() == 403) {
                            session.invalidate(SessionCredential(token, generation))
                            session.clear()
                        }
                        result(AuthResult.Failure(if (response.isSuccessful)
                            ApiError(displayMessage = text(R.string.auth_invalid_response)) else ApiError.parse(response)))
                    }
                } catch (_: Exception) {
                    session.clear()
                    result(AuthResult.Failure(ApiError(displayMessage = text(R.string.auth_storage_error))))
                }
            }
            override fun onFailure(call: Call<UserResponse>, t: Throwable) {
                if (!call.isCanceled && generation == session.generation) result(AuthResult.Failure(networkError()))
            }
        })
    }

    private fun validUser(user: UserResponse?): Boolean =
        user != null && !user.id.isNullOrBlank()

    private fun networkError() = ApiError(displayMessage = text(R.string.auth_network_error))
    fun cancel() { pending?.cancel(); pending = null }

    fun sendPasswordReset(email: String, liveData: MutableLiveData<Resource<Void>>) {
        liveData.value = Resource.error(text(R.string.auth_password_reset_unavailable))
    }
}
