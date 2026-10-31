package com.example.frigus_mobile.data.api

import com.example.frigus_mobile.data.model.MoneySavingEventPage
import com.example.frigus_mobile.data.model.MoneySavingSummary
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface MoneySavingApiService {
    @GET("mobile/commercial/groups/{groupId}/money-saving/summary")
    fun summary(@Path("groupId") groupId: String, @Query("from") from: String, @Query("to") to: String): Call<MoneySavingSummary>

    @GET("mobile/commercial/groups/{groupId}/money-saving/events")
    fun events(
        @Path("groupId") groupId: String, @Query("from") from: String, @Query("to") to: String,
        @Query("category") category: String?, @Query("kind") kind: String?,
        @Query("page") page: Int, @Query("size") size: Int = 20
    ): Call<MoneySavingEventPage>
}
