package com.example.frigus_mobile.data.local

import android.content.Context
import android.content.SharedPreferences
import com.example.frigus_mobile.data.api.RetrofitClient

class SessionManager(context: Context) {
    private val preferences: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    init {
        // Assegura que o RetrofitClient sempre tenha o token do usuário autenticado
        RetrofitClient.setTokenProvider { accessToken }
    }

    fun saveTokens(accessToken: String?, refreshToken: String?, name: String? = null) {
        val editor = preferences.edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putString(KEY_REFRESH_TOKEN, refreshToken)
        if (!name.isNullOrBlank()) {
            editor.putString(KEY_USER_NAME, name)
        }
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
        private const val KEY_ACTIVE_GROUP_ID = "activeGroupId"
        private const val KEY_ACTIVE_STOCK_ID = "activeStockId"
    }
}
