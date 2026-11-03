package com.example.frigus_mobile

import com.example.frigus_mobile.data.api.FirebaseSessionApiService
import com.example.frigus_mobile.data.api.MobileHttp
import com.example.frigus_mobile.data.local.SessionCredential
import okhttp3.MediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class FirebaseSessionContractTest {
    @Test fun firebaseSessionUsesOnlyTheAuthenticatedBffPost() {
        val origin = MobileHttp.origin("https://bff.example/", false)
        val client = MobileHttp.client(origin, { SessionCredential("core-fixture", 2) }, { fail("Unexpected invalidation") })
            .newBuilder().addInterceptor { chain ->
                assertEquals("POST", chain.request().method())
                assertEquals("/mobile/firebase/session", chain.request().url().encodedPath())
                assertEquals("Bearer core-fixture", chain.request().header("Authorization"))
                assertEquals("bff.example", chain.request().url().host())
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                    .body(ResponseBody.create(MediaType.parse("application/json"),
                        "{\"uid\":\"owner\",\"projectId\":\"project\",\"customToken\":\"fixture\"}" )).build()
            }.build()
        val api = Retrofit.Builder().baseUrl(origin).client(client).addConverterFactory(GsonConverterFactory.create())
            .build().create(FirebaseSessionApiService::class.java)
        val result = api.create().execute().body()!!
        assertEquals("owner", result.uid)
        assertEquals("project", result.projectId)
        assertEquals("fixture", result.customToken)
        assertFalse(MobileHttp.isPublicPath("/mobile/firebase/session"))
        assertFalse(MobileHttp.allowed("GET", "/mobile/firebase/session"))
        assertFalse(MobileHttp.allowed("POST", "/firebase/session"))
    }

    @Test fun missingCoreCredentialCannotReachTheFirebaseBroker() {
        var reached = false
        val origin = MobileHttp.origin("https://bff.example/", false)
        val client = MobileHttp.client(origin, { null }, {})
            .newBuilder().addInterceptor { reached = true; error("Must not reach an upstream") }.build()
        val api = Retrofit.Builder().baseUrl(origin).client(client).addConverterFactory(GsonConverterFactory.create())
            .build().create(FirebaseSessionApiService::class.java)
        try { api.create().execute(); fail("Unvalidated session must not issue a credential") }
        catch (_: java.io.IOException) { }
        assertFalse(reached)
    }
}
