package com.example.frigus_mobile.data.api

import com.example.frigus_mobile.data.model.MoneySavingEventPage
import com.example.frigus_mobile.data.model.MoneySavingSummary
import com.example.frigus_mobile.data.model.FoodPurchaseRequest
import com.example.frigus_mobile.data.model.FoodPurchaseResponse
import com.example.frigus_mobile.data.model.FoodPurchaseUpdateRequest
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT

interface MoneySavingApiService {
    @GET("mobile/commercial/groups/{groupId}/money-saving/summary")
    fun summary(@Path("groupId") groupId: String, @Query("from") from: String, @Query("to") to: String): Call<MoneySavingSummary>

    @GET("mobile/commercial/groups/{groupId}/money-saving/events")
    fun events(
        @Path("groupId") groupId: String, @Query("from") from: String, @Query("to") to: String,
        @Query("category") category: String?, @Query("kind") kind: String?,
        @Query("page") page: Int, @Query("size") size: Int = 20
    ): Call<MoneySavingEventPage>

    @POST("mobile/commercial/groups/{groupId}/food-purchases")
    fun createFoodPurchase(@Path("groupId") groupId: String,
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body request: FoodPurchaseRequest): Call<FoodPurchaseResponse>

    @GET("mobile/commercial/groups/{groupId}/food-purchases/{purchaseId}")
    fun getFoodPurchase(@Path("groupId") groupId: String,
        @Path("purchaseId") purchaseId: String): Call<FoodPurchaseResponse>

    @PUT("mobile/commercial/groups/{groupId}/food-purchases/{purchaseId}")
    fun updateFoodPurchase(@Path("groupId") groupId: String,
        @Path("purchaseId") purchaseId: String,
        @Body request: FoodPurchaseUpdateRequest): Call<FoodPurchaseResponse>

    @DELETE("mobile/commercial/groups/{groupId}/food-purchases/{purchaseId}")
    fun deleteFoodPurchase(@Path("groupId") groupId: String,
        @Path("purchaseId") purchaseId: String): Call<Void>
}
