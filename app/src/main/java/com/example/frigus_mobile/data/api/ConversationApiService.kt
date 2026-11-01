package com.example.frigus_mobile.data.api

import com.example.frigus_mobile.data.model.*
import retrofit2.Call
import retrofit2.http.*

interface ConversationApiService {
    @GET("mobile/core/conversations") fun conversations(): Call<List<HumanConversation>>
    @GET("mobile/core/conversations/{id}") fun conversation(@Path("id") id: String): Call<HumanConversation>
    @GET("mobile/core/conversations/{id}/messages")
    fun messages(@Path("id") id: String, @Query("page") page: Int,
                 @Query("size") size: Int = 30, @Query("sort") sort: String = "createdAt,desc"): Call<PageResponse<HumanMessage>>
    @POST("mobile/core/conversations/{id}/messages")
    fun send(@Path("id") id: String, @Body body: ConversationSendRequest): Call<HumanMessage>
    @POST("mobile/core/conversations/{id}/messages/{messageId}/read")
    fun read(@Path("id") id: String, @Path("messageId") messageId: Int): Call<Void>
}
