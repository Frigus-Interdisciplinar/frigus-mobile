package com.example.frigus_mobile.data.api

import com.example.frigus_mobile.data.model.PageResponse
import com.example.frigus_mobile.data.model.RecipeResponse
import com.example.frigus_mobile.data.model.RecipeSuggestionResponse
import retrofit2.Call
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.PUT
import retrofit2.http.Query

interface RecipeApiService {

    @GET("mobile/recipes/ai/suggestions/{stockId}")
    fun getSuggestionsByStock(@Path("stockId") stockId: Int): Call<List<RecipeResponse>>

    @GET("mobile/recipes")
    fun getAllRecipes(
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20,
        @Query("q") query: String? = null
    ): Call<PageResponse<RecipeResponse>>

    @GET("mobile/recipes/favorites")
    fun getFavorites(@Query("page") page: Int = 0, @Query("size") size: Int = 20): Call<PageResponse<RecipeResponse>>

    @GET("mobile/recipes/suggestions")
    fun getSuggestions(
        @Query("stockId") stockId: Int? = null,
        @Query("limit") limit: Int = 10
    ): Call<List<RecipeSuggestionResponse>>

    @GET("mobile/recipes/{id}")
    fun getRecipe(@Path("id") id: Int, @Query("stockId") stockId: Int? = null): Call<RecipeResponse>

    @PUT("mobile/recipes/{id}/favorite")
    fun addFavorite(@Path("id") id: Int): Call<Void>

    @DELETE("mobile/recipes/{id}/favorite")
    fun removeFavorite(@Path("id") id: Int): Call<Void>
}
