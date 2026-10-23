package com.example.frigus_mobile.data.api

import com.example.frigus_mobile.data.model.UserLoginRequest
import com.example.frigus_mobile.data.model.UserLoginResponse
import com.example.frigus_mobile.data.model.UserRegisterRequest
import com.example.frigus_mobile.data.model.UserResponse
import com.example.frigus_mobile.data.model.SubscriptionResponse
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface AuthApiService {
    @POST("mobile/user/register")
    fun cadastrar(@Body request: UserRegisterRequest): Call<UserResponse>

    @POST("mobile/user/login")
    fun login(@Body request: UserLoginRequest): Call<UserLoginResponse>

    @GET("mobile/user/profile")
    fun getProfile(): Call<UserResponse>

    // Kept for source compatibility with G11 only. MobileHttp blocks this unpublished route.
    @GET("subscriptions/me")
    fun getMySubscription(): Call<SubscriptionResponse>
}
