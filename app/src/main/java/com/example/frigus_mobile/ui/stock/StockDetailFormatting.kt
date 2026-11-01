package com.example.frigus_mobile.ui.stock

import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

internal enum class StockDetailPriceSource { PURCHASE, CATALOG }
internal data class StockDetailPrice(val amount: Double, val source: StockDetailPriceSource)

/** The lot contract publishes a LocalDate, not a timestamp or an already localized date. */
internal fun stockDetailExpiration(value: String?): String? {
    if (value == null || !Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}").matches(value)) return null
    return runCatching {
        LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE)
            .format(DateTimeFormatter.ofPattern("dd-MM-uuuu", Locale.ROOT))
    }.getOrNull()
}

/** Catalog price is identified separately; it never becomes a confirmed acquisition cost. */
internal fun stockDetailUnitPrice(purchase: Double?, catalog: Double?): StockDetailPrice? {
    purchase?.takeIf { it.isFinite() && it > 0 }?.let { return StockDetailPrice(it, StockDetailPriceSource.PURCHASE) }
    return catalog?.takeIf { it.isFinite() && it > 0 }?.let { StockDetailPrice(it, StockDetailPriceSource.CATALOG) }
}

internal fun stockDetailCurrency(price: StockDetailPrice): String =
    NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR")).format(price.amount)
