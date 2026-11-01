package com.example.frigus_mobile.data.model

data class PasswordRecoveryRequest(val email: String)
data class PasswordCodeRequest(val email: String, val code: String)
data class PasswordResetRequest(val email: String, val code: String, val newPassword: String)
