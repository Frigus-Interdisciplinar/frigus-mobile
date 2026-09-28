package com.example.frigus_mobile.ui.recipes

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import com.example.frigus_mobile.data.model.RecipeResponse
import com.example.frigus_mobile.data.model.Resource
import com.example.frigus_mobile.data.repository.RecipeRepository

class RecipesViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = RecipeRepository(application)

    private val _recipesResource = MutableLiveData<Resource<List<RecipeResponse>>>()
    val recipesResource: LiveData<Resource<List<RecipeResponse>>> = _recipesResource

    private val _searchQuery = MutableLiveData("")
    val searchQuery: LiveData<String> = _searchQuery

    val featuredRecipe = MediatorLiveData<RecipeResponse?>()
    val popularRecipes = MediatorLiveData<List<RecipeResponse>>()

    init {
        featuredRecipe.addSource(_recipesResource) { updateFilteredData() }
        featuredRecipe.addSource(_searchQuery) { updateFilteredData() }

        popularRecipes.addSource(_recipesResource) { updateFilteredData() }
        popularRecipes.addSource(_searchQuery) { updateFilteredData() }

        loadRecipes()
    }

    fun loadRecipes(stockId: Int = 1) {
        repository.getRecipeSuggestions(stockId, _recipesResource)
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    private fun updateFilteredData() {
        val resource = _recipesResource.value
        val list = resource?.data ?: emptyList()
        val query = _searchQuery.value?.trim()?.lowercase() ?: ""

        val filtered = if (query.isEmpty()) {
            list
        } else {
            list.filter { recipe ->
                val matchName = recipe.recipeName?.lowercase()?.contains(query) == true
                val matchDesc = recipe.recipeDescription?.lowercase()?.contains(query) == true
                val matchIngredients = recipe.ingredients?.any {
                    it.productName?.lowercase()?.contains(query) == true
                } == true
                matchName || matchDesc || matchIngredients
            }
        }

        if (filtered.isNotEmpty()) {
            featuredRecipe.value = filtered.first()
            popularRecipes.value = if (filtered.size > 1) filtered.drop(1) else emptyList()
        } else {
            featuredRecipe.value = null
            popularRecipes.value = emptyList()
        }
    }
}
