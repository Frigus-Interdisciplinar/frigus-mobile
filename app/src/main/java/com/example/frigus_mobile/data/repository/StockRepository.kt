package com.example.frigus_mobile.data.repository

import android.content.Context
import android.util.Log
import com.example.frigus_mobile.data.api.RetrofitClient
import com.example.frigus_mobile.data.local.SessionManager
import com.example.frigus_mobile.data.model.*
import com.example.frigus_mobile.utils.StockQuantityTracker
import com.google.gson.JsonElement
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.IOException

class StockRepository(context: Context? = null) {

    private val api = RetrofitClient.stockService
    private val appContext = context?.applicationContext
    private val sessionManager = context?.let { SessionManager(it) }

    /**
     * Resolve o ID do estoque real vinculado ao grupo do usuário autenticado.
     * Fluxo no frigus-core-api:
     * 1. Verifica cache da sessão.
     * 2. GET /groups -> obtém os grupos do usuário.
     * 3. Se não houver grupo, POST /groups -> cria o grupo padrão.
     * 4. GET /stocks?groupId={groupId} -> obtém o estoque daquele grupo.
     * 5. Se não houver estoque, POST /stocks -> cria o estoque do grupo.
     */
    suspend fun resolveUserStockId(): Int? = withContext(NonCancellable) {
        val cachedStock = sessionManager?.activeStockId
        val cachedGroup = sessionManager?.activeGroupId

        // Se já temos grupo e estoque válidos em cache (maior que 1), usa direto
        if (cachedStock != null && cachedStock > 1 && !cachedGroup.isNullOrBlank()) {
            Log.d("StockRepository", "Utilizando estoque em cache: $cachedStock (grupo: $cachedGroup)")
            return@withContext cachedStock
        } else if (cachedStock == 1) {
            sessionManager?.saveActiveGroupAndStock(cachedGroup, null)
        }

        // 1. Busca grupos do usuário autenticado
        Log.d("StockRepository", "Consultando grupos em GET /groups...")
        val groupsResponse = api.getMyGroups()
        if (!groupsResponse.isSuccessful) {
            if (groupsResponse.code() == 401 || groupsResponse.code() == 403) {
                sessionManager?.saveActiveGroupAndStock(null, null)
                sessionManager?.clearSession()
            }
            val errBody = groupsResponse.errorBody()?.string()
            val msg = parseDisplayMessage(errBody)
                ?: "Não foi possível consultar os grupos do usuário (Código ${groupsResponse.code()})."
            throw IOException(msg)
        }

        var groupId = extractGroupId(groupsResponse.body())
        if (groupId.isNullOrBlank()) {
            groupId = cachedGroup
        }

        // Se o usuário ainda não tiver grupo vinculado, tenta criar o grupo doméstico inicial
        if (groupId.isNullOrBlank()) {
            Log.d("StockRepository", "Nenhum grupo em GET /groups. Criando grupo padrão em POST /groups...")
            val createGroupResp = api.createGroup(mapOf("name" to "Minha Casa"))
            if (createGroupResp.isSuccessful) {
                groupId = extractGroupId(createGroupResp.body())
                Log.d("StockRepository", "Grupo criado com sucesso: $groupId")
            } else {
                if (createGroupResp.code() == 401 || createGroupResp.code() == 403) {
                    sessionManager?.clearSession()
                }
                sessionManager?.saveActiveGroupAndStock(null, null)
                val errBody = createGroupResp.errorBody()?.string()
                val displayMsg = parseDisplayMessage(errBody)
                    ?: "Nenhum grupo doméstico encontrado para sua conta (Código ${createGroupResp.code()})."
                Log.w("StockRepository", "Não foi possível criar grupo: $displayMsg")
                throw IOException(displayMsg)
            }
        }

        val finalGroupId = groupId?.takeIf { it.isNotBlank() }
            ?: throw IOException("Não foi possível identificar o grupo doméstico da sua conta.")

        // 2. Busca o estoque vinculado ao grupo
        Log.d("StockRepository", "Buscando estoque para o grupo $finalGroupId em GET /stocks?groupId=$finalGroupId...")
        val stockResponse = api.getStocksByGroup(finalGroupId)
        var stockId: Int? = null

        if (stockResponse.isSuccessful) {
            stockId = extractStockId(stockResponse.body())
            Log.d("StockRepository", "Estoque retornado para o grupo $finalGroupId: $stockId")
        } else if (stockResponse.code() != 404) {
            if (stockResponse.code() == 401 || stockResponse.code() == 403) {
                sessionManager?.saveActiveGroupAndStock(null, null)
                sessionManager?.clearSession()
            }
            val errBody = stockResponse.errorBody()?.string()
            val msg = parseDisplayMessage(errBody)
                ?: "Falha ao consultar estoque do grupo (Código ${stockResponse.code()})."
            throw IOException(msg)
        }

        // 3. Se o grupo ainda não tiver estoque, tenta criar o estoque
        if (stockId == null) {
            Log.d("StockRepository", "Grupo $finalGroupId sem estoque. Criando estoque em POST /stocks...")
            val createStockResp = api.createStock(
                StockCreateRequest(groupId = finalGroupId, name = "Estoque Principal")
            )
            if (createStockResp.isSuccessful) {
                stockId = extractStockId(createStockResp.body())
                Log.d("StockRepository", "Estoque criado com sucesso: $stockId")
            } else if (createStockResp.code() == 409) {
                val retryStock = api.getStocksByGroup(finalGroupId)
                stockId = extractStockId(retryStock.body())
            } else {
                if (createStockResp.code() == 401 || createStockResp.code() == 403) {
                    sessionManager?.saveActiveGroupAndStock(null, null)
                    sessionManager?.clearSession()
                }
                val errBody = createStockResp.errorBody()?.string()
                val displayMsg = parseDisplayMessage(errBody)
                    ?: "Falha ao criar estoque do grupo (Código ${createStockResp.code()})."
                Log.e("StockRepository", "Erro ao criar estoque: $displayMsg")
                throw IOException(displayMsg)
            }
        }

        if (stockId == null || stockId <= 0) {
            sessionManager?.saveActiveGroupAndStock(finalGroupId, null)
            throw IOException("Nenhum estoque encontrado para o grupo $finalGroupId.")
        }

        sessionManager?.saveActiveGroupAndStock(finalGroupId, stockId)
        return@withContext stockId
    }

    /**
     * Obtém os alimentos reais do estoque vinculado ao grupo do usuário autenticado na API.
     */
    suspend fun getStockProducts(): List<StockProductResponse> {
        val stockId = try {
            resolveUserStockId()
        } catch (e: Exception) {
            Log.w("StockRepository", "Não foi possível obter estoque ativo: ${e.message}")
            return emptyList()
        } ?: return emptyList()

        Log.d("StockRepository", "Buscando alimentos da API em GET /stock-products?stockId=$stockId...")
        val response = api.getStockProducts(stockId)
        if (response.isSuccessful) {
            val apiProducts = response.body()?.content.orEmpty()
            Log.d("StockRepository", "Sucesso! ${apiProducts.size} alimentos carregados da API para o estoque $stockId")
            return apiProducts
        } else {
            if (response.code() == 401 || response.code() == 403) {
                sessionManager?.saveActiveGroupAndStock(null, null)
                sessionManager?.clearSession()
            } else if (response.code() == 404) {
                sessionManager?.saveActiveGroupAndStock(null, null)
            }
            val err = parseDisplayMessage(response.errorBody()?.string())
                ?: "Falha ao buscar alimentos na API (Código ${response.code()})."
            throw IOException(err)
        }
    }

    suspend fun getStockProductById(id: Int): StockProductResponse? {
        val response = api.getStockProductById(id)
        if (response.isSuccessful) {
            return response.body()
        } else {
            if (response.code() == 401 || response.code() == 403) {
                sessionManager?.clearSession()
            }
            throw IOException("Falha ao buscar detalhes do alimento na API (Código ${response.code()}).")
        }
    }

    suspend fun createProduct(
        name: String,
        category: String,
        storagePlace: String,
        unitPrice: Double = 1.0,
        unitOfMeasure: String = "UNIT"
    ): StockProductProductResponse {
        var groupId = sessionManager?.activeGroupId
        if (groupId.isNullOrBlank()) {
            resolveUserStockId()
            groupId = sessionManager?.activeGroupId
        }
        val request = com.example.frigus_mobile.data.model.ProductCreateRequest(
            name = name.trim(),
            category = category,
            storagePlace = storagePlace,
            unitPrice = unitPrice,
            unitOfMeasure = unitOfMeasure,
            ownerGroupId = groupId
        )
        val response = api.createProduct(request)
        if (response.isSuccessful && response.body() != null) {
            return response.body()!!
        }
        if (response.code() == 401 || response.code() == 403) {
            sessionManager?.clearSession()
        }
        val errBody = response.errorBody()?.string()
        val displayMsg = parseDisplayMessage(errBody)
            ?: "Falha ao cadastrar novo produto (Código ${response.code()})."
        throw IOException(displayMsg)
    }

    suspend fun getCatalogProducts(): List<StockProductProductResponse> {
        try {
            val groupId = sessionManager?.activeGroupId
            val response = api.getCatalogProducts(page = 0, size = 100, groupId = groupId)
            if (response.isSuccessful) {
                return response.body()?.content ?: emptyList()
            }
            if (response.code() == 401 || response.code() == 403) {
                sessionManager?.clearSession()
            }
        } catch (e: Exception) {
            Log.w("StockRepository", "Erro ao buscar catálogo: ${e.message}")
        }
        return emptyList()
    }

    suspend fun updateStockProduct(
        id: Int,
        quantity: Int?,
        expireDate: String?,
        storagePlace: String?
    ): Boolean {
        val isoExpireDate = expireDate?.let { normalizeToIsoDate(it) }
        val req = StockProductUpdateRequest(
            quantity = quantity,
            expireDate = isoExpireDate,
            storagePlace = storagePlace
        )
        val response = api.updateStockProduct(id, req)
        if (response.isSuccessful) {
            Log.d("StockRepository", "Alimento atualizado na API: ID $id")
            return true
        } else {
            if (response.code() == 401 || response.code() == 403) {
                sessionManager?.clearSession()
            }
            val err = parseDisplayMessage(response.errorBody()?.string())
                ?: "Falha ao atualizar alimento na API (Código ${response.code()})."
            throw IOException(err)
        }
    }

    suspend fun deleteStockProduct(id: Int): Boolean {
        val response = api.deleteStockProduct(id)
        if (response.isSuccessful) {
            Log.d("StockRepository", "Alimento excluído da API: ID $id")
            return true
        } else {
            if (response.code() == 401 || response.code() == 403) {
                sessionManager?.clearSession()
            }
            val err = parseDisplayMessage(response.errorBody()?.string())
                ?: "Falha ao excluir alimento na API (Código ${response.code()})."
            throw IOException(err)
        }
    }

    suspend fun addStockProduct(item: StockProductResponse) {
        val stockId = resolveUserStockId() ?: item.stockId ?: throw IOException("Nenhum estoque ativo encontrado para sua conta.")
        val isoExpireDate = normalizeToIsoDate(item.expireDate)
        val validPrice = if (item.purchaseUnitPrice != null && item.purchaseUnitPrice > 0.0) {
            item.purchaseUnitPrice
        } else {
            1.0
        }

        val prodId = item.productId ?: item.id ?: throw IllegalArgumentException("ID do produto é obrigatório para cadastrar no estoque.")
        val req = StockProductCreateRequest(
            productId = prodId,
            stockId = stockId,
            quantity = item.quantity ?: 1,
            minimalQuantity = item.minimalQuantity ?: 1,
            purchaseUnitPrice = validPrice,
            expireDate = isoExpireDate
        )

        Log.d("StockRepository", "Cadastrando produto no estoque: productId=${req.productId}, stockId=$stockId, expireDate=$isoExpireDate, price=$validPrice")
        val resp = api.createStockProduct(req)

        if (resp.isSuccessful) {
            val createdId = resp.body()?.id
            if (createdId != null && appContext != null) {
                StockQuantityTracker.setInitialQuantity(appContext, createdId, item.quantity ?: 1)
            }
            if (createdId != null && !item.storagePlace.isNullOrBlank()) {
                try {
                    api.updateStockProduct(createdId, StockProductUpdateRequest(storagePlace = item.storagePlace))
                } catch (e: Exception) {
                    Log.w("StockRepository", "Não foi possível atualizar storagePlace após cadastro: ${e.message}")
                }
            }
            Log.d("StockRepository", "Alimento adicionado à API no estoque $stockId: $createdId")
            return
        }

        // Se der conflito (409), significa que o produto com a mesma validade já existe neste estoque.
        // Nesse caso, soma a quantidade ao item existente.
        if (resp.code() == 409) {
            try {
                val stockProductsResp = api.getStockProducts(stockId)
                if (stockProductsResp.isSuccessful) {
                    val existing = stockProductsResp.body()?.content?.firstOrNull {
                        it.productId == req.productId && (it.expireDate == isoExpireDate || it.expireDate == item.expireDate)
                    }
                    if (existing?.id != null) {
                        val newQty = (existing.quantity ?: 0) + req.quantity
                        val updateResp = api.updateStockProduct(
                            existing.id,
                            StockProductUpdateRequest(quantity = newQty, expireDate = isoExpireDate, storagePlace = item.storagePlace)
                        )
                        if (updateResp.isSuccessful) {
                            Log.d("StockRepository", "Item existente no estoque atualizado: ID ${existing.id}, Nova Qtd $newQty")
                            return
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("StockRepository", "Falha ao somar quantidade de item já existente: ${e.message}")
            }
        }

        if (resp.code() == 401 || resp.code() == 403) {
            sessionManager?.saveActiveGroupAndStock(null, null)
            sessionManager?.clearSession()
        }

        val errorBodyStr = resp.errorBody()?.string()
        Log.e("StockRepository", "Falha ao cadastrar produto no estoque (Código ${resp.code()}): $errorBodyStr")
        val displayMsg = parseDisplayMessage(errorBodyStr)
            ?: "Falha ao cadastrar produto no estoque (Código ${resp.code()})."
        throw IOException(displayMsg)
    }

    private fun JsonElement?.safeString(): String? {
        if (this == null || this.isJsonNull) return null
        return try {
            if (this.isJsonPrimitive) this.asString.takeIf { it.isNotBlank() } else null
        } catch (_: Exception) {
            null
        }
    }

    private fun JsonElement?.safeInt(): Int? {
        if (this == null || this.isJsonNull) return null
        return try {
            if (this.isJsonPrimitive) this.asInt.takeIf { it > 0 } else null
        } catch (_: Exception) {
            null
        }
    }

    private fun extractStockId(json: JsonElement?): Int? {
        if (json == null || json.isJsonNull) return null
        if (json.isJsonArray) {
            for (element in json.asJsonArray) {
                if (element != null && element.isJsonObject) {
                    val obj = element.asJsonObject
                    val id = obj.get("id").safeInt() ?: obj.get("stockId").safeInt()
                    if (id != null) return id
                }
            }
        }
        if (json.isJsonObject) {
            val obj = json.asJsonObject
            if (obj.has("content") && obj.get("content").isJsonArray) {
                return extractStockId(obj.get("content"))
            }
            val id = obj.get("id").safeInt() ?: obj.get("stockId").safeInt()
            if (id != null) return id
        }
        return null
    }

    private fun extractGroupId(json: JsonElement?): String? {
        if (json == null || json.isJsonNull) return null
        if (json.isJsonArray) {
            val arr = json.asJsonArray
            // Prioriza grupo onde o usuário é dono
            for (element in arr) {
                if (element != null && element.isJsonObject) {
                    val obj = element.asJsonObject
                    val isOwner = try { obj.get("isOwner")?.asBoolean ?: false } catch (_: Exception) { false }
                    val id = obj.get("id").safeString() ?: obj.get("groupId").safeString()
                    if (isOwner && id != null) return id
                }
            }
            // Caso contrário, pega o primeiro id válido
            for (element in arr) {
                if (element != null && element.isJsonObject) {
                    val obj = element.asJsonObject
                    val id = obj.get("id").safeString() ?: obj.get("groupId").safeString()
                    if (id != null) return id
                }
            }
        }
        if (json.isJsonObject) {
            val obj = json.asJsonObject
            if (obj.has("content") && obj.get("content").isJsonArray) {
                return extractGroupId(obj.get("content"))
            }
            return obj.get("id").safeString() ?: obj.get("groupId").safeString()
        }
        return null
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
