package com.example.frigus_mobile.ui.ai

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.frigus_mobile.data.local.SessionManager
import com.example.frigus_mobile.data.model.ApiError
import com.example.frigus_mobile.data.model.InventoryHomeResponse
import com.example.frigus_mobile.data.model.RecipeResponse
import com.example.frigus_mobile.data.model.StockResponse
import com.example.frigus_mobile.data.repository.AiSuggestionsRepository
import com.example.frigus_mobile.data.repository.InventoryHomeRepository
import retrofit2.Call

data class AiSuggestionsState(
    val loadingStocks: Boolean = false,
    val stocks: List<StockResponse> = emptyList(),
    val stockError: ApiError? = null,
    val selectedStockId: Int? = null,
    val loadingSuggestions: Boolean = false,
    val suggestions: List<RecipeResponse>? = null,
    val suggestionsError: ApiError? = null
)

class AiSuggestionsViewModel(application: Application) : AndroidViewModel(application) {
    private val session = SessionManager(application)
    private val homeRepository = InventoryHomeRepository()
    private val suggestionsRepository = AiSuggestionsRepository()
    private val mutableState = MutableLiveData<AiSuggestionsState>()
    val state: LiveData<AiSuggestionsState> = mutableState
    private var homeCall: Call<InventoryHomeResponse>? = null
    private var suggestionsCall: Call<List<RecipeResponse>>? = null
    private var request = 0L

    fun loadIfNeeded() {
        if (mutableState.value == null) loadStocks()
    }

    fun loadStocks() {
        if (!session.isLoggedIn) return
        homeCall?.cancel()
        suggestionsCall?.cancel()
        val currentRequest = ++request
        val generation = session.state.generation
        mutableState.value = AiSuggestionsState(loadingStocks = true)
        homeCall = homeRepository.load { home, error ->
            if (!isCurrent(generation, currentRequest)) return@load
            if (error != null) {
                mutableState.value = AiSuggestionsState(stockError = error)
                return@load
            }
            val stocks = home?.stocks.orEmpty().filterNotNull()
            mutableState.value = AiSuggestionsState(stocks = stocks)
        }
    }

    fun selectStock(stockId: Int?) {
        val current = mutableState.value ?: return
        if (current.loadingStocks || current.stockError != null ||
            (stockId != null && current.stocks.none { it.id == stockId })) return
        if (current.selectedStockId == stockId) return
        suggestionsCall?.cancel()
        ++request
        mutableState.value = current.copy(
            selectedStockId = stockId,
            suggestions = null,
            suggestionsError = null,
            loadingSuggestions = false
        )
        if (stockId != null) refreshSuggestions()
    }

    fun refreshSuggestions() {
        val current = mutableState.value ?: return
        val stockId = current.selectedStockId ?: return
        if (!session.isLoggedIn || current.stocks.none { it.id == stockId }) return
        suggestionsCall?.cancel()
        val currentRequest = ++request
        val generation = session.state.generation
        mutableState.value = current.copy(loadingSuggestions = true, suggestionsError = null)
        suggestionsCall = suggestionsRepository.load(stockId) { suggestions, error ->
            if (!isCurrent(generation, currentRequest) ||
                mutableState.value?.selectedStockId != stockId) return@load
            mutableState.value = mutableState.value?.copy(
                loadingSuggestions = false,
                suggestions = suggestions,
                suggestionsError = error
            )
        }
    }

    private fun isCurrent(generation: Long, currentRequest: Long): Boolean =
        session.isLoggedIn && session.state.generation == generation && request == currentRequest

    override fun onCleared() {
        homeCall?.cancel()
        suggestionsCall?.cancel()
    }
}
