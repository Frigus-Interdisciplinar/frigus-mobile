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
    val appSettingsService: AppSettingsApiService get() = retrofit.create(AppSettingsApiService::class.java)
    val accountSecurityService: AccountSecurityApiService get() = retrofit.create(AccountSecurityApiService::class.java)
    val supportService: SupportApiService get() = retrofit.create(SupportApiService::class.java)
    val recipeService: RecipeApiService get() = retrofit.create(RecipeApiService::class.java)
    val stockService: StockApiService get() = retrofit.create(StockApiService::class.java)
    val notificationService: NotificationApiService get() = retrofit.create(NotificationApiService::class.java)
    val notificationSettingsService: NotificationSettingsApiService get() = retrofit.create(NotificationSettingsApiService::class.java)
    val aiRecipeService: AiRecipeApiService get() = retrofit.create(AiRecipeApiService::class.java)
    val bffAiService: AiRecipeApiService get() = aiRecipeService
    val coreAiService: AiRecipeApiService get() = aiRecipeService
    val advertisementService: AdvertisementApiService get() = retrofit.create(AdvertisementApiService::class.java)
    val receiptService: ReceiptApiService get() = retrofit.create(ReceiptApiService::class.java)
    val subscriptionService: SubscriptionApiService get() = retrofit.create(SubscriptionApiService::class.java)
    val moneySavingService: MoneySavingApiService get() = retrofit.create(MoneySavingApiService::class.java)
    val shoppingListService: ShoppingListApiService get() = retrofit.create(ShoppingListApiService::class.java)
    val firebaseSessionService: FirebaseSessionApiService get() = retrofit.create(FirebaseSessionApiService::class.java)
    val conversationService: ConversationApiService get() = retrofit.create(ConversationApiService::class.java)
}
