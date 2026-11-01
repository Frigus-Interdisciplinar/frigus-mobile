package com.example.frigus_mobile.data.model

import java.io.Serializable

data class ShoppingListItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String = "",
    val quantity: Int = 1,
    val unit: String = "Un",
    val category: String = "Outros",
    val categoryCode: String = "OTHER",
    val observation: String = "",
    val addedBy: String = "Adicionado por você",
    var isChecked: Boolean = false,
    val imageUrl: String? = null,
    val remoteId: Int? = null,
    val listId: String? = null,
    val productId: Int? = null,
    val unitCode: String? = null,
    val stockId: Int? = null
) : Serializable {

    companion object {
        fun mapCategoryToCode(category: String): String = when (category) {
            "Carnes" -> "MEAT"
            "Laticínios" -> "DAIRY"
            "Hortifruti" -> "VEGETABLE"
            "Bebidas" -> "BEVERAGE"
            "Grãos" -> "GRAIN"
            "Peixes" -> "MEAT"
            "Congelados" -> "FROZEN"
            "Doces" -> "SWEETS"
            "Enlatados" -> "CANNED"
            "Temperos" -> "SEASONING"
            else -> "OTHER"
        }

        fun mapCodeToCategory(code: String): String = when (code.uppercase()) {
            "MEAT" -> "Carnes"
            "DAIRY" -> "Laticínios"
            "VEGETABLE", "FRUIT" -> "Hortifruti"
            "BEVERAGE" -> "Bebidas"
            "GRAIN" -> "Grãos"
            "FROZEN" -> "Congelados"
            "SWEETS" -> "Doces"
            "CANNED" -> "Enlatados"
            "SEASONING" -> "Temperos"
            else -> "Outros"
        }

        fun mapUnitToCode(unit: String): String = when (unit.lowercase()) {
            "kg", "quilos" -> "KILOGRAM"
            "g", "gramas" -> "GRAM"
            "l", "litros" -> "LITER"
            "ml", "mililitros" -> "MILLILITER"
            else -> "UNIT"
        }
    }
}
