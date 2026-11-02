package com.example.frigus_mobile.data.repository

import android.content.Context
import com.example.frigus_mobile.data.api.BffException
import com.example.frigus_mobile.data.api.RetrofitClient
import com.example.frigus_mobile.data.model.ApiError
import com.example.frigus_mobile.data.model.DiscardCreateRequest
import com.example.frigus_mobile.data.model.DiscardResponse
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
import com.example.frigus_mobile.data.model.ProductImageUploadRequest
import com.example.frigus_mobile.data.model.StockMovementRequest
import com.example.frigus_mobile.data.model.StockMovementResponse
import com.example.frigus_mobile.data.model.StockProductProductResponse
import com.example.frigus_mobile.data.model.StockProductResponse
import com.example.frigus_mobile.data.model.StockProductUpdateRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import java.io.IOException
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

class StockRepository(context: Context? = null) {
    private val api = RetrofitClient.stockService
    private val sessionManager = context?.let { SessionManager(it) }

    private val uploadClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /** M04 resolves the active group and its stocks on the BFF. */
    suspend fun resolveUserStockId(): Int? = withContext(Dispatchers.IO) {
        val credential = sessionManager?.state?.credential()
        val cachedStock = sessionManager?.activeStockId
        val cachedGroup = sessionManager?.activeGroupId
        if (cachedStock != null && cachedStock > 0 && !cachedGroup.isNullOrBlank()) {
            return@withContext cachedStock
        }
        try {
            val home = api.getInventoryHome(cachedGroup).requireBody()
            if (sessionManager != null && (!sessionManager.isLoggedIn || sessionManager.state.credential() != credential ||
                    sessionManager.activeGroupId != cachedGroup))
                throw kotlinx.coroutines.CancellationException()
            if (cachedGroup != null && home.groupId != cachedGroup)
                throw com.example.frigus_mobile.data.api.InvalidBffResponseException()
            val stockId = home.stocks?.filterNotNull()?.firstOrNull()?.id
            sessionManager?.saveActiveGroupAndStock(home.groupId, stockId)
            stockId
        } catch (e: Exception) {
            if (sessionManager?.state?.credential() == credential && sessionManager?.activeGroupId == cachedGroup)
                sessionManager?.saveActiveGroupAndStock(null, null)
            throw e
        }
    }

    suspend fun getInventoryContext(): InventoryHomeResponse = withContext(Dispatchers.IO) {
        val groupId = sessionManager?.activeGroupId
        api.getInventoryHome(groupId).requireBody().also {
            if (groupId != null && it.groupId != groupId)
                throw com.example.frigus_mobile.data.api.InvalidBffResponseException()
        }
    }

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
            request.minimalQuantity, request.expireDate, request.storagePlace, request.unitOfMeasure,
            request.batch, request.imageUrl).any { it != null }) {
            "Informe ao menos um campo para atualizar o alimento."
        }
        return requireLot(api.updateStockProduct(id, request).awaitLotResponse(), id)
    }

    suspend fun uploadProductImage(bytes: ByteArray, mimeType: String): String = withContext(Dispatchers.IO) {
        val uploadRes = api.getProductImageUpload(ProductImageUploadRequest(mimeType)).awaitLotResponse()
        if (uploadRes.code() != 200) throw BffException(ApiError.parse(uploadRes), "Falha ao preparar upload da imagem")
        val uploadInfo = uploadRes.body() ?: throw BffException(ApiError(code = "INVALID_RESPONSE"), "Resposta de upload vazia")
        val uploadUrl = uploadInfo.uploadUrl ?: throw BffException(ApiError(code = "INVALID_RESPONSE"), "URL de upload ausente")

        val mediaType = MediaType.parse(mimeType)
        val request = Request.Builder()
            .url(uploadUrl)
            .put(RequestBody.create(mediaType, bytes))
            .header("Content-Type", mimeType)
            .build()
        val response = uploadClient.newCall(request).execute()
        if (!response.isSuccessful) {
            throw IOException("Falha no upload da foto: HTTP ${response.code()}")
        }
        uploadInfo.publicUrl ?: throw BffException(ApiError(code = "INVALID_RESPONSE"), "URL pública ausente")
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

    suspend fun recordConsumption(id: Int, quantity: Int, observation: String? = null): StockMovementResponse {
        val request = StockMovementRequest(
            movementType = "OUT",
            quantity = quantity,
            reasonCode = "CONSUMED",
            observation = observation ?: "Consumido"
        )
        val response = api.recordStockMovement(id, request).awaitLotResponse()
        if (response.code() != 201) throw BffException(
            if (response.isSuccessful) ApiError(code = "INVALID_RESPONSE") else ApiError.parse(response), "")
        val movement = response.body()
        if (movement == null || (movement.id ?: 0) <= 0)
            throw BffException(ApiError(code = "INVALID_RESPONSE"), "")
        return movement
    }

    suspend fun recordDiscard(id: Int, quantity: Int, reason: String? = null): DiscardResponse {
        val request = DiscardCreateRequest(
            stockProductId = id,
            quantity = quantity,
            reasonCode = "EXPIRED",
            reason = reason ?: "Descarte por validade"
        )
        val response = api.recordDiscard(request).awaitLotResponse()
        if (response.code() != 201) throw BffException(
            if (response.isSuccessful) ApiError(code = "INVALID_RESPONSE") else ApiError.parse(response), "")
        val discard = response.body()
        if (discard == null || (discard.id ?: 0) <= 0)
            throw BffException(ApiError(code = "INVALID_RESPONSE"), "")
        return discard
    }

    /** There is no mobile catalog or direct lot creation endpoint yet. */
    @Deprecated("No published /mobile catalog endpoint")
    suspend fun getCatalogProducts(): List<StockProductProductResponse> = unavailableMobileContract()

    @Deprecated("No published /mobile lot creation endpoint")
    suspend fun addStockProduct(@Suppress("UNUSED_PARAMETER") item: StockProductResponse): Unit =
        unavailableMobileContract()

    companion object {
        fun isoDate(value: String): String {
            val trimmed = value.trim()
            val format = when {
                trimmed.matches(Regex("""^\d{2}[-/]\d{2}[-/]\d{4}$""")) ->
                    DateTimeFormatter.ofPattern(if (trimmed.contains('/')) "dd/MM/uuuu" else "dd-MM-uuuu")
                        .withResolverStyle(java.time.format.ResolverStyle.STRICT)
                else -> DateTimeFormatter.ISO_LOCAL_DATE
            }
            return LocalDate.parse(trimmed, format).toString()
        }
    }
}
