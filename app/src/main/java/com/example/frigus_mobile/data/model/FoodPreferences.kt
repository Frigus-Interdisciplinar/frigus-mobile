package com.example.frigus_mobile.data.model

enum class FoodPreference(val storageKey: String, val defaultValue: Boolean = false) {
    VEGETARIAN("food_vegetarian"), VEGAN("food_vegan"), NO_RED_MEAT("food_no_red_meat"),
    NO_SEAFOOD("food_no_seafood"), NO_LACTOSE("food_no_lactose"), NO_GLUTEN("food_no_gluten"),
    NO_SUGAR("food_no_sugar"), QUICK_RECIPES("food_quick_recipes", true),
    ECONOMICAL_RECIPES("food_economical_recipes", true)
}

data class FoodPreferences(val enabled: Set<FoodPreference>) {
    fun isEnabled(preference: FoodPreference): Boolean = preference in enabled
}
