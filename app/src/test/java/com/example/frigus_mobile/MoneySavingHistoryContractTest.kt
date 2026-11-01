package com.example.frigus_mobile

import com.example.frigus_mobile.data.api.BffException
import com.example.frigus_mobile.data.api.MoneySavingApiService
import com.example.frigus_mobile.data.api.MobileHttp
import com.example.frigus_mobile.data.model.*
import com.example.frigus_mobile.data.repository.MoneySavingRepository
import com.example.frigus_mobile.data.repository.MoneySavingUncertainMutationException
import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class MoneySavingHistoryContractTest {
    private val group = "c39671e5-a166-473a-b9a2-f82ff13831d1"
    private val purchase = "d9c3ce6c-769a-475b-8b8d-e2842c3f6117"
    private val request = FoodPurchaseRequest("2026-11-01", lines = listOf(
        FoodPurchaseLineRequest(42, 7, "2026-12-31", 2, 4.59)))

    private fun repository(status: Int, body: String = "", observed: (Request) -> Unit = {}): MoneySavingRepository {
        val client = OkHttpClient.Builder().retryOnConnectionFailure(false).addInterceptor { chain ->
            observed(chain.request())
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(status).message("fixture").body(ResponseBody.create(MediaType.parse("application/json"), body)).build()
        }.build()
        val api = Retrofit.Builder().baseUrl("https://bff.invalid/").client(client)
            .addConverterFactory(GsonConverterFactory.create()).build().create(MoneySavingApiService::class.java)
        return MoneySavingRepository(api)
    }

    @Test fun forbiddenGroupMessageDoesNotBecomeAnUpgradeOrDestroyItsMeaning() = runBlocking {
        val repo = repository(403, """{"code":"GROUP_ACCESS_DENIED","displayMessage":"Sem acesso a este grupo"}""")
        val failure = try { repo.summary(group, "2026-11-01", "2026-12-01"); null } catch (error: BffException) { error }
        assertEquals(403, failure?.error?.status)
        assertEquals("Sem acesso a este grupo", failure?.error?.displayMessage)
        assertFalse(isMoneySavingPlanRequired(failure?.error))
        assertTrue(isMoneySavingPlanRequired(ApiError(status = 403, code = "MONEY_SAVING_PLAN_REQUIRED")))
        assertFalse(isMoneySavingPlanRequired(ApiError(status = 401, code = "MONEY_SAVING_PLAN_REQUIRED")))
    }

    @Test fun purchasePostKeepsCallerIdempotencyKeyAndNeverReplaysAnUncertainResult() = runBlocking {
        var calls = 0
        val repo = repository(503, """{"displayMessage":"Serviço indisponível"}""") { sent ->
            calls++
            assertEquals("POST", sent.method())
            assertEquals("/mobile/commercial/groups/$group/food-purchases", sent.url().encodedPath())
            assertEquals("persisted-key", sent.header("Idempotency-Key"))
        }
        try { repo.createFoodPurchase(group, "persisted-key", request); fail("Expected uncertain result") }
        catch (error: MoneySavingUncertainMutationException) { assertEquals(503, error.apiError?.status) }
        assertEquals(1, calls)
    }

    @Test fun deletionRequiresExactly204AndUnexpected200IsUncertain() = runBlocking {
        repository(204).deleteFoodPurchase(group, purchase)
        try { repository(200).deleteFoodPurchase(group, purchase); fail("Expected uncertain result") }
        catch (_: MoneySavingUncertainMutationException) { }
    }

    @Test fun malformedCreatedPurchaseCannotBecomeASuccessWithInventedZeros() = runBlocking {
        try { repository(201, "{}").createFoodPurchase(group, "persisted-key", request); fail("Expected uncertain result") }
        catch (_: MoneySavingUncertainMutationException) { }
        val empty = Gson().fromJson("{}", FoodPurchaseResponse::class.java)
        assertNull(empty.total)
        assertNull(empty.version)
        assertNull(empty.lines)
    }

    @Test fun correctionCannotChangeProductStockOrExpiryAndKeepsIntegerQuantity() {
        val line = FoodPurchaseUpdateLineRequest(11, 2, 4.59)
        val json = Gson().toJsonTree(line).asJsonObject
        assertEquals(setOf("id", "quantity", "unitPrice"), json.keySet())
        assertEquals("2", json["quantity"].toString())
    }

    @Test fun recoveredPurchaseRoutesUseOnlyThePublishedBffPaths() {
        val api = Retrofit.Builder().baseUrl("https://bff.invalid/")
            .addConverterFactory(GsonConverterFactory.create()).build().create(MoneySavingApiService::class.java)
        val calls = listOf(api.createFoodPurchase(group, "persisted-key", request),
            api.getFoodPurchase(group, purchase),
            api.updateFoodPurchase(group, purchase, FoodPurchaseUpdateRequest("2026-11-01", version = 0,
                lines = listOf(FoodPurchaseUpdateLineRequest(11, 2, 4.59)))),
            api.deleteFoodPurchase(group, purchase))
        assertEquals(listOf("POST", "GET", "PUT", "DELETE"), calls.map { it.request().method() })
        calls.forEach { call ->
            val sent = call.request()
            assertEquals("bff.invalid", sent.url().host())
            assertTrue(MobileHttp.allowed(sent.method(), sent.url().encodedPath()))
        }
        assertFalse(MobileHttp.allowed("GET", "/mobile/commercial/groups/$group/food-purchases"))
    }

    @Test fun invalidPurchaseNeverSendsARequest() = runBlocking {
        var calls = 0
        val repo = repository(201) { calls++ }
        try {
            repo.createFoodPurchase(group, "persisted-key", request.copy(lines = request.lines.map { it.copy(unitPrice = Double.NaN) }))
            fail("Expected validation failure")
        } catch (error: BffException) { assertEquals(422, error.error.status) }
        assertEquals(0, calls)
    }
}
