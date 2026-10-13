package com.example.frigus_mobile.data.api

import android.util.Log
import com.example.frigus_mobile.BuildConfig
import com.example.frigus_mobile.data.model.UserResponse
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {
    private val BASE_URL: String = BuildConfig.BASE_URL
    private var retrofit: Retrofit? = null

    private var authTokenProvider: (() -> String?)? = null
    private var refreshTokenProvider: (() -> String?)? = null
    private var onTokensRefreshedCallback: ((String?, String?, UserResponse?) -> Unit)? = null

    fun setTokenProvider(
        accessTokenProvider: () -> String?,
        refreshTokenProvider: (() -> String?)? = null,
        onTokensRefreshed: ((String?, String?, UserResponse?) -> Unit)? = null
    ) {
        this.authTokenProvider = accessTokenProvider
        if (refreshTokenProvider != null) {
            this.refreshTokenProvider = refreshTokenProvider
        }
        if (onTokensRefreshed != null) {
            this.onTokensRefreshedCallback = onTokensRefreshed
        }
    }

    private val okHttpClient: OkHttpClient by lazy {
        val loggingAndAuthInterceptor = Interceptor { chain ->
            val original = chain.request()
            val path = original.url().encodedPath()
            val isPublicAuthEndpoint = path.endsWith("/auth/login") ||
                path.endsWith("/auth/register") ||
                path.endsWith("/auth/google") ||
                path.endsWith("/auth/refresh")

            val token = authTokenProvider?.invoke()
            val requestBuilder = original.newBuilder()

            if (!token.isNullOrEmpty() && !isPublicAuthEndpoint) {
                requestBuilder.header("Authorization", "Bearer $token")
            }

            val request = requestBuilder.build()
            Log.d("RetrofitClient", "--> REQUISIÇÃO: ${request.method()} ${request.url()}")

            try {
                val startNs = System.nanoTime()
                var response = chain.proceed(request)
                val tookMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs)

                Log.d("RetrofitClient", "<-- RESPOSTA [${response.code()}] (${tookMs}ms): ${request.url()}")

                // Se o accessToken expirou (401 ou 403 sem corpo de ForbiddenException), tenta renovar via refreshToken
                if ((response.code() == 401 || response.code() == 403) && !isPublicAuthEndpoint) {
                    val currentRefresh = refreshTokenProvider?.invoke()
                    if (!currentRefresh.isNullOrBlank()) {
                        try {
                            val refreshCall = authService.refresh(mapOf("refreshToken" to currentRefresh))
                            val refreshResp = refreshCall.execute()
                            val refreshBody = refreshResp.body()
                            if (refreshResp.isSuccessful && refreshBody?.accessToken != null) {
                                Log.d("RetrofitClient", "Token renovado com sucesso via /auth/refresh. Repetindo requisição...")
                                onTokensRefreshedCallback?.invoke(
                                    refreshBody.accessToken,
                                    refreshBody.refreshToken,
                                    refreshBody.user
                                )
                                response.close()
                                val retryRequest = original.newBuilder()
                                    .header("Authorization", "Bearer ${refreshBody.accessToken}")
                                    .build()
                                response = chain.proceed(retryRequest)
                            }
                        } catch (re: Exception) {
                            Log.w("RetrofitClient", "Não foi possível renovar token automaticamente: ${re.message}")
                        }
                    }
                }

                response
            } catch (e: Exception) {
                Log.e("RetrofitClient", "<-- ERRO DE REDE em ${request.url()}: ${e.javaClass.simpleName} - ${e.message}", e)
                throw e
            }
        }

        OkHttpClient.Builder()
            .addInterceptor(loggingAndAuthInterceptor)
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
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

    val stockService: StockApiService
        get() = getRetrofit().create(StockApiService::class.java)

    val notificationService: NotificationApiService
        get() = getRetrofit().create(NotificationApiService::class.java)

    val aiRecipeService: AiRecipeApiService
        get() = getRetrofit().create(AiRecipeApiService::class.java)
}
