package com.example.frigus_mobile.utils

import android.content.Context
import android.content.SharedPreferences

/**
 * Utilitário leve para persistir e recuperar observações personalizadas
 * associadas a um alimento do estoque.
 */
object StockObservationsTracker {

    private const val PREFS_NAME = "frigus_stock_observations_tracker"
    private const val KEY_PREFIX = "obs_stock_"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Salva as observações de um alimento do estoque.
     */
    fun setObservation(context: Context, stockProductId: Int, observation: String?) {
        if (stockProductId <= 0) return
        val prefs = getPrefs(context)
        if (observation.isNullOrBlank()) {
            prefs.edit().remove("$KEY_PREFIX$stockProductId").apply()
        } else {
            prefs.edit().putString("$KEY_PREFIX$stockProductId", observation.trim()).apply()
        }
    }

    /**
     * Recupera as observações salvas de um alimento do estoque.
     */
    fun getObservation(context: Context, stockProductId: Int): String? {
        if (stockProductId <= 0) return null
        return getPrefs(context).getString("$KEY_PREFIX$stockProductId", null)
    }

    /**
     * Remove as observações salvas de um alimento do estoque.
     */
    fun remove(context: Context, stockProductId: Int) {
        if (stockProductId <= 0) return
        getPrefs(context).edit().remove("$KEY_PREFIX$stockProductId").apply()
    }
}
