package com.example.frigus_mobile.data.model

import com.google.gson.annotations.SerializedName
import java.io.Serializable

data class RecipeResponse(
    @SerializedName(value = "recipeId", alternate = ["id"])
    val recipeId: Int? = null,

    @SerializedName(value = "recipeName", alternate = ["name"])
    val recipeName: String? = null,

    @SerializedName(value = "recipeDescription", alternate = ["description"])
    val recipeDescription: String? = null,

    @SerializedName(value = "recipeInstructions", alternate = ["instructions"])
    val recipeInstructions: String? = null,

    @SerializedName("ingredients")
    val ingredients: List<RecipeIngredientItem>? = emptyList(),

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

    @SerializedName("favorite")
    val favorite: Boolean? = null,

    @SerializedName("domesticOnly")
    val domesticOnly: Boolean? = null,

    @SerializedName("active")
    val active: Boolean? = null,

    @SerializedName("createdAt")
    val createdAt: String? = null,

    @SerializedName("availability")
    val availability: List<RecipeAvailability>? = null,

    // Propriedades visuais adicionais
    var rating: Double = 4.8,
    var timeMinutes: Int = 35,
    var difficulty: String = "Fácil",
    var imageDrawableRes: Int? = null,
    var imageUrl: String? = null
) : Serializable {

    val formattedRating: String
        get() = String.format(java.util.Locale.US, "%.1f", rating).replace(".", ",")

    val formattedTime: String
        get() = "$timeMinutes min"

    val expiringBadgeText: String
        get() {
            val count = matchedIngredients ?: 0
            return when {
                count <= 0 -> recipeDescription ?: "Sugestão de receita"
                count == 1 -> "Usa 1 ingrediente do seu estoque"
                else -> "Usa $count ingredientes do seu estoque"
            }
        }

    val availableIngredients: List<RecipeIngredientItem>
        get() = ingredients?.filter { it.inStock == true } ?: emptyList()
}

data class RecipeSuggestionResponse(
    val recipe: RecipeResponse? = null,
    val matchedIngredients: Int? = null,
    val missingIngredients: Int? = null,
    val score: Double? = null,
    val availability: List<RecipeAvailability>? = null
)

data class RecipeAvailability(
    val productId: Int? = null,
    val productName: String? = null,
    val requiredQuantity: Double? = null,
    val availableQuantity: Double? = null,
    val unit: String? = null,
    val required: Boolean? = null,
    val available: Boolean? = null
)

data class RecipeIngredientItem(
    @SerializedName("id")
    val id: Int? = null,

    @SerializedName("recipeId")
    val recipeId: Int? = null,

    @SerializedName("recipeName")
    val recipeName: String? = null,

    @SerializedName("productId")
    val productId: Int? = null,

    @SerializedName("productName")
    val productName: String? = null,

    @SerializedName("productCategory")
    val productCategory: String? = null,

    @SerializedName("productUnitOfMeasure")
    val productUnitOfMeasure: String? = null,

    @SerializedName("quantity")
    val quantity: Double? = null,

    @SerializedName("unit")
    val unit: String? = null,

    @SerializedName("required")
    val required: Boolean? = true,

    @SerializedName("inStock")
    val inStock: Boolean? = false,

    @SerializedName("expireDate")
    val expireDate: String? = null
) : Serializable {

    val formattedQuantity: String
        get() {
            if (quantity == null) return ""
            val qStr = if (quantity % 1.0 == 0.0) {
                quantity.toInt().toString()
            } else {
                String.format(java.util.Locale.US, "%.1f", quantity).replace(".", ",")
            }
            val u = unit ?: ""
            return if (u.isNotBlank()) "$qStr $u" else qStr
        }
}
