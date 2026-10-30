package com.example.frigus_mobile.utils

import android.content.Context
import android.content.SharedPreferences

/**
 * Gerenciador ultraleve para rastrear a quantidade inicial/original de cada item de estoque.
 * Usa SharedPreferences (O(1) em memória, consumo insignificante de poucos kilobytes),
 * permitindo que a barra de quantidade restante reflita a diminuição real proporcional
 * conforme o alimento é consumido ou editado.
 */
object StockQuantityTracker {

    private const val PREFS_NAME = "frigus_stock_quantity_tracker"
    private const val KEY_PREFIX = "initial_qty_"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Retorna a quantidade inicial registrada para o item.
     * Se for a primeira vez que o item é exibido, ou se a quantidade atual for maior
     * que a inicial salva (ex: reabastecimento), registra a quantidade atual como novo ponto de partida.
     */
    fun getOrSetInitialQuantity(context: Context, stockProductId: Int, currentQuantity: Int): Int {
        if (stockProductId <= 0) return currentQuantity.coerceAtLeast(1)
        val prefs = getPrefs(context)
        val key = "$KEY_PREFIX$stockProductId"
        val saved = prefs.getInt(key, -1)
        return if (saved <= 0 || currentQuantity > saved) {
            val initial = currentQuantity.coerceAtLeast(1)
            prefs.edit().putInt(key, initial).apply()
            initial
        } else {
            saved
        }
    }

    /**
     * Registra explicitamente a quantidade inicial quando o item é cadastrado no estoque.
     */
    fun setInitialQuantity(context: Context, stockProductId: Int, quantity: Int) {
        if (stockProductId <= 0 || quantity <= 0) return
        getPrefs(context).edit().putInt("$KEY_PREFIX$stockProductId", quantity).apply()
    }

    /**
     * Limpa o registro quando o item for removido do estoque para não deixar chaves órfãs.
     */
    fun remove(context: Context, stockProductId: Int) {
        if (stockProductId <= 0) return
        getPrefs(context).edit().remove("$KEY_PREFIX$stockProductId").apply()
    }
}
