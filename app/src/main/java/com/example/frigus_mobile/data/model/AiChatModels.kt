package com.example.frigus_mobile.data.model

import com.google.gson.annotations.SerializedName

data class AiRecipeChatRequest(
    @SerializedName("message")
    val message: String,

    @SerializedName("stockId")
    val stockId: Int = 1,

    @SerializedName("sessionId")
    val sessionId: String? = null
)

data class AiRecipeChatResponse(
    @SerializedName("sessionId")
    val sessionId: String? = null,

    @SerializedName("chatMessage")
    val chatMessage: String? = null,

    @SerializedName("recipeId")
    val recipeId: Int? = null,

    @SerializedName("recipeName")
    val recipeName: String? = null,

    @SerializedName("recipeDescription")
    val recipeDescription: String? = null,

    @SerializedName("recipeInstructions")
    val recipeInstructions: String? = null,

    @SerializedName("suggestionId")
    val suggestionId: Int? = null,

    @SerializedName("matchedIngredients")
    val matchedIngredients: Int? = null,

    @SerializedName("missingIngredients")
    val missingIngredients: Int? = null,

    @SerializedName("score")
    val score: Double? = null
)

data class ChatUiMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)
