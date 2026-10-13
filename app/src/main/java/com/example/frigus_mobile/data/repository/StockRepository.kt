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
        val response = api.updateStockProduct(id, req).execute()
        if (response.isSuccessful) {
            Log.d("StockRepository", "Alimento atualizado na API: ID $id")
            return@withContext true
        } else {
            throw IOException("Falha ao atualizar alimento na API (Código ${response.code()}).")
        }
    }

    suspend fun deleteStockProduct(id: Int): Boolean = withContext(Dispatchers.IO) {
        val response = api.deleteStockProduct(id).execute()
        if (response.isSuccessful) {
            Log.d("StockRepository", "Alimento excluído da API: ID $id")
            return@withContext true
        } else {
            throw IOException("Falha ao excluir alimento na API (Código ${response.code()}).")
        }
    }

    suspend fun addStockProduct(item: StockProductResponse) = withContext(Dispatchers.IO) {
        val stockId = resolveUserStockId() ?: item.stockId ?: throw IOException("Nenhum estoque vinculado ao grupo do usuário.")
        val req = StockProductCreateRequest(
            productId = item.productId ?: item.id ?: 1,
            stockId = stockId,
            quantity = item.quantity ?: 1,
            minimalQuantity = item.minimalQuantity ?: 1,
            purchaseUnitPrice = item.purchaseUnitPrice ?: 0.0,
            expireDate = item.expireDate
        )
        val resp = api.createStockProduct(req).execute()
        if (!resp.isSuccessful) {
            throw IOException("Falha ao cadastrar produto no estoque (Código ${resp.code()}).")
        }
        Log.d("StockRepository", "Alimento adicionado à API no estoque $stockId: ${resp.body()?.id}")
    }
}

