package com.example.frigus_mobile.data.model

import com.google.gson.annotations.SerializedName
import java.util.UUID

data class AiRecipeChatRequest(
    @SerializedName("message")
    val message: String,

    @SerializedName("stockId")
    val stockId: Int,

    @SerializedName("sessionId")
    val sessionId: String? = null,

    @SerializedName("foodPreferences")
    val foodPreferences: List<String> = emptyList()
)

data class AiRecipeChatResponse(
    @SerializedName("recipeIds")
    val recipeIds: List<Int>? = null,

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
    val score: Double? = null,

    @SerializedName("nearestExpireDate")
    val nearestExpireDate: String? = null,

    @SerializedName("ingredients")
    val ingredients: List<AiIngredientItem>? = null
)

data class AiIngredientItem(
    @SerializedName("id")
    val id: Int? = null,

    @SerializedName("productId")
    val productId: Int? = null,

    @SerializedName("productName")
    val productName: String? = null,

    @SerializedName("quantity")
    val quantity: Double? = null,

    @SerializedName("unit")
    val unit: String? = null,

    @SerializedName("inStock")
    val inStock: Boolean? = null,

    @SerializedName("expireDate")
    val expireDate: String? = null
)

data class ChatUiMessage(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val isLoading: Boolean = false,
    val delivery: AiMessageDelivery? = null,
    val reply: AiRecipeChatResponse? = null
)

enum class AiMessageDelivery { SENDING, CONFIRMED, UNCERTAIN, REJECTED }
