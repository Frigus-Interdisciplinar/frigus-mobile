package com.example.frigus_mobile.ui.stock

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import com.example.frigus_mobile.data.api.BffException
import com.example.frigus_mobile.data.api.InvalidBffResponseException
import com.example.frigus_mobile.data.local.SessionManager
import com.example.frigus_mobile.data.model.ApiError
import com.example.frigus_mobile.data.model.StockProductResponse
import com.example.frigus_mobile.data.model.InventoryHomeResponse
import com.example.frigus_mobile.data.repository.StockRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.Locale
import java.util.UUID

data class CategoryBrowserState(
    val items: List<StockProductResponse> = emptyList(),
    val query: String = "",
    val category: String? = null,
    val storagePlace: String? = null,
    val loading: Boolean = false,
    val loaded: Boolean = false,
    val error: ApiError? = null
) {
    val visibleItems: List<StockProductResponse> get() {
        val search = query.trim().lowercase(Locale.ROOT)
        return items.filter { item -> (category == null || matchesStockCategory(item, category)) &&
            (search.isEmpty() || item.product?.name.orEmpty().lowercase(Locale.ROOT).contains(search)) }
    }
    fun count(category: String): Int = items.count { matchesStockCategory(it, category) }
}

/** The category screens from a8ed829, using validated pages of the active group. */
class CategoryBrowserViewModel(application: Application) : AndroidViewModel(application) {
    private val session = SessionManager(application)
    private val repository = StockRepository(application)
    private val mutableState = MutableLiveData(CategoryBrowserState())
    val state: LiveData<CategoryBrowserState> = mutableState
    private var request: Job? = null
    private var owner: String? = null
    private var group: String? = null

    fun search(query: String) { mutableState.value = mutableState.value?.copy(query = query) }
    fun configure(category: String?, storagePlace: String?) {
        mutableState.value = mutableState.value?.copy(category = category, storagePlace = storagePlace)
    }

    fun refresh() {
        if (!session.isLoggedIn) return
        val sameContext = owner == session.userId && group == session.activeGroupId
        if (sameContext && mutableState.value?.loading == true) return
        request?.cancel()
        owner = session.userId
        group = session.activeGroupId
        val generation = session.state.generation
        val currentOwner = owner
        val currentGroup = group
        fun active() = session.isLoggedIn && session.userId == currentOwner &&
            session.state.generation == generation && session.activeGroupId == currentGroup
        val previous = if (sameContext) mutableState.value ?: CategoryBrowserState()
            else CategoryBrowserState(category = mutableState.value?.category, storagePlace = mutableState.value?.storagePlace)
        mutableState.value = previous.copy(loading = true, error = null)
        request = viewModelScope.launch {
            try {
                val context = repository.getInventoryContext()
                if (!validCategoryContext(context)) throw InvalidBffResponseException()
                val stockIds = context.stocks.orEmpty().filterNotNull().map { it.id }.toSet()
                val items = mutableListOf<StockProductResponse>()
                if (!context.stocks.isNullOrEmpty()) {
                    var page = 0
                    do {
                        val result = repository.getProductsPage(page = page, size = 100,
                            storagePlace = previous.storagePlace, groupId = context.groupId)
                        if (result.content.any { it.stockId !in stockIds ||
                                (it.product?.ownerGroupId != null && it.product.ownerGroupId != context.groupId) })
                            throw InvalidBffResponseException()
                        items += result.content
                        page++
                    } while (page < result.totalPages)
                }
                if (active()) mutableState.value = (mutableState.value ?: previous).copy(
                    items = items.distinctBy { it.id }, loading = false, loaded = true, error = null)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                if (active()) mutableState.value = (mutableState.value ?: previous).copy(
                    items = emptyList(), loaded = false, loading = false, error = when (failure) {
                        is BffException -> failure.error
                        is InvalidBffResponseException -> ApiError(code = "INVALID_RESPONSE")
                        is IOException -> ApiError(code = "NETWORK_ERROR")
                        else -> ApiError(code = "INVALID_RESPONSE")
                    })
            }
        }
    }
}

internal fun matchesStockCategory(item: StockProductResponse, category: String): Boolean {
    val actual = item.category ?: item.product?.category
    return actual == category || (category == "VEGETABLE" && actual == "FRUIT")
}

internal fun validCategoryContext(context: InventoryHomeResponse): Boolean {
    val group = context.groupId ?: return false
    if (!runCatching { UUID.fromString(group).toString().equals(group, true) }.getOrDefault(false)) return false
    val stocks = context.stocks ?: return false
    return stocks.all { it != null && it.id > 0 && (it.groupId == null || it.groupId == group) } &&
        stocks.map { it?.id }.distinct().size == stocks.size
}
