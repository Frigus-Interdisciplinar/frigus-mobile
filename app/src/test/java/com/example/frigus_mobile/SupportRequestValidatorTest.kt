package com.example.frigus_mobile

import com.example.frigus_mobile.data.api.SupportRequestValidationIssue
import com.example.frigus_mobile.data.api.SupportRequestValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SupportRequestValidatorTest {
    @Test
    fun acceptsTrimmedValuesAtTheCoreApiBoundaries() {
        assertNull(SupportRequestValidator.validate(" abc ", " 1234567890 "))
        assertNull(SupportRequestValidator.validate("a".repeat(120), "b".repeat(5000)))
    }

    @Test
    fun rejectsMissingOrOutOfRangeFieldsBeforeSending() {
        assertEquals(
            SupportRequestValidationIssue.REQUIRED_FIELDS,
            SupportRequestValidator.validate(" ", "description long enough")
        )
        assertEquals(
            SupportRequestValidationIssue.SUBJECT_LENGTH,
            SupportRequestValidator.validate("ab", "description long enough")
        )
        assertEquals(
            SupportRequestValidationIssue.DESCRIPTION_LENGTH,
            SupportRequestValidator.validate("subject", "short")
        )
        assertEquals(
            SupportRequestValidationIssue.SUBJECT_LENGTH,
            SupportRequestValidator.validate("a".repeat(121), "description long enough")
        )
        assertEquals(
            SupportRequestValidationIssue.DESCRIPTION_LENGTH,
            SupportRequestValidator.validate("subject", "a".repeat(5001))
        )
    }
}
