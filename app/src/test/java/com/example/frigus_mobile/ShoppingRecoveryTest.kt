package com.example.frigus_mobile

import com.example.frigus_mobile.data.model.*
import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

class ShoppingRecoveryTest {
    @Test fun destinationNeverChangesAnExplicitInvalidSelection() {
        val stocks = listOf(StockResponse(4), StockResponse(9))
        assertEquals(9, inferredShoppingStock(stocks, 9)?.id)
        assertNull(inferredShoppingStock(stocks, null))
        assertNull(inferredShoppingStock(stocks.take(1), 99))
        assertEquals(4, inferredShoppingStock(stocks.take(1), null)?.id)
    }

    @Test fun productPriceMustBeProvidedAndRepresentableWithoutRounding() {
        assertFalse(validShoppingPrice(null))
        assertFalse(validShoppingPrice(BigDecimal.ZERO))
        assertFalse(validShoppingPrice(BigDecimal("1.001")))
        assertFalse(validShoppingPrice(BigDecimal("100000000")))
        assertTrue(validShoppingPrice(BigDecimal("2.500")))
    }

    @Test fun lotCommandCarriesTheRealUnitPriceWithoutAskingForAStockIdInItsBody() {
        val json = Gson().toJsonTree(ShoppingCreateLot(7, 2, "2026-12-31", BigDecimal("4.59"))).asJsonObject
        assertEquals(setOf("productId", "quantity", "expireDate", "purchaseUnitPrice"), json.keySet())
        assertEquals(7, json["productId"].asInt)
        assertEquals(BigDecimal("4.59"), json["purchaseUnitPrice"].asBigDecimal)
    }

    @Test fun interruptedPostOnlyReusesANewUnambiguousObservedLot() {
        val pending = ShoppingPendingMutation("group", 3, lotKey = "item/1", productId = 7,
            quantity = 2, expiry = "2026-12-31", baselineLotIds = listOf(10))
        val old = StockProductResponse(id = 10, productId = 7, stockId = 3, quantity = 2, expireDate = "2026-12-31")
        val observed = old.copy(id = 11)
        assertNull(recoveredShoppingLot(pending, listOf(old)))
        assertEquals(11, recoveredShoppingLot(pending, listOf(old, observed))?.id)
        assertNull(recoveredShoppingLot(pending, listOf(old, observed, observed.copy(id = 12))))
        assertEquals(11, recoveredShoppingLot(pending.copy(confirmedLotId = 11), listOf(old, observed, observed.copy(id = 12)))?.id)
    }

    @Test fun coreNestedStoragePlaceIsAuthoritative() {
        assertEquals("FREEZER", StockProductResponse(storagePlace = "FRIDGE",
            product = StockProductProductResponse(storagePlace = "FREEZER")).shoppingPlace())
    }

    @Test fun grainCanOnlyBeTransferredToPantry() {
        assertFalse(validShoppingPlace("GRAIN", "FRIDGE"))
        assertFalse(validShoppingPlace("GRAIN", "FREEZER"))
        assertTrue(validShoppingPlace("GRAIN", "PANTRY"))
        assertTrue(validShoppingPlace("MEAT", "FREEZER"))
    }

    @Test fun reconciliationNeverMatchesAnotherPurchasePrice() {
        val pending = ShoppingPendingMutation("group", 3, lotKey = "item/1", productId = 7,
            quantity = 2, expiry = "2026-12-31", baselineLotIds = emptyList(), purchaseUnitPrice = BigDecimal("4.59"))
        val lot = StockProductResponse(id = 11, productId = 7, stockId = 3, quantity = 2,
            expireDate = "2026-12-31", purchaseUnitPrice = 9.0)
        assertNull(recoveredShoppingLot(pending, listOf(lot)))
        assertNull(recoveredShoppingLot(pending.copy(confirmedLotId = 11), listOf(lot)))
        assertEquals(11, recoveredShoppingLot(pending, listOf(lot.copy(purchaseUnitPrice = 4.59)))?.id)
    }

    @Test fun transportFailuresRequireExplicitReconciliationButForbiddenDoesNotExpireSession() {
        assertTrue(shoppingMutationUncertain(null))
        assertTrue(shoppingMutationUncertain(408))
        assertTrue(shoppingMutationUncertain(503))
        assertFalse(shoppingMutationUncertain(403))
        assertFalse(shoppingMutationUncertain(422))
    }
}
