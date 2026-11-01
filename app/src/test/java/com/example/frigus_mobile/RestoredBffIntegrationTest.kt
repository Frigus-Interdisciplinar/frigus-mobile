package com.example.frigus_mobile

import com.example.frigus_mobile.data.api.AuthApiService
import com.example.frigus_mobile.data.api.BffException
import com.example.frigus_mobile.data.api.InvalidBffResponseException
import com.example.frigus_mobile.data.api.MobileHttp
import com.example.frigus_mobile.data.api.requireCreatedBody
import com.example.frigus_mobile.data.local.SessionState
import okhttp3.MediaType
import okhttp3.Protocol
import okhttp3.ResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class RestoredBffIntegrationTest {
    @Test fun forbiddenKeepsTheSessionWhileUnauthorizedClearsIt() {
        val session = SessionState()
        session.authenticate(session.generation, "test-credential", null)
        val origin = MobileHttp.origin("https://bff.example/", false)
        var status = 403
        val client = MobileHttp.client(origin, session::credential, session::invalidate)
            .newBuilder().addInterceptor { chain ->
                okhttp3.Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                    .code(status).message("Denied")
                    .body(ResponseBody.create(MediaType.parse("application/json"), "{}"))
                    .build()
            }.build()
        val api = Retrofit.Builder().baseUrl(origin).client(client)
            .addConverterFactory(GsonConverterFactory.create()).build().create(AuthApiService::class.java)
        assertEquals(403, api.getProfile().execute().code())
        assertTrue(session.validated)
        assertEquals("test-credential", session.accessToken)
        status = 401
        assertEquals(401, api.getProfile().execute().code())
        assertFalse(session.validated)
        assertNull(session.credential())
    }

    @Test fun creationRequires201AndNeverTreats200AsConfirmedCreation() {
        val origin = MobileHttp.origin("https://bff.example/", false)
        var status = 201
        val client = MobileHttp.client(origin, { null }, {})
            .newBuilder().addInterceptor { chain ->
                okhttp3.Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                    .code(status).message("Response")
                    .body(ResponseBody.create(MediaType.parse("application/json"), "{\"id\":\"confirmed\"}"))
                    .build()
            }.build()
        val api = Retrofit.Builder().baseUrl(origin).client(client)
            .addConverterFactory(GsonConverterFactory.create()).build().create(AuthApiService::class.java)
        val request = com.example.frigus_mobile.data.model.UserRegisterRequest(
            "Test", "01/01/2000", "test@example.test", "test-password")
        assertEquals("confirmed", api.cadastrar(request).requireCreatedBody().id)
        status = 200
        try {
            api.cadastrar(request).requireCreatedBody()
            fail("An unexpected success code must leave the mutation unconfirmed")
        } catch (_: InvalidBffResponseException) { }
        status = 403
        try {
            api.cadastrar(request).requireCreatedBody()
            fail("Forbidden must keep its structured error")
        } catch (failure: BffException) {
            assertEquals(403, failure.error.status)
        }
    }

    @Test fun newFeaturesUseOnlyPublishedMobilePaths() {
        val group = "123e4567-e89b-12d3-a456-426614174000"
        listOf(
            "GET" to "/mobile/core/plans/active",
            "GET" to "/mobile/core/plans/me/limits",
            "GET" to "/mobile/core/subscriptions/me",
            "POST" to "/mobile/core/subscriptions/me/cancel",
            "POST" to "/mobile/core/subscriptions/me/reactivate",
            "GET" to "/mobile/core/transactions",
            "POST" to "/mobile/core/transactions/checkout",
            "GET" to "/mobile/core/groups",
            "POST" to "/mobile/core/groups",
            "GET" to "/mobile/core/groups/$group",
            "POST" to "/mobile/core/stocks",
            "GET" to "/mobile/core/groups/$group/invitations",
            "POST" to "/mobile/core/groups/$group/invitations",
            "DELETE" to "/mobile/core/groups/$group/invitations/$group",
            "GET" to "/mobile/commercial/groups/$group/money-saving/summary",
            "GET" to "/mobile/commercial/groups/$group/money-saving/events"
        ).forEach { (method, path) -> assertTrue("$method $path", MobileHttp.allowed(method, path)) }
        assertFalse(MobileHttp.allowed("POST", "/mobile/commercial/groups/$group/money-saving/events"))
        assertFalse(MobileHttp.allowed("POST", "/mobile/core/groups/not-a-uuid/invitations"))
        assertFalse(MobileHttp.allowed("GET", "/subscriptions/me"))
        assertFalse(MobileHttp.public("/anything/auth/google"))
    }
}
