package io.github.tournierjc.mercury.data

import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MercuryApiTest {

    private lateinit var server: MockWebServer
    private lateinit var jar: SessionCookieJar
    private lateinit var api: MercuryApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        jar = SessionCookieJar(InMemoryCookieStore())
        val client = OkHttpClient.Builder().cookieJar(jar).build()
        api = MercuryApi(client, jar)
        api.restoreBaseUrl(server.url("/").toString())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `login posts the form and keeps the session cookie`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(302)
                .addHeader("Location", "/")
                .addHeader("Set-Cookie", "mercury_session=tok-1; Path=/; HttpOnly; SameSite=Strict"),
        )

        api.login(server.url("/").toString(), "mercury", "s3cret")

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/auth/login", request.path)
        val body = request.body.readUtf8()
        assertTrue(body.contains("username=mercury"))
        assertTrue(body.contains("password=s3cret"))
        assertTrue(api.hasSession())
    }

    @Test
    fun `login rejects bad credentials`() {
        server.enqueue(MockResponse().setResponseCode(401).setBody("<html>bad</html>"))
        assertThrows(AuthException::class.java) {
            runBlocking { api.login(server.url("/").toString(), "mercury", "nope") }
        }
    }

    @Test
    fun `threads are decoded`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"threads":[{"id":"t1","shortId":"T1","alias":"alias1","title":"Titre","updatedAt":"2026-10-25T10:00:00Z"}]}""",
            ),
        )
        val threads = api.threads()
        assertEquals(1, threads.size)
        assertEquals("t1", threads[0].id)
        assertEquals("Titre", threads[0].title)
    }

    @Test
    fun `send returns the bound session id`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"sessionId":"sess-42"}"""))

        val sessionId = api.send("bonjour", null)

        assertEquals("sess-42", sessionId)
        val request = server.takeRequest()
        assertEquals("/api/chat/send", request.path)
        val body = request.body.readUtf8()
        assertTrue(body.contains("\"content\":\"bonjour\""))
    }

    @Test
    fun `permission answer hits the interaction endpoint`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"ok":true}"""))

        api.resolvePermission("perm_1", "always")

        val request = server.takeRequest()
        assertEquals("/api/chat/permission/perm_1", request.path)
        assertTrue(request.body.readUtf8().contains("always"))
    }

    @Test
    fun `api errors surface their message`() {
        server.enqueue(MockResponse().setResponseCode(500).setBody("""{"error":"boom"}"""))
        val error = assertThrows(MercuryException::class.java) {
            runBlocking { api.threads() }
        }
        assertEquals("boom", error.message)
    }

    @Test
    fun `401 surfaces as an auth error`() {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":"Unauthorized"}"""))
        assertThrows(AuthException::class.java) {
            runBlocking { api.threads() }
        }
    }

    @Test
    fun `403 is a refusal the UI can show, not a lost session`() {
        server.enqueue(
            MockResponse().setResponseCode(403).setBody("""{"error":"Shutdown is only accepted from this machine"}"""),
        )
        val error = assertThrows(MercuryException::class.java) {
            runBlocking { api.threads() }
        }
        // The server's own text travels, and the session stays signed in.
        assertEquals("Shutdown is only accepted from this machine", error.message)
    }

    @Test
    fun `a 403 whose body says the session expired still signs out`() {
        server.enqueue(MockResponse().setResponseCode(403).setBody("""{"error":"session expired"}"""))
        assertThrows(AuthException::class.java) {
            runBlocking { api.threads() }
        }
    }

    @Test
    fun `login refuses a redirect that carries no session cookie`() {
        server.enqueue(MockResponse().setResponseCode(302).addHeader("Location", "/"))
        val error = assertThrows(MercuryException::class.java) {
            runBlocking { api.login(server.url("/").toString(), "mercury", "s3cret") }
        }
        assertTrue(error.message!!.contains("session cookie"))
    }

    @Test
    fun `sse stream is decoded into chat events`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody(
                    "event: connected\ndata: {\"provider\":\"deepseek\",\"model\":\"deepseek-flash\"}\n\n" +
                        "event: text_delta\ndata: {\"text\":\"Bonjour\"}\n\n",
                ),
        )

        val events = api.events("sess-1").take(2).toList()

        assertEquals(ChatEvent.Connected("deepseek", "deepseek-flash"), events[0])
        assertEquals(ChatEvent.TextDelta("Bonjour"), events[1])
        assertEquals("/api/chat/events?sessionId=sess-1", server.takeRequest().path)
    }
}
