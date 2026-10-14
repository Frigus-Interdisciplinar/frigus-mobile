package com.example.frigus_mobile.data.repository

import android.content.Context
import android.util.Log
import com.example.frigus_mobile.data.api.RetrofitClient
import com.example.frigus_mobile.data.local.SessionManager
import com.example.frigus_mobile.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

class StockRepository(context: Context? = null) {

    private val api = RetrofitClient.stockService
    private val sessionManager = context?.let { SessionManager(it) }

    /**
     * Resolve o ID do estoque do grupo do usuário autenticado.
     * Fluxo no frigus-core-api:
     * 1. GET /groups -> obtém os grupos do usuário.
     * 2. GET /stocks?groupId={groupId} -> obtém o estoque daquele grupo.
     */
    suspend fun resolveUserStockId(): Int? = withContext(Dispatchers.IO) {
        Log.d("StockRepository", "Buscando grupos do usuário em GET /groups...")
        val groupsResponse = api.getMyGroups().execute()
        if (!groupsResponse.isSuccessful) {
            throw IOException("Não foi possível consultar os grupos do usuário (Código ${groupsResponse.code()}).")
        }

        val groups = groupsResponse.body().orEmpty()
        var groupId = groups.firstOrNull { it.id != null }?.id
        if (groupId == null) {
            try {
                val createGroupResp = api.createGroup(
                    mapOf("name" to "Minha Casa", "description" to "Grupo principal")
                ).execute()
                if (createGroupResp.isSuccessful) {
                    groupId = createGroupResp.body()?.id
                }
            } catch (e: Exception) {
                Log.w("StockRepository", "Não foi possível criar grupo automático: ${e.message}")
            }
        }
        if (groupId == null) {
            sessionManager?.saveActiveGroupAndStock(null, null)
            return@withContext null
        }

        Log.d("StockRepository", "Grupo encontrado: $groupId. Buscando estoque em GET /stocks?groupId=$groupId...")
        val stockResponse = api.getStocksByGroup(groupId).execute()
        if (!stockResponse.isSuccessful) {
            throw IOException("Não foi possível consultar o estoque do grupo (Código ${stockResponse.code()}).")
        }

        val existingStock = stockResponse.body()?.content?.firstOrNull()
        val stockId = if (existingStock?.id != null) {
            existingStock.id
        } else {
            val createResp = api.createStock(StockCreateRequest(groupId = groupId, name = "Estoque Principal")).execute()
            if (createResp.isSuccessful) createResp.body()?.id else null
        }

        sessionManager?.saveActiveGroupAndStock(groupId, stockId)
        return@withContext stockId
    }

    /**
     * Obtém os alimentos reais do estoque vinculado ao grupo do usuário autenticado na API.
     */
    suspend fun getStockProducts(): List<StockProductResponse> = withContext(Dispatchers.IO) {
        val stockId = resolveUserStockId() ?: return@withContext emptyList()

        Log.d("StockRepository", "Buscando alimentos da API em GET /stock-products?stockId=$stockId...")
        val response = api.getStockProducts(stockId).execute()
        if (response.isSuccessful) {
            val apiProducts = response.body()?.content.orEmpty()
            Log.d("StockRepository", "Sucesso! ${apiProducts.size} alimentos carregados da API para o estoque $stockId")
            return@withContext apiProducts
        } else {
            throw IOException("Falha ao buscar alimentos na API (Código ${response.code()}).")
        }
    }

    suspend fun getStockProductById(id: Int): StockProductResponse? = withContext(Dispatchers.IO) {
        val response = api.getStockProductById(id).execute()
        if (response.isSuccessful) {
            return@withContext response.body()
        } else {
            throw IOException("Falha ao buscar detalhes do alimento na API (Código ${response.code()}).")
        }
    }

    suspend fun getCatalogProducts(): List<StockProductProductResponse> = withContext(Dispatchers.IO) {
        try {
            val response = api.getCatalogProducts().execute()
            if (response.isSuccessful) {
                return@withContext response.body()?.content ?: emptyList<StockProductProductResponse>()
            }
        } catch (e: Exception) {
            Log.w("StockRepository", "Erro ao buscar catálogo: ${e.message}")
        }
        emptyList<StockProductProductResponse>()
    }

    suspend fun updateStockProduct(
        id: Int,
        quantity: Int?,
        expireDate: String?,
        storagePlace: String?
    ): Boolean = withContext(Dispatchers.IO) {
        val isoExpireDate = expireDate?.let { normalizeToIsoDate(it) }
        val req = StockProductUpdateRequest(
            quantity = quantity,
            expireDate = isoExpireDate,
            storagePlace = storagePlace
        )
        val response = api.updateStockProduct(id, req).execute()
        if (response.isSuccessful) {
            Log.d("StockRepository", "Alimento atualizado na API: ID $id")
            return@withContext true
        } else {
            val err = parseDisplayMessage(response.errorBody()?.string())
                ?: "Falha ao atualizar alimento na API (Código ${response.code()})."
            throw IOException(err)
        }
    }

    suspend fun deleteStockProduct(id: Int): Boolean = withContext(Dispatchers.IO) {
        val response = api.deleteStockProduct(id).execute()
        if (response.isSuccessful) {
            Log.d("StockRepository", "Alimento excluído da API: ID $id")
            return@withContext true
        } else {
            val err = parseDisplayMessage(response.errorBody()?.string())
                ?: "Falha ao excluir alimento na API (Código ${response.code()})."
            throw IOException(err)
        }
    }

    suspend fun addStockProduct(item: StockProductResponse) = withContext(Dispatchers.IO) {
        val stockId = resolveUserStockId() ?: item.stockId ?: throw IOException("Nenhum estoque vinculado ao grupo do usuário.")
        val isoExpireDate = normalizeToIsoDate(item.expireDate)
        val validPrice = if (item.purchaseUnitPrice != null && item.purchaseUnitPrice > 0.0) {
            item.purchaseUnitPrice
        } else {
            1.0
        }

        val req = StockProductCreateRequest(
            productId = item.productId ?: item.id ?: 1,
            stockId = stockId,
            quantity = item.quantity ?: 1,
            minimalQuantity = item.minimalQuantity ?: 1,
            purchaseUnitPrice = validPrice,
            expireDate = isoExpireDate
        )

        Log.d("StockRepository", "Cadastrando produto no estoque: productId=${req.productId}, stockId=$stockId, expireDate=$isoExpireDate, price=$validPrice")
        val resp = api.createStockProduct(req).execute()

        if (resp.isSuccessful) {
            Log.d("StockRepository", "Alimento adicionado à API no estoque $stockId: ${resp.body()?.id}")
            return@withContext
        }

        // Se der conflito (409), significa que o produto com a mesma validade já existe neste estoque.
        // Nesse caso, soma a quantidade ao item existente.
        if (resp.code() == 409) {
            try {
                val stockProductsResp = api.getStockProducts(stockId).execute()
                if (stockProductsResp.isSuccessful) {
                    val existing = stockProductsResp.body()?.content?.firstOrNull {
                        it.productId == req.productId && (it.expireDate == isoExpireDate || it.expireDate == item.expireDate)
                    }
                    if (existing?.id != null) {
                        val newQty = (existing.quantity ?: 0) + req.quantity
                        val updateResp = api.updateStockProduct(
                            existing.id,
                            StockProductUpdateRequest(quantity = newQty, expireDate = isoExpireDate)
                        ).execute()
                        if (updateResp.isSuccessful) {
                            Log.d("StockRepository", "Item existente no estoque atualizado: ID ${existing.id}, Nova Qtd $newQty")
                            return@withContext
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("StockRepository", "Falha ao somar quantidade de item já existente: ${e.message}")
            }
        }

        val errorBodyStr = resp.errorBody()?.string()
        Log.e("StockRepository", "Falha ao cadastrar produto no estoque (Código ${resp.code()}): $errorBodyStr")
        val displayMsg = parseDisplayMessage(errorBodyStr)
            ?: "Falha ao cadastrar produto no estoque (Código ${resp.code()})."
        throw IOException(displayMsg)
    }

    private fun normalizeToIsoDate(dateStr: String?): String {
        if (dateStr.isNullOrBlank()) {
            return java.time.LocalDate.now().plusDays(15).toString()
        }
        val trimmed = dateStr.trim()
        if (trimmed.matches(Regex("""^\d{4}-\d{2}-\d{2}$"""))) {
            return trimmed
        }
        if (trimmed.matches(Regex("""^\d{2}/\d{2}/\d{4}$"""))) {
            val parts = trimmed.split("/")
            return "${parts[2]}-${parts[1]}-${parts[0]}"
        }
        return try {
            java.time.LocalDate.parse(trimmed).toString()
        } catch (e: Exception) {
            java.time.LocalDate.now().plusDays(15).toString()
        }
    }

    private fun parseDisplayMessage(errorJson: String?): String? {
        if (errorJson.isNullOrBlank()) return null
        return try {
            val json = org.json.JSONObject(errorJson)
            json.optString("displayMessage").takeIf { it.isNotBlank() }
                ?: json.optString("message").takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            null
        }
    }
}

