package com.example.frigus_mobile.data.api

import com.example.frigus_mobile.data.model.GroupResponse
import com.example.frigus_mobile.data.model.PageResponse
import com.example.frigus_mobile.data.model.StockProductCreateRequest
import com.example.frigus_mobile.data.model.StockProductResponse
import com.example.frigus_mobile.data.model.StockProductUpdateRequest
import com.example.frigus_mobile.data.model.StockResponse
import retrofit2.Call
import retrofit2.http.*

interface StockApiService {

    @GET("groups")
    fun getMyGroups(): Call<List<GroupResponse>>

    @GET("stocks")
    fun getStocksByGroup(
        @Query("groupId") groupId: String
    ): Call<PageResponse<StockResponse>>

    @GET("stock-products")
    fun getStockProducts(
        @Query("stockId") stockId: Int
    ): Call<PageResponse<StockProductResponse>>

    @GET("stock-products/{id}")
    fun getStockProductById(
        @Path("id") id: Int
    ): Call<StockProductResponse>

    @POST("stock-products")
    fun createStockProduct(
        @Body request: StockProductCreateRequest
    ): Call<StockProductResponse>

    @PUT("stock-products/{id}")
    fun updateStockProduct(
        @Path("id") id: Int,
        @Body request: StockProductUpdateRequest
    ): Call<StockProductResponse>

    @DELETE("stock-products/{id}")
    fun deleteStockProduct(
        @Path("id") id: Int
    ): Call<Void>
}
