package com.example.frigus_mobile.data.api

import com.example.frigus_mobile.data.model.AiRecipeChatRequest
import com.example.frigus_mobile.data.model.AiRecipeChatResponse
import retrofit2.Call
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface AiRecipeApiService {

    /**
     * Contrato M19 BFF: POST /mobile/recipes/ai/chat
     * Encaminha para frigus-ai /v1/chats/{chatId}/messages com timeout de 120s.
     */
    @POST("mobile/recipes/ai/chat")
    suspend fun chatBff(
        @Body request: AiRecipeChatRequest
    ): Response<AiRecipeChatResponse>

    /**
     * Fallback Core API: POST /ai/recipes/chat
     */
    @POST("ai/recipes/chat")
    suspend fun chatCore(
        @Body request: AiRecipeChatRequest
    ): Response<AiRecipeChatResponse>

    /**
     * Contrato M18 BFF: GET /mobile/recipes/ai/suggestions/{stockId}
     */
    @GET("mobile/recipes/ai/suggestions/{stockId}")
    suspend fun getSuggestionsBff(
        @Path("stockId") stockId: Int
    ): Response<List<AiRecipeChatResponse>>

    /**
     * Fallback Core API: GET /ai/recipes/suggestions/stock/{stockId}
     */
    @GET("ai/recipes/suggestions/stock/{stockId}")
    suspend fun getSuggestionsCore(
        @Path("stockId") stockId: Int
    ): Response<List<AiRecipeChatResponse>>

    /**
     * Chamada síncrona / legada
     */
    @POST("ai/recipes/chat")
    fun chat(
        @Body request: AiRecipeChatRequest
    ): Call<AiRecipeChatResponse>
}
