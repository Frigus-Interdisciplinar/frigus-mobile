package com.example.frigus_mobile.data.local

import android.content.Context
import android.content.SharedPreferences

internal data class PreferenceOwner(val userId: String, val credential: SessionCredential)

/** Durable local preferences. The account key remains compatible with the recovered screens. */
class ProfilePreferenceStore internal constructor(
    private val preferences: SharedPreferences,
    private val owner: PreferenceOwner?,
    private val currentOwner: () -> PreferenceOwner?
) {
    constructor(context: Context) : this(context.applicationContext, SessionManager(context.applicationContext))
    private constructor(context: Context, session: SessionManager) : this(
        context.getSharedPreferences("profile_local", Context.MODE_PRIVATE),
        sessionOwner(session), { sessionOwner(session) }
    )

    val isActive: Boolean get() = owner != null && currentOwner() == owner
    private fun key(name: String) = owner?.userId + "." + name

    fun getBoolean(name: String, fallback: Boolean): Boolean =
        if (isActive) runCatching { preferences.getBoolean(key(name), fallback) }.getOrDefault(fallback) else fallback

    fun putBoolean(name: String, value: Boolean): Boolean {
        if (!isActive) return false
        val key = key(name)
        val existed = preferences.contains(key)
        val previous = getBoolean(name, false)
        val saved = preferences.edit().putBoolean(key, value).commit()
        if (!saved) {
            // Android updates its in-memory map even if the disk commit fails.
            preferences.edit().apply { if (existed) putBoolean(key, previous) else remove(key) }.apply()
        }
        return saved && isActive
    }

    fun getString(name: String, fallback: String?): String? =
        if (isActive) runCatching { preferences.getString(key(name), fallback) }.getOrDefault(fallback) else fallback

    fun putString(name: String, value: String?): Boolean =
        isActive && preferences.edit().putString(key(name), value).commit()

    fun getList(name: String): List<String> =
        getString(name, null)?.split("\n")?.filter(String::isNotBlank).orEmpty()

    fun putList(name: String, values: List<String>): Boolean = putString(name, values.joinToString("\n"))

    private companion object {
        fun sessionOwner(session: SessionManager): PreferenceOwner? {
            if (!session.isLoggedIn) return null
            val credential = session.state.credential() ?: return null
            val userId = session.userId?.takeIf(String::isNotBlank) ?: return null
            return PreferenceOwner(userId, credential)
        }
    }
}
