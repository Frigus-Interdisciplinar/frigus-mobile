package com.example.frigus_mobile.data.api

import com.example.frigus_mobile.data.model.InventoryHomeResponse
import com.example.frigus_mobile.data.model.ManualStockProductRequest
import com.example.frigus_mobile.data.model.PageResponse
import com.example.frigus_mobile.data.model.StockMovementRequest
import com.example.frigus_mobile.data.model.StockMovementResponse
import com.example.frigus_mobile.data.model.StockProductResponse
import com.example.frigus_mobile.data.model.StockProductUpdateRequest
import retrofit2.Call
import retrofit2.http.*

/** Published /mobile/inventory contract on frigus-bff/dev. */
interface StockApiService {

    @GET("mobile/inventory/home")
    fun getInventoryHome(@Query("groupId") groupId: String? = null): Call<InventoryHomeResponse>

    @GET("mobile/inventory/products")
    fun getProducts(
        @Query("groupId") groupId: String? = null,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 30,
        @Query("category") category: String? = null,
        @Query("storagePlace") storagePlace: String? = null,
        @Query("q") query: String? = null
    ): Call<PageResponse<StockProductResponse>>

    @GET("mobile/inventory/stocks/{stockId}/products")
    fun getStockProducts(
        @Path("stockId") stockId: Int,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 30,
        @Query("category") category: String? = null,
        @Query("storagePlace") storagePlace: String? = null,
        @Query("q") query: String? = null
    ): Call<PageResponse<StockProductResponse>>

    @POST("mobile/inventory/stocks/{stockId}/products")
    fun createStockProduct(
        @Path("stockId") stockId: Int,
        @Body request: ManualStockProductRequest
    ): Call<StockProductResponse>

    @GET("mobile/inventory/stock-products/{id}")
    fun getStockProductById(
        @Path("id") id: Int
    ): Call<StockProductResponse>

    @PUT("mobile/inventory/stock-products/{id}")
    fun updateStockProduct(
        @Path("id") id: Int,
        @Body request: StockProductUpdateRequest
    ): Call<StockProductResponse>

    @DELETE("mobile/inventory/stock-products/{id}")
    fun deleteStockProduct(
        @Path("id") id: Int
    ): Call<Void>

    @GET("mobile/inventory/stock-products/{id}/movements")
    fun getMovements(
        @Path("id") id: Int,
        @Query("page") page: Int? = null,
        @Query("size") size: Int? = null
    ): Call<PageResponse<StockMovementResponse>>

    @POST("mobile/inventory/stock-products/{id}/movements")
    fun createMovement(@Path("id") id: Int, @Body request: StockMovementRequest): Call<StockMovementResponse>
}
