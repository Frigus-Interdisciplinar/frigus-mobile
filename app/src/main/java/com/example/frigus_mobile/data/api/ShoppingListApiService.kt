package com.example.frigus_mobile.data.api

import com.example.frigus_mobile.data.model.*
import retrofit2.Call
import retrofit2.http.*

/** Only stock transfers. Shopping list/item CRUD never calls the BFF. */
interface ShoppingListApiService {
    @GET("mobile/core/products")
    fun catalog(@Query("groupId") groupId: String, @Query("page") page: Int = 0, @Query("size") size: Int = 100): Call<PageResponse<ShoppingCatalogProduct>>
    @POST("mobile/core/products")
    fun createProduct(@Body request: ShoppingCreateProduct): Call<ShoppingCatalogProduct>
    @POST("mobile/inventory/stocks/{stockId}/products")
    fun createLot(@Path("stockId") stockId: Int, @Query("groupId") groupId: String, @Body request: ShoppingCreateLot): Call<StockProductResponse>
    @PUT("mobile/inventory/stock-products/{id}")
    fun setLotPlace(@Path("id") id: Int, @Body request: StockProductUpdateRequest): Call<StockProductResponse>
    @GET("mobile/inventory/stocks/{stockId}/products")
    fun lots(@Path("stockId") stockId: Int, @Query("page") page: Int = 0, @Query("size") size: Int = 100): Call<PageResponse<StockProductResponse>>
}
