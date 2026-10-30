package com.example.frigus_mobile.data.model

/** M04 is an aggregate, not a page or the complete list of stock products. */
data class InventoryHomeResponse(
    val groupId: String? = null,
    val stocks: List<StockResponse?>? = null,
    val summary: InventoryHomeSummary? = null,
    val statusCounts: InventoryStatusCounts? = null,
    val categoryCounts: Map<String, Long?>? = null,
    val nearExpiration: List<StockProductResponse>? = null
)

data class InventoryHomeSummary(
    val groupId: String? = null,
    val stockCount: Long? = null,
    val productCount: Long? = null,
    val itemCount: Long? = null,
    val estimatedValue: Double? = null,
    val pendingShoppingItems: Long? = null,
    val quantitiesByUnit: Map<String, Double?>? = null,
    val itemsByStoragePlace: Map<String, Long?>? = null,
    val consumedThisWeek: Map<String, Double?>? = null,
    val weekStart: String? = null,
    val nearExpiration: List<StockProductResponse>? = null
)

data class InventoryStatusCounts(
    val fresh: Long? = null,
    val nearExpiration: Long? = null,
    val expired: Long? = null
)
