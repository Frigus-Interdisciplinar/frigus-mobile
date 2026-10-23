package com.example.frigus_mobile.data.model

/** M04 is an aggregate, not a page or the complete list of stock products. */
data class InventoryHomeResponse(
    val groupId: String? = null,
    val stocks: List<StockResponse?>? = null,
    val summary: InventoryHomeSummary? = null,
    val statusCounts: InventoryStatusCounts? = null
)

data class InventoryHomeSummary(
    val itemCount: Long? = null,
    val itemsByStoragePlace: Map<String, Long?>? = null
)

data class InventoryStatusCounts(
    val fresh: Long? = null,
    val nearExpiration: Long? = null,
    val expired: Long? = null
)
