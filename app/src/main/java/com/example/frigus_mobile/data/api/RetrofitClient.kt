package com.example.frigus_mobile.data.api

import android.util.Log
import com.example.frigus_mobile.BuildConfig
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {
    private val BASE_URL: String = BuildConfig.BASE_URL
    private var retrofit: Retrofit? = null

    private var authTokenProvider: (() -> String?)? = null

    fun setTokenProvider(provider: () -> String?) {
        this.authTokenProvider = provider
    }

    private val okHttpClient: OkHttpClient by lazy {
        val loggingAndAuthInterceptor = Interceptor { chain ->
            val original = chain.request()
            val token = authTokenProvider?.invoke()
            val requestBuilder = original.newBuilder()

            if (!token.isNullOrEmpty()) {
                requestBuilder.header("Authorization", "Bearer $token")
            }

            val request = requestBuilder.build()
            Log.d("RetrofitClient", "--> REQUISIÇÃO: ${request.method()} ${request.url()}")

            try {
                val startNs = System.nanoTime()
                val response = chain.proceed(request)
                val tookMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs)

                Log.d("RetrofitClient", "<-- RESPOSTA [${response.code()}] (${tookMs}ms): ${request.url()}")
                response
            } catch (e: Exception) {
                Log.e("RetrofitClient", "<-- ERRO DE REDE em ${request.url()}: ${e.javaClass.simpleName} - ${e.message}", e)
                throw e
            }
        }

        OkHttpClient.Builder()
            .addInterceptor(loggingAndAuthInterceptor)
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    private fun getRetrofit(): Retrofit {
        if (retrofit == null) {
            Log.d("RetrofitClient", "Inicializando Retrofit com BASE_URL: $BASE_URL")
            retrofit = Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
        }
        return retrofit!!
    }

    val authService: AuthApiService
        get() = getRetrofit().create(AuthApiService::class.java)

    val recipeService: RecipeApiService
        get() = getRetrofit().create(RecipeApiService::class.java)
}
