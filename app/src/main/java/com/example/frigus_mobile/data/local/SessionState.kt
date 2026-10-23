package com.example.frigus_mobile.data.local

import com.example.frigus_mobile.data.model.UserResponse

data class SessionCredential(val token: String, val generation: Long)

/** Process state; restored tokens require M03 before access is granted. */
class SessionState(
    initialToken: String? = null,
    initialUser: UserResponse? = null,
    private val persist: (String, UserResponse?) -> Unit = { _, _ -> },
    private val erase: () -> Unit = {},
    private val changed: (Boolean) -> Unit = {}
) {
    @Volatile var accessToken: String? = initialToken?.takeIf { it.isNotBlank() }
        private set
    @Volatile var user: UserResponse? = initialUser
        private set
    @Volatile var validated: Boolean = false
        private set
    @Volatile var generation: Long = 0
        private set

    @Synchronized fun clear() {
        generation++
        accessToken = null
        user = null
        validated = false
        erase()
        changed(false)
    }

    @Synchronized fun beginAuthentication(): Long {
        clear()
        return generation
    }

    @Synchronized fun authenticate(expected: Long, token: String?, profile: UserResponse?): Boolean {
        if (expected != generation || token.isNullOrBlank()) return false
        persist(token, profile)
        accessToken = token
        user = profile
        validated = true
        changed(true)
        return true
    }

    @Synchronized fun validate(expected: Long, token: String, profile: UserResponse): Boolean {
        if (expected != generation || accessToken != token) return false
        return authenticate(expected, token, profile)
    }

    @Synchronized fun credential(): SessionCredential? = accessToken?.let { SessionCredential(it, generation) }

    @Synchronized fun invalidate(credential: SessionCredential) {
        if (accessToken == credential.token && generation == credential.generation) clear()
    }

    @Synchronized fun updateUser(profile: UserResponse) {
        val token = accessToken ?: return
        persist(token, profile)
        user = profile
    }
}
