package com.example.frigus_mobile.data.api

import com.example.frigus_mobile.data.model.ReceiptConfirmRequest
import com.example.frigus_mobile.data.model.ReceiptConfirmationResponse
import com.example.frigus_mobile.data.model.ReceiptResponse
import com.example.frigus_mobile.data.model.ReceiptScanRequest
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface ReceiptApiService {
    @POST("mobile/inventory/receipts/scan")
    fun scan(@Body request: ReceiptScanRequest): Call<ReceiptResponse>

    @GET("mobile/inventory/receipts/{receiptId}")
    fun get(@Path("receiptId") receiptId: String): Call<ReceiptResponse>

    @POST("mobile/inventory/receipts/{receiptId}/confirm")
    fun confirm(
        @Path("receiptId") receiptId: String,
        @Body request: ReceiptConfirmRequest
    ): Call<ReceiptConfirmationResponse>
}
