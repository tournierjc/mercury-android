package io.github.tournierjc.mercury.data

import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A bot answers in two different registers, and the daemon routes them separately: `/chat`
 * runs the turn as a conversation (the answer is the point), `/message` as a delivery task
 * (a report, and "Nothing to do" when nothing needed doing). Sending a question through the
 * wrong one is how a chat with a teammate ended up replying "Nothing to do".
 */
class BotChatApiTest {

    private val paths = mutableListOf<String>()

    /**
     * A daemon that has one route and not the other: `codes` says which answer each path gets,
     * so a test can lay down the 404 a released daemon answers on `/chat`.
     */
    private fun api(codes: Map<String, Int> = emptyMap()): MercuryApi {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            paths += chain.request().url.encodedPath
            val code = codes[chain.request().url.encodedPath] ?: 202
            val body = when (code) {
                202 -> """{"accepted":true,"jobId":"job-1","status":"accepted"}"""
                // A refusal the daemon words itself: the client must surface it, not replace it.
                409 -> """{"accepted":false,"reasonCode":"target_busy"}"""
                else -> """{"error":"Not Found"}"""
            }
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message(if (code == 202) "Accepted" else "Not Found")
                .body(body.toResponseBody("application/json".toMediaType()))
                .build()
        }.build()
        return MercuryApi(client).apply { restoreBaseUrl("http://127.0.0.1:6181") }
    }

    @Test
    fun `a chat message goes to the chat route`() = runBlocking {
        val result = api().chatWithBot("atelier", "que sais-tu de ce Spark ?")

        assertEquals(true, result.accepted)
        assertEquals(listOf("/api/bots/atelier/chat"), paths)
    }

    @Test
    fun `a task still goes to the message route`() = runBlocking {
        api().dispatchBot("atelier", "écris un fichier")

        assertEquals(listOf("/api/bots/atelier/message"), paths)
    }

    // A daemon older than the `/chat` route answers 404. The conversation must not die on a route
    // the server does not have: the message goes through `/message`, and the owner still gets an
    // answer instead of "HTTP 404" written under the question.
    @Test
    fun `a daemon without the chat route still takes the message`() = runBlocking {
        val result = api(mapOf("/api/bots/atelier/chat" to 404)).chatWithBot("atelier", "et le graphe ?")

        assertEquals(true, result.accepted)
        assertEquals(listOf("/api/bots/atelier/chat", "/api/bots/atelier/message"), paths)
    }

    // Only a missing route falls back. A 409 (busy, paused) is the daemon answering about this
    // bot, and retrying on the other register would send the same question twice.
    @Test
    fun `a refusal is not a missing route`() = runBlocking {
        val result = api(mapOf("/api/bots/atelier/chat" to 409)).chatWithBot("atelier", "et le graphe ?")

        assertEquals(false, result.accepted)
        assertEquals("target_busy", result.reasonCode)
        assertEquals(listOf("/api/bots/atelier/chat"), paths)
    }
}
