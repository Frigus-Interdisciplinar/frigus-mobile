package com.example.frigus_mobile.utils

import android.text.TextUtils
import android.util.Patterns

object ValidationUtils {

    fun isEmailValido(email: String?): Boolean {
        return !TextUtils.isEmpty(email) && Patterns.EMAIL_ADDRESS.matcher(email ?: "").matches()
    }

    fun isSenhaValida(senha: String?): Boolean {
        if (senha.isNullOrEmpty()) return false
        // Requisitos do backend: 8 a 20 caracteres, com pelo menos 1 maiúscula, 1 minúscula, 1 número e 1 especial
        val regex = Regex("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?]).{8,20}$")
        return regex.matches(senha)
    }
}
