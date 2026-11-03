package com.example.frigus_mobile.utils

import android.text.TextUtils
import android.util.Patterns

object ValidationUtils {

    fun isEmailValido(email: String?): Boolean {
        return !TextUtils.isEmpty(email) && Patterns.EMAIL_ADDRESS.matcher(email ?: "").matches()
    }

    fun isBirthDateValida(value: String, today: java.time.LocalDate = java.time.LocalDate.now()): Boolean {
        return try {
            val format = java.time.format.DateTimeFormatter.ofPattern("dd/MM/uuuu")
                .withResolverStyle(java.time.format.ResolverStyle.STRICT)
            val birth = java.time.LocalDate.parse(value, format)
            !birth.isAfter(today.minusYears(16))
        } catch (_: java.time.DateTimeException) { false }
    }

    fun isSenhaValida(senha: String?): Boolean {
        if (senha.isNullOrEmpty()) return false
        // Requisitos do backend: 8 a 20 caracteres, com pelo menos 1 maiúscula, 1 minúscula, 1 número e 1 especial
        val regex = Regex("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?]).{8,20}$")
        return regex.matches(senha)
    }
}
