package com.example.frigus_mobile.ui.recipes

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.frigus_mobile.data.api.BffException
import com.example.frigus_mobile.data.local.SessionManager
import com.example.frigus_mobile.data.model.ApiError
import com.example.frigus_mobile.data.model.RecipeResponse
import com.example.frigus_mobile.data.repository.RecipeRepository
import com.google.gson.JsonParseException
import com.google.gson.stream.MalformedJsonException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.IOException

data class RecipeListState(
    val items: List<RecipeResponse> = emptyList(),
    val loading: Boolean = false,
    val error: ApiError? = null,
    val nextPage: Int = 0,
    val totalPages: Int = 0,
    val totalElements: Long = 0
)

internal fun recipeFailure(failure: Exception): ApiError = when (failure) {
    is BffException -> failure.error
    is JsonParseException, is MalformedJsonException -> ApiError(code = "INVALID_RESPONSE")
    is IOException -> ApiError(code = "NETWORK_ERROR")
    else -> ApiError(code = "INVALID_RESPONSE")
}

class RecipesViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = RecipeRepository(application)
    private val session = SessionManager(application)
    private val mutableCatalog = MutableLiveData<RecipeListState>()
    val catalog: LiveData<RecipeListState> = mutableCatalog
    private val mutableSuggestions = MutableLiveData<RecipeListState>()
    val suggestions: LiveData<RecipeListState> = mutableSuggestions
    var query: String = ""
        private set
    private var catalogCall: Job? = null
    private var suggestionsCall: Job? = null

    fun loadIfNeeded() {
        if (mutableCatalog.value == null) refreshCatalog()
        if (mutableSuggestions.value == null) refreshSuggestions()
    }

    fun setSearchQuery(value: String) {
        if (query == value) return
        query = value
        loadCatalog(reset = true, debounce = true)
    }

    fun refreshCatalog() = loadCatalog(reset = true)

    fun loadMore() {
        val state = mutableCatalog.value ?: return
        if (!state.loading && state.error == null && state.nextPage < state.totalPages)
            loadCatalog(reset = false)
    }

    fun retryCatalog() {
        val state = mutableCatalog.value ?: return refreshCatalog()
        if (!state.loading) loadCatalog(reset = state.nextPage == 0)
    }

    private fun loadCatalog(reset: Boolean, debounce: Boolean = false) {
        if (!session.isLoggedIn) return
        catalogCall?.cancel()
        val generation = session.state.generation
        val previous = if (reset) RecipeListState() else mutableCatalog.value ?: RecipeListState()
        val search = query.trim().takeIf(String::isNotEmpty)
        mutableCatalog.value = previous.copy(loading = true, error = null)
        catalogCall = viewModelScope.launch {
            try {
                if (debounce) delay(350)
                val result = repository.list(page = previous.nextPage, size = 20, query = search)
                if (session.isLoggedIn && session.state.generation == generation) {
                    mutableCatalog.value = RecipeListState(
                        items = (previous.items + result.content).distinctBy { it.recipeId },
                        nextPage = result.number + 1, totalPages = result.totalPages,
                        totalElements = result.totalElements
                    )
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                if (session.isLoggedIn && session.state.generation == generation)
                    mutableCatalog.value = previous.copy(error = recipeFailure(failure))
            }
        }
    }

    fun refreshSuggestions() {
        if (!session.isLoggedIn || mutableSuggestions.value?.loading == true) return
        suggestionsCall?.cancel()
        val generation = session.state.generation
        mutableSuggestions.value = RecipeListState(loading = true)
        suggestionsCall = viewModelScope.launch {
            try {
                val result = repository.suggestions()
                if (session.isLoggedIn && session.state.generation == generation)
                    mutableSuggestions.value = RecipeListState(items = result)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                if (session.isLoggedIn && session.state.generation == generation)
                    mutableSuggestions.value = RecipeListState(error = recipeFailure(failure))
            }
        }
    }
}

data class RecipeDetailState(
    val recipe: RecipeResponse? = null,
    val loading: Boolean = false,
    val error: ApiError? = null
)

class RecipeDetailViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = RecipeRepository(application)
    private val session = SessionManager(application)
    private val mutableState = MutableLiveData<RecipeDetailState>()
    val state: LiveData<RecipeDetailState> = mutableState
    private var call: Job? = null

    fun loadIfNeeded(id: Int) { if (mutableState.value == null) load(id) }

    fun load(id: Int) {
        if (!session.isLoggedIn || mutableState.value?.loading == true) return
        if (id <= 0) {
            mutableState.value = RecipeDetailState(error = ApiError(code = "INVALID_RESPONSE"))
            return
        }
        call?.cancel()
        val generation = session.state.generation
        mutableState.value = RecipeDetailState(loading = true)
        call = viewModelScope.launch {
            try {
                val recipe = repository.detail(id)
                if (session.isLoggedIn && session.state.generation == generation)
                    mutableState.value = RecipeDetailState(recipe = recipe)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                if (session.isLoggedIn && session.state.generation == generation)
                    mutableState.value = RecipeDetailState(error = recipeFailure(failure))
            }
        }
    }
}
