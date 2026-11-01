package com.example.frigus_mobile.utils

import android.content.Context
import android.content.SharedPreferences

/**
 * Utilitário para persistir e recuperar o nome personalizado
 * associado a um alimento do estoque.
 */
object ProductNameTracker {

    private const val PREFS_NAME = "frigus_product_name_tracker"
    private const val KEY_PREFIX = "name_stock_"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Salva o nome de um alimento do estoque.
     */
    fun setName(context: Context, stockProductId: Int, name: String?) {
        if (stockProductId <= 0) return
        val prefs = getPrefs(context)
        if (name.isNullOrBlank()) {
            prefs.edit().remove("$KEY_PREFIX$stockProductId").apply()
        } else {
            prefs.edit().putString("$KEY_PREFIX$stockProductId", name.trim()).apply()
        }
    }

    /**
     * Recupera o nome salvo de um alimento do estoque.
     */
    fun getName(context: Context, stockProductId: Int): String? {
        if (stockProductId <= 0) return null
        return getPrefs(context).getString("$KEY_PREFIX$stockProductId", null)
    }

    /**
     * Remove o nome salvo de um alimento do estoque.
     */
    fun remove(context: Context, stockProductId: Int) {
        if (stockProductId <= 0) return
        getPrefs(context).edit().remove("$KEY_PREFIX$stockProductId").apply()
    }
}
