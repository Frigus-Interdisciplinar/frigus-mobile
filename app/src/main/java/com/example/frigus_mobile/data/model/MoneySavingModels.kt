package com.example.frigus_mobile.data.model

data class MoneySavingAmount(val amount: Double? = null, val actions: Long? = null)
data class MoneySavingCategory(
    val category: String? = null, val spent: Double? = null,
    val saved: Double? = null, val lost: Double? = null
)
data class MoneySavingMonth(
    val month: String? = null, val spent: Double? = null,
    val saved: Double? = null, val lost: Double? = null
)
data class MoneySavingSummary(
    val spending: MoneySavingAmount? = null, val saving: MoneySavingAmount? = null,
    val loss: MoneySavingAmount? = null, val previousSpending: Double? = null,
    val spendingChangePercent: Double? = null, val hasFinancialData: Boolean = false,
    val categories: List<MoneySavingCategory>? = null,
    val months: List<MoneySavingMonth>? = null
)
data class MoneySavingEvent(
    val id: String? = null, val kind: String? = null, val date: String? = null,
    val productName: String? = null, val category: String? = null,
    val categoryGroup: String? = null, val unitOfMeasure: String? = null,
    val quantity: Int? = null, val amount: Double? = null,
    val verifiedValue: Boolean = false, val includedInMetric: Boolean = false,
    val reasonCode: String? = null
)
data class MoneySavingEventPage(
    val content: List<MoneySavingEvent>? = null, val totalElements: Long = 0,
    val number: Int = 0, val size: Int = 20, val totalPages: Int = 0
)

/** An unverified amount must never become a displayed zero or a financial total. */
fun confirmedMoneySavingAmount(verified: Boolean, amount: Double?): Double? =
    amount?.takeIf { verified && it.isFinite() && it >= 0 }

/** The original food-purchase contract uses whole quantities; response values stay nullable, never invented. */
data class FoodPurchaseRequest(
    val purchaseDate: String, val source: String = "USER_CONFIRMED",
    val receiptReference: String? = null, val lines: List<FoodPurchaseLineRequest>
)

data class FoodPurchaseLineRequest(
    val stockId: Int, val productId: Int, val expireDate: String,
    val quantity: Int, val unitPrice: Double, val category: String? = null
)

data class FoodPurchaseUpdateRequest(
    val purchaseDate: String, val source: String = "USER_CONFIRMED",
    val receiptReference: String? = null, val version: Int,
    val lines: List<FoodPurchaseUpdateLineRequest>
)

data class FoodPurchaseUpdateLineRequest(
    val id: Long, val quantity: Int, val unitPrice: Double, val category: String? = null
)

data class FoodPurchaseResponse(
    val id: String? = null, val purchaseDate: String? = null, val source: String? = null,
    val receiptReference: String? = null, val version: Int? = null,
    val total: Double? = null, val lines: List<FoodPurchaseLineResponse>? = null
)

data class FoodPurchaseLineResponse(
    val id: Long? = null, val stockKey: Int? = null, val stockProductId: Int? = null,
    val productName: String? = null, val category: String? = null,
    val categoryGroup: String? = null, val unitOfMeasure: String? = null,
    val expireDate: String? = null, val quantity: Int? = null,
    val unitPrice: Double? = null, val total: Double? = null
)

fun isMoneySavingPlanRequired(error: ApiError?): Boolean =
    error?.status == 403 && error.code == "MONEY_SAVING_PLAN_REQUIRED"
