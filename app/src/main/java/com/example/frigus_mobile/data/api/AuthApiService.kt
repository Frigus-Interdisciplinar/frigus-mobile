package com.example.frigus_mobile.data.api

import com.example.frigus_mobile.data.model.UserLoginRequest
import com.example.frigus_mobile.data.model.UserLoginResponse
import com.example.frigus_mobile.data.model.UserRegisterRequest
import com.example.frigus_mobile.data.model.UserResponse
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

interface AuthApiService {
    @POST("auth/register")
    fun cadastrar(@Body request: UserRegisterRequest): Call<UserResponse>

    @POST("auth/login")
    fun login(@Body request: UserLoginRequest): Call<UserLoginResponse>
}
