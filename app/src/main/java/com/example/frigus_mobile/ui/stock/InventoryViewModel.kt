package com.example.frigus_mobile.ui.stock

import android.app.Application
import android.os.Bundle
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.frigus_mobile.data.api.BffException
import com.example.frigus_mobile.data.local.SessionManager
import com.example.frigus_mobile.data.model.ApiError
import com.example.frigus_mobile.data.model.InventoryHomeResponse
import com.example.frigus_mobile.data.model.StockProductResponse
import com.example.frigus_mobile.data.repository.StockRepository
import com.google.gson.JsonParseException
import com.google.gson.stream.MalformedJsonException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.IOException

data class InventoryFilters(
    val stockId: Int? = null,
    val storagePlace: String? = null,
    val category: String? = null,
    val query: String = ""
)

data class InventoryUiState(
    val filters: InventoryFilters = InventoryFilters(),
    val home: InventoryHomeResponse? = null,
    val items: List<StockProductResponse> = emptyList(),
    val loading: Boolean = false,
    val nextPage: Int = 0,
    val totalPages: Int = 0,
    val totalElements: Long = 0,
    val error: ApiError? = null,
    val showAllStatusesNotice: Boolean = false,
    val scrollRevision: Long = 0
) {
    val hasMore: Boolean get() = nextPage < totalPages
}

class InventoryViewModel(application: Application) : AndroidViewModel(application) {
    private val session = SessionManager(application)
    private val repository = StockRepository()
    private val mutableState = MutableLiveData(InventoryUiState())
    val state: LiveData<InventoryUiState> = mutableState
    private var request: Job? = null
    private var started = false
    private var awaitingLotDetail = false

    fun expectLotDetailReturn() { awaitingLotDetail = true }

    fun consumeLotDetailReturn(): Boolean {
        val pending = awaitingLotDetail
        awaitingLotDetail = false
        return pending
    }
    var scrollPosition: Int = 0
        private set

    fun saveScrollPosition(position: Int, revision: Long) {
        if (mutableState.value?.scrollRevision == revision) scrollPosition = position.coerceAtLeast(0)
    }

    fun saveNavigationState(): Bundle = Bundle().apply {
        val current = mutableState.value ?: return@apply
        current.filters.stockId?.let { putInt("stock_id", it) }
        putString("storage_place", current.filters.storagePlace)
        putString("category", current.filters.category)
        putString("query", current.filters.query)
        putBoolean("all_statuses_notice", current.showAllStatusesNotice)
    }

    fun restoreNavigationState(saved: Bundle?) {
        if (started || saved == null) return
        val places = getApplication<Application>().resources.getStringArray(
            com.example.frigus_mobile.R.array.inventory_place_codes
        ).filter(String::isNotEmpty)
        val categories = getApplication<Application>().resources.getStringArray(
            com.example.frigus_mobile.R.array.inventory_category_codes
        ).filter(String::isNotEmpty)
        mutableState.value = InventoryUiState(
            filters = InventoryFilters(
                stockId = saved.getInt("stock_id", -1).takeIf { it > 0 },
                storagePlace = saved.getString("storage_place")?.takeIf { it in places },
                category = saved.getString("category")?.takeIf { it in categories },
                query = saved.getString("query").orEmpty()
            ),
            showAllStatusesNotice = saved.getBoolean("all_statuses_notice")
        )
    }

    fun openShortcut(storagePlace: String? = null, showAllStatusesNotice: Boolean = false) {
        if (!session.isLoggedIn) return
        val current = mutableState.value ?: return
        val place = storagePlace?.takeIf { it in setOf("FRIDGE", "FREEZER", "PANTRY") }
        started = true
        scrollPosition = 0
        mutableState.value = current.copy(
            filters = InventoryFilters(storagePlace = place),
            showAllStatusesNotice = showAllStatusesNotice,
            scrollRevision = current.scrollRevision + 1
        )
        load(reset = true)
    }

    fun loadIfNeeded() {
        if (!started) {
            started = true
            refresh()
        }
    }

    fun changeFilters(filters: InventoryFilters) {
        val current = mutableState.value ?: return
        if (filters == current.filters) return
        scrollPosition = 0
        mutableState.value = current.copy(
            filters = filters,
            scrollRevision = current.scrollRevision + 1
        )
        load(reset = true, debounce = filters.query != current.filters.query)
    }

    fun refresh() {
        val current = mutableState.value ?: return
        scrollPosition = 0
        mutableState.value = current.copy(scrollRevision = current.scrollRevision + 1)
        load(reset = true, refreshContext = true)
    }

    fun loadMore() {
        val current = mutableState.value ?: return
        if (!current.loading && current.hasMore && current.error == null) load(reset = false)
    }

    fun retry() {
        val current = mutableState.value ?: return
        if (current.loading) return
        load(reset = current.nextPage == 0, refreshContext = current.home == null)
    }

    private fun load(reset: Boolean, refreshContext: Boolean = false, debounce: Boolean = false) {
        if (!session.isLoggedIn) return
        request?.cancel()
        val generation = session.state.generation
        var snapshot = mutableState.value ?: return
        snapshot = snapshot.copy(
            home = if (refreshContext) null else snapshot.home,
            items = if (reset) emptyList() else snapshot.items,
            nextPage = if (reset) 0 else snapshot.nextPage,
            totalPages = if (reset) 0 else snapshot.totalPages,
            totalElements = if (reset) 0 else snapshot.totalElements,
            loading = true,
            error = null
        )
        mutableState.value = snapshot
        request = viewModelScope.launch {
            try {
                if (debounce) delay(350)
                val home = snapshot.home ?: repository.getInventoryContext()
                val stocks = home.stocks.orEmpty().filterNotNull()
                val filters = snapshot.filters.let {
                    if (it.stockId != null && stocks.none { stock -> stock.id == it.stockId })
                        it.copy(stockId = null) else it
                }
                snapshot = snapshot.copy(home = home, filters = filters)
                val result = if (stocks.isEmpty()) null else {
                    val query = filters.query.trim().takeIf { it.isNotEmpty() }
                    if (filters.stockId == null) {
                        repository.getProductsPage(
                            page = snapshot.nextPage, category = filters.category,
                            storagePlace = filters.storagePlace, query = query, groupId = home.groupId
                        )
                    } else {
                        repository.getStockProductsPage(
                            filters.stockId, page = snapshot.nextPage, category = filters.category,
                            storagePlace = filters.storagePlace, query = query
                        )
                    }
                }
                if (session.isLoggedIn && session.state.generation == generation) {
                    mutableState.value = snapshot.copy(
                        items = (snapshot.items + result?.content.orEmpty()).distinctBy { it.id },
                        loading = false,
                        nextPage = if (result == null) 0 else result.number + 1,
                        totalPages = result?.totalPages ?: 0,
                        totalElements = result?.totalElements ?: 0
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (session.isLoggedIn && session.state.generation == generation) {
                    val error = when (failure) {
                        is BffException -> failure.error
                        is JsonParseException, is MalformedJsonException -> ApiError(code = "INVALID_RESPONSE")
                        is IOException -> ApiError(code = "NETWORK_ERROR")
                        else -> ApiError(code = "INVALID_RESPONSE")
                    }
                    mutableState.value = snapshot.copy(loading = false, error = error)
                }
            }
        }
    }
}
