package com.example.frigus_mobile.data.api

import com.example.frigus_mobile.data.model.AiRecipeChatRequest
import com.example.frigus_mobile.data.model.AiRecipeChatResponse
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

interface AiRecipeApiService {

    @POST("ai/recipes/chat")
    fun chat(
        @Body request: AiRecipeChatRequest
    ): Call<AiRecipeChatResponse>
}
