package io.github.tournierjc.mercury.data

import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.util.concurrent.atomic.AtomicReference

/**
 * `NetworkOnMainThreadException` is what the phone threw at `send`: a blocking socket call on
 * the UI thread. `viewModelScope` launches on `Dispatchers.Main.immediate`, so a `suspend fun`
 * that never switches dispatcher runs there — and Android kills the call.
 *
 * The JVM has no such exception, so this test asserts the property that prevents it: the
 * request must never be built and executed on the thread that called the method.
 */
class MainThreadGuardTest {

    private val caller = AtomicReference<String>()
    private val requestThread = AtomicReference<String>()
    private val dispatchedOn = java.util.concurrent.atomic.AtomicInteger(0)

    private fun apiFor(body: String, code: Int = 200): MercuryApi {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            requestThread.set(Thread.currentThread().name)
            dispatchedOn.incrementAndGet()
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("OK")
                .body(body.toResponseBody("application/json".toMediaType()))
                .build()
        }.build()
        return MercuryApi(client).apply { restoreBaseUrl("http://127.0.0.1:6181") }
    }

    @Test
    fun `send does not run its request on the calling thread`() = runBlocking {
        val api = apiFor("""{"sent":true,"sessionId":"session-1"}""")
        caller.set(Thread.currentThread().name)

        val session = api.send("bonjour", null)

        assertEquals("session-1", session)
        assertEquals(1, dispatchedOn.get())
        assertNotEquals(caller.get(), requestThread.get())
    }

    @Test
    fun `a plain GET does not run its request on the calling thread either`() = runBlocking {
        val api = apiFor("""{"bots":[],"queue":{"pending":0,"claimed":0,"dlq":0},"available":true}""")
        caller.set(Thread.currentThread().name)

        api.bots()

        assertNotEquals(caller.get(), requestThread.get())
    }

    @Test
    fun `dispatch does not run its request on the calling thread`() = runBlocking {
        val api = apiFor("""{"accepted":true,"jobId":"job-1","status":"accepted"}""", code = 202)
        caller.set(Thread.currentThread().name)

        val result = api.dispatchBot("bricoleur", "écris un fichier")

        assertEquals(true, result.accepted)
        assertNotEquals(caller.get(), requestThread.get())
    }
}
