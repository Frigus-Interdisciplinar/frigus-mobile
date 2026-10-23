package com.example.frigus_mobile.data.api

import com.example.frigus_mobile.data.model.InventoryHomeResponse
import com.example.frigus_mobile.data.model.GroupResponse
import com.example.frigus_mobile.data.model.PageResponse
import com.example.frigus_mobile.data.model.StockCreateRequest
import com.example.frigus_mobile.data.model.StockProductCreateRequest
import com.example.frigus_mobile.data.model.StockProductProductResponse
import com.example.frigus_mobile.data.model.StockProductResponse
import com.example.frigus_mobile.data.model.StockProductUpdateRequest
import com.example.frigus_mobile.data.model.StockResponse
import retrofit2.Call
import retrofit2.http.*

interface StockApiService {

    @GET("mobile/inventory/home")
    fun getInventoryHome(): Call<InventoryHomeResponse>

    @GET("groups")
    fun getMyGroups(): Call<List<GroupResponse>>

    @POST("groups")
    fun createGroup(
        @Body request: Map<String, String>
    ): Call<GroupResponse>

    @GET("stocks")
    fun getStocksByGroup(
        @Query("groupId") groupId: String
    ): Call<PageResponse<StockResponse>>

    @POST("stocks")
    fun createStock(
        @Body request: StockCreateRequest
    ): Call<StockResponse>

    @GET("products")
    fun getCatalogProducts(
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 50
    ): Call<PageResponse<StockProductProductResponse>>

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
