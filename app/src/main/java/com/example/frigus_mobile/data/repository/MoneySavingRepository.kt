package com.example.frigus_mobile.data.repository

import com.example.frigus_mobile.data.api.BffException
import com.example.frigus_mobile.data.api.InvalidBffResponseException
import com.example.frigus_mobile.data.api.RetrofitClient
import com.example.frigus_mobile.data.api.MoneySavingApiService
import com.example.frigus_mobile.data.api.requireNoContent
import com.example.frigus_mobile.data.model.ApiError
import com.example.frigus_mobile.data.model.MoneySavingEventPage
import com.example.frigus_mobile.data.model.MoneySavingSummary
import com.example.frigus_mobile.data.model.FoodPurchaseRequest
import com.example.frigus_mobile.data.model.FoodPurchaseResponse
import com.example.frigus_mobile.data.model.FoodPurchaseUpdateRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Call
import java.util.UUID
import java.time.LocalDate
import java.math.BigDecimal
import java.io.IOException

/** There is no automatic replay. A caller must explicitly GET the resource before considering another mutation. */
class MoneySavingUncertainMutationException(val apiError: ApiError?, cause: Exception) : IOException(cause)

class MoneySavingRepository(private val service: MoneySavingApiService? = null) {
    private val api get() = service ?: RetrofitClient.moneySavingService
    suspend fun groupId(selectedGroupId: String?): String = withContext(Dispatchers.IO) {
        val id = RetrofitClient.stockService.getInventoryHome(selectedGroupId).read().groupId
        if (id.isNullOrBlank() || runCatching { UUID.fromString(id).toString().equals(id, true) }.getOrDefault(false).not()) {
            throw InvalidBffResponseException()
        }
        if (selectedGroupId != null && !selectedGroupId.equals(id, true)) throw InvalidBffResponseException()
        id
    }

    suspend fun summary(groupId: String, from: String, to: String): MoneySavingSummary =
        withContext(Dispatchers.IO) { api.summary(groupId, from, to).read() }

    suspend fun events(groupId: String, from: String, to: String, category: String?, kind: String?, page: Int): MoneySavingEventPage =
        withContext(Dispatchers.IO) {
            api.events(groupId, from, to, category, kind, page).read().also {
                if (it.content == null || it.number != page || it.totalElements < it.content.size || it.totalPages < 0 ||
                    it.size !in 1..100 || it.content.size > it.size ||
                    (it.totalPages == 0 && (it.content.isNotEmpty() || it.totalElements != 0L)) ||
                    (it.totalPages > 0 && it.number >= it.totalPages) ||
                    it.content.any { event -> event.id.isNullOrBlank() || !date(event.date) ||
                        event.kind !in setOf("PURCHASE", "CONSUMED", "DISCARDED") || (event.quantity ?: 0) <= 0 ||
                        (event.amount != null && (!event.amount.isFinite() || event.amount < 0)) ||
                        (event.verifiedValue && event.amount == null) ||
                        (event.includedInMetric && !event.verifiedValue) }) throw InvalidBffResponseException()
            }
        }

    /** Recovered f942ebc contract. Keep the same persisted key for the same user-confirmed purchase. */
    suspend fun createFoodPurchase(groupId: String, idempotencyKey: String, request: FoodPurchaseRequest): FoodPurchaseResponse =
        withContext(Dispatchers.IO) {
            validInput(uuid(groupId) && idempotencyKey.isNotBlank() && idempotencyKey.trim().length <= 160 &&
                validHeader(request.purchaseDate, request.source, request.receiptReference) && request.lines.isNotEmpty() &&
                request.lines.all { it.stockId > 0 && it.productId > 0 && date(it.expireDate) &&
                    it.quantity > 0 && price(it.unitPrice) && (it.category == null || it.category in FOOD_CATEGORIES) })
            mutation { api.createFoodPurchase(groupId, idempotencyKey, request).read(201).also(::validatePurchase) }
        }

    suspend fun getFoodPurchase(groupId: String, purchaseId: String): FoodPurchaseResponse = withContext(Dispatchers.IO) {
        validInput(uuid(groupId) && uuid(purchaseId))
        api.getFoodPurchase(groupId, purchaseId).read().also {
            validatePurchase(it)
            if (it.id != purchaseId) throw InvalidBffResponseException()
        }
    }

    suspend fun updateFoodPurchase(groupId: String, purchaseId: String, request: FoodPurchaseUpdateRequest): FoodPurchaseResponse =
        withContext(Dispatchers.IO) {
            validInput(uuid(groupId) && uuid(purchaseId) && request.version >= 0 &&
                validHeader(request.purchaseDate, request.source, request.receiptReference) && request.lines.isNotEmpty() &&
                request.lines.map { it.id }.distinct().size == request.lines.size &&
                request.lines.all { it.id > 0 && it.quantity > 0 && price(it.unitPrice) &&
                    (it.category == null || it.category in FOOD_CATEGORIES) })
            mutation { api.updateFoodPurchase(groupId, purchaseId, request).read().also {
                validatePurchase(it)
                if (it.id != purchaseId) throw InvalidBffResponseException()
            } }
        }

    suspend fun deleteFoodPurchase(groupId: String, purchaseId: String) = withContext(Dispatchers.IO) {
        validInput(uuid(groupId) && uuid(purchaseId))
        mutation { api.deleteFoodPurchase(groupId, purchaseId).requireNoContent() }
    }

    private fun validatePurchase(purchase: FoodPurchaseResponse) {
        if (!uuid(purchase.id) || !validHeader(purchase.purchaseDate, purchase.source, purchase.receiptReference) ||
            (purchase.version ?: -1) < 0 || purchase.total?.let { !it.isFinite() || it <= 0 } != false ||
            purchase.lines.isNullOrEmpty() || purchase.lines.map { it.id }.distinct().size != purchase.lines.size ||
            purchase.lines.any { (it.id ?: 0) <= 0 || (it.stockKey ?: 0) <= 0 ||
                (it.stockProductId != null && it.stockProductId <= 0) || it.productName.isNullOrBlank() ||
                it.category !in FOOD_CATEGORIES || !date(it.expireDate) || (it.quantity ?: 0) <= 0 ||
                it.unitPrice?.let(::price) != true || it.total?.let { amount -> amount.isFinite() && amount > 0 } != true })
            throw InvalidBffResponseException()
    }

    private fun validHeader(purchaseDate: String?, source: String?, reference: String?): Boolean =
        date(purchaseDate) && source in setOf("USER_CONFIRMED", "RECEIPT_CAPTURED") && (reference == null || reference.length <= 160)
    private fun date(value: String?): Boolean = value != null && runCatching { LocalDate.parse(value) }.isSuccess
    private fun uuid(value: String?): Boolean = value != null && runCatching {
        UUID.fromString(value).toString().equals(value, true)
    }.getOrDefault(false)
    private fun price(value: Double): Boolean = value.isFinite() && value > 0 && value <= 99_999_999.99 &&
        BigDecimal.valueOf(value).stripTrailingZeros().scale() <= 2
    private fun validInput(valid: Boolean) {
        if (!valid) throw BffException(ApiError(status = 422, code = "INVALID_PURCHASE_INPUT"), "")
    }

    private inline fun <T> mutation(operation: () -> T): T = try { operation() }
    catch (error: BffException) {
        if (error.error.status == 408 || (error.error.status ?: 0) >= 500)
            throw MoneySavingUncertainMutationException(error.error, error)
        throw error
    } catch (error: Exception) { throw MoneySavingUncertainMutationException(null, error) }

    private fun <T> Call<T>.read(expected: Int = 200): T {
        val response = execute()
        if (!response.isSuccessful) throw BffException(ApiError.parse(response), "")
        if (response.code() != expected) throw InvalidBffResponseException()
        return response.body() ?: throw InvalidBffResponseException()
    }

    companion object {
        private val FOOD_CATEGORIES = setOf("FRUIT", "VEGETABLE", "MEAT", "DAIRY", "GRAIN", "BEVERAGE")
    }
}
