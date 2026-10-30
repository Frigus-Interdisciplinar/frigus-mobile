package com.example.frigus_mobile.utils

import android.content.Context
import android.content.SharedPreferences

/**
 * Utilitário leve para persistir e recuperar URIs de fotos personalizadas
 * associadas a um alimento do estoque.
 */
object ProductImageTracker {

    private const val PREFS_NAME = "frigus_product_images_tracker"
    private const val KEY_PREFIX = "img_stock_"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Salva o URI da imagem personalizada selecionada para o alimento.
     */
    fun setImageUri(context: Context, stockProductId: Int, uri: String?) {
        if (stockProductId <= 0) return
        val prefs = getPrefs(context)
        if (uri.isNullOrBlank()) {
            prefs.edit().remove("$KEY_PREFIX$stockProductId").apply()
        } else {
            prefs.edit().putString("$KEY_PREFIX$stockProductId", uri).apply()
        }
    }

    /**
     * Retorna o URI da imagem personalizada ou nulo se não houver.
     */
    fun getImageUri(context: Context, stockProductId: Int): String? {
        if (stockProductId <= 0) return null
        return getPrefs(context).getString("$KEY_PREFIX$stockProductId", null)
    }

    /**
     * Remove o registro da foto associada ao alimento.
     */
    fun remove(context: Context, stockProductId: Int) {
        if (stockProductId <= 0) return
        getPrefs(context).edit().remove("$KEY_PREFIX$stockProductId").apply()
    }
}
