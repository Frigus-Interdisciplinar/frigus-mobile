package com.example.frigus_mobile.data.repository

import android.content.Context
import androidx.lifecycle.MutableLiveData
import com.example.frigus_mobile.R
import com.example.frigus_mobile.data.api.RecipeApiService
import com.example.frigus_mobile.data.api.RetrofitClient
import com.example.frigus_mobile.data.local.SessionManager
import com.example.frigus_mobile.data.model.RecipeResponse
import com.example.frigus_mobile.data.model.Resource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class RecipeRepository(private val context: Context) {

    private val sessionManager = SessionManager(context)
    private val apiService: RecipeApiService = RetrofitClient.recipeService
    private val stockRepository = StockRepository(context)

    fun getRecipeSuggestions(
        stockId: Int? = null,
        liveData: MutableLiveData<Resource<List<RecipeResponse>>>
    ) {
        liveData.value = Resource.loading()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Resolve o stockId real do grupo do usuário autenticado (sem usar stockId = 1 de outro usuário)
                val resolvedStockId = stockId ?: stockRepository.resolveUserStockId()

                if (resolvedStockId != null) {
                    val suggResp = apiService.getSuggestionsByStock(resolvedStockId).execute()
                    if (suggResp.isSuccessful) {
                        val suggestions = suggResp.body().orEmpty()
                        if (suggestions.isNotEmpty()) {
                            val enriched = suggestions.mapIndexed { idx, r -> enrichRecipeWithUiDefaults(r, idx) }
                            liveData.postValue(Resource.success(enriched))
                            return@launch
                        }
                    }
                }

                // Caso o usuário ainda não tenha grupo/estoque ou não haja sugestões da IA para o estoque,
                // consulta o catálogo geral de receitas da API (GET /recipes)
                val generalResp = apiService.getAllRecipes().execute()
                if (generalResp.isSuccessful) {
                    val recipes = generalResp.body()?.content.orEmpty()
                    val enriched = recipes.mapIndexed { idx, r ->
                        val ingList = if (r.recipeId != null && r.ingredients.isNullOrEmpty()) {
                            try {
                                val ingResp = apiService.getIngredientsByRecipeId(r.recipeId).execute()
                                if (ingResp.isSuccessful) ingResp.body().orEmpty() else emptyList()
                            } catch (_: Exception) {
                                emptyList()
                            }
                        } else {
                            r.ingredients.orEmpty()
                        }
                        enrichRecipeWithUiDefaults(r.copy(ingredients = ingList), idx)
                    }
                    liveData.postValue(Resource.success(enriched))
                } else {
                    liveData.postValue(
                        Resource.error("Servidor indisponível ao buscar receitas (Código ${generalResp.code()}).")
                    )
                }
            } catch (e: Exception) {
                liveData.postValue(
                    Resource.error("Não foi possível conectar ao servidor: ${e.message}")
                )
            }
        }
    }

    private fun enrichRecipeWithUiDefaults(recipe: RecipeResponse, index: Int): RecipeResponse {
        val defaultImages = listOf(
            R.drawable.img_frango_molho_cremoso,
            R.drawable.img_lasanha_frango,
            R.drawable.img_omelete_legumes
        )
        recipe.imageDrawableRes = defaultImages.getOrElse(index % defaultImages.size) {
            R.drawable.img_frango_molho_cremoso
        }
        return recipe
    }
}
