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
import com.example.frigus_mobile.data.api.unavailableMobileContract
import com.example.frigus_mobile.data.local.SessionManager
import com.example.frigus_mobile.data.model.PageResponse
import com.example.frigus_mobile.data.model.StockMovementRequest
import com.example.frigus_mobile.data.model.StockMovementResponse
import com.example.frigus_mobile.data.model.StockProductProductResponse
import com.example.frigus_mobile.data.model.StockProductResponse
import com.example.frigus_mobile.data.model.StockProductUpdateRequest
import com.example.frigus_mobile.utils.StockQuantityTracker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class StockRepository(context: Context? = null) {
    private val api = RetrofitClient.stockService
    private val appContext = context?.applicationContext
    private val sessionManager = context?.let { SessionManager(it) }

    /** M04 resolves the active group and its stocks on the BFF. */
    suspend fun resolveUserStockId(): Int? = withContext(Dispatchers.IO) {
        val cachedStock = sessionManager?.activeStockId
        val cachedGroup = sessionManager?.activeGroupId
        if (cachedStock != null && cachedStock > 0 && !cachedGroup.isNullOrBlank()) {
            return@withContext cachedStock
        }
        try {
            val home = api.getInventoryHome().requireBody()
            val stockId = home.stocks?.filterNotNull()?.firstOrNull()?.id
            sessionManager?.saveActiveGroupAndStock(home.groupId, stockId)
            stockId
        } catch (e: Exception) {
            sessionManager?.saveActiveGroupAndStock(null, null)
            throw e
        }
    }

    suspend fun getInventoryContext(): InventoryHomeResponse = api.getInventoryHome().requireBody()

    suspend fun getStockProducts(): List<StockProductResponse> = withContext(Dispatchers.IO) {
        val stockId = resolveUserStockId() ?: return@withContext emptyList()
        getStockProducts(stockId)
    }

    suspend fun getStockProducts(stockId: Int): List<StockProductResponse> = withContext(Dispatchers.IO) {
        var page = 0
        val allItems = mutableListOf<StockProductResponse>()
        while (true) {
            val result = api.getStockProducts(stockId = stockId, page = page, size = 100).awaitPage(page, stockId)
            val items = result.content ?: emptyList()
            allItems.addAll(items.filterNotNull())
            if (result.totalPages <= 0 || page >= result.totalPages - 1 || items.isEmpty()) {
                break
            }
            page++
        }
        // Inicializa o rastreador de quantidade original para cÃ¡lculo da barra de progresso
        if (appContext != null) {
            allItems.forEach { prod ->
                prod.id?.let { id ->
                    StockQuantityTracker.getOrSetInitialQuantity(appContext, id, prod.quantity ?: 0)
                }
            }
        }
        allItems
    }

    suspend fun getProductsPage(
        page: Int = 0,
        size: Int = 30,
        category: String? = null,
        storagePlace: String? = null,
        query: String? = null,
        groupId: String? = null
    ): PageResponse<StockProductResponse> =
        api.getProducts(groupId, page, size, category, storagePlace, query).awaitPage(page, null)

    suspend fun getStockProductsPage(
        stockId: Int,
        page: Int = 0,
        size: Int = 30,
        category: String? = null,
        storagePlace: String? = null,
        query: String? = null
    ): PageResponse<StockProductResponse> =
        api.getStockProducts(stockId, page, size, category, storagePlace, query).awaitPage(page, stockId)

    private suspend fun Call<PageResponse<StockProductResponse>>.awaitPage(
        requestedPage: Int,
        stockId: Int?
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
                    else requestedPage < body.totalPages && (items.isNotEmpty() || requestedPage == body.totalPages - 1)) &&
                    items.all { it != null && (it.id ?: 0) > 0 && (stockId == null || it.stockId == stockId) }
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
        quantity: Int? = null,
        expireDate: String? = null,
        storagePlace: String? = null,
        name: String? = null,
        category: String? = null
    ): Boolean {
        val request = StockProductUpdateRequest(
            name = name?.trim()?.takeIf { it.isNotEmpty() },
            category = category,
            quantity = quantity,
            expireDate = expireDate?.let { try { isoDate(it) } catch (_: Exception) { it } },
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

    suspend fun getMovements(id: Int, page: Int, size: Int): PageResponse<StockMovementResponse> {
        val response = api.getMovements(id, page, size).awaitLotResponse()
        if (response.code() != 200) throw BffException(ApiError.parse(response), "")
        val result = response.body()
        val items: List<StockMovementResponse?>? = result?.content
        val valid = result != null && items != null && result.number == page && result.size > 0 &&
            result.totalPages >= 0 && result.totalElements >= items.size && items.size <= result.size &&
            (if (result.totalPages == 0) items.isEmpty() && result.totalElements == 0L
                else page < result.totalPages && (items.isNotEmpty() || page == result.totalPages - 1)) &&
            items.all { it != null && (it.id ?: 0) > 0 && it.stockProductId == id }
        if (!valid) throw BffException(ApiError(code = "INVALID_RESPONSE"), "")
        return result!!
    }

    suspend fun createMovement(id: Int, request: StockMovementRequest): StockMovementResponse {
        val response = api.createMovement(id, request).awaitLotResponse()
        if (response.code() != 201) throw BffException(
            if (response.isSuccessful) ApiError(code = "INVALID_RESPONSE") else ApiError.parse(response), "")
        val movement = response.body()
        if (movement == null || (movement.id ?: 0) <= 0 || movement.stockProductId != id ||
            movement.movementType != request.movementType || movement.quantity != request.quantity)
            throw BffException(ApiError(code = "INVALID_RESPONSE"), "")
        return movement
    }

    suspend fun createProduct(
        name: String,
        category: String,
        storagePlace: String,
        unitPrice: Double = 1.0,
        unitOfMeasure: String = "UNIT"
    ): StockProductProductResponse = withContext(Dispatchers.IO) {
        var groupId = sessionManager?.activeGroupId
        if (groupId.isNullOrBlank()) {
            try { resolveUserStockId() } catch (_: Exception) {}
            groupId = sessionManager?.activeGroupId
        }
        val request = com.example.frigus_mobile.data.model.ProductCreateRequest(
            name = name.trim(),
            category = category,
            storagePlace = storagePlace,
            unitPrice = unitPrice,
            unitOfMeasure = unitOfMeasure,
            ownerGroupId = groupId
        )
        try {
            val response = api.createProduct(request)
            if (response.isSuccessful && response.body() != null) {
                return@withContext response.body()!!
            }
        } catch (_: Exception) {
        }
        StockProductProductResponse(
            id = (System.currentTimeMillis() % 100000).toInt(),
            name = name.trim(),
            category = category,
            storagePlace = storagePlace,
            unitPrice = unitPrice,
            unitOfMeasure = unitOfMeasure
        )
    }

    suspend fun getCatalogProducts(): List<StockProductProductResponse> = withContext(Dispatchers.IO) {
        try {
            val groupId = sessionManager?.activeGroupId
            val response = api.getCatalogProducts(page = 0, size = 100, groupId = groupId)
            if (response.isSuccessful) {
                return@withContext response.body()?.content.orEmpty()
            }
        } catch (_: Exception) {
        }
        emptyList()
    }

    suspend fun addStockProduct(item: StockProductResponse): Unit = withContext(Dispatchers.IO) {
        val stockId = try { resolveUserStockId() ?: item.stockId ?: 1 } catch (_: Exception) { item.stockId ?: 1 }
        val isoExpireDate = item.expireDate?.let {
            try { isoDate(it) } catch (_: Exception) { LocalDate.now().plusDays(15).toString() }
        } ?: LocalDate.now().plusDays(15).toString()
        val validPrice = if (item.purchaseUnitPrice != null && item.purchaseUnitPrice > 0.0) {
            item.purchaseUnitPrice
        } else {
            1.0
        }
        val prodId = item.productId ?: item.id ?: 1
        val req = com.example.frigus_mobile.data.model.StockProductCreateRequest(
            productId = prodId,
            stockId = stockId,
            quantity = item.quantity ?: 1,
            minimalQuantity = item.minimalQuantity ?: 1,
            purchaseUnitPrice = validPrice,
            expireDate = isoExpireDate
        )
        try {
            val resp = api.createStockProduct(req)
            if (resp.isSuccessful) {
                val createdId = resp.body()?.id
                if (createdId != null && appContext != null) {
                    StockQuantityTracker.setInitialQuantity(appContext, createdId, item.quantity ?: 1)
                }
            }
        } catch (_: Exception) {
        }
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
