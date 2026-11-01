package com.example.frigus_mobile

import com.example.frigus_mobile.data.model.confirmedMoneySavingAmount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneySavingValueTest {
    @Test fun unverifiedValuesNeverBecomeDisplayedAmounts() {
        assertNull(confirmedMoneySavingAmount(false, 123.45))
        assertNull(confirmedMoneySavingAmount(false, 0.0))
        assertNull(confirmedMoneySavingAmount(true, null))
    }

    @Test fun verifiedZeroIsValidButInvalidNumbersAreNot() {
        assertEquals(0.0, confirmedMoneySavingAmount(true, 0.0)!!, 0.0)
        assertEquals(27.50, confirmedMoneySavingAmount(true, 27.50)!!, 0.0)
        assertNull(confirmedMoneySavingAmount(true, Double.NaN))
        assertNull(confirmedMoneySavingAmount(true, Double.POSITIVE_INFINITY))
        assertNull(confirmedMoneySavingAmount(true, -1.0))
    }
}
