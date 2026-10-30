package com.example.frigus_mobile.data.api

import com.example.frigus_mobile.data.model.NotificationResponse
import com.example.frigus_mobile.data.model.NotificationPageResponse
import retrofit2.Call
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.Path
import retrofit2.http.Query

interface NotificationApiService {

    @GET("mobile/notifications")
    fun getNotifications(
        @Query("page") page: Int,
        @Query("size") size: Int,
        @Query("unreadOnly") unreadOnly: Boolean? = null
    ): Call<NotificationPageResponse>

    @PATCH("mobile/notifications/{id}/read")
    fun markAsRead(
        @Path("id") id: String
    ): Call<NotificationResponse>

    @DELETE("mobile/notifications")
    fun clearAll(): Call<Void>

    @PATCH("mobile/notifications/read-all")
    fun markAllAsRead(): Call<Void>
}
