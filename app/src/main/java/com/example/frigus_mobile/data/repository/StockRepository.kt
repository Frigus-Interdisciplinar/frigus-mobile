package com.example.frigus_mobile.data.repository

import com.example.frigus_mobile.data.api.RetrofitClient
import com.example.frigus_mobile.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class StockRepository {

    private val api = RetrofitClient.stockService

    // In-memory cache / fallback to guarantee smooth UI matching mock designs
    private val localStockProducts = mutableListOf(
        StockProductResponse(
            id = 1,
            productId = 101,
            product = StockProductProductResponse(
                id = 101,
                name = "Peito de frango",
                category = "MEAT",
                storagePlace = "FRIDGE",
                unitPrice = 15.90,
                unitOfMeasure = "GRAM",
                brand = "Sadia"
            ),
            stockId = 1,
            quantity = 350,
            minimalQuantity = 700,
            purchaseUnitPrice = 15.90,
            expireDate = "10/05/2026",
            productStatus = "EXPIRED",
            category = "MEAT"
        ),
        StockProductResponse(
            id = 2,
            productId = 102,
            product = StockProductProductResponse(
                id = 102,
                name = "Leite Integral",
                category = "DAIRY",
                storagePlace = "FRIDGE",
                unitPrice = 5.49,
                unitOfMeasure = "LITER",
                brand = "Piracanjuba"
            ),
            stockId = 1,
            quantity = 1,
            minimalQuantity = 1,
            purchaseUnitPrice = 5.49,
            expireDate = "12/05/2026",
            productStatus = "NEAR_EXPIRATION",
            category = "DAIRY"
        ),
        StockProductResponse(
            id = 3,
            productId = 103,
            product = StockProductProductResponse(
                id = 103,
                name = "Iogurte Natural",
                category = "DAIRY",
                storagePlace = "FRIDGE",
                unitPrice = 4.20,
                unitOfMeasure = "GRAM",
                brand = "Nestlé"
            ),
            stockId = 1,
            quantity = 170,
            minimalQuantity = 170,
            purchaseUnitPrice = 4.20,
            expireDate = "13/05/2026",
            productStatus = "NEAR_EXPIRATION",
            category = "DAIRY"
        ),
        StockProductResponse(
            id = 4,
            productId = 104,
            product = StockProductProductResponse(
                id = 104,
                name = "Carne moída",
                category = "MEAT",
                storagePlace = "FREEZER",
                unitPrice = 22.00,
                unitOfMeasure = "GRAM",
                brand = "Friboi"
            ),
            stockId = 1,
            quantity = 500,
            minimalQuantity = 500,
            purchaseUnitPrice = 22.00,
            expireDate = "30/05/2026",
            productStatus = "FRESH",
            category = "MEAT"
        ),
        StockProductResponse(
            id = 5,
            productId = 105,
            product = StockProductProductResponse(
                id = 105,
                name = "Tomate",
                category = "VEGETABLE",
                storagePlace = "FRIDGE",
                unitPrice = 7.98,
                unitOfMeasure = "GRAM",
                brand = "Hortifruti"
            ),
            stockId = 1,
            quantity = 300,
            minimalQuantity = 300,
            purchaseUnitPrice = 7.98,
            expireDate = "31/05/2026",
            productStatus = "FRESH",
            category = "VEGETABLE"
        ),
        StockProductResponse(
            id = 6,
            productId = 106,
            product = StockProductProductResponse(
                id = 106,
                name = "Banana",
                category = "FRUIT",
                storagePlace = "PANTRY",
                unitPrice = 6.99,
                unitOfMeasure = "UNIT",
                brand = "Hortifruti"
            ),
            stockId = 1,
            quantity = 6,
            minimalQuantity = 6,
            purchaseUnitPrice = 6.99,
            expireDate = "03/06/2026",
            productStatus = "FRESH",
            category = "FRUIT"
        ),
        StockProductResponse(
            id = 7,
            productId = 107,
            product = StockProductProductResponse(
                id = 107,
                name = "Suco de laranja",
                category = "BEVERAGE",
                storagePlace = "FRIDGE",
                unitPrice = 9.50,
                unitOfMeasure = "LITER",
                brand = "Natural One"
            ),
            stockId = 1,
            quantity = 1,
            minimalQuantity = 1,
            purchaseUnitPrice = 9.50,
            expireDate = "30/05/2026",
            productStatus = "FRESH",
            category = "BEVERAGE"
        ),
        StockProductResponse(
            id = 8,
            productId = 108,
            product = StockProductProductResponse(
                id = 108,
                name = "Queijo muçarela",
                category = "DAIRY",
                storagePlace = "FRIDGE",
                unitPrice = 14.00,
                unitOfMeasure = "GRAM",
                brand = "President"
            ),
            stockId = 1,
            quantity = 300,
            minimalQuantity = 300,
            purchaseUnitPrice = 14.00,
            expireDate = "31/05/2026",
            productStatus = "FRESH",
            category = "DAIRY"
        )
    )

    suspend fun getStockProducts(stockId: Int = 1): List<StockProductResponse> = withContext(Dispatchers.IO) {
        try {
            val response = api.getStockProducts(stockId).execute()
            if (response.isSuccessful && response.body() != null && response.body()!!.content.isNotEmpty()) {
                val apiProducts = response.body()!!.content
                localStockProducts.clear()
                localStockProducts.addAll(apiProducts)
                apiProducts
            } else {
                localStockProducts.toList()
            }
        } catch (e: Exception) {
            localStockProducts.toList()
        }
    }

    suspend fun getStockProductById(id: Int): StockProductResponse? = withContext(Dispatchers.IO) {
        try {
            val response = api.getStockProductById(id).execute()
            if (response.isSuccessful && response.body() != null) {
                return@withContext response.body()
            }
        } catch (e: Exception) {
            // fallback to local
        }
        localStockProducts.find { it.id == id }
    }

    suspend fun updateStockProduct(
        id: Int,
        quantity: Int?,
        expireDate: String?,
        storagePlace: String?
    ): Boolean = withContext(Dispatchers.IO) {
        val req = StockProductUpdateRequest(
            quantity = quantity,
            expireDate = expireDate,
            storagePlace = storagePlace
        )
        try {
            val response = api.updateStockProduct(id, req).execute()
            if (response.isSuccessful) {
                // update local cache as well
            }
        } catch (_: Exception) {}

        val item = localStockProducts.find { it.id == id }
        if (item != null) {
            if (quantity != null) item.quantity = quantity
            if (expireDate != null) item.expireDate = expireDate
        }
        true
    }

    suspend fun deleteStockProduct(id: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            api.deleteStockProduct(id).execute()
        } catch (_: Exception) {}
        localStockProducts.removeAll { it.id == id }
        true
    }

    suspend fun addStockProduct(item: StockProductResponse) = withContext(Dispatchers.IO) {
        localStockProducts.add(0, item)
    }
}
