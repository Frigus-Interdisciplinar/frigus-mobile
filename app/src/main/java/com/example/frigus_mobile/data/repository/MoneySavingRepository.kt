package com.example.frigus_mobile.data.repository

import com.example.frigus_mobile.data.api.BffException
import com.example.frigus_mobile.data.api.InvalidBffResponseException
import com.example.frigus_mobile.data.api.MoneySavingApiService
import com.example.frigus_mobile.data.api.RetrofitClient
import com.example.frigus_mobile.data.api.requireBody
import com.example.frigus_mobile.data.api.requireNoContent
import com.example.frigus_mobile.data.local.SessionManager
import com.example.frigus_mobile.data.model.ApiError
import com.example.frigus_mobile.data.model.FoodPurchaseRequest
import com.example.frigus_mobile.data.model.FoodPurchaseResponse
import com.example.frigus_mobile.data.model.FoodPurchaseUpdateRequest
import com.example.frigus_mobile.data.model.MoneySavingEventPage
import com.example.frigus_mobile.data.model.MoneySavingSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Call
import java.io.IOException
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

/** A mutation with an uncertain result must be reconciled with a read before another POST. */
class MoneySavingUncertainMutationException(val apiError: ApiError?, cause: Exception) : IOException(cause)

class MoneySavingRepository private constructor(
    private val session: SessionManager?,
    service: MoneySavingApiService?
) {
    constructor(session: SessionManager) : this(session, null)
    constructor(service: MoneySavingApiService) : this(null, service)

    private val api: MoneySavingApiService = service ?: RetrofitClient.moneySavingService

    suspend fun groupId(): String = groupId(session?.activeGroupId)

    suspend fun groupId(selectedGroupId: String?): String = withContext(Dispatchers.IO) {
        val id = RetrofitClient.stockService.getInventoryHome(selectedGroupId).requireBody().groupId
        if (!isUuid(id) || (selectedGroupId != null && !selectedGroupId.equals(id, ignoreCase = true))) {
            throw InvalidBffResponseException()
        }
        id!!
    }

    suspend fun summary(groupId: String, from: String, to: String): MoneySavingSummary =
        withContext(Dispatchers.IO) { api.summary(groupId, from, to).read() }

    suspend fun events(
        groupId: String,
        from: String,
        to: String,
        category: String?,
        kind: String?,
        page: Int
    ): MoneySavingEventPage = withContext(Dispatchers.IO) {
        api.events(groupId, from, to, category, kind, page).read().also { result ->
            val content = result.content
            if (content == null || result.number != page || result.totalElements < content.size ||
                result.totalPages < 0 || result.size !in 1..100 || content.size > result.size ||
                (result.totalPages == 0 && (content.isNotEmpty() || result.totalElements != 0L)) ||
                (result.totalPages > 0 && result.number >= result.totalPages) ||
                content.any { event ->
                    event.id.isNullOrBlank() || !isDate(event.date) ||
                        event.kind !in setOf("PURCHASE", "CONSUMED", "DISCARDED") || (event.quantity ?: 0) <= 0 ||
                        (event.amount != null && (!event.amount.isFinite() || event.amount < 0)) ||
                        (event.verifiedValue && event.amount == null) ||
                        (event.includedInMetric && !event.verifiedValue)
                }) throw InvalidBffResponseException()
        }
    }

    suspend fun createFoodPurchase(
        groupId: String,
        idempotencyKey: String,
        request: FoodPurchaseRequest
    ): FoodPurchaseResponse = withContext(Dispatchers.IO) {
        validInput(
            isUuid(groupId) && idempotencyKey.isNotBlank() && idempotencyKey.trim().length <= 160 &&
                validHeader(request.purchaseDate, request.source, request.receiptReference) && request.lines.isNotEmpty() &&
                request.lines.all { line ->
                    line.stockId > 0 && line.productId > 0 && isDate(line.expireDate) && line.quantity > 0 &&
                        isValidPrice(line.unitPrice) && (line.category == null || line.category in FOOD_CATEGORIES)
                }
        )
        mutation {
            api.createFoodPurchase(groupId, idempotencyKey, request).read(expected = 201).also(::validatePurchase)
        }
    }

    suspend fun getFoodPurchase(groupId: String, purchaseId: String): FoodPurchaseResponse = withContext(Dispatchers.IO) {
        validInput(isUuid(groupId) && isUuid(purchaseId))
        api.getFoodPurchase(groupId, purchaseId).read().also { purchase ->
            validatePurchase(purchase)
            if (purchase.id != purchaseId) throw InvalidBffResponseException()
        }
    }

    suspend fun updateFoodPurchase(
        groupId: String,
        purchaseId: String,
        request: FoodPurchaseUpdateRequest
    ): FoodPurchaseResponse = withContext(Dispatchers.IO) {
        validInput(
            isUuid(groupId) && isUuid(purchaseId) && request.version >= 0 &&
                validHeader(request.purchaseDate, request.source, request.receiptReference) && request.lines.isNotEmpty() &&
                request.lines.map { it.id }.distinct().size == request.lines.size &&
                request.lines.all { line ->
                    line.id > 0 && line.quantity > 0 && isValidPrice(line.unitPrice) &&
                        (line.category == null || line.category in FOOD_CATEGORIES)
                }
        )
        mutation {
            api.updateFoodPurchase(groupId, purchaseId, request).read().also { purchase ->
                validatePurchase(purchase)
                if (purchase.id != purchaseId) throw InvalidBffResponseException()
            }
        }
    }

    suspend fun deleteFoodPurchase(groupId: String, purchaseId: String) = withContext(Dispatchers.IO) {
        validInput(isUuid(groupId) && isUuid(purchaseId))
        mutation { api.deleteFoodPurchase(groupId, purchaseId).requireNoContent() }
    }

    private fun validatePurchase(purchase: FoodPurchaseResponse) {
        if (!isUuid(purchase.id) ||
            !validHeader(purchase.purchaseDate, purchase.source, purchase.receiptReference) ||
            (purchase.version ?: -1) < 0 || purchase.total?.let { !it.isFinite() || it <= 0 } != false ||
            purchase.lines.isNullOrEmpty() || purchase.lines.map { it.id }.distinct().size != purchase.lines.size ||
            purchase.lines.any { line ->
                (line.id ?: 0) <= 0 || (line.stockKey ?: 0) <= 0 ||
                    (line.stockProductId != null && line.stockProductId <= 0) || line.productName.isNullOrBlank() ||
                    line.category !in FOOD_CATEGORIES || !isDate(line.expireDate) || (line.quantity ?: 0) <= 0 ||
                    line.unitPrice?.let(::isValidPrice) != true || line.total?.let { it.isFinite() && it > 0 } != true
            }) throw InvalidBffResponseException()
    }

    private fun validHeader(purchaseDate: String?, source: String?, reference: String?): Boolean =
        isDate(purchaseDate) && source in setOf("USER_CONFIRMED", "RECEIPT_CAPTURED") &&
            (reference == null || reference.length <= 160)

    private fun isDate(value: String?): Boolean = value != null && runCatching { LocalDate.parse(value) }.isSuccess

    private fun isUuid(value: String?): Boolean = value != null && runCatching {
        UUID.fromString(value).toString().equals(value, ignoreCase = true)
    }.getOrDefault(false)

    private fun isValidPrice(value: Double): Boolean = value.isFinite() && value > 0 && value <= 99_999_999.99 &&
        BigDecimal.valueOf(value).stripTrailingZeros().scale() <= 2

    private fun validInput(valid: Boolean) {
        if (!valid) throw BffException(ApiError(status = 422, code = "INVALID_PURCHASE_INPUT"), "")
    }

    private inline fun <T> mutation(operation: () -> T): T = try {
        operation()
    } catch (error: BffException) {
        if (error.error.status == 408 || (error.error.status ?: 0) >= 500) {
            throw MoneySavingUncertainMutationException(error.error, error)
        }
        throw error
    } catch (error: Exception) {
        throw MoneySavingUncertainMutationException(null, error)
    }

    private fun <T> Call<T>.read(expected: Int = 200): T {
        val response = execute()
        if (!response.isSuccessful) throw BffException(ApiError.parse(response), "")
        if (response.code() != expected) throw InvalidBffResponseException()
        return response.body() ?: throw InvalidBffResponseException()
    }

    private companion object {
        val FOOD_CATEGORIES = setOf("FRUIT", "VEGETABLE", "MEAT", "DAIRY", "GRAIN", "BEVERAGE")
    }
}
