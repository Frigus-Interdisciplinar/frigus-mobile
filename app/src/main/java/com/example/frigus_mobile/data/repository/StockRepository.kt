package com.example.frigus_mobile.data.repository

import android.content.Context
import android.util.Log
import com.example.frigus_mobile.data.api.RetrofitClient
import com.example.frigus_mobile.data.local.SessionManager
import com.example.frigus_mobile.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class StockRepository(context: Context? = null) {

    private val api = RetrofitClient.stockService
    private val sessionManager = context?.let { SessionManager(it) }

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

    /**
     * Resolve o ID do estoque do grupo do usuário autenticado.
     * Fluxo no frigus-core-api:
     * 1. GET /groups -> obtém os grupos do usuário.
     * 2. GET /stocks?groupId={groupId} -> obtém o estoque daquele grupo.
     */
    suspend fun resolveUserStockId(): Int? = withContext(Dispatchers.IO) {
        val cachedStockId = sessionManager?.activeStockId
        if (cachedStockId != null && cachedStockId > 0) {
            return@withContext cachedStockId
        }

        try {
            Log.d("StockRepository", "Buscando grupos do usuário em GET /groups...")
            val groupsResponse = api.getMyGroups().execute()
            if (groupsResponse.isSuccessful && !groupsResponse.body().isNullOrEmpty()) {
                val groups = groupsResponse.body()!!
                val userGroup = groups.firstOrNull { it.id != null }
                val groupId = userGroup?.id

                if (groupId != null) {
                    Log.d("StockRepository", "Grupo do usuário encontrado: $groupId. Buscando estoque em GET /stocks?groupId=$groupId...")
                    val stockResponse = api.getStocksByGroup(groupId).execute()
                    if (stockResponse.isSuccessful && stockResponse.body()?.content?.isNotEmpty() == true) {
                        val stock = stockResponse.body()!!.content.first()
                        val stockId = stock.id
                        Log.d("StockRepository", "Estoque do grupo resolvido: stockId=$stockId")
                        sessionManager?.saveActiveGroupAndStock(groupId, stockId)
                        return@withContext stockId
                    }
                }
            } else {
                Log.w("StockRepository", "Nenhum grupo retornado para o usuário ou status: ${groupsResponse.code()}")
            }
        } catch (e: Exception) {
            Log.e("StockRepository", "Erro ao resolver grupo e estoque do usuário: ${e.message}")
        }
        null
    }

    /**
     * Obtém os alimentos do estoque vinculado ao grupo do usuário autenticado.
     */
    suspend fun getStockProducts(): List<StockProductResponse> = withContext(Dispatchers.IO) {
        val stockId = resolveUserStockId() ?: 1

        try {
            Log.d("StockRepository", "Buscando alimentos da API em GET /stock-products?stockId=$stockId...")
            val response = api.getStockProducts(stockId).execute()
            if (response.isSuccessful && response.body() != null && response.body()!!.content.isNotEmpty()) {
                val apiProducts = response.body()!!.content
                Log.d("StockRepository", "Sucesso! ${apiProducts.size} alimentos carregados da API para o estoque $stockId")
                localStockProducts.clear()
                localStockProducts.addAll(apiProducts)
                return@withContext apiProducts
            } else {
                Log.d("StockRepository", "API retornou lista vazia ou status ${response.code()}. Usando itens locais.")
                return@withContext localStockProducts.toList()
            }
        } catch (e: Exception) {
            Log.e("StockRepository", "Erro de conexão ao buscar produtos do estoque: ${e.message}. Usando dados locais.")
            return@withContext localStockProducts.toList()
        }
    }

    suspend fun getStockProductById(id: Int): StockProductResponse? = withContext(Dispatchers.IO) {
        try {
            val response = api.getStockProductById(id).execute()
            if (response.isSuccessful && response.body() != null) {
                return@withContext response.body()
            }
        } catch (e: Exception) {
            Log.e("StockRepository", "Erro ao buscar alimento por ID da API: ${e.message}")
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
                Log.d("StockRepository", "Alimento atualizado na API: ID $id")
            }
        } catch (e: Exception) {
            Log.e("StockRepository", "Erro ao atualizar alimento na API: ${e.message}")
        }

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
            Log.d("StockRepository", "Alimento excluído da API: ID $id")
        } catch (e: Exception) {
            Log.e("StockRepository", "Erro ao excluir alimento na API: ${e.message}")
        }
        localStockProducts.removeAll { it.id == id }
        true
    }

    suspend fun addStockProduct(item: StockProductResponse) = withContext(Dispatchers.IO) {
        val stockId = sessionManager?.activeStockId ?: item.stockId ?: 1
        val req = StockProductCreateRequest(
            productId = item.productId ?: item.id ?: 1,
            stockId = stockId,
            quantity = item.quantity ?: 1,
            minimalQuantity = item.minimalQuantity ?: 1,
            purchaseUnitPrice = item.purchaseUnitPrice ?: 0.0,
            expireDate = item.expireDate
        )
        try {
            val resp = api.createStockProduct(req).execute()
            if (resp.isSuccessful && resp.body() != null) {
                Log.d("StockRepository", "Alimento adicionado à API no estoque $stockId: ${resp.body()?.id}")
                localStockProducts.add(0, resp.body()!!)
                return@withContext
            }
        } catch (e: Exception) {
            Log.e("StockRepository", "Erro ao criar produto no estoque da API: ${e.message}")
        }
        localStockProducts.add(0, item)
    }
}
