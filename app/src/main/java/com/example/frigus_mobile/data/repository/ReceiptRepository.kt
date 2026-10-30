package com.example.frigus_mobile.data.repository

import com.example.frigus_mobile.data.api.ReceiptApiService
import com.example.frigus_mobile.data.api.BffException
import com.example.frigus_mobile.data.api.InvalidBffResponseException
import com.example.frigus_mobile.data.api.RetrofitClient
import com.example.frigus_mobile.data.model.ApiError
import com.example.frigus_mobile.data.model.ReceiptConfirmItem
import com.example.frigus_mobile.data.model.ReceiptConfirmRequest
import com.example.frigus_mobile.data.model.ReceiptConfirmationResponse
import com.example.frigus_mobile.data.model.ReceiptResponse
import com.example.frigus_mobile.data.model.ReceiptScanRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import retrofit2.Call
import retrofit2.Response

class ReceiptRepository(private val api: ReceiptApiService = RetrofitClient.receiptService) {
    suspend fun scan(stockId: Int, qrPayload: String): ReceiptResponse = withContext(Dispatchers.IO) {
        val response = api.scan(ReceiptScanRequest(stockId, qrPayload)).execute()
        response.requireStatus(201)
        response.body()?.also(::requireReceipt) ?: throw InvalidBffResponseException()
    }

    suspend fun get(receiptId: String): ReceiptResponse = withContext(Dispatchers.IO) {
        val response = api.get(receiptId).execute()
        response.requireStatus(200)
        response.body()?.also { requireReceipt(it, receiptId) } ?: throw InvalidBffResponseException()
    }

    suspend fun confirm(receiptId: String, items: List<ReceiptConfirmItem>): ReceiptConfirmationResponse =
        withContext(Dispatchers.IO) {
            require(items.isNotEmpty() && items.all { it.action == IMPORT || it.action == IGNORE })
            val response = api.confirm(receiptId, ReceiptConfirmRequest(items)).execute()
            response.requireStatus(200)
            val body = response.body() ?: throw InvalidBffResponseException()
            requireConfirmation(receiptId, items, body)
            body
        }

    private fun requireReceipt(receipt: ReceiptResponse, expectedId: String? = null) {
        val receiptId = receipt.receiptId?.takeIf(::isUuid) ?: throw InvalidBffResponseException()
        if ((expectedId != null && receiptId != expectedId) || receipt.status !in STATUSES)
            throw InvalidBffResponseException()
        if (receipt.status == REVIEW_REQUIRED) {
            val lines = receipt.items ?: throw InvalidBffResponseException()
            if (lines.isEmpty() || lines.any { it?.lineId?.let(::isUuid) != true } ||
                lines.mapNotNull { it?.lineId }.toSet().size != lines.size) throw InvalidBffResponseException()
        }
    }

    private fun requireConfirmation(
        receiptId: String,
        request: List<ReceiptConfirmItem>,
        response: ReceiptConfirmationResponse
    ) {
        if (response.receiptId != receiptId || response.status != IMPORTED) throw InvalidBffResponseException()
        val imported = response.importedItems ?: throw InvalidBffResponseException()
        val ignored = response.ignoredLineIds ?: throw InvalidBffResponseException()
        if (imported.any { it.lineId?.let(::isUuid) != true || (it.stockProductId ?: 0) <= 0 ||
                (it.productId ?: 0) <= 0 || (it.quantity ?: 0) <= 0 } || ignored.any { !isUuid(it) })
            throw InvalidBffResponseException()
        val importedByLine = imported.associateBy { it.lineId!! }
        val expectedImports = request.filter { it.action == IMPORT }.associateBy { it.lineId }
        val expectedIgnores = request.filter { it.action == IGNORE }.map { it.lineId }.toSet()
        if (importedByLine.size != imported.size || importedByLine.keys != expectedImports.keys ||
            ignored.size != ignored.toSet().size || ignored.toSet() != expectedIgnores ||
            imported.any { item ->
                val expected = expectedImports[item.lineId] ?: return@any true
                item.productId != expected.productId || item.quantity != expected.quantity
            }) throw InvalidBffResponseException()
    }

    private fun Response<*>.requireStatus(expected: Int) {
        if (code() != expected) {
            if (isSuccessful) throw InvalidBffResponseException()
            throw BffException(ApiError.parse(this), "Falha na leitura da nota (HTTP ${code()}).")
        }
    }

    private fun isUuid(value: String): Boolean = try {
        UUID.fromString(value).toString().equals(value, ignoreCase = true)
    } catch (_: IllegalArgumentException) { false }

    companion object {
        const val REVIEW_REQUIRED = "REVIEW_REQUIRED"
        const val IMPORTED = "IMPORTED"
        const val FAILED = "FAILED"
        private const val IMPORT = "IMPORT"
        private const val IGNORE = "IGNORE"
        private val STATUSES = setOf(REVIEW_REQUIRED, IMPORTED, FAILED)
    }
}
