package io.github.tournierjc.mercury.data

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The avatar contract merged upstream (`#226`, follow-up `#229`):
 * `GET/PUT/DELETE /api/bots/:id/avatar`, bytes in `{data}`, `hasAvatar` on the roster.
 */
class BotAvatarApiTest {

    private lateinit var server: MockWebServer
    private lateinit var api: MercuryApi

    private val png = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0x00, 0x01)

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = MercuryApi(OkHttpClient.Builder().build())
        api.restoreBaseUrl(server.url("/").toString())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `fetching a face returns the bytes`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "image/png")
                .addHeader("X-Content-Type-Options", "nosniff")
                .setBody(okio.Buffer().write(png)),
        )

        val bytes = api.botAvatar("ceo")

        assertArrayEquals(png, bytes)
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/api/bots/ceo/avatar", request.path)
    }

    @Test
    fun `no avatar is null, not an exception`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"error":"Bot has no avatar"}"""))
        assertNull(api.botAvatar("ceo"))
    }

    @Test
    fun `an expired session still surfaces as auth`() {
        server.enqueue(MockResponse().setResponseCode(401))
        assertThrows(AuthException::class.java) { runBlocking { api.botAvatar("ceo") } }
    }

    @Test
    fun `uploading posts base64 data to the avatar route`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"ok":true,"mime":"image/png","bytes":10}"""))

        api.uploadBotAvatar("ceo", "iVBORw0KGgo=")

        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals("/api/bots/ceo/avatar", request.path)
        val body = Json.parseToJsonElement(request.body.readUtf8()).let { it as JsonObject }
        assertEquals("iVBORw0KGgo=", (body["data"] as kotlinx.serialization.json.JsonPrimitive).content)
        // Only the bytes travel: no name, no path, no invented mime.
        assertEquals(setOf("data"), body.keys)
    }

    @Test
    fun `a refused image surfaces its reason`() {
        server.enqueue(
            MockResponse().setResponseCode(400)
                .setBody("""{"error":"avatar must be a png, jpeg, webp or gif image"}"""),
        )
        val failure = assertThrows(MercuryException::class.java) {
            runBlocking { api.uploadBotAvatar("ceo", "aGVsbG8=") }
        }
        assertEquals("avatar must be a png, jpeg, webp or gif image", failure.message)
    }

    @Test
    fun `deleting uses DELETE on the same path`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"ok":true,"removed":true}"""))

        api.deleteBotAvatar("ceo")

        val request = server.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals("/api/bots/ceo/avatar", request.path)
    }

    @Test
    fun `the roster flag drives whether to fetch at all`() {
        fun bot(json: String): JsonObject = Json.parseToJsonElement(json) as JsonObject

        assertTrue(BotJson.hasAvatar(bot("""{"id":"ceo","hasAvatar":true}""")))
        assertFalse(BotJson.hasAvatar(bot("""{"id":"ceo","hasAvatar":false}""")))
        // An older Mercury without the field has no avatars: do not probe.
        assertFalse(BotJson.hasAvatar(bot("""{"id":"ceo"}""")))
        assertFalse(BotJson.hasAvatar(null))
    }
}
