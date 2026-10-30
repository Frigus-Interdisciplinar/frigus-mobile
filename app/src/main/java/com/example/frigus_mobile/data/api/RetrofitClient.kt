package com.example.frigus_mobile.data.api

import com.example.frigus_mobile.BuildConfig
import com.example.frigus_mobile.data.local.SessionState
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {
    @Volatile private var session: SessionState? = null
    fun setSession(state: SessionState) { session = state }

    private val retrofit: Retrofit by lazy {
        val origin = MobileHttp.origin(BuildConfig.BFF_BASE_URL, BuildConfig.DEBUG)
        Retrofit.Builder().baseUrl(origin)
            .client(MobileHttp.client(origin, { session?.credential() }, { session?.invalidate(it) }))
            .addConverterFactory(GsonConverterFactory.create()).build()
    }

    val authService: AuthApiService get() = retrofit.create(AuthApiService::class.java)
    val recipeService: RecipeApiService get() = retrofit.create(RecipeApiService::class.java)
    val stockService: StockApiService get() = retrofit.create(StockApiService::class.java)
    val notificationService: NotificationApiService get() = retrofit.create(NotificationApiService::class.java)
    val aiRecipeService: AiRecipeApiService get() = retrofit.create(AiRecipeApiService::class.java)
    val bffAiService: AiRecipeApiService get() = aiRecipeService
    val coreAiService: AiRecipeApiService get() = aiRecipeService
    val advertisementService: AdvertisementApiService get() = retrofit.create(AdvertisementApiService::class.java)
    val receiptService: ReceiptApiService get() = retrofit.create(ReceiptApiService::class.java)
}
