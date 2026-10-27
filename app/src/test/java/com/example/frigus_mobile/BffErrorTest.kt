package com.example.frigus_mobile

import com.example.frigus_mobile.data.model.ApiError
import okhttp3.MediaType
import okhttp3.ResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.Response

class BffErrorTest {
    @Test fun displaysSafeMessageAndKeepsValidationFields() {
        val body = ResponseBody.create(MediaType.parse("application/json"),
            """{"status":400,"code":"BAD_REQUEST","message":"technical detail","displayMessage":"Dados inválidos","fields":[{"fieldName":"quantity","displayMessage":"Valor inválido"}]}""")
        val error = ApiError.parse(Response.error<Any>(400, body))
        assertEquals(400, error.status)
        assertEquals("BAD_REQUEST", error.code)
        assertEquals("quantity", error.fields?.first()?.fieldName)
        assertEquals("Dados inválidos", error.userMessage("Falha"))
    }

    @Test fun plainTextErrorDoesNotLeakTechnicalBody() {
        val body = ResponseBody.create(MediaType.parse("text/plain"), "internal trace")
        val error = ApiError.parse(Response.error<Any>(502, body))
        assertEquals(502, error.status)
        assertEquals("Falha", error.userMessage("Falha"))
    }
}
