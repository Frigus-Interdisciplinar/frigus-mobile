package com.example.frigus_mobile.data.repository

import android.content.Context
import androidx.lifecycle.MutableLiveData
import com.example.frigus_mobile.R
import com.example.frigus_mobile.data.api.RecipeApiService
import com.example.frigus_mobile.data.api.RetrofitClient
import com.example.frigus_mobile.data.local.SessionManager
import com.example.frigus_mobile.data.model.RecipeResponse
import com.example.frigus_mobile.data.model.Resource
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class RecipeRepository(context: Context) {

    private val sessionManager = SessionManager(context)
    private val apiService: RecipeApiService

    init {
        RetrofitClient.setTokenProvider { sessionManager.accessToken }
        apiService = RetrofitClient.recipeService
    }

    fun getRecipeSuggestions(
        stockId: Int? = null,
        liveData: MutableLiveData<Resource<List<RecipeResponse>>>
    ) {
        liveData.value = Resource.loading()

        val targetStockId = stockId ?: sessionManager.activeStockId ?: 1

        apiService.getSuggestionsByStock(targetStockId).enqueue(object : Callback<List<RecipeResponse>> {
            override fun onResponse(
                call: Call<List<RecipeResponse>>,
                response: Response<List<RecipeResponse>>
            ) {
                if (response.isSuccessful) {
                    val body = response.body().orEmpty()
                    val enriched = body.mapIndexed { index, recipe ->
                        enrichRecipeWithUiDefaults(recipe, index)
                    }
                    liveData.postValue(Resource.success(enriched))
                } else {
                    liveData.postValue(
                        Resource.error("Servidor indisponível ao buscar receitas (Código ${response.code()}).")
                    )
                }
            }

            override fun onFailure(call: Call<List<RecipeResponse>>, t: Throwable) {
                liveData.postValue(
                    Resource.error("Não foi possível conectar ao servidor: ${t.message}")
                )
            }
        })
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

