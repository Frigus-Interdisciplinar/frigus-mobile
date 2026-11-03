package com.example.frigus_mobile

import com.example.frigus_mobile.data.api.*
import com.example.frigus_mobile.data.local.SessionCredential
import com.example.frigus_mobile.data.local.SessionState
import com.example.frigus_mobile.data.model.ConversationSendRequest
import com.example.frigus_mobile.data.model.UserResponse
import com.google.gson.JsonParser
import okhttp3.MediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException

/** Exercises the real Retrofit/Gson/MobileHttp boundary without Context, sockets or a backend. */
class ConversationApiContractTest {
    private val conversation = "123e4567-e89b-12d3-a456-426614174000"
    private val owner = "123e4567-e89b-12d3-a456-426614174001"
    private val peer = "123e4567-e89b-12d3-a456-426614174002"
    private val root = "/mobile/core/conversations"
    private val origin = MobileHttp.origin("https://bff.example/", false)
    private val content = "Olá! Comprei café, pão e 2 litros de leite 🥛."
    private val messageJson get() = """{"id":101,"conversationId":"$conversation","senderId":"$owner","senderName":"Erik","messageType":"TEXT","content":"$content","createdAt":"2026-10-30T21:41:26Z","updatedAt":"2026-10-30T21:41:26Z"}"""
    private val conversationJson get() = """{"id":"$conversation","conversationType":"PRIVATE","groupId":null,"groupName":null,"name":null,"participants":[{"userId":"$owner","name":"Erik","email":"erik@example.test","joinedAt":"2026-10-30T20:00:00Z","leftAt":null},{"userId":"$peer","name":"Colega","joinedAt":"2026-10-30T20:00:00Z","leftAt":null}],"latestMessage":$messageJson,"createdAt":"2026-10-30T20:00:00Z","updatedAt":"2026-10-30T21:41:26Z"}"""

    @Test fun realCoreFixturesDeserializeAndUtf8CommandUsesOnlyPublishedFields() {
        val seen = mutableListOf<Request>()
        val client = MobileHttp.client(origin, { SessionCredential("core-bff-access", 7) }, {})
            .newBuilder().addInterceptor { chain ->
                val request = chain.request()
                seen += request
                val path = request.url().encodedPath()
                val (status, body) = when {
                    path.endsWith("/read") -> 204 to ""
                    request.method() == "POST" -> 201 to messageJson
                    path.endsWith("/messages") -> 200 to """{"content":[$messageJson],"number":2,"size":12,"totalElements":40,"totalPages":4,"last":false}"""
                    path == root -> 200 to "[$conversationJson]"
                    else -> 200 to conversationJson
                }
                response(request, status, body)
            }.build()
        val api = service(client)
        val list = api.conversations().requireBody()
        assertEquals(conversation, list.single().id)
        assertEquals(peer, list.single().participants!![1].userId)
        assertNull(list.single().participants!![1].leftAt)
        assertEquals(content, list.single().latestMessage!!.content)
        assertEquals(conversation, api.conversation(conversation).requireBody().id)
        val page = api.messages(conversation, 2, 12).requireBody()
        assertEquals(2, page.number)
        assertEquals(4, page.totalPages)
        assertEquals(101, page.content.single().id)
        assertEquals(conversation, page.content.single().conversationId)
        val messageRequest = seen.last()
        assertEquals("2", messageRequest.url().queryParameter("page"))
        assertEquals("12", messageRequest.url().queryParameter("size"))
        assertEquals("createdAt,desc", messageRequest.url().queryParameter("sort"))
        val posted = api.send(conversation, ConversationSendRequest(content = content)).requireCreatedBody()
        assertEquals(101, posted.id)
        assertEquals(conversation, posted.conversationId)
        assertEquals(owner, posted.senderId)
        assertEquals(content, posted.content)
        val buffer = Buffer()
        seen.last().body()!!.writeTo(buffer)
        val json = JsonParser.parseString(buffer.readUtf8()).asJsonObject
        assertEquals(setOf("messageType", "content"), json.keySet())
        assertEquals("TEXT", json["messageType"].asString)
        assertEquals(content, json["content"].asString)
        api.read(conversation, 101).requireNoContent()
        assertEquals(listOf("GET $root", "GET $root/$conversation", "GET $root/$conversation/messages",
            "POST $root/$conversation/messages", "POST $root/$conversation/messages/101/read"),
            seen.map { "${it.method()} ${it.url().encodedPath()}" })
        seen.forEach {
            assertEquals("bff.example", it.url().host())
            assertEquals("Bearer core-bff-access", it.header("Authorization"))
        }
    }

    @Test fun renameUnsupportedMethodsInvalidIdentifiersAndOtherOriginsNeverReachTransport() {
        var reached = 0
        val client = MobileHttp.client(origin, { SessionCredential("access", 1) }, {})
            .newBuilder().addInterceptor { chain -> reached++; response(chain.request(), 200, "{}") }.build()
        val json = RequestBody.create(MediaType.parse("application/json"), "{}")
        val requests = listOf(
            Request.Builder().url("https://bff.example$root/$conversation/name").patch(json).build(),
            Request.Builder().url("https://bff.example$root/$conversation").put(json).build(),
            Request.Builder().url("https://bff.example$root/not-a-uuid/messages").post(json).build(),
            Request.Builder().url("https://bff.example$root/$conversation/messages/0/read").post(json).build(),
            Request.Builder().url("https://bff.example$root/$conversation/messages/2147483648/read").post(json).build(),
            Request.Builder().url("https://other.example$root").get().build()
        )
        requests.forEach { request ->
            assertThrows(IOException::class.java) { client.newCall(request).execute().close() }
        }
        assertEquals(0, reached)
    }

    @Test fun late401CannotInvalidateANewerAccountAnd403KeepsTheCurrentCredential() {
        val session = SessionState()
        assertTrue(session.authenticate(session.generation, "old-access", UserResponse(id = owner)))
        var status = 401
        var replaceDuringResponse = true
        val invalidated = mutableListOf<SessionCredential>()
        val seen = mutableListOf<String?>()
        val client = MobileHttp.client(origin, session::credential, {
            invalidated += it
            session.invalidate(it)
        }).newBuilder().addInterceptor { chain ->
            seen += chain.request().header("Authorization")
            if (replaceDuringResponse) {
                replaceDuringResponse = false
                val generation = session.beginAuthentication()
                assertTrue(session.authenticate(generation, "new-access", UserResponse(id = peer)))
            }
            response(chain.request(), status, "{}")
        }.build()
        val api = service(client)
        assertEquals(401, api.conversation(conversation).execute().code())
        assertEquals("old-access", invalidated.single().token)
        assertEquals("new-access", session.credential()!!.token)
        assertEquals(peer, session.user!!.id)
        status = 403
        assertEquals(403, api.conversation(conversation).execute().code())
        assertEquals(1, invalidated.size)
        assertTrue(session.validated)
        status = 401
        assertEquals(401, api.conversation(conversation).execute().code())
        assertNull(session.credential())
        assertEquals(listOf("Bearer old-access", "Bearer new-access", "Bearer new-access"), seen)
    }

    @Test fun sendIsNotReplayedOnTransportFailureAndCommandsRequireTheirExactSuccessStatus() {
        var attempts = 0
        val failed = MobileHttp.client(origin, { SessionCredential("access", 1) }, {})
            .newBuilder().addInterceptor { attempts++; throw IOException("transport lost") }.build()
        assertFalse(failed.retryOnConnectionFailure())
        assertFalse(failed.followRedirects())
        assertThrows(IOException::class.java) {
            service(failed).send(conversation, ConversationSendRequest(content = content)).requireCreatedBody()
        }
        assertEquals(1, attempts)
        val wrongStatus = MobileHttp.client(origin, { SessionCredential("access", 1) }, {})
            .newBuilder().addInterceptor { chain -> response(chain.request(), 200, messageJson) }.build()
        assertThrows(InvalidBffResponseException::class.java) {
            service(wrongStatus).send(conversation, ConversationSendRequest(content = content)).requireCreatedBody()
        }
        assertThrows(InvalidBffResponseException::class.java) {
            service(wrongStatus).read(conversation, 101).requireNoContent()
        }
    }

    private fun service(client: okhttp3.OkHttpClient): ConversationApiService = Retrofit.Builder()
        .baseUrl(origin).client(client).addConverterFactory(GsonConverterFactory.create()).build()
        .create(ConversationApiService::class.java)
    private fun response(request: Request, status: Int, body: String): Response = Response.Builder()
        .request(request).protocol(Protocol.HTTP_1_1).code(status).message("Test response")
        .body(ResponseBody.create(MediaType.parse("application/json; charset=utf-8"), body)).build()
}
