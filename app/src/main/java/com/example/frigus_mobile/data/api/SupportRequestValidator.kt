package com.example.frigus_mobile.data.api

enum class SupportRequestValidationIssue {
    REQUIRED_FIELDS,
    SUBJECT_LENGTH,
    DESCRIPTION_LENGTH
}

object SupportRequestValidator {
    const val SUBJECT_MIN_LENGTH = 3
    const val SUBJECT_MAX_LENGTH = 120
    const val DESCRIPTION_MIN_LENGTH = 10
    const val DESCRIPTION_MAX_LENGTH = 5000

    fun validate(subject: String, description: String): SupportRequestValidationIssue? {
        val normalizedSubject = subject.trim()
        val normalizedDescription = description.trim()
        if (normalizedSubject.isEmpty() || normalizedDescription.isEmpty()) {
            return SupportRequestValidationIssue.REQUIRED_FIELDS
        }
        if (normalizedSubject.length !in SUBJECT_MIN_LENGTH..SUBJECT_MAX_LENGTH) {
            return SupportRequestValidationIssue.SUBJECT_LENGTH
        }
        if (normalizedDescription.length !in DESCRIPTION_MIN_LENGTH..DESCRIPTION_MAX_LENGTH) {
            return SupportRequestValidationIssue.DESCRIPTION_LENGTH
        }
        return null
    }
}
