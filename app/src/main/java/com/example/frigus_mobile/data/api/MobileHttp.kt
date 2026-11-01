package com.example.frigus_mobile.data.api

import com.example.frigus_mobile.data.local.SessionCredential
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

object MobileHttp {
    private class SendOnce(val sent: AtomicBoolean = AtomicBoolean(false))
    private val uuid = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"
    private val routes = listOf(
        "POST" to "/mobile/user/(login|register)",
        "POST" to "/mobile/core/auth/google",
        "POST" to "/mobile/core/auth/(forgot-password|verify-reset-code|reset-password)",
        "POST" to "/mobile/firebase/session",
        "GET|PATCH" to "/mobile/core/profile",
        "PATCH" to "/mobile/core/profile/password",
        "GET" to "/mobile/core/profile/sessions",
        "DELETE" to "/mobile/core/profile/sessions/$uuid",
        "GET" to "/mobile/core/profile/email-verification",
        "POST" to "/mobile/core/profile/email-verification/(request|verify)",
        "GET|PUT" to "/mobile/core/profile/app-settings",
        "GET|POST" to "/mobile/core/support/requests",
        "GET" to "/mobile/user/profile",
        "GET" to "/mobile/inventory/(home|products)",
        "GET|POST" to "/mobile/inventory/stocks/[1-9][0-9]*/products",
        "GET|POST" to "/mobile/core/products",
        "GET|POST" to "/mobile/shopping-list/?",
        "GET|DELETE" to "/mobile/shopping-list/$uuid",
        "POST" to "/mobile/shopping-list/$uuid/items",
        "PATCH|DELETE" to "/mobile/shopping-list/$uuid/items/[1-9][0-9]*",
        "POST" to "/mobile/inventory/receipts/scan",
        "GET" to "/mobile/inventory/receipts/$uuid",
        "POST" to "/mobile/inventory/receipts/$uuid/confirm",
        "GET|PUT|DELETE" to "/mobile/inventory/stock-products/[1-9][0-9]*",
        "GET|POST" to "/mobile/inventory/stock-products/[1-9][0-9]*/movements",
        "GET" to "/mobile/recipes",
        "GET" to "/mobile/recipes/(favorites|suggestions)",
        "GET" to "/mobile/recipes/[1-9][0-9]*",
        "PUT|DELETE" to "/mobile/recipes/[1-9][0-9]*/favorite",
        "GET" to "/mobile/recipes/ai/suggestions/[1-9][0-9]*",
        "POST" to "/mobile/recipes/ai/chat",
        "GET|DELETE" to "/mobile/notifications",
        "PATCH" to "/mobile/notifications/$uuid/read",
        "PATCH" to "/mobile/notifications/read-all",
        "GET|PUT" to "/mobile/core/profile/preferences",
        "GET" to "/mobile/advertisements/active",
        "POST" to "/mobile/advertisements/$uuid/(impressions|clicks)",
        "GET" to "/mobile/core/plans/(active|me/limits)",
        "GET" to "/mobile/core/subscriptions/me",
        "POST" to "/mobile/core/subscriptions/me/(cancel|reactivate)",
        "GET" to "/mobile/core/transactions",
        "POST" to "/mobile/core/transactions/checkout",
        "GET|POST" to "/mobile/core/groups",
        "POST" to "/mobile/core/stocks",
        "GET" to "/mobile/core/groups/$uuid",
        "GET|POST" to "/mobile/core/groups/$uuid/invitations",
        "DELETE" to "/mobile/core/groups/$uuid/invitations/$uuid",
        "GET" to "/mobile/commercial/groups/$uuid/money-saving/(summary|events)",
        "POST" to "/mobile/commercial/groups/$uuid/food-purchases",
        "GET|PUT|DELETE" to "/mobile/commercial/groups/$uuid/food-purchases/$uuid",
        "GET" to "/mobile/core/conversations",
        "GET" to "/mobile/core/conversations/$uuid",
        "GET|POST" to "/mobile/core/conversations/$uuid/messages",
        "POST" to "/mobile/core/conversations/$uuid/messages/[1-9][0-9]*/read"
    ).map { (methods, path) -> Regex("(?:$methods) $path") }

    fun allowed(method: String, path: String): Boolean = routes.any { it.matches("$method $path") } &&
        path.split('/').filter { it.isNotEmpty() && it.all(Char::isDigit) }.all { it.toIntOrNull() != null }

    fun public(path: String): Boolean {
        return path in setOf("/mobile/user/login", "/mobile/user/register", "/mobile/core/auth/google",
            "/mobile/core/auth/forgot-password", "/mobile/core/auth/verify-reset-code", "/mobile/core/auth/reset-password")
    }

    fun isPublicPath(path: String): Boolean = public(path)

    fun origin(value: String, debug: Boolean): HttpUrl {
        val url = HttpUrl.parse(value) ?: error("Configure FRIGUS_BFF_BASE_URL with a BFF origin")
        require(url.encodedPath() == "/" && url.query() == null && url.fragment() == null &&
            url.username().isEmpty() && url.password().isEmpty()) { "BFF URL must contain only the origin" }
        require(url.host() != "frigus-core-api.onrender.com") { "Core is not a mobile BFF" }
        require(url.isHttps || (debug && url.host() in setOf("10.0.2.2", "localhost", "127.0.0.1"))) {
            "Use HTTPS for the BFF; HTTP is allowed only for local debug"
        }
        return url
    }

    fun bffOrigin(value: String, debug: Boolean): HttpUrl = origin(value, debug)

    fun client(origin: HttpUrl, credential: () -> SessionCredential?, unauthorized: (SessionCredential) -> Unit): OkHttpClient =
        OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .retryOnConnectionFailure(false)
            .connectTimeout(40, TimeUnit.SECONDS)
            .readTimeout(40, TimeUnit.SECONDS)
            .writeTimeout(40, TimeUnit.SECONDS)
            // OkHttp can also follow up HTTP 503 + Retry-After: 0; never send a call twice.
            .addNetworkInterceptor(Interceptor { chain ->
                val attempt = chain.request().tag(SendOnce::class.java)
                if (attempt == null || !attempt.sent.compareAndSet(false, true))
                    throw IOException("Automatic replay disabled")
                chain.proceed(chain.request())
            })
            .addInterceptor(Interceptor { chain ->
                val request = chain.request()
                val url = request.url()
                if (url.scheme() != origin.scheme() || url.host() != origin.host() || url.port() != origin.port() ||
                    !allowed(request.method(), url.encodedPath())) throw IOException("Unavailable mobile contract")
                val isPublic = public(url.encodedPath())
                val active = if (isPublic) null else credential()
                val access = active?.token?.takeIf { it.isNotBlank() }
                if (!isPublic && access == null) throw IOException("Authentication required")
                val builder = request.newBuilder().tag(SendOnce::class.java, SendOnce()).removeHeader("Authorization").header("Accept", "application/json")
                if (access != null) builder.header("Authorization", "Bearer $access")
                val longRunning = url.encodedPath() == "/mobile/recipes/ai/chat" ||
                    url.encodedPath() == "/mobile/inventory/receipts/scan"
                val requestChain = if (longRunning) chain.withReadTimeout(135, TimeUnit.SECONDS) else chain
                val response = requestChain.proceed(builder.build())
                if (response.code() == 401 && access != null && !isPublic) {
                    unauthorized(active!!)
                }
                response
            }).build()
}
