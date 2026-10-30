package com.example.frigus_mobile.ui.stock

import com.example.frigus_mobile.R

internal fun android.content.Context.lotUnitLabel(code: String?): String = getString(when (code) {
    "GRAM" -> R.string.inventory_unit_g
    "KILOGRAM" -> R.string.inventory_unit_kg
    "LITER" -> R.string.inventory_unit_l
    "MILLILITER" -> R.string.inventory_unit_ml
    "UNIT" -> R.string.inventory_unit_each
    "DOZEN" -> R.string.inventory_unit_dozen
    "PACKAGE" -> R.string.inventory_unit_package
    else -> R.string.inventory_unit_unknown
})

internal fun stockCategoryIcon(code: String?): Int = when (code) {
    "MEAT" -> R.drawable.ic_cat_meat
    "DAIRY" -> R.drawable.ic_cat_dairy
    "FRUIT", "VEGETABLE" -> R.drawable.ic_cat_hortifruti
    "BEVERAGE" -> R.drawable.ic_cat_beverages
    else -> R.drawable.ic_food_package
}

internal fun stockDisplayDate(value: String?): String {
    if (value.isNullOrBlank()) return ""
    return try {
        java.time.LocalDate.parse(value).format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/uuuu"))
    } catch (_: java.time.DateTimeException) { value }
}
