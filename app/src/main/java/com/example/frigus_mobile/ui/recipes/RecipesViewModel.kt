package com.example.frigus_mobile.ui.recipes

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.viewModelScope
import com.example.frigus_mobile.data.api.BffException
import com.example.frigus_mobile.data.local.SessionManager
import com.example.frigus_mobile.data.model.ApiError
import com.example.frigus_mobile.data.model.RecipeResponse
import com.example.frigus_mobile.data.model.Resource
import com.example.frigus_mobile.R
import com.example.frigus_mobile.data.repository.RecipeRepository
import com.example.frigus_mobile.data.repository.AiRecipeUpdates
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

data class RecipeFavoriteAction(
    val busy: Boolean = false,
    val error: ApiError? = null,
    val uncertain: Boolean = false
)

data class RecipeSectionsState(val suggestionsExpanded: Boolean = true, val catalogExpanded: Boolean = true)

/** Prevent simultaneous changes to one recipe while list and detail Activities coexist. */
internal object RecipeFavoriteMutationGate {
    private val active = mutableSetOf<Int>()
    @Synchronized fun acquire(id: Int): Boolean = active.add(id)
    @Synchronized fun release(id: Int) { active.remove(id) }
    @Synchronized fun isBusy(id: Int): Boolean = id in active
}

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
    private val mutableFavorites = MutableLiveData<RecipeListState>()
    val favorites: LiveData<RecipeListState> = mutableFavorites
    private val mutableFavoriteActions = MutableLiveData<Map<Int, RecipeFavoriteAction>>(emptyMap())
    val favoriteActions: LiveData<Map<Int, RecipeFavoriteAction>> = mutableFavoriteActions
    private val mutableSections = MutableLiveData(RecipeSectionsState())
    val sections: LiveData<RecipeSectionsState> = mutableSections
    var showingFavorites = false
        private set
    private var detailOpenedId: Int? = null
    var query: String = ""
        private set
    private var catalogCall: Job? = null
    private var suggestionsCall: Job? = null
    private var favoritesCall: Job? = null
    private var favoriteGeneration = 0
    private var suggestionGroup: String? = null
    private var suggestionStock: Int? = null
    private var suggestionSession: Long? = null
    private var aiRevision = AiRecipeUpdates.current()

    // Compatibility with the recovered 93933ee screen; transport and mutation state stay in the BFF flow.
    private val originalResource = MediatorLiveData<Resource<List<RecipeResponse>>>()
    val recipesResource: LiveData<Resource<List<RecipeResponse>>> = originalResource
    private val originalFeatured = MutableLiveData<RecipeResponse?>()
    val featuredRecipe: LiveData<RecipeResponse?> = originalFeatured
    private val originalPopular = MutableLiveData<List<RecipeResponse>>()
    val popularRecipes: LiveData<List<RecipeResponse>> = originalPopular

    init {
        originalResource.addSource(catalog) { updateOriginalBindings() }
        originalResource.addSource(suggestions) { updateOriginalBindings() }
        originalResource.addSource(favorites) { updateOriginalBindings() }
        loadIfNeeded()
    }

    fun loadRecipes(stockId: Int? = null) {
        if (showingFavorites) refreshFavorites() else refreshCatalog()
        refreshSuggestions(stockId ?: session.activeStockId)
    }

    private fun updateOriginalBindings() {
        val current = (if (showingFavorites) favorites.value else catalog.value) ?: RecipeListState()
        val search = query.trim()
        fun matches(recipe: RecipeResponse): Boolean = search.isEmpty() ||
            recipe.recipeName?.contains(search, ignoreCase = true) == true ||
            recipe.recipeDescription?.contains(search, ignoreCase = true) == true ||
            recipe.ingredients.orEmpty().any { it.productName?.contains(search, ignoreCase = true) == true }
        val catalogById = current.items.associateBy { it.recipeId }
        val suggested = suggestions.value?.items.orEmpty().map { recipe ->
            recipe.copy(favorite = catalogById[recipe.recipeId]?.favorite ?: recipe.favorite)
        }
        originalFeatured.value = if (showingFavorites) null else suggested.firstOrNull(::matches)
        originalPopular.value = (if (showingFavorites) current.items else
            suggested + current.items).distinctBy { it.recipeId }.filter(::matches)
        originalResource.value = when {
            current.loading -> Resource.loading()
            current.error != null -> Resource.error(current.error.userMessage(getApplication<Application>().getString(
                if (current.error.status == 403) R.string.recipe_browser_forbidden else R.string.recipe_browser_load_error)), current.items)
            else -> Resource.success(current.items)
        }
    }

    fun selectFavorites(show: Boolean) {
        showingFavorites = show
        if (show && mutableFavorites.value == null) refreshFavorites()
        updateOriginalBindings()
    }

    fun toggleSuggestions() {
        val current = mutableSections.value ?: RecipeSectionsState()
        mutableSections.value = current.copy(suggestionsExpanded = !current.suggestionsExpanded)
    }

    fun toggleCatalog() {
        val current = mutableSections.value ?: RecipeSectionsState()
        mutableSections.value = current.copy(catalogExpanded = !current.catalogExpanded)
    }

    fun markDetailOpened(id: Int) { detailOpenedId = id }
    fun consumeDetailReturn(): Int? = detailOpenedId.also { detailOpenedId = null }

    fun loadIfNeeded() {
        val recipesChanged = aiRevision != AiRecipeUpdates.current()
        aiRevision = AiRecipeUpdates.current()
        if (mutableCatalog.value == null) refreshCatalog()
        if (recipesChanged || mutableSuggestions.value == null || suggestionGroup != session.activeGroupId ||
            suggestionStock != session.activeStockId || suggestionSession != session.state.generation) refreshSuggestions()
    }

    fun setSearchQuery(value: String) {
        if (query == value) return
        query = value
        updateOriginalBindings()
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

    fun refreshSuggestions(stockId: Int? = session.activeStockId) {
        if (!session.isLoggedIn) return
        val group = session.activeGroupId
        if (mutableSuggestions.value?.loading == true && suggestionGroup == group && suggestionStock == stockId &&
            suggestionSession == session.state.generation) return
        suggestionsCall?.cancel()
        val generation = session.state.generation
        suggestionGroup = group
        suggestionStock = stockId
        suggestionSession = generation
        mutableSuggestions.value = RecipeListState(loading = true)
        suggestionsCall = viewModelScope.launch {
            try {
                val result = repository.suggestions(stockId)
                if (session.isLoggedIn && session.state.generation == generation && session.activeGroupId == group &&
                    session.activeStockId == stockId)
                    mutableSuggestions.value = RecipeListState(items = result)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                if (session.isLoggedIn && session.state.generation == generation && session.activeGroupId == group &&
                    session.activeStockId == stockId)
                    mutableSuggestions.value = RecipeListState(error = recipeFailure(failure))
            }
        }
    }

    fun refreshFavorites() = loadFavorites(reset = true)

    fun loadMoreFavorites() {
        val state = mutableFavorites.value ?: return
        if (!state.loading && state.error == null && state.nextPage < state.totalPages)
            loadFavorites(reset = false)
    }

    fun retryFavorites() {
        val state = mutableFavorites.value ?: return refreshFavorites()
        if (!state.loading) loadFavorites(reset = state.nextPage == 0)
    }

    private fun loadFavorites(reset: Boolean) {
        if (!session.isLoggedIn) return
        favoritesCall?.cancel()
        val generation = session.state.generation
        val request = ++favoriteGeneration
        val previous = if (reset) RecipeListState() else mutableFavorites.value ?: RecipeListState()
        mutableFavorites.value = previous.copy(loading = true, error = null)
        favoritesCall = viewModelScope.launch {
            try {
                val result = repository.favorites(page = previous.nextPage, size = 20)
                if (session.isLoggedIn && session.state.generation == generation && request == favoriteGeneration)
                    mutableFavorites.value = RecipeListState(
                        items = (previous.items + result.content.map { it.copy(favorite = true) }).distinctBy { it.recipeId },
                        nextPage = result.number + 1,
                        totalPages = result.totalPages,
                        totalElements = result.totalElements
                    )
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                if (session.isLoggedIn && session.state.generation == generation && request == favoriteGeneration)
                    mutableFavorites.value = previous.copy(error = recipeFailure(failure))
            }
        }
    }

    fun setFavorite(recipe: RecipeResponse, enabled: Boolean) {
        val id = recipe.recipeId?.takeIf { it > 0 } ?: return
        if (!session.isLoggedIn || recipe.favorite == null || recipe.favorite == enabled) return
        val current = mutableFavoriteActions.value.orEmpty()[id]
        if (current?.busy == true || current?.uncertain == true || !RecipeFavoriteMutationGate.acquire(id)) return
        val generation = session.state.generation
        updateAction(id, RecipeFavoriteAction(busy = true))
        viewModelScope.launch {
            try {
                repository.favorite(id, enabled)
                if (session.isLoggedIn && session.state.generation == generation) {
                    updateAction(id, RecipeFavoriteAction())
                    mutableCatalog.value = mutableCatalog.value?.copy(items = mutableCatalog.value!!.items.map {
                        if (it.recipeId == id) it.copy(favorite = enabled) else it
                    })
                    mutableSuggestions.value = mutableSuggestions.value?.copy(items = mutableSuggestions.value!!.items.map {
                        if (it.recipeId == id) it.copy(favorite = enabled) else it
                    })
                    if (mutableFavorites.value != null) refreshFavorites()
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                if (session.isLoggedIn && session.state.generation == generation) {
                    val error = recipeFailure(failure)
                    updateAction(id, RecipeFavoriteAction(error = error,
                        uncertain = failure !is BffException || error.status == null || error.status == 408 || error.status >= 500))
                }
            } finally {
                RecipeFavoriteMutationGate.release(id)
            }
        }
    }

    fun reconcileFavorite(id: Int) {
        if (!session.isLoggedIn || mutableFavoriteActions.value.orEmpty()[id]?.busy == true) return
        val generation = session.state.generation
        updateAction(id, RecipeFavoriteAction(busy = true))
        viewModelScope.launch {
            try {
                // A detail mutation may still be cancelling after the user returns to the list.
                while (RecipeFavoriteMutationGate.isBusy(id)) delay(50)
                if (!session.isLoggedIn || session.state.generation != generation) return@launch
                val detail = repository.detail(id)
                if (session.isLoggedIn && session.state.generation == generation) {
                    val known = detail.favorite
                    if (known == null) {
                        updateAction(id, RecipeFavoriteAction(uncertain = true,
                            error = ApiError(code = "INVALID_RESPONSE")))
                    } else {
                        mutableCatalog.value = mutableCatalog.value?.copy(items = mutableCatalog.value!!.items.map {
                            if (it.recipeId == id) it.copy(favorite = known) else it
                        })
                        mutableSuggestions.value = mutableSuggestions.value?.copy(items = mutableSuggestions.value!!.items.map {
                            if (it.recipeId == id) it.copy(favorite = known) else it
                        })
                        updateAction(id, RecipeFavoriteAction())
                        if (mutableFavorites.value != null) refreshFavorites()
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                if (session.isLoggedIn && session.state.generation == generation)
                    updateAction(id, RecipeFavoriteAction(error = recipeFailure(failure), uncertain = true))
            }
        }
    }

    fun refreshAfterDetail(id: Int) = reconcileFavorite(id)

    private fun updateAction(id: Int, action: RecipeFavoriteAction) {
        mutableFavoriteActions.value = mutableFavoriteActions.value.orEmpty() + (id to action)
    }
}

data class RecipeDetailState(
    val recipe: RecipeResponse? = null,
    val loading: Boolean = false,
    val error: ApiError? = null,
    val favoriteBusy: Boolean = false,
    val favoriteError: ApiError? = null,
    val favoriteUncertain: Boolean = false
)

class RecipeDetailViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = RecipeRepository(application)
    private val session = SessionManager(application)
    private val mutableState = MutableLiveData<RecipeDetailState>()
    val state: LiveData<RecipeDetailState> = mutableState
    private var call: Job? = null

    fun loadIfNeeded(id: Int) { if (mutableState.value == null) load(id) }

    fun load(id: Int) {
        if (!session.isLoggedIn || mutableState.value?.loading == true || mutableState.value?.favoriteBusy == true) return
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

    fun setFavorite(enabled: Boolean) {
        val previous = mutableState.value ?: return
        val recipe = previous.recipe ?: return
        val id = recipe.recipeId?.takeIf { it > 0 } ?: return
        if (!session.isLoggedIn || previous.favoriteBusy || previous.favoriteUncertain ||
            recipe.favorite == null || recipe.favorite == enabled || !RecipeFavoriteMutationGate.acquire(id)) return
        val generation = session.state.generation
        mutableState.value = previous.copy(favoriteBusy = true, favoriteError = null)
        viewModelScope.launch {
            try {
                repository.favorite(id, enabled)
                if (session.isLoggedIn && session.state.generation == generation)
                    mutableState.value = mutableState.value?.copy(recipe = recipe.copy(favorite = enabled),
                        favoriteBusy = false, favoriteError = null, favoriteUncertain = false)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                if (session.isLoggedIn && session.state.generation == generation) {
                    val error = recipeFailure(failure)
                    mutableState.value = mutableState.value?.copy(favoriteBusy = false,
                        favoriteError = error,
                        favoriteUncertain = failure !is BffException || error.status == null || error.status == 408 || error.status >= 500)
                }
            } finally {
                RecipeFavoriteMutationGate.release(id)
            }
        }
    }
}
