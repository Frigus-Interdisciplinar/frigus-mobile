package com.example.frigus_mobile.data.api

import com.example.frigus_mobile.data.model.UserResponse
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path

data class ProfilePatch(val name: String, val birthDate: String)
data class ChangePasswordBody(val oldPassword: String, val newPassword: String)
data class ConnectedSession(
    val id: String? = null,
    val deviceName: String? = null,
    val createdAt: String? = null,
    val lastActiveAt: String? = null,
    val current: Boolean = false,
    val location: String? = null
)
data class EmailVerificationState(val email: String? = null, val verified: Boolean = false)
data class EmailCodeBody(val code: String)

interface AccountSecurityApiService {
    @GET("mobile/core/profile")
    fun profile(): Call<UserResponse>

    @PATCH("mobile/core/profile")
    fun updateProfile(@Body patch: ProfilePatch): Call<UserResponse>

    @PATCH("mobile/core/profile/password")
    fun changePassword(@Body body: ChangePasswordBody): Call<Void>

    @GET("mobile/core/profile/sessions")
    fun sessions(): Call<List<ConnectedSession>>

    @DELETE("mobile/core/profile/sessions/{sessionId}")
    fun revoke(@Path("sessionId") id: String): Call<Void>

    @GET("mobile/core/profile/email-verification")
    fun emailStatus(): Call<EmailVerificationState>

    @POST("mobile/core/profile/email-verification/request")
    fun requestEmailCode(): Call<Void>

    @POST("mobile/core/profile/email-verification/verify")
    fun verifyEmail(@Body body: EmailCodeBody): Call<EmailVerificationState>
}
