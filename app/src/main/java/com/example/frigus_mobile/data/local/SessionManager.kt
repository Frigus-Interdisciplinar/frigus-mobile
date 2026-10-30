package com.example.frigus_mobile.data.local

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.lifecycle.MutableLiveData
import com.example.frigus_mobile.BuildConfig
import com.example.frigus_mobile.data.api.RetrofitClient
import com.example.frigus_mobile.data.model.UserResponse
import com.google.gson.Gson
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SessionManager(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    val state: SessionState

    init {
        synchronized(SessionManager::class.java) {
            if (sharedState == null) {
                // Old Core credentials must never become BFF credentials implicitly.
                context.applicationContext.getSharedPreferences("auth", Context.MODE_PRIVATE).edit().clear().apply()
                var token: String? = null
                var user: UserResponse? = null
                try {
                    val ciphertext = preferences.getString("access", null)
                    val iv = preferences.getString("iv", null)
                    if (ciphertext != null && iv != null) {
                        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
                        token = String(cipher.doFinal(Base64.decode(ciphertext, Base64.NO_WRAP)), Charsets.UTF_8)
                        user = Gson().fromJson(preferences.getString("profile", null), UserResponse::class.java)
                    }
                } catch (_: Exception) {
                    token = null
                    user = null
                    preferences.edit().clear().apply()
                }
                sharedState = SessionState(token, user,
                    persist = { access, profile ->
                        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                        cipher.init(Cipher.ENCRYPT_MODE, key())
                        val encrypted = cipher.doFinal(access.toByteArray(Charsets.UTF_8))
                        check(preferences.edit().clear()
                            .putString("origin", BuildConfig.BFF_BASE_URL)
                            .putString("access", Base64.encodeToString(encrypted, Base64.NO_WRAP))
                            .putString("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
                            .putString("profile", Gson().toJson(profile))
                            .commit()) { "Session persistence failed" }
                    },
                    erase = { preferences.edit().clear().apply() },
                    changed = { authenticated.postValue(it) })
            }
            state = sharedState!!
        }
        RetrofitClient.setSession(state)
    }

    val accessToken: String? get() = state.accessToken
    val isLoggedIn: Boolean get() = state.validated && !accessToken.isNullOrBlank()
    val userName: String? get() = state.user?.name
    val userEmail: String? get() = state.user?.email
    val userBirthDate: String? get() = state.user?.birthDate
    val userAccountType: String? get() = state.user?.accountType
    val userId: String? get() = state.user?.id
    /** Conta do tipo Doméstico (backend: "DOMESTIC"). Usado para liberar o MoneySaving. */
    val isDomestic: Boolean get() = userAccountType.equals("DOMESTIC", ignoreCase = true)
    val activeGroupId: String? get() = preferences.getString("group", null)
    val activeStockId: Int? get() = preferences.getInt("stock", -1).takeIf { it > 0 }
    val googlePhotoUrl: String? get() = preferences.getString("google_photo_url", null)

    fun saveGooglePhotoUrl(url: String?) {
        if (!url.isNullOrBlank()) {
            preferences.edit().putString("google_photo_url", url).apply()
        }
    }

    fun saveUserProfile(user: UserResponse?) { if (user != null) state.updateUser(user) }
    fun clearSession() = state.clear()
    fun saveActiveGroupAndStock(groupId: String?, stockId: Int?) {
        preferences.edit().putString("group", groupId).apply {
            if (stockId != null) putInt("stock", stockId) else remove("stock")
        }.apply()
    }

    companion object {
        const val PREF_NAME = "mobile_bff_session"
        val authenticated = MutableLiveData(false)
        private var sharedState: SessionState? = null
        private const val KEY_ALIAS = "frigus_mobile_bff_access_v1"

        private fun key(): SecretKey {
            val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
            return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
                init(KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build())
            }.generateKey()
        }
    }
}
