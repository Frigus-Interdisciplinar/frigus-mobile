package com.example.frigus_mobile.data.repository

import android.content.Context
import com.example.frigus_mobile.data.api.BffException
import com.example.frigus_mobile.data.api.RetrofitClient
import com.example.frigus_mobile.data.model.ApiError
import com.example.frigus_mobile.data.model.InventoryHomeResponse
import kotlinx.coroutines.suspendCancellableCoroutine
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import com.example.frigus_mobile.data.api.requireBody
import com.example.frigus_mobile.data.api.requireNoContent
import com.example.frigus_mobile.data.local.SessionManager
import com.example.frigus_mobile.data.model.ManualStockProductRequest
import com.example.frigus_mobile.data.model.PageResponse
import com.example.frigus_mobile.data.model.StockMovementRequest
import com.example.frigus_mobile.data.model.StockMovementResponse
import com.example.frigus_mobile.data.model.StockProductProductResponse
import com.example.frigus_mobile.data.model.StockProductResponse
import com.example.frigus_mobile.data.model.StockProductUpdateRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class StockRepository(context: Context? = null) {
    private val api = RetrofitClient.stockService
    private val sessionManager = context?.let { SessionManager(it) }

    /** M04 resolves the active group and its stocks on the BFF. */
    suspend fun resolveUserStockId(): Int? = withContext(Dispatchers.IO) {
        val home = api.getInventoryHome().requireBody()
        val stockId = home.stocks?.filterNotNull()?.firstOrNull()?.id
        sessionManager?.saveActiveGroupAndStock(home.groupId, stockId)
        stockId
    }

    /** Existing screens expect all products; M05 is paged across the active group. */
    suspend fun getStockProducts(): List<StockProductResponse> = withContext(Dispatchers.IO) {
        val items = mutableListOf<StockProductResponse>()
        var page = 0
        var totalPages: Int
        do {
            val result = api.getProducts(page = page, size = 100).requireBody()
            items.addAll(result.content)
            totalPages = result.totalPages
            page++
        } while (page < totalPages)
        items
    }

    /** Reuse the M04 validation used by Home, without choosing or creating a stock. */
    suspend fun getInventoryContext(): InventoryHomeResponse = suspendCancellableCoroutine { continuation ->
        val call = InventoryHomeRepository().load { home, error ->
            if (continuation.isActive) {
                if (home != null) continuation.resume(home)
                else continuation.resumeWithException(BffException(
                    error ?: ApiError(code = "INVALID_RESPONSE"), ""
                ))
            }
        }
        continuation.invokeOnCancellation { call.cancel() }
    }

    suspend fun getProductsPage(
        page: Int = 0,
        size: Int = 30,
        category: String? = null,
        storagePlace: String? = null,
        query: String? = null,
        groupId: String? = null
    ): PageResponse<StockProductResponse> =
        api.getProducts(groupId = groupId, page = page, size = size, category = category,
            storagePlace = storagePlace, query = query).awaitPage(page)

    suspend fun getStockProductsPage(
        stockId: Int,
        page: Int = 0,
        size: Int = 30,
        category: String? = null,
        storagePlace: String? = null,
        query: String? = null
    ): PageResponse<StockProductResponse> =
        api.getStockProducts(stockId, page, size, category, storagePlace, query).awaitPage(page, stockId)

    /** Cancel obsolete GETs and reject invalid pages instead of rendering them as empty stock. */
    private suspend fun Call<PageResponse<StockProductResponse>>.awaitPage(
        requestedPage: Int,
        stockId: Int? = null
    ): PageResponse<StockProductResponse> = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(object : Callback<PageResponse<StockProductResponse>> {
            override fun onResponse(
                call: Call<PageResponse<StockProductResponse>>,
                response: Response<PageResponse<StockProductResponse>>
            ) {
                if (!continuation.isActive) return
                val body = response.body()
                val items: List<StockProductResponse?>? = body?.content
                val valid = body != null && items != null && body.number == requestedPage &&
                    body.size > 0 && body.totalElements >= items.size && body.totalPages >= 0 &&
                    items.size <= body.size &&
                    (if (body.totalPages == 0) items.isEmpty() && body.totalElements == 0L
                        else body.number < body.totalPages && (items.isNotEmpty() || body.number == body.totalPages - 1)) &&
                    items.all { it != null && (it.id ?: 0) > 0 && (it.stockId ?: 0) > 0 &&
                        (stockId == null || it.stockId == stockId) }
                when {
                    response.code() != 200 -> continuation.resumeWithException(BffException(ApiError.parse(response), ""))
                    !valid -> continuation.resumeWithException(BffException(ApiError(code = "INVALID_RESPONSE"), ""))
                    else -> continuation.resume(body!!)
                }
            }

            override fun onFailure(call: Call<PageResponse<StockProductResponse>>, failure: Throwable) {
                if (continuation.isActive) continuation.resumeWithException(failure)
            }
        })
    }

    suspend fun getStockProductById(id: Int): StockProductResponse =
        requireLot(api.getStockProductById(id).awaitLotResponse(), id)

    private fun requireLot(response: Response<StockProductResponse>, id: Int): StockProductResponse {
        if (response.code() != 200) throw BffException(ApiError.parse(response), "")
        val lot = response.body()
        if (lot == null || lot.id != id || (lot.stockId ?: 0) <= 0)
            throw BffException(ApiError(code = "INVALID_RESPONSE"), "")
        return lot
    }

    private suspend fun <T> Call<T>.awaitLotResponse(): Response<T> = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(object : Callback<T> {
            override fun onResponse(call: Call<T>, response: Response<T>) {
                if (continuation.isActive) continuation.resume(response)
            }
            override fun onFailure(call: Call<T>, failure: Throwable) {
                if (continuation.isActive) continuation.resumeWithException(failure)
            }
        })
    }

    suspend fun updateStockProduct(
        id: Int,
        quantity: Int?,
        expireDate: String?,
        storagePlace: String?
    ): Boolean {
        val request = StockProductUpdateRequest(
            quantity = quantity,
            expireDate = expireDate?.let(::isoDate),
            storagePlace = storagePlace
        )
        updateStockProduct(id, request)
        return true
    }

    suspend fun updateStockProduct(id: Int, request: StockProductUpdateRequest): StockProductResponse {
        require(listOf(request.name, request.category, request.quantity, request.initialQuantity,
            request.minimalQuantity, request.expireDate, request.storagePlace, request.batch).any { it != null }) {
            "Informe ao menos um campo para atualizar o alimento."
        }
        return requireLot(api.updateStockProduct(id, request).awaitLotResponse(), id)
    }

    suspend fun deleteStockProduct(id: Int): Boolean {
        api.deleteStockProduct(id).awaitLotResponse().requireNoContent()
        return true
    }

    suspend fun getMovements(id: Int, page: Int? = null, size: Int? = null):
        PageResponse<StockMovementResponse> = withContext(Dispatchers.IO) {
        api.getMovements(id, page, size).requireBody()
    }

    suspend fun createMovement(id: Int, request: StockMovementRequest): StockMovementResponse =
        withContext(Dispatchers.IO) { api.createMovement(id, request).requireBody() }

    suspend fun addManualStockProduct(
        stockId: Int,
        request: ManualStockProductRequest
    ): StockProductResponse = withContext(Dispatchers.IO) {
        api.createStockProduct(stockId, request).requireBody()
    }

    suspend fun getCatalogProducts(): List<StockProductProductResponse> = withContext(Dispatchers.IO) {
        val stockProducts = getStockProducts()
        stockProducts.mapNotNull { it.product }.distinctBy { it.id }
    }

    suspend fun addStockProduct(item: StockProductResponse): StockProductResponse {
        val stockId = item.stockId ?: resolveUserStockId() ?: throw java.io.IOException("Nenhum estoque ativo encontrado.")
        val expire = item.expireDate?.let(::isoDate) ?: LocalDate.now().plusDays(15).toString()
        val req = if (item.productId != null && item.productId > 0) {
            ManualStockProductRequest(
                productId = item.productId,
                quantity = item.quantity ?: 1,
                expireDate = expire,
                minimalQuantity = item.minimalQuantity ?: 1,
                batch = item.batch
            )
        } else {
            val prod = item.product
            ManualStockProductRequest(
                name = prod?.name ?: "Alimento",
                category = prod?.category ?: item.category ?: "OTHER",
                storagePlace = prod?.storagePlace ?: item.storagePlace ?: "FRIDGE",
                unitPrice = prod?.unitPrice ?: item.purchaseUnitPrice ?: 1.0,
                unitOfMeasure = prod?.unitOfMeasure ?: "UNIT",
                brand = prod?.brand,
                imageUrl = prod?.imageUrl,
                quantity = item.quantity ?: 1,
                expireDate = expire,
                minimalQuantity = item.minimalQuantity ?: 1,
                batch = item.batch
            )
        }
        return addManualStockProduct(stockId, req)
    }

    companion object {
        fun isoDate(value: String): String {
            val trimmed = value.trim()
            val format = if (trimmed.contains('/')) DateTimeFormatter.ofPattern("dd/MM/uuuu")
                .withResolverStyle(java.time.format.ResolverStyle.STRICT) else DateTimeFormatter.ISO_LOCAL_DATE
            return LocalDate.parse(trimmed, format).toString()
        }
    }
}
