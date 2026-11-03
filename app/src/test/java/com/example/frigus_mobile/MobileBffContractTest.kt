package com.example.frigus_mobile

import com.example.frigus_mobile.data.api.*
import com.example.frigus_mobile.data.local.SessionCredential
import com.example.frigus_mobile.data.model.*
import okhttp3.HttpUrl
import okhttp3.MediaType
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class MobileBffContractTest {
    private val id = "123e4567-e89b-12d3-a456-426614174000"

    @Test fun allPublishedRoutesUseTheBffOriginAndCorrectMethod() {
        val requests = mutableListOf<okhttp3.Request>()
        val origin = MobileHttp.origin("https://bff.example/", false)
        val client = MobileHttp.client(origin, { SessionCredential("access", 1) }, {})
            .newBuilder().addInterceptor { chain ->
                requests.add(chain.request())
                val path = chain.request().url().encodedPath()
                val method = chain.request().method()
                val noContent = method == "DELETE" || path.endsWith("/favorite") ||
                    path.endsWith("/read-all") || path.endsWith("/impressions") || path.endsWith("/clicks")
                val list = path.endsWith("/suggestions") || path.contains("/ai/suggestions/")
                val body = if (list) "[]" else "{\"content\":[],\"totalPages\":0,\"number\":0,\"size\":20,\"totalElements\":0}"
                Response.Builder().request(chain.request()).protocol(okhttp3.Protocol.HTTP_1_1)
                    .code(if (noContent) 204 else if (path.endsWith("/register") || path.endsWith("/movements") && method == "POST" || path.endsWith("/scan")) 201 else 200)
                    .message("OK")
                    .body(ResponseBody.create(MediaType.parse("application/json"), if (noContent) "" else body))
                    .build()
            }.build()
        val retrofit = Retrofit.Builder().baseUrl(origin).client(client)
            .addConverterFactory(GsonConverterFactory.create()).build()
        val auth = retrofit.create(AuthApiService::class.java)
        val inventory = retrofit.create(StockApiService::class.java)
        val recipes = retrofit.create(RecipeApiService::class.java)
        val ai = retrofit.create(AiRecipeApiService::class.java)
        val notifications = retrofit.create(NotificationApiService::class.java)
        val advertisements = retrofit.create(AdvertisementApiService::class.java)
        val receipts = retrofit.create(ReceiptApiService::class.java)
        val calls: List<Pair<String, Call<*>>> = listOf(
            "POST /mobile/user/login" to auth.login(UserLoginRequest("a@example.test", "secret")),
            "POST /mobile/user/register" to auth.cadastrar(UserRegisterRequest("A", "01/01/2000", "a@example.test", "secret")),
            "GET /mobile/user/profile" to auth.getProfile(),
            "GET /mobile/inventory/home" to inventory.getInventoryHome(),
            "GET /mobile/inventory/products" to inventory.getProducts(),
            "GET /mobile/inventory/stocks/3/products" to inventory.getStockProducts(3),
            "GET /mobile/inventory/stock-products/4" to inventory.getStockProductById(4),
            "PUT /mobile/inventory/stock-products/4" to inventory.updateStockProduct(4, StockProductUpdateRequest(quantity = 2)),
            "DELETE /mobile/inventory/stock-products/4" to inventory.deleteStockProduct(4),
            "GET /mobile/inventory/stock-products/4/movements" to inventory.getMovements(4),
            "POST /mobile/inventory/stock-products/4/movements" to inventory.createMovement(4, StockMovementRequest("OUT", 1)),
            "GET /mobile/recipes" to recipes.getAllRecipes(),
            "GET /mobile/recipes/favorites" to recipes.getFavorites(),
            "GET /mobile/recipes/suggestions" to recipes.getSuggestions(),
            "GET /mobile/recipes/7" to recipes.getRecipe(7),
            "PUT /mobile/recipes/7/favorite" to recipes.addFavorite(7),
            "DELETE /mobile/recipes/7/favorite" to recipes.removeFavorite(7),
            "GET /mobile/recipes/ai/suggestions/3" to recipes.getSuggestionsByStock(3),
            "POST /mobile/recipes/ai/chat" to ai.chat(AiRecipeChatRequest("Olá", 3)),
            "GET /mobile/notifications" to notifications.getNotifications(0, 20),
            "PATCH /mobile/notifications/$id/read" to notifications.markAsRead(id),
            "PATCH /mobile/notifications/read-all" to notifications.markAllAsRead(),
            "DELETE /mobile/notifications" to notifications.clearAll(),
            "GET /mobile/advertisements/active" to advertisements.getActive(),
            "POST /mobile/advertisements/$id/impressions" to advertisements.recordImpression(id),
            "POST /mobile/advertisements/$id/clicks" to advertisements.recordClick(id),
            "POST /mobile/inventory/receipts/scan" to receipts.scan(ReceiptScanRequest(3, "qr")),
            "GET /mobile/inventory/receipts/$id" to receipts.get(id),
            "POST /mobile/inventory/receipts/$id/confirm" to receipts.confirm(id, ReceiptConfirmRequest(listOf(ReceiptConfirmItem(id, "IGNORE"))))
        )
        assertEquals(29, calls.size)
        calls.forEach { (expected, call) ->
            val response = call.execute()
            assertTrue(expected, response.isSuccessful)
            if (response.code() == 204) assertEquals(null, response.body())
            val request = requests.last()
            assertEquals(expected, "${request.method()} ${request.url().encodedPath()}")
            assertEquals("bff.example", request.url().host())
            assertEquals(if (expected.contains("/mobile/user/login") || expected.contains("/mobile/user/register")) null else "Bearer access",
                request.header("Authorization"))
        }
    }

    @Test fun protectedRequestsInvalidateOnlyOn401AndNeverAllowCoreRoutes() {
        val origin = HttpUrl.parse("https://bff.example/")!!
        var invalidations = 0
        val client = MobileHttp.client(origin, { SessionCredential("token", 1) }, { invalidations++ })
            .newBuilder().addInterceptor { chain ->
                Response.Builder().request(chain.request()).protocol(okhttp3.Protocol.HTTP_1_1)
                    .code(401).message("Unauthorized")
                    .body(ResponseBody.create(MediaType.parse("application/json"), "{}"))
                    .build()
            }.build()
        assertFalse(client.followRedirects())
        assertFalse(client.retryOnConnectionFailure())
        val retrofit = Retrofit.Builder().baseUrl(origin).client(client)
            .addConverterFactory(GsonConverterFactory.create()).build()
        assertEquals(401, retrofit.create(AuthApiService::class.java).getProfile().execute().code())
        assertEquals(1, invalidations)
        assertFalse(MobileHttp.allowed("GET", "/subscriptions/me"))
        assertFalse(MobileHttp.allowed("POST", "/mobile/push-tokens"))
        assertFalse(MobileHttp.allowed("POST", "/stock-products"))
    }

    @Test fun releaseOriginRequiresHttpsAndRejectsCoreHost() {
        try {
            MobileHttp.origin("http://bff.example/", false)
            throw AssertionError("HTTP release origin accepted")
        } catch (_: IllegalArgumentException) { }
        try {
            MobileHttp.origin("https://frigus-core-api.onrender.com/", false)
            throw AssertionError("Core origin accepted")
        } catch (_: IllegalArgumentException) { }
    }

    @Test fun filtersBodiesAndLongRunningTimeoutMatchTheContract() {
        val origin = MobileHttp.origin("https://bff.example/", false)
        val seen = mutableListOf<Pair<okhttp3.Request, Int>>()
        val client = MobileHttp.client(origin, { SessionCredential("token", 1) }, {})
            .newBuilder().addInterceptor { chain ->
                seen.add(chain.request() to chain.readTimeoutMillis())
                Response.Builder().request(chain.request()).protocol(okhttp3.Protocol.HTTP_1_1)
                    .code(200).message("OK")
                    .body(ResponseBody.create(MediaType.parse("application/json"),
                        "{\"content\":[],\"totalPages\":0,\"number\":0,\"size\":30,\"totalElements\":0}"))
                    .build()
            }.build()
        val retrofit = Retrofit.Builder().baseUrl(origin).client(client)
            .addConverterFactory(GsonConverterFactory.create()).build()
        val inventory = retrofit.create(StockApiService::class.java)
        val ai = retrofit.create(AiRecipeApiService::class.java)
        val receipts = retrofit.create(ReceiptApiService::class.java)
        inventory.getProducts(page = 2, size = 15, category = "DAIRY", storagePlace = "FRIDGE", query = "leite").execute()
        val url = seen.last().first.url()
        assertEquals("2", url.queryParameter("page"))
        assertEquals("15", url.queryParameter("size"))
        assertEquals("DAIRY", url.queryParameter("category"))
        assertEquals("FRIDGE", url.queryParameter("storagePlace"))
        assertEquals("leite", url.queryParameter("q"))
        assertEquals(40_000, seen.last().second)

        inventory.updateStockProduct(4, StockProductUpdateRequest(quantity = 2, expireDate = "2026-11-15")).execute()
        val buffer = Buffer()
        seen.last().first.body()!!.writeTo(buffer)
        assertEquals("{\"quantity\":2,\"expireDate\":\"2026-11-15\"}", buffer.readUtf8())

        ai.chat(AiRecipeChatRequest("Olá", 3)).execute()
        assertEquals(135_000, seen.last().second)
        receipts.scan(ReceiptScanRequest(3, "qr-data")).execute()
        assertEquals(135_000, seen.last().second)
    }
}
