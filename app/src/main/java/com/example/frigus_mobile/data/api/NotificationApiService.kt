package com.example.frigus_mobile.data.api

import com.example.frigus_mobile.data.model.NotificationResponse
import com.example.frigus_mobile.data.model.PageResponse
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.Path

interface NotificationApiService {

    @GET("notifications")
    fun getNotifications(): Call<PageResponse<NotificationResponse>>

    @PATCH("notifications/{id}/read")
    fun markAsRead(
        @Path("id") id: String
    ): Call<NotificationResponse>
}
