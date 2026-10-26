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
        "GET" to "/mobile/user/profile",
        "GET" to "/mobile/inventory/(home|products)",
        "GET" to "/mobile/inventory/stocks/[1-9][0-9]*/products",
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
        "POST|DELETE" to "/mobile/push-tokens",
        "GET" to "/mobile/advertisements/active",
        "POST" to "/mobile/advertisements/$uuid/(impressions|clicks)"
    ).map { (methods, path) -> Regex("(?:$methods) $path") }

    fun allowed(method: String, path: String): Boolean = routes.any { it.matches("$method $path") } &&
        path.split('/').filter { it.isNotEmpty() && it.all(Char::isDigit) }.all { it.toIntOrNull() != null }
    fun public(path: String): Boolean = path == "/mobile/user/login" || path == "/mobile/user/register"

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

    fun client(origin: HttpUrl, credential: () -> SessionCredential?, unauthorized: (SessionCredential) -> Unit): OkHttpClient =
        OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .retryOnConnectionFailure(false)
            .connectTimeout(10, TimeUnit.SECONDS)
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
                val public = public(url.encodedPath())
                val active = if (public) null else credential()
                val access = active?.token?.takeIf { it.isNotBlank() }
                if (!public && access == null) throw IOException("Authentication required")
                val builder = request.newBuilder().tag(SendOnce::class.java, SendOnce()).removeHeader("Authorization").header("Accept", "application/json")
                if (access != null) builder.header("Authorization", "Bearer $access")
                val response = chain.proceed(builder.build())
                if (response.code() == 401 && access != null) unauthorized(active!!)
                response
            }).build()
}
