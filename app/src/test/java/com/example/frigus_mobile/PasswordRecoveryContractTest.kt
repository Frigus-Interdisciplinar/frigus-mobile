package com.example.frigus_mobile

import com.example.frigus_mobile.data.api.AuthApiService
import com.example.frigus_mobile.data.api.InvalidBffResponseException
import com.example.frigus_mobile.data.api.MobileHttp
import com.example.frigus_mobile.data.api.requireNoContent
import com.example.frigus_mobile.data.local.SessionCredential
import com.example.frigus_mobile.data.model.PasswordCodeRequest
import com.example.frigus_mobile.data.model.PasswordRecoveryRequest
import com.example.frigus_mobile.data.model.PasswordResetRequest
import com.google.gson.JsonParser
import okhttp3.MediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class PasswordRecoveryContractTest {
    @Test fun recoveryUsesPublicBffRoutesAndNeverSendsSessionCredentials() {
        val requests = mutableListOf<Request>()
        val api = api(204, requests)
        api.requestPasswordRecovery(PasswordRecoveryRequest("a@example.test")).requireNoContent()
        api.verifyPasswordCode(PasswordCodeRequest("a@example.test", "123456")).requireNoContent()
        api.resetPassword(PasswordResetRequest("a@example.test", "123456", "NewPassword1!")).requireNoContent()
        assertEquals(listOf("forgot-password", "verify-reset-code", "reset-password"), requests.map { it.url().pathSegments().last() })
        requests.forEach { request ->
            assertEquals("POST", request.method())
            assertEquals("bff.example", request.url().host())
            assertNull(request.header("Authorization"))
            assertTrue(MobileHttp.isPublicPath(request.url().encodedPath()))
        }
        val buffer = Buffer()
        requests.last().body()!!.writeTo(buffer)
        val body = JsonParser.parseString(buffer.readUtf8()).asJsonObject
        assertEquals("123456", body.get("code").asString)
        assertEquals("NewPassword1!", body.get("newPassword").asString)
        assertEquals(3, body.size())
    }

    @Test fun recoveryRequires204AndDoesNotTreat200AsSuccess() {
        try {
            api(200, mutableListOf()).requestPasswordRecovery(PasswordRecoveryRequest("a@example.test")).requireNoContent()
            fail("A generic successful response must not confirm recovery")
        } catch (_: InvalidBffResponseException) { }
    }

    @Test fun publicRecovery401DoesNotInvalidateAnExistingSession() {
        var invalidations = 0
        val origin = MobileHttp.origin("https://bff.example/", false)
        val client = MobileHttp.client(origin, { SessionCredential("private", 1) }, { invalidations++ })
            .newBuilder().addInterceptor { chain ->
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(401).message("Unauthorized")
                    .body(ResponseBody.create(MediaType.parse("application/json"), "{}" )).build()
            }.build()
        val api = Retrofit.Builder().baseUrl(origin).client(client).addConverterFactory(GsonConverterFactory.create())
            .build().create(AuthApiService::class.java)
        assertEquals(401, api.requestPasswordRecovery(PasswordRecoveryRequest("a@example.test")).execute().code())
        assertEquals(0, invalidations)
        assertFalse(MobileHttp.allowed("GET", "/mobile/core/auth/reset-password"))
        assertFalse(MobileHttp.allowed("POST", "/auth/reset-password"))
    }

    private fun api(code: Int, requests: MutableList<Request>): AuthApiService {
        val origin = MobileHttp.origin("https://bff.example/", false)
        val client = MobileHttp.client(origin, { SessionCredential("private", 1) }, { error("Public request invalidated a session") })
            .newBuilder().addInterceptor { chain ->
                requests.add(chain.request())
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("Response")
                    .body(ResponseBody.create(MediaType.parse("application/json"), "")).build()
            }.build()
        return Retrofit.Builder().baseUrl(origin).client(client).addConverterFactory(GsonConverterFactory.create())
            .build().create(AuthApiService::class.java)
    }
}
