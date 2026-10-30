package com.example.frigus_mobile.data.api

import com.example.frigus_mobile.data.model.PageResponse
import com.example.frigus_mobile.data.model.StockCreateRequest
import com.example.frigus_mobile.data.model.StockProductCreateRequest
import com.example.frigus_mobile.data.model.StockProductProductResponse
import com.example.frigus_mobile.data.model.StockProductResponse
import com.example.frigus_mobile.data.model.StockProductUpdateRequest
import com.google.gson.JsonElement
import retrofit2.Response
import retrofit2.http.*

interface StockApiService {

    @GET("groups")
    suspend fun getMyGroups(): Response<JsonElement>

    @POST("groups")
    suspend fun createGroup(
        @Body request: Map<String, String>
    ): Response<JsonElement>

    @GET("stocks")
    suspend fun getStocksByGroup(
        @Query("groupId") groupId: String
    ): Response<JsonElement>

    @POST("stocks")
    suspend fun createStock(
        @Body request: StockCreateRequest
    ): Response<JsonElement>

    @GET("products")
    suspend fun getCatalogProducts(
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 100,
        @Query("groupId") groupId: String? = null
    ): Response<PageResponse<StockProductProductResponse>>

    @POST("products")
    suspend fun createProduct(
        @Body request: com.example.frigus_mobile.data.model.ProductCreateRequest
    ): Response<StockProductProductResponse>

    @GET("stock-products")
    suspend fun getStockProducts(
        @Query("stockId") stockId: Int
    ): Response<PageResponse<StockProductResponse>>

    @GET("stock-products/{id}")
    suspend fun getStockProductById(
        @Path("id") id: Int
    ): Response<StockProductResponse>

    @POST("stock-products")
    suspend fun createStockProduct(
        @Body request: StockProductCreateRequest
    ): Response<StockProductResponse>

    @PUT("stock-products/{id}")
    suspend fun updateStockProduct(
        @Path("id") id: Int,
        @Body request: StockProductUpdateRequest
    ): Response<StockProductResponse>

    @DELETE("stock-products/{id}")
    suspend fun deleteStockProduct(
        @Path("id") id: Int
    ): Response<Unit>
}
