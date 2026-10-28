package com.example.frigus_mobile.data.repository

import android.content.Context
import androidx.lifecycle.MutableLiveData
import com.example.frigus_mobile.R
import com.example.frigus_mobile.data.api.RecipeApiService
import com.example.frigus_mobile.data.api.RetrofitClient
import com.example.frigus_mobile.data.api.requireBody
import com.example.frigus_mobile.data.api.requireNoContent
import com.example.frigus_mobile.data.model.PageResponse
import com.example.frigus_mobile.data.model.RecipeResponse
import com.example.frigus_mobile.data.model.RecipeSuggestionResponse
import com.example.frigus_mobile.data.model.Resource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RecipeRepository(private val context: Context) {
    private val api: RecipeApiService = RetrofitClient.recipeService

    fun getRecipeSuggestions(
        stockId: Int? = null,
        liveData: MutableLiveData<Resource<List<RecipeResponse>>>
    ) {
        liveData.value = Resource.loading()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val ai = stockId?.let { api.getSuggestionsByStock(it).requireBody() }.orEmpty()
                val suggestions = if (ai.isNotEmpty()) ai else
                    api.getSuggestions(stockId).requireBody().mapNotNull(::suggestedRecipe)
                val recipes = if (suggestions.isNotEmpty()) suggestions else
                    api.getAllRecipes().requireBody().content
                liveData.postValue(Resource.success(recipes.mapIndexed(::withUiImage)))
            } catch (e: Exception) {
                liveData.postValue(Resource.error(e.message ?: "Não foi possível carregar receitas."))
            }
        }
    }

    suspend fun list(page: Int = 0, size: Int = 20, query: String? = null):
        PageResponse<RecipeResponse> = withContext(Dispatchers.IO) {
        api.getAllRecipes(page, size, query).requireBody()
    }

    suspend fun favorites(page: Int = 0, size: Int = 20):
        PageResponse<RecipeResponse> = withContext(Dispatchers.IO) {
        api.getFavorites(page, size).requireBody()
    }

    suspend fun detail(id: Int, stockId: Int? = null): RecipeResponse = withContext(Dispatchers.IO) {
        api.getRecipe(id, stockId).requireBody()
    }

    suspend fun favorite(id: Int, enabled: Boolean) = withContext(Dispatchers.IO) {
        if (enabled) api.addFavorite(id).requireNoContent() else api.removeFavorite(id).requireNoContent()
    }

    private fun suggestedRecipe(suggestion: RecipeSuggestionResponse): RecipeResponse? =
        suggestion.recipe?.copy(
            matchedIngredients = suggestion.matchedIngredients,
            missingIngredients = suggestion.missingIngredients,
            score = suggestion.score,
            availability = suggestion.availability
        )

    private fun withUiImage(index: Int, recipe: RecipeResponse): RecipeResponse {
        val images = listOf(
            R.drawable.img_frango_molho_cremoso,
            R.drawable.img_lasanha_frango,
            R.drawable.img_omelete_legumes
        )
        recipe.imageDrawableRes = images[index % images.size]
        return recipe
    }
}
