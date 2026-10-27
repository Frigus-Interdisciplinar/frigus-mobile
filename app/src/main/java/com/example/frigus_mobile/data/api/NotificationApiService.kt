package com.example.frigus_mobile.data.api

import com.example.frigus_mobile.data.model.NotificationResponse
import com.example.frigus_mobile.data.model.PageResponse
import retrofit2.Call
import retrofit2.Response
import retrofit2.http.DELETE
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.PATCH
import retrofit2.http.Path
import retrofit2.http.POST
import retrofit2.http.Query

interface NotificationApiService {

    @GET("mobile/notifications")
    fun getNotifications(
        @Query("page") page: Int,
        @Query("size") size: Int
    ): Call<PageResponse<NotificationResponse>>

    @PATCH("mobile/notifications/{id}/read")
    fun markAsRead(
        @Path("id") id: String
    ): Call<NotificationResponse>

    @DELETE("mobile/notifications")
    fun clearAll(): Call<Void>

    @POST("mobile/push-tokens")
    suspend fun registerPushToken(@Body request: PushTokenRegistration): Response<Void>

    @HTTP(method = "DELETE", path = "mobile/push-tokens", hasBody = true)
    suspend fun unregisterPushToken(@Body request: PushTokenRemoval): Response<Void>
}

data class PushTokenRegistration(val token: String, val platform: String = "ANDROID")
data class PushTokenRemoval(val token: String)
