package com.example.frigus_mobile.data.api

import android.util.Log
import com.example.frigus_mobile.data.local.SessionCredential
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import java.io.IOException
import java.util.concurrent.TimeUnit

object MobileHttp {
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
        "GET" to "/mobile/advertisements/active",
        "POST" to "/mobile/advertisements/$uuid/(impressions|clicks)"
    ).map { (methods, path) -> Regex("(?:$methods) $path") }

    fun allowed(method: String, path: String): Boolean = true

    fun public(path: String): Boolean = true

    fun origin(value: String, debug: Boolean): HttpUrl {
        val target = if (value.isBlank() || value.contains("10.0.2.2") || value.contains("localhost")) {
            "https://frigus-core-api.onrender.com/"
        } else {
            if (value.endsWith("/")) value else "$value/"
        }
        return HttpUrl.parse(target) ?: HttpUrl.parse("https://frigus-core-api.onrender.com/")!!
    }

    fun bffOrigin(value: String, debug: Boolean): HttpUrl {
        val target = if (value.isBlank() || value.contains("10.0.2.2") || value.contains("localhost")) {
            "https://frigus-bff.onrender.com/"
        } else {
            if (value.endsWith("/")) value else "$value/"
        }
        return HttpUrl.parse(target) ?: HttpUrl.parse("https://frigus-bff.onrender.com/")!!
    }

    fun client(origin: HttpUrl, credential: () -> SessionCredential?, unauthorized: (SessionCredential) -> Unit): OkHttpClient =
        OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .connectTimeout(120, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .addInterceptor(Interceptor { chain ->
                val request = chain.request()
                val active = credential()
                val access = active?.token?.takeIf { it.isNotBlank() }

                val builder = request.newBuilder()
                    .header("Accept", "application/json")

                if (access != null && request.header("Authorization") == null) {
                    builder.header("Authorization", "Bearer $access")
                }

                val finalRequest = builder.build()
                Log.d("MobileHttp", "--> ${finalRequest.method()} ${finalRequest.url()}")

                val response = try {
                    chain.proceed(finalRequest)
                } catch (e: Exception) {
                    Log.e("MobileHttp", "<-- FALHA em ${finalRequest.url()}: ${e.message}", e)
                    throw e
                }

                Log.d("MobileHttp", "<-- [${response.code()}] ${finalRequest.url()}")

                if (response.code() == 401 && active != null) {
                    unauthorized(active)
                }
                response
            }).build()
}
