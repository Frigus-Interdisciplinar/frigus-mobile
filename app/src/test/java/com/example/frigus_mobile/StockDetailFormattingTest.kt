package com.example.frigus_mobile

import com.example.frigus_mobile.ui.stock.StockDetailPriceSource
import com.example.frigus_mobile.ui.stock.stockDetailCurrency
import com.example.frigus_mobile.ui.stock.stockDetailExpiration
import com.example.frigus_mobile.ui.stock.stockDetailUnitPrice
import org.junit.Assert.*
import org.junit.Test

class StockDetailFormattingTest {
    @Test fun displaysAnIsoLocalDateWithDayMonthYearAndHyphens() {
        assertEquals("01-11-2026", stockDetailExpiration("2026-11-01"))
        assertEquals("29-02-2028", stockDetailExpiration("2028-02-29"))
    }

    @Test fun rejectsImpossibleLocalizedAndTimestampDatesWithoutReinterpretation() {
        listOf(null, "", "2026-02-29", "2026-04-31", "01/11/2026", "01-11-2026",
            "2026-1-01", "2026-11-01T00:00:00Z").forEach { assertNull(stockDetailExpiration(it)) }
    }

    @Test fun prefersTheConfirmedLotPriceAndKeepsTheCatalogFallbackIdentified() {
        val purchase = stockDetailUnitPrice(12.34, 19.90)!!
        assertEquals(StockDetailPriceSource.PURCHASE, purchase.source)
        assertEquals(12.34, purchase.amount, 0.0)
        assertEquals("R$ 12,34", stockDetailCurrency(purchase).replace('\u00a0', ' '))
        val catalog = stockDetailUnitPrice(null, 19.90)!!
        assertEquals(StockDetailPriceSource.CATALOG, catalog.source)
        assertEquals(19.90, catalog.amount, 0.0)
    }

    @Test fun neverTurnsMissingOrInvalidPricesIntoZero() {
        listOf(null, 0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY).forEach {
            assertNull(stockDetailUnitPrice(it, null))
            assertNull(stockDetailUnitPrice(null, it))
        }
        assertEquals(StockDetailPriceSource.CATALOG, stockDetailUnitPrice(0.0, 5.0)?.source)
    }
}
