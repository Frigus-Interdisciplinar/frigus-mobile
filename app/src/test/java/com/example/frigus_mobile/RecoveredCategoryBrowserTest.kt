package com.example.frigus_mobile

import com.example.frigus_mobile.data.api.MobileHttp
import com.example.frigus_mobile.data.model.StockProductProductResponse
import com.example.frigus_mobile.data.model.StockProductResponse
import com.example.frigus_mobile.ui.stock.CategoryBrowserState
import com.example.frigus_mobile.ui.stock.matchesStockCategory
import com.example.frigus_mobile.ui.stock.validCategoryContext
import com.example.frigus_mobile.data.model.InventoryHomeResponse
import com.example.frigus_mobile.data.model.StockResponse
import org.junit.Assert.*
import org.junit.Test

class RecoveredCategoryBrowserTest {
    @Test fun produceIncludesFruitAndVegetablesWithoutIncludingOtherCategories() {
        val fruit = lot(1, "FRUIT", "Maçã")
        val vegetable = lot(2, "VEGETABLE", "Cenoura")
        val dairy = lot(3, "DAIRY", "Leite")
        val state = CategoryBrowserState(items = listOf(fruit, vegetable, dairy), category = "VEGETABLE")
        assertEquals(listOf(fruit, vegetable), state.visibleItems)
        assertEquals(2, state.count("VEGETABLE"))
        assertFalse(matchesStockCategory(dairy, "VEGETABLE"))
        assertEquals(listOf(vegetable), state.copy(query = "  CENOURA  ").visibleItems)
    }

    @Test fun lotCategoryTakesPrecedenceOverCatalogCategory() {
        val item = lot(1, "DAIRY", "Produto").copy(product = StockProductProductResponse(category = "FRUIT"))
        assertFalse(matchesStockCategory(item, "VEGETABLE"))
    }

    @Test fun missingOrMalformedContextCannotBecomeAnEmptyCategoryCount() {
        val group = "11111111-1111-4111-8111-111111111111"
        assertFalse(validCategoryContext(InventoryHomeResponse()))
        assertFalse(validCategoryContext(InventoryHomeResponse(groupId = group)))
        assertFalse(validCategoryContext(InventoryHomeResponse(groupId = "invalid", stocks = emptyList())))
        assertFalse(validCategoryContext(InventoryHomeResponse(groupId = group,
            stocks = listOf(StockResponse(id = 1, groupId = "22222222-2222-4222-8222-222222222222")))))
        assertTrue(validCategoryContext(InventoryHomeResponse(groupId = group, stocks = emptyList())))
    }

    @Test fun recoveredContractsStayProtectedAndDoNotBroadenTheCoreProxy() {
        val id = "11111111-1111-4111-8111-111111111111"
        val purchases = "/mobile/commercial/groups/$id/food-purchases"
        assertTrue(MobileHttp.allowed("POST", purchases))
        assertFalse(MobileHttp.allowed("GET", purchases))
        listOf("GET", "PUT", "DELETE").forEach { assertTrue(MobileHttp.allowed(it, "$purchases/$id")) }
        val conversation = "/mobile/core/conversations/$id"
        assertTrue(MobileHttp.allowed("GET", conversation))
        assertTrue(MobileHttp.allowed("POST", "$conversation/messages"))
        assertTrue(MobileHttp.allowed("POST", "$conversation/messages/1/read"))
        assertFalse(MobileHttp.allowed("PUT", conversation))
        assertFalse(MobileHttp.public(conversation))
        assertFalse(MobileHttp.public(purchases))
        assertFalse(MobileHttp.allowed("POST", "/mobile/core/groups/$id/food-purchases"))
    }

    @Test fun networkCommitTimeoutKeepsMutationReplayDisabled() {
        val client = MobileHttp.client(MobileHttp.origin("https://bff.example/", false), { null }, { })
        assertEquals(40_000, client.connectTimeoutMillis())
        assertFalse(client.retryOnConnectionFailure())
        assertFalse(client.followRedirects())
    }

    private fun lot(id: Int, category: String, name: String) = StockProductResponse(
        id = id, category = category, product = StockProductProductResponse(name = name, category = category))
}
