package com.example.frigus_mobile.data.api

import com.example.frigus_mobile.data.model.RecipeResponse
import com.google.gson.annotations.SerializedName
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

data class AiRecipeChatRequest(
    @SerializedName("message")
    val message: String,

    @SerializedName("stockId")
    val stockId: Int,

    @SerializedName("sessionId")
    val sessionId: String? = null
)

interface RecipeApiService {

    @GET("ai/recipes/suggestions/stock/{stockId}")
    fun getSuggestionsByStock(@Path("stockId") stockId: Int): Call<List<RecipeResponse>>

    @POST("ai/recipes/chat")
    fun chat(@Body request: AiRecipeChatRequest): Call<RecipeResponse>
}
