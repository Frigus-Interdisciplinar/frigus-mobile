package com.example.frigus_mobile.data.api

import com.example.frigus_mobile.data.model.AdvertisementResponse
import com.example.frigus_mobile.data.model.PageResponse
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface AdvertisementApiService {
    @GET("mobile/advertisements/active")
    fun getActive(@Query("page") page: Int? = null, @Query("size") size: Int? = null): Call<PageResponse<AdvertisementResponse>>

    @POST("mobile/advertisements/{id}/impressions")
    fun recordImpression(@Path("id") id: String): Call<Void>

    @POST("mobile/advertisements/{id}/clicks")
    fun recordClick(@Path("id") id: String): Call<Void>
}
