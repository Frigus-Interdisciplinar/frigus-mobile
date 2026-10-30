package com.example.frigus_mobile.data.api

import com.example.frigus_mobile.data.model.GoogleLoginRequest
import com.example.frigus_mobile.data.model.SubscriptionResponse
import com.example.frigus_mobile.data.model.UserLoginRequest
import com.example.frigus_mobile.data.model.UserLoginResponse
import com.example.frigus_mobile.data.model.UserRegisterRequest
import com.example.frigus_mobile.data.model.UserResponse
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface AuthApiService {
    @POST("auth/register")
    fun cadastrar(@Body request: UserRegisterRequest): Call<UserResponse>

    @POST("auth/login")
    fun login(@Body request: UserLoginRequest): Call<UserLoginResponse>

    @POST("auth/google")
    fun loginComGoogle(@Body request: GoogleLoginRequest): Call<UserLoginResponse>

    @GET("profile")
    fun getProfile(): Call<UserResponse>

    @GET("subscriptions/me")
    fun getMySubscription(): Call<SubscriptionResponse>
}
