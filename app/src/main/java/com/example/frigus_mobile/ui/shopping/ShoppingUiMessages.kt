package com.example.frigus_mobile.ui.shopping

import android.content.Context
import androidx.appcompat.app.AlertDialog
import com.example.frigus_mobile.R

fun shoppingErrorMessage(context: Context, state: ShoppingState): String {
    if (state.error?.code == "SHOPPING_RECONCILIATION_REQUIRED") return context.getString(R.string.shopping_remote_reconciliation_required)
    if (state.uncertain) return context.getString(R.string.shopping_remote_uncertain)
    val error = state.error ?: return context.getString(R.string.shopping_remote_load_error)
    return error.userMessage(context.getString(when {
        error.status == 403 -> R.string.shopping_remote_forbidden
        error.code == "SHOPPING_NO_STOCK" -> R.string.shopping_remote_no_stock
        error.code == "SHOPPING_CHOOSE_STOCK" -> R.string.shopping_remote_choose_stock
        error.code == "SHOPPING_AMBIGUOUS_PRODUCT" -> R.string.shopping_remote_ambiguous_product
        error.code == "SHOPPING_EXPIRY_REQUIRED" -> R.string.shopping_remote_expiry_required
        error.code == "SHOPPING_INVALID_ITEM" -> R.string.shopping_remote_invalid_item
        error.code == "SHOPPING_PRICE_REQUIRED" -> R.string.shopping_remote_price_invalid
        error.code == "SHOPPING_GRAIN_PANTRY" -> R.string.shopping_grain_pantry
        error.code == "SHOPPING_LOCAL_SAVE_FAILED" -> R.string.shopping_local_save_failed
        error.code == "SHOPPING_UNIT_MISMATCH" -> R.string.shopping_unit_mismatch
        error.code == "SHOPPING_AUTH_REQUIRED" -> R.string.shopping_auth_required
        error.code == "NETWORK_ERROR" -> R.string.shopping_remote_network_error
        error.code == "INVALID_RESPONSE" -> R.string.shopping_remote_invalid_response
        else -> R.string.shopping_remote_load_error
    }))
}

fun shoppingUnitLabel(context: Context, code: String?): String {
    val index = context.resources.getStringArray(R.array.shopping_remote_unit_codes).indexOf(code)
    return if (index >= 0) context.resources.getStringArray(R.array.shopping_remote_unit_labels)[index]
        else code.orEmpty()
}

/** Real choices only; selecting a destination never replays the pending user action. */
fun showShoppingStockSelection(context: Context, state: ShoppingState, select: (Int) -> Unit): AlertDialog =
    AlertDialog.Builder(context).setTitle(R.string.shopping_remote_choose_stock)
        .setItems(state.stocks.map { it.name?.takeIf(String::isNotBlank)
            ?: context.getString(R.string.shopping_remote_stock_unnamed) }.toTypedArray()) { _, index ->
            select(state.stocks[index].id)
        }.setNegativeButton(android.R.string.cancel, null).show()
