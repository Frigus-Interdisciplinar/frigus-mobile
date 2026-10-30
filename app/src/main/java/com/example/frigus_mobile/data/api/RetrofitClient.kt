package com.example.frigus_mobile.data.api

import com.example.frigus_mobile.BuildConfig
import com.example.frigus_mobile.data.local.SessionState
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {
    @Volatile private var session: SessionState? = null
    fun setSession(state: SessionState) { session = state }

    // Core API default client (usado para auth, estoques, movimentações, etc.)
    private val retrofit: Retrofit by lazy {
        val origin = MobileHttp.origin(BuildConfig.CORE_BASE_URL, BuildConfig.DEBUG)
        Retrofit.Builder().baseUrl(origin)
            .client(MobileHttp.client(origin, { session?.credential() }, { session?.invalidate(it) }))
            .addConverterFactory(GsonConverterFactory.create()).build()
    }

    // BFF client (usado prioritariamente para endpoints /mobile, ex: chat IA)
    private val bffRetrofit: Retrofit by lazy {
        val bffUrl = MobileHttp.bffOrigin(BuildConfig.BFF_BASE_URL, BuildConfig.DEBUG)
        Retrofit.Builder().baseUrl(bffUrl)
            .client(MobileHttp.client(bffUrl, { session?.credential() }, { session?.invalidate(it) }))
            .addConverterFactory(GsonConverterFactory.create()).build()
    }

    val authService: AuthApiService get() = retrofit.create(AuthApiService::class.java)
    val recipeService: RecipeApiService get() = retrofit.create(RecipeApiService::class.java)
    val stockService: StockApiService get() = retrofit.create(StockApiService::class.java)
    val notificationService: NotificationApiService get() = retrofit.create(NotificationApiService::class.java)

    // Serviços de IA: BFF (prioritário) e Core API (fallback)
    val bffAiService: AiRecipeApiService get() = bffRetrofit.create(AiRecipeApiService::class.java)
    val coreAiService: AiRecipeApiService get() = retrofit.create(AiRecipeApiService::class.java)
    val aiRecipeService: AiRecipeApiService get() = bffAiService
}
