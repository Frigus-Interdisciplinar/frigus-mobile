package com.example.frigus_mobile.data.model

import com.google.gson.annotations.SerializedName
import java.io.Serializable

data class RecipeResponse(
    @SerializedName("recipeId")
    val recipeId: Int? = null,

    @SerializedName("recipeName")
    val recipeName: String? = null,

    @SerializedName("recipeDescription")
    val recipeDescription: String? = null,

    @SerializedName("recipeInstructions")
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

    // Propriedades visuais adicionais (fallback / enriquecimento de UI)
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
            val count = matchedIngredients ?: 3
            return "Usa $count ingredientes que vencem em breve"
        }

    val availableIngredients: List<RecipeIngredientItem>
        get() = ingredients?.filter { it.inStock == true } ?: emptyList()
}

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
