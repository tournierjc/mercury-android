package io.github.tournierjc.mercury.data

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/**
 * The two routes that make a card attachment readable, plus the stream that makes a board
 * live. Shapes come from `src/web/api/kanban.ts`:
 *  - `/content` answers `{ok,attachment,content,size}` — the file read as UTF-8;
 *  - `/download` answers the raw file with `Content-Type` and
 *    `Content-Disposition: attachment; filename="…"`;
 *  - `/events` is SSE, one `board-update` frame holding the whole board.
 */
class BoardAttachmentApiTest {

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

    @Test
    fun `content hands the file over as text plus the attachment it belongs to`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"ok":true,"attachment":{"id":"att_1","name":"rapport.md","type":"markdown","size":11},""" +
                    """"content":"# Rapport\nOK","size":13}""",
            ),
        )

        val root = api.cardAttachmentContent("b1", "c1", "att_1")

        assertEquals("# Rapport\nOK", BoardJson.attachmentContent(root))
        assertEquals(13L, BoardJson.attachmentSize(root))
        assertEquals("/api/boards/b1/cards/c1/attachments/att_1/content", server.takeRequest().path)
    }

    @Test
    fun `download returns the bytes, typed and named by the daemon`() = runBlocking {
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "image/png")
                .setHeader("Content-Disposition", "attachment; filename=\"maquette.png\"")
                .setBody(Buffer().write(png)),
        )

        val file = api.cardAttachmentDownload("b1", "c1", "att_2")

        assertArrayEquals(png, file.bytes)
        assertEquals("image/png", file.mimeType)
        assertEquals("maquette.png", file.fileName)
        assertEquals("/api/boards/b1/cards/c1/attachments/att_2/download", server.takeRequest().path)
    }

    @Test
    fun `a missing attachment keeps the server message`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"error":"Attachment not found"}"""))

        try {
            api.cardAttachmentDownload("b1", "c1", "att_404")
            fail("une pièce jointe absente doit lever")
        } catch (e: MercuryException) {
            assertEquals("Attachment not found", e.message)
        }
    }

    @Test
    fun `a filename header never names a path`() {
        assertEquals("rapport.md", DownloadedAttachment.fileNameFrom("attachment; filename=\"rapport.md\""))
        assertEquals("rendu final.md", DownloadedAttachment.fileNameFrom("attachment; filename*=UTF-8''rendu%20final.md"))
        assertEquals("passwd", DownloadedAttachment.fileNameFrom("attachment; filename=\"../../etc/passwd\""))
        assertEquals("", DownloadedAttachment.fileNameFrom(null))
    }

    @Test
    fun `the board stream yields every frame until the daemon closes it`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody(
                    "event: board-update\n" +
                        "data: {\"id\":\"b1\",\"cards\":[{\"id\":\"c1\",\"task\":\"Écrire\",\"status\":\"running\"}]}\n\n" +
                        ": keepalive\n\n" +
                        "event: feedback-request\n" +
                        "data: {\"id\":\"fb_1\",\"boardId\":\"b1\",\"cardId\":\"c1\",\"question\":\"Quel format ?\"}\n\n",
                ),
        )

        val events = withTimeout(10_000) { api.boardEvents("b1").toList() }

        assertEquals("/api/boards/b1/events", server.takeRequest().path)
        assertEquals(2, events.size)
        val update = events[0] as BoardEvent.Update
        assertEquals("running", BoardJson.status(update.cards.single()))
        val feedback = events[1] as BoardEvent.FeedbackRequest
        assertTrue(BoardJson.id(feedback.request).startsWith("fb_"))
    }

    @Test
    fun `a refused stream fails with a message the screen can show`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(503).setBody("""{"error":"Board manager not available"}"""))

        try {
            withTimeout(10_000) { api.boardEvents("b1").toList() }
            fail("un flux refusé doit lever")
        } catch (e: MercuryException) {
            assertTrue(e.message.orEmpty().contains("503"))
        }
    }
}
