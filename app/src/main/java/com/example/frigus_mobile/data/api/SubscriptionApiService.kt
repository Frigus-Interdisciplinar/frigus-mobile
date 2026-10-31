package com.example.frigus_mobile.data.api

import com.example.frigus_mobile.data.model.*
import retrofit2.Call
import retrofit2.http.*

interface SubscriptionApiService {
    @GET("mobile/core/plans/active") fun plans(): Call<List<ActivePlan>>
    @GET("mobile/core/subscriptions/me") fun subscription(): Call<Subscription>
    @GET("mobile/core/plans/me/limits") fun limits(): Call<PlanLimits>
    @GET("mobile/core/transactions") fun transactions(@Query("page") page: Int, @Query("size") size: Int = 20): Call<BillingTransactionPage>
    @POST("mobile/core/subscriptions/me/cancel") fun cancel(): Call<Subscription>
    @POST("mobile/core/subscriptions/me/reactivate") fun reactivate(): Call<Subscription>
    @POST("mobile/core/transactions/checkout")
    fun checkout(@Header("Idempotency-Key") key: String, @Body request: CheckoutRequest): Call<BillingTransaction>
    @GET("mobile/core/groups") fun groups(): Call<List<FamilyGroup>>
    @POST("mobile/core/groups") fun createGroup(@Body request: GroupCreateRequest): Call<FamilyGroup>
    @POST("mobile/core/stocks") fun createStock(@Body request: StockCreateRequest): Call<com.example.frigus_mobile.data.model.StockResponse>
    @GET("mobile/core/groups/{id}") fun group(@Path("id") id: String): Call<FamilyGroup>
    @GET("mobile/core/groups/{id}/invitations") fun invitations(@Path("id") id: String): Call<List<GroupInvitation>>
    @POST("mobile/core/groups/{id}/invitations")
    fun invite(@Path("id") id: String, @Body request: InviteMemberRequest): Call<GroupInvitation>
    @DELETE("mobile/core/groups/{id}/invitations/{invitationId}")
    fun revoke(@Path("id") id: String, @Path("invitationId") invitationId: String): Call<Void>
}
