package com.example.frigus_mobile.data.model

data class MoneySavingAmount(val amount: Double? = null, val actions: Int? = null)
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
    val quantity: Double? = null, val amount: Double? = null,
    val verifiedValue: Boolean = false, val includedInMetric: Boolean = false,
    val reasonCode: String? = null
)
data class MoneySavingEventPage(
    val content: List<MoneySavingEvent>? = null, val totalElements: Long = 0,
    val number: Int = 0, val size: Int = 20, val totalPages: Int = 0
)
