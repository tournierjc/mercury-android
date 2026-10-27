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

    private fun api(): MercuryApi {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            paths += chain.request().url.encodedPath
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(202)
                .message("Accepted")
                .body("""{"accepted":true,"jobId":"job-1","status":"accepted"}""".toResponseBody("application/json".toMediaType()))
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
}
