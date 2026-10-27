package com.example.frigus_mobile.data.model

import com.google.gson.Gson
import retrofit2.Response

data class ApiFieldError(val fieldName: String? = null, val errorMessage: String? = null, val displayMessage: String? = null)
data class ApiError(
    val status: Int? = null,
    val code: String? = null,
    val message: String? = null,
    val displayMessage: String? = null,
    val fields: List<ApiFieldError>? = null,
    val timestamp: String? = null
) {
    fun userMessage(fallback: String): String = displayMessage?.takeIf { it.isNotBlank() } ?: fallback

    companion object {
        fun parse(response: Response<*>): ApiError {
            val parsed = try {
                response.errorBody()?.string()?.let { Gson().fromJson(it, ApiError::class.java) }
            } catch (_: Exception) { null }
            return (parsed ?: ApiError()).copy(status = response.code())
        }
    }
}

sealed class AuthResult {
    data object Loading : AuthResult()
    data object Authenticated : AuthResult()
    data class CreatedNeedsLogin(val error: ApiError) : AuthResult()
    data class Failure(val error: ApiError) : AuthResult()
}
