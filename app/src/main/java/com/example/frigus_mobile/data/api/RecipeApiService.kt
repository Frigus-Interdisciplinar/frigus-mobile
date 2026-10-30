package com.example.frigus_mobile.data.api

import com.example.frigus_mobile.data.model.AiRecipeChatRequest
import com.example.frigus_mobile.data.model.PageResponse
import com.example.frigus_mobile.data.model.RecipeIngredientItem
import com.example.frigus_mobile.data.model.RecipeResponse
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface RecipeApiService {

    @GET("ai/recipes/suggestions/stock/{stockId}")
    fun getSuggestionsByStock(@Path("stockId") stockId: Int): Call<List<RecipeResponse>>

    @GET("recipes")
    fun getAllRecipes(): Call<PageResponse<RecipeResponse>>

    @GET("ingredients/recipe/{recipeId}")
    fun getIngredientsByRecipeId(@Path("recipeId") recipeId: Int): Call<List<RecipeIngredientItem>>

    @POST("ai/recipes/chat")
    fun chat(@Body request: AiRecipeChatRequest): Call<RecipeResponse>
}
