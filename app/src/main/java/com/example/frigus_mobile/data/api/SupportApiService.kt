package com.example.frigus_mobile.data.api

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

data class SupportRequestBody(val subject: String, val description: String)
data class SupportRequestItem(
    val id: String? = null,
    val protocol: String? = null,
    val status: String? = null,
    val subject: String? = null,
    val description: String? = null,
    val createdAt: String? = null
)
data class SupportRequestPage(
    val content: List<SupportRequestItem>? = null,
    val number: Int? = null,
    val last: Boolean? = null
)

interface SupportApiService {
    @GET("mobile/core/support/requests")
    fun requests(@Query("page") page: Int, @Query("size") size: Int = 20): Call<SupportRequestPage>

    @POST("mobile/core/support/requests")
    fun create(@Body request: SupportRequestBody): Call<SupportRequestItem>
}
