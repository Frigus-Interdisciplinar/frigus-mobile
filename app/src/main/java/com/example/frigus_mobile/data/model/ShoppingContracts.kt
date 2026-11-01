package com.example.frigus_mobile.data.model

import java.math.BigDecimal

enum class ShoppingSyncStatus { LOCAL_PENDING, SYNCING, SYNCED, AUTH_REQUIRED, ERROR }

data class ShoppingLocalChange(val id: String, val revision: String, val item: ShoppingListItem? = null,
    val fromLegacyCache: Boolean = false)
data class ShoppingLocalDocument(
    val items: List<ShoppingListItem> = emptyList(),
    val pending: List<ShoppingLocalChange> = emptyList()
)

data class ShoppingCatalogProduct(
    val id: Int? = null, val name: String? = null, val category: String? = null,
    val unitOfMeasure: String? = null, val storagePlace: String? = null,
    val unitPrice: BigDecimal? = null, val brand: String? = null, val ownerGroupId: String? = null
)
data class ShoppingCreateProduct(
    val name: String, val category: String, val storagePlace: String,
    val unitPrice: BigDecimal, val unitOfMeasure: String, val ownerGroupId: String
)
/** Real UNIT purchase price is mandatory for every new lot sent by shopping. */
data class ShoppingCreateLot(val productId: Int, val quantity: Int, val expireDate: String, val purchaseUnitPrice: BigDecimal)
data class ShoppingDraft(
    val name: String, val quantity: Int, val unitCode: String, val categoryCode: String,
    val storagePlace: String = "FRIDGE", val expireDate: String? = null,
    val observation: String = "", val imageUri: String? = null,
    val productId: Int? = null, val unitPrice: BigDecimal? = null
)

fun shoppingMutationUncertain(status: Int?): Boolean = status == null || status == 408 || status >= 500

fun validShoppingPrice(price: BigDecimal?): Boolean = price != null && price > BigDecimal.ZERO &&
    price <= BigDecimal("99999999.99") && price.stripTrailingZeros().scale() <= 2

/** Only a saved authenticated selection or a unique real stock can be inferred without asking for an ID. */
fun inferredShoppingStock(stocks: List<StockResponse>, selectedId: Int?): StockResponse? =
    if (selectedId != null) stocks.firstOrNull { it.id == selectedId } else stocks.singleOrNull()

/** Persisted before sending. This contains resource identities, never credentials or invented results. */
data class ShoppingPendingMutation(
    val groupId: String, val stockId: Int, val listId: String? = null,
    val confirmedWrite: Boolean = false, val lotKey: String? = null,
    val productId: Int? = null, val quantity: Int? = null, val expiry: String? = null,
    val baselineLotIds: List<Int>? = null, val confirmedLotId: Int? = null,
    val purchaseUnitPrice: BigDecimal? = null
)

fun StockProductResponse.shoppingPlace(): String? = product?.storagePlace ?: storagePlace

/** Only a newly observed, unambiguous matching lot can reconcile an interrupted POST. */
fun recoveredShoppingLot(pending: ShoppingPendingMutation, lots: List<StockProductResponse>): StockProductResponse? {
    pending.confirmedLotId?.let { id -> return lots.singleOrNull { it.id == id &&
        (pending.purchaseUnitPrice == null || it.hasShoppingPrice(pending.purchaseUnitPrice)) } }
    val baseline = pending.baselineLotIds ?: return null
    return lots.filter { it.id !in baseline && it.productId == pending.productId &&
        it.quantity == pending.quantity && it.expireDate?.take(10) == pending.expiry &&
        (pending.purchaseUnitPrice == null || it.hasShoppingPrice(pending.purchaseUnitPrice)) }.singleOrNull()
}

fun StockProductResponse.hasShoppingPrice(price: BigDecimal): Boolean = purchaseUnitPrice
    ?.takeIf { it.isFinite() }?.let { BigDecimal.valueOf(it).compareTo(price) == 0 } == true

fun validShoppingPlace(category: String, place: String): Boolean =
    place in setOf("FRIDGE", "FREEZER", "PANTRY") && (category != "GRAIN" || place == "PANTRY")
