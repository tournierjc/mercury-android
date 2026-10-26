package io.github.tournierjc.mercury.data

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Files to a bot — the route upstream `#231` extends.
 *
 * The client's only obligation is the shape the route reads: `attachments[].data` in
 * base64 plus a name, the same as `/api/chat/send`. Everything else (type sniffing,
 * size caps, where the bytes land) belongs to the server.
 */
class BotAttachmentsApiTest {

    private lateinit var server: MockWebServer
    private lateinit var api: MercuryApi

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

    private fun accepted(): MockResponse = MockResponse()
        .setResponseCode(202)
        .setBody("""{"accepted":true,"jobId":"job-1","status":"accepted"}""")

    @Test
    fun `a photo rides the dispatch as base64 with its name`() = runBlocking {
        server.enqueue(accepted())

        val dispatch = api.dispatchBot(
            "ceo",
            "Voilà le devis",
            attachments = listOf(AttachmentUpload(name = "devis.jpg", data = "QUJD")),
        )

        assertTrue(dispatch.accepted)
        assertEquals("job-1", dispatch.jobId)

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/bots/ceo/message", request.path)
        val body = Json.parseToJsonElement(request.body.readUtf8()) as JsonObject
        assertEquals("Voilà le devis", body["message"]!!.jsonPrimitive.content)
        val files = body["attachments"]!!.jsonArray
        assertEquals(1, files.size)
        val file = files[0] as JsonObject
        assertEquals("devis.jpg", file["name"]!!.jsonPrimitive.content)
        assertEquals("QUJD", file["data"]!!.jsonPrimitive.content)
    }

    @Test
    fun `a text-only dispatch keeps the exact body the old server understands`() = runBlocking {
        server.enqueue(accepted())

        api.dispatchBot("ceo", "Et maintenant ?")

        val body = Json.parseToJsonElement(server.takeRequest().body.readUtf8()) as JsonObject
        // No `attachments` key at all: an empty array is still a shape a 1.4.2 server
        // has never seen, and this route is the one that must keep working there.
        assertFalse(body.containsKey("attachments"))
        assertEquals(1, body.size)
        assertEquals("Et maintenant ?", body["message"]!!.jsonPrimitive.content)
    }

    @Test
    fun `the sender is still carried next to the files`() = runBlocking {
        server.enqueue(accepted())

        api.dispatchBot(
            "ceo",
            "note",
            from = "dev",
            attachments = listOf(AttachmentUpload(name = "a.pdf", data = "JVBERi0=")),
        )

        val body = Json.parseToJsonElement(server.takeRequest().body.readUtf8()) as JsonObject
        assertEquals("dev", body["from"]!!.jsonPrimitive.content)
        assertEquals(1, body["attachments"]!!.jsonArray.size)
    }

    @Test
    fun `four files go out as four entries, in the order they were chosen`() = runBlocking {
        server.enqueue(accepted())

        api.dispatchBot(
            "ceo",
            "les quatre",
            attachments = listOf("1.png", "2.png", "3.png", "4.png").map {
                AttachmentUpload(name = it, data = "QQ==")
            },
        )

        val files = (Json.parseToJsonElement(server.takeRequest().body.readUtf8()) as JsonObject)["attachments"]!!
            .jsonArray
        assertEquals(listOf("1.png", "2.png", "3.png", "4.png"), files.map { (it as JsonObject)["name"]!!.jsonPrimitive.content })
    }

    @Test
    fun `a refusal still decodes so the UI can explain it`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(409)
                .setBody("""{"accepted":false,"reasonCode":"bot-busy"}"""),
        )

        val dispatch = api.dispatchBot(
            "ceo",
            "regarde",
            attachments = listOf(AttachmentUpload(name = "x.png", data = "QQ==")),
        )

        assertFalse(dispatch.accepted)
        assertEquals("bot-busy", dispatch.reasonCode)
        // No job was queued: the field is present but blank, not a fabricated id.
        assertTrue(dispatch.jobId.isBlank())
    }
}
