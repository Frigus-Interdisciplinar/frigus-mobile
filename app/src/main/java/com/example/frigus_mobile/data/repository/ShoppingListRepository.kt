package com.example.frigus_mobile.data.repository

import android.content.Context
import com.example.frigus_mobile.data.api.BffException
import com.example.frigus_mobile.data.api.RetrofitClient
import com.example.frigus_mobile.data.model.*
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext
import retrofit2.Call
import java.math.BigDecimal
import java.util.UUID

data class ShoppingContext(val groupId: String, val stocks: List<StockResponse>)

/** List CRUD is strictly local. BFF operations exist only for explicitly requested stock transfers. */
class ShoppingListRepository(context: Context) {
    private val application = context.applicationContext
    private val localPreferences = context.applicationContext.getSharedPreferences("shopping_list_prefs", Context.MODE_PRIVATE)
    private val local = ShoppingLocalStore(
        read = { localPreferences.getString(it, null) },
        write = { key, json -> shoppingDurablePreferenceWrite(key, json,
            { localPreferences.getString(it, null) },
            { storedKey, value -> localPreferences.edit().putString(storedKey, value).commit() }) }
    )
    private val mutations = context.applicationContext.getSharedPreferences("shopping_mutation_checkpoints", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val api get() = RetrofitClient.shoppingListService
    private var firebaseSync: ShoppingFirebaseSync? = null

    fun startSync(owner: String, scope: CoroutineScope, guard: () -> Boolean,
                  report: (ShoppingSyncStatus, Int, ApiError?) -> Unit) {
        val sync = firebaseSync ?: ShoppingFirebaseSync(application, local, scope).also { firebaseSync = it }
        sync.start(owner, guard, report)
    }
    fun syncNow() { firebaseSync?.syncNow() }
    fun stopSync() { firebaseSync?.stop() }
    fun close() { firebaseSync?.close() }

    fun localItems(owner: String): List<ShoppingListItem> = local.items(owner)
    fun localPendingCount(owner: String): Int = local.document(owner).pending.size
    fun saveLocalItem(owner: String, item: ShoppingListItem): List<ShoppingListItem> = local.mutate(owner) { items ->
        val index = items.indexOfFirst { it.id == item.id }
        if (index < 0) listOf(item) + items else items.toMutableList().apply { set(index, item) }
    }
    fun deleteLocalItem(owner: String, id: String): List<ShoppingListItem> =
        local.mutate(owner) { items -> items.filterNot { it.id == id } }

    suspend fun context(groupId: String?): ShoppingContext {
        val home = request(RetrofitClient.stockService.getInventoryHome(groupId))
        val id = home.groupId
        valid(uuid(id) && (groupId == null || groupId.equals(id, true)))
        val stocks = home.stocks
        valid(stocks != null && stocks.all { it != null && it.id > 0 &&
            (it.groupId == null || it.groupId.equals(id, true)) })
        return ShoppingContext(id!!, stocks.orEmpty().filterNotNull())
    }

    suspend fun catalog(groupId: String, guard: () -> Unit): List<ShoppingCatalogProduct> {
        val products = mutableListOf<ShoppingCatalogProduct>()
        var page = 0
        do {
            guard()
            val response = request(api.catalog(groupId, page))
            validatePage(response, page)
            valid(response.content.all { (it.id ?: 0) > 0 && !it.name.isNullOrBlank() &&
                (it.ownerGroupId == null || it.ownerGroupId.equals(groupId, true)) })
            products += response.content
            page++
        } while (page < response.totalPages)
        return products.distinctBy { it.id }
    }

    suspend fun createProduct(body: ShoppingCreateProduct): ShoppingCatalogProduct =
        request(api.createProduct(body), 201).also {
            valid((it.id ?: 0) > 0 && it.name == body.name && it.ownerGroupId == body.ownerGroupId &&
                it.category == body.category && it.unitOfMeasure == body.unitOfMeasure)
        }

    suspend fun createLot(stockId: Int, groupId: String, productId: Int, quantity: Int,
                          expiry: String, price: BigDecimal): StockProductResponse =
        request(api.createLot(stockId, groupId, ShoppingCreateLot(productId, quantity, expiry, price)), 201).also {
            valid((it.id ?: 0) > 0 && it.stockId == stockId && it.productId == productId &&
                it.quantity == quantity && it.expireDate?.take(10) == expiry && it.hasShoppingPrice(price))
        }

    suspend fun setLotPlace(id: Int, place: String, price: BigDecimal): StockProductResponse =
        request(api.setLotPlace(id, StockProductUpdateRequest(storagePlace = place))).also {
            valid(it.id == id && it.shoppingPlace() == place && it.hasShoppingPrice(price))
        }

    suspend fun lots(stockId: Int, guard: () -> Unit): List<StockProductResponse> {
        val lots = mutableListOf<StockProductResponse>()
        var page = 0
        do {
            guard()
            val response = request(api.lots(stockId, page))
            validatePage(response, page)
            valid(response.content.all { (it.id ?: 0) > 0 && it.stockId == stockId })
            lots += response.content
            page++
        } while (page < response.totalPages)
        return lots
    }

    fun hasPending(owner: String): Boolean = mutations.contains("pending/$owner")
    fun pending(owner: String): ShoppingPendingMutation? {
        val json = mutations.getString("pending/$owner", null) ?: return null
        val result = runCatching { gson.fromJson(json, ShoppingPendingMutation::class.java) }.getOrNull()
        valid(result != null && uuid(result.groupId) && result.stockId > 0)
        return result
    }

    fun savePending(owner: String, pending: ShoppingPendingMutation) {
        mutationWrite("pending/$owner", gson.toJson(pending))
    }
    fun clearPending(owner: String) { mutationWrite("pending/$owner", null) }

    fun lotCheckpoint(owner: String, key: String): StockProductResponse? =
        mutations.getString("lot/$owner/$key", null)?.let {
            val lot = runCatching { gson.fromJson(it, StockProductResponse::class.java) }.getOrNull()
            valid(lot != null && (lot.id ?: 0) > 0)
            lot
        }

    fun saveLotCheckpoint(owner: String, key: String, lot: StockProductResponse) {
        mutationWrite("lot/$owner/$key", gson.toJson(lot))
    }
    fun clearLotCheckpoint(owner: String, key: String) {
        mutationWrite("lot/$owner/$key", null)
    }

    private fun mutationWrite(key: String, json: String?) {
        valid(shoppingDurablePreferenceWrite(key, json, { mutations.getString(it, null) },
            { storedKey, value -> mutations.edit().putString(storedKey, value).commit() }))
    }

    private fun <T> validatePage(response: PageResponse<T>, page: Int) {
        valid(response.number == page && response.size > 0 && response.totalPages >= 0 &&
            response.totalElements >= response.content.size && response.content.size <= response.size &&
            (if (response.totalPages == 0) response.content.isEmpty() && response.totalElements == 0L
                else page < response.totalPages))
    }
    private fun uuid(id: String?): Boolean = id != null &&
        runCatching { UUID.fromString(id).toString().equals(id, true) }.getOrDefault(false)
    private fun valid(condition: Boolean) {
        if (!condition) throw BffException(ApiError(code = "INVALID_RESPONSE"), "")
    }
    private suspend fun <T> request(call: Call<T>, expected: Int = 200): T = withContext(Dispatchers.IO) {
        val response = call.execute()
        if (!response.isSuccessful) throw BffException(ApiError.parse(response), "")
        if (response.code() != expected || response.body() == null)
            throw BffException(ApiError(status = response.code(), code = "INVALID_RESPONSE"), "")
        response.body()!!
    }
}
