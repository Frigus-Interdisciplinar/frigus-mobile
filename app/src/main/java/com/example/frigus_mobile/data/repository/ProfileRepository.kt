package com.example.frigus_mobile.data.repository

import com.example.frigus_mobile.data.api.RetrofitClient
import com.example.frigus_mobile.data.local.SessionState
import com.example.frigus_mobile.data.model.ApiError
import com.example.frigus_mobile.data.model.UserResponse
import com.google.gson.JsonParseException
import com.google.gson.stream.MalformedJsonException
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.IOException
import java.util.UUID

/** M03 refreshes only the credential generation that initiated the request. */
class ProfileRepository {
    fun load(session: SessionState, result: (UserResponse?, ApiError?) -> Unit): Call<UserResponse>? {
        if (!session.validated) return null
        val credential = session.credential() ?: return null
        val call = RetrofitClient.authService.getProfile()
        call.enqueue(object : Callback<UserResponse> {
            override fun onResponse(call: Call<UserResponse>, response: Response<UserResponse>) {
                if (call.isCanceled || session.credential() != credential || !session.validated) return
                val user = response.body()
                when {
                    response.code() != 200 -> result(null, ApiError.parse(response))
                    !valid(user) -> result(null, ApiError(code = "INVALID_RESPONSE"))
                    else -> {
                        val saved = try {
                            session.validate(credential.generation, credential.token, user!!)
                        } catch (_: Exception) {
                            result(null, ApiError(code = "PROFILE_SAVE_FAILED"))
                            return
                        }
                        if (saved) result(user, null)
                    }
                }
            }

            override fun onFailure(call: Call<UserResponse>, failure: Throwable) {
                if (call.isCanceled || session.credential() != credential || !session.validated) return
                val code = when (failure) {
                    is JsonParseException, is MalformedJsonException -> "INVALID_RESPONSE"
                    is IOException -> "NETWORK_ERROR"
                    else -> "INVALID_RESPONSE"
                }
                result(null, ApiError(code = code))
            }
        })
        return call
    }

    private fun valid(user: UserResponse?): Boolean = try {
        val id = user?.id
        id != null && UUID.fromString(id).toString().equals(id, ignoreCase = true)
    } catch (_: IllegalArgumentException) {
        false
    }
}
