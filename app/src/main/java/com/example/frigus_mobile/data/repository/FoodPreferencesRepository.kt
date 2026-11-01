package com.example.frigus_mobile.data.repository

import android.content.Context
import com.example.frigus_mobile.R
import com.example.frigus_mobile.data.local.ProfilePreferenceStore
import com.example.frigus_mobile.data.model.FoodPreference
import com.example.frigus_mobile.data.model.FoodPreferences

/** Local user choices; the catalog API does not publish dietary-filter parameters. */
class FoodPreferencesRepository(context: Context) {
    private val context = context.applicationContext
    private val store = ProfilePreferenceStore(this.context)

    fun snapshot(): FoodPreferences? = if (!store.isActive) null else FoodPreferences(
        FoodPreference.entries.filter { store.getBoolean(it.storageKey, it.defaultValue) }.toSet())

    fun update(preference: FoodPreference, enabled: Boolean): Boolean =
        store.putBoolean(preference.storageKey, enabled)

    /** Appended to the assistant request only, while the visible user message stays unchanged. */
    fun promptContext(): String {
        val preferences = snapshot() ?: return ""
        if (preferences.enabled.isEmpty()) return ""
        val choices = preferences.enabled.map { preference -> context.getString(when (preference) {
            FoodPreference.VEGETARIAN -> R.string.profile_food_vegetarian
            FoodPreference.VEGAN -> R.string.profile_food_vegan
            FoodPreference.NO_RED_MEAT -> R.string.profile_food_no_red_meat
            FoodPreference.NO_SEAFOOD -> R.string.profile_food_no_seafood
            FoodPreference.NO_LACTOSE -> R.string.profile_food_no_lactose
            FoodPreference.NO_GLUTEN -> R.string.profile_food_no_gluten
            FoodPreference.NO_SUGAR -> R.string.profile_food_no_sugar
            FoodPreference.QUICK_RECIPES -> R.string.profile_food_quick_recipes
            FoodPreference.ECONOMICAL_RECIPES -> R.string.profile_food_economical_recipes
        }) }
        return if (store.isActive) context.getString(R.string.profile_food_assistant_context, choices.joinToString(", ")) else ""
    }
}
