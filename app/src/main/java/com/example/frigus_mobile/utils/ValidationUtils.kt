package com.example.frigus_mobile.utils

import android.text.TextUtils
import android.util.Patterns

object ValidationUtils {

    fun isEmailValido(email: String?): Boolean {
        return !TextUtils.isEmpty(email) && Patterns.EMAIL_ADDRESS.matcher(email ?: "").matches()
    }

    fun isSenhaValida(senha: String?): Boolean {
        return !TextUtils.isEmpty(senha) &&
                (senha?.length ?: 0) >= 8 &&
                (senha?.matches(Regex(".*[^a-zA-Z0-9].*")) == true)
    }
}
