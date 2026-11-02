package com.example.frigus_mobile.data.local

import com.example.frigus_mobile.data.model.UserResponse

data class SessionCredential(val token: String, val generation: Long)

/** Process state; restored tokens require M03 before access is granted. */
class SessionState(
    initialToken: String? = null,
    initialRefreshToken: String? = null,
    initialUser: UserResponse? = null,
    private val persist: (String, String?, UserResponse?) -> Unit = { _, _, _ -> },
    private val erase: () -> Unit = {},
    private val changed: (Boolean) -> Unit = {}
) {
    @Volatile var accessToken: String? = initialToken?.takeIf { it.isNotBlank() }
        private set
    @Volatile var refreshToken: String? = initialRefreshToken?.takeIf { it.isNotBlank() }
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
        refreshToken = null
        user = null
        validated = false
        erase()
        changed(false)
    }

    @Synchronized fun beginAuthentication(): Long {
        clear()
        return generation
    }

    @Synchronized fun authenticate(expected: Long, token: String?, refreshToken: String?, profile: UserResponse?): Boolean {
        if (expected != generation || token.isNullOrBlank()) return false
        persist(token, refreshToken, profile)
        accessToken = token
        this.refreshToken = refreshToken
        user = profile
        validated = true
        changed(true)
        return true
    }

    @Synchronized fun authenticate(expected: Long, token: String?, profile: UserResponse?): Boolean =
        authenticate(expected, token, this.refreshToken, profile)

    @Synchronized fun validate(expected: Long, token: String, refreshToken: String?, profile: UserResponse): Boolean {
        if (expected != generation || accessToken != token) return false
        return authenticate(expected, token, refreshToken, profile)
    }

    @Synchronized fun validate(expected: Long, token: String, profile: UserResponse): Boolean =
        validate(expected, token, this.refreshToken, profile)

    @Synchronized fun updateTokens(accessToken: String, refreshToken: String?, profile: UserResponse? = null) {
        persist(accessToken, refreshToken, profile ?: user)
        this.accessToken = accessToken
        if (!refreshToken.isNullOrBlank()) this.refreshToken = refreshToken
        if (profile != null) this.user = profile
        this.validated = true
        changed(true)
    }

    @Synchronized fun credential(): SessionCredential? = accessToken?.let { SessionCredential(it, generation) }

    @Synchronized fun invalidate(credential: SessionCredential) {
        if (accessToken == credential.token && generation == credential.generation) clear()
    }

    @Synchronized fun updateUser(profile: UserResponse) {
        val token = accessToken ?: return
        persist(token, refreshToken, profile)
        user = profile
    }
}
