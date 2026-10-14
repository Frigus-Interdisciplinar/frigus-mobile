package com.example.frigus_mobile.data.local

import android.content.Context
import android.content.SharedPreferences
import com.example.frigus_mobile.data.api.RetrofitClient
import com.example.frigus_mobile.data.model.UserResponse

class SessionManager(context: Context) {
    private val preferences: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    init {
        RetrofitClient.setTokenProvider(
            accessTokenProvider = { accessToken },
            refreshTokenProvider = { refreshToken },
            onTokensRefreshed = { newAccess, newRefresh, user ->
                updateTokensOnly(newAccess, newRefresh)
                if (user != null) {
                    saveUserProfile(user)
                }
            }
        )
    }

    fun saveTokens(accessToken: String?, refreshToken: String?, name: String? = null) {
        val editor = preferences.edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putString(KEY_REFRESH_TOKEN, refreshToken)
            // Limpa grupo/estoque em cache ao iniciar nova sessão para não usar stockId de outro usuário
            .remove(KEY_ACTIVE_GROUP_ID)
            .remove(KEY_ACTIVE_STOCK_ID)
        if (!name.isNullOrBlank()) {
            editor.putString(KEY_USER_NAME, name)
        }
        editor.apply()
    }

    private fun updateTokensOnly(accessToken: String?, refreshToken: String?) {
        preferences.edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putString(KEY_REFRESH_TOKEN, refreshToken)
            .apply()
    }

    fun saveUserProfile(user: UserResponse?) {
        if (user == null) return
        val editor = preferences.edit()
        if (!user.name.isNullOrBlank()) editor.putString(KEY_USER_NAME, user.name)
        if (!user.email.isNullOrBlank()) editor.putString(KEY_USER_EMAIL, user.email)
        if (!user.birthDate.isNullOrBlank()) editor.putString(KEY_USER_BIRTH_DATE, user.birthDate)
        if (!user.accountType.isNullOrBlank()) editor.putString(KEY_USER_ACCOUNT_TYPE, user.accountType)
        editor.apply()
    }

    fun saveActiveGroupAndStock(groupId: String?, stockId: Int?) {
        preferences.edit()
            .putString(KEY_ACTIVE_GROUP_ID, groupId)
            .apply {
                if (stockId != null) {
                    putInt(KEY_ACTIVE_STOCK_ID, stockId)
                } else {
                    remove(KEY_ACTIVE_STOCK_ID)
                }
            }
            .apply()
    }

    val accessToken: String?
        get() = preferences.getString(KEY_ACCESS_TOKEN, null)

    val refreshToken: String?
        get() = preferences.getString(KEY_REFRESH_TOKEN, null)

    val userName: String?
        get() = preferences.getString(KEY_USER_NAME, null)

    val userEmail: String?
        get() = preferences.getString(KEY_USER_EMAIL, null)

    val userBirthDate: String?
        get() = preferences.getString(KEY_USER_BIRTH_DATE, null)

    val userAccountType: String?
        get() = preferences.getString(KEY_USER_ACCOUNT_TYPE, null)

    val activeGroupId: String?
        get() = preferences.getString(KEY_ACTIVE_GROUP_ID, null)

    val activeStockId: Int?
        get() = if (preferences.contains(KEY_ACTIVE_STOCK_ID)) preferences.getInt(KEY_ACTIVE_STOCK_ID, -1).takeIf { it > 0 } else null

    fun clearSession() {
        preferences.edit().clear().apply()
    }

    val isLoggedIn: Boolean
        get() = accessToken != null

    companion object {
        private const val PREF_NAME = "auth"
        private const val KEY_ACCESS_TOKEN = "accessToken"
        private const val KEY_REFRESH_TOKEN = "refreshToken"
        private const val KEY_USER_NAME = "userName"
        private const val KEY_USER_EMAIL = "userEmail"
        private const val KEY_USER_BIRTH_DATE = "userBirthDate"
        private const val KEY_USER_ACCOUNT_TYPE = "userAccountType"
        private const val KEY_ACTIVE_GROUP_ID = "activeGroupId"
        private const val KEY_ACTIVE_STOCK_ID = "activeStockId"
    }
}
