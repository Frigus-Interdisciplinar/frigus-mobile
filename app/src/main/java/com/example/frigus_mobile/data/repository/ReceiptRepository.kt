package com.example.frigus_mobile.data.repository

import com.example.frigus_mobile.data.api.ReceiptApiService
import com.example.frigus_mobile.data.api.RetrofitClient
import com.example.frigus_mobile.data.api.requireBody
import com.example.frigus_mobile.data.model.ReceiptConfirmItem
import com.example.frigus_mobile.data.model.ReceiptConfirmRequest
import com.example.frigus_mobile.data.model.ReceiptConfirmationResponse
import com.example.frigus_mobile.data.model.ReceiptResponse
import com.example.frigus_mobile.data.model.ReceiptScanRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ReceiptRepository(private val api: ReceiptApiService = RetrofitClient.receiptService) {
    suspend fun scan(stockId: Int, qrPayload: String): ReceiptResponse = withContext(Dispatchers.IO) {
        api.scan(ReceiptScanRequest(stockId, qrPayload)).requireBody()
    }

    suspend fun get(receiptId: String): ReceiptResponse = withContext(Dispatchers.IO) {
        api.get(receiptId).requireBody()
    }

    suspend fun confirm(receiptId: String, items: List<ReceiptConfirmItem>): ReceiptConfirmationResponse =
        withContext(Dispatchers.IO) { api.confirm(receiptId, ReceiptConfirmRequest(items)).requireBody() }
}
