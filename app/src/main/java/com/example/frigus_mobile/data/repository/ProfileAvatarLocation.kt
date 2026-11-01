package com.example.frigus_mobile.data.repository

import java.net.URI
import java.security.MessageDigest
import java.util.Locale

internal object ProfileAvatarLocation {
    fun googleHttpsUrl(value: String?): String? {
        if (value.isNullOrBlank()) return null
        val uri = runCatching { URI(value) }.getOrNull() ?: return null
        val host = uri.host?.lowercase(Locale.ROOT) ?: return null
        val google = host == "googleusercontent.com" || host.endsWith(".googleusercontent.com") ||
            host == "ggpht.com" || host.endsWith(".ggpht.com")
        return value.takeIf { google && uri.scheme.equals("https", true) && uri.rawUserInfo == null &&
            uri.port in listOf(-1, 443) && uri.rawFragment == null }
    }

    fun cacheKey(userId: String, url: String): String = MessageDigest.getInstance("SHA-256")
        .digest((userId + "\n" + url).toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
