package io.github.tournierjc.mercury.data

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The routes the deliverable and run-health screen calls, checked against the
 * daemon (`src/web/api/bots.ts`): paths, URL-encoded deliverable names (they
 * contain a `work/` prefix and spaces) and the binary download body.
 */
class BotOpsApiTest {

    private lateinit var server: MockWebServer
    private lateinit var api: MercuryApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val jar = SessionCookieJar(InMemoryCookieStore())
        api = MercuryApi(OkHttpClient.Builder().cookieJar(jar).build(), jar)
        api.restoreBaseUrl(server.url("/").toString())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `fleet inbox reads api bots outputs`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"outputs":[{"botId":"veille","name":"brief.md","bytes":11300,"mtimeMs":1793000600000,"path":"/d/brief.md","final":true}]}""",
            ),
        )

        val page = api.fleetOutputs()

        assertEquals("/api/bots/outputs", server.takeRequest().path)
        assertEquals(1, page.outputs.size)
        assertEquals("brief.md", page.outputs[0].name)
        assertTrue(page.outputs[0].final)
    }

    @Test
    fun `per-bot outputs and preview encode the whole name`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"outputs":[]}"""))
        api.botOutputs("publisher")
        assertEquals("/api/bots/publisher/outputs", server.takeRequest().path)

        server.enqueue(MockResponse().setBody("""{"preview":"# The final report","truncated":false}"""))
        val preview = api.outputPreview("publisher", "work/2026-10-27 final report.md")

        // The name is one path segment: the slash and the spaces are percent-encoded,
        // which is what the daemon's `:name` parameter decodes.
        assertEquals(
            "/api/bots/publisher/outputs/work%2F2026-10-27%20final%20report.md/preview",
            server.takeRequest().path,
        )
        assertEquals("# The final report", preview.preview)
        assertFalse(preview.truncated)
    }

    @Test
    fun `download returns the raw bytes`() = runBlocking {
        val bytes = "# Rapport\nligne 2\n".toByteArray()
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/markdown")
                .setBody(okio.Buffer().write(bytes)),
        )

        val downloaded = api.downloadOutput("publisher", "rapport.md")

        assertEquals("/api/bots/publisher/outputs/rapport.md/download", server.takeRequest().path)
        assertArrayEquals(bytes, downloaded)
    }

    @Test
    fun `a missing deliverable is a readable refusal`() {
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"error":"Deliverable not found"}"""))
        val failure = assertThrows(MercuryException::class.java) {
            runBlocking { api.downloadOutput("publisher", "ghost.md") }
        }
        assertEquals("Livrable introuvable", failure.message)
    }

    @Test
    fun `delete sends DELETE on the deliverable path`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"ok":true}"""))

        val response = api.deleteOutput("publisher", "brief.md")

        val request = server.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals("/api/bots/publisher/outputs/brief.md", request.path)
        assertTrue(response.ok)
    }

    @Test
    fun `runs, one run and the journal use the documented routes`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"runs":[{"runId":"3f2a9b1c","mtimeMs":1793000600000}]}"""))
        val runs = api.botRuns("veille")
        assertEquals("/api/bots/veille/runs", server.takeRequest().path)
        assertEquals("3f2a9b1c", runs.runs[0].runId)

        server.enqueue(
            MockResponse().setBody(
                """{"record":{"runId":"3f2a9b1c","state":"completed","startedAt":1793000500000,"durationMs":12300},
                    "prompt":"Brief","output":"Fait","trace":[{"name":"write_file","ok":true,"arg":"brief.md"}]}""",
            ),
        )
        val transcript = api.botRun("veille", "3f2a9b1c")
        assertEquals("/api/bots/veille/runs/3f2a9b1c", server.takeRequest().path)
        assertEquals("completed", transcript.record.state)
        assertEquals(1, transcript.trace.size)

        server.enqueue(
            MockResponse().setBody(
                """{"journal":[{"runId":"3f2a9b1c","state":"completed","startedAt":1793000500000,"tokensIn":51000}]}""",
            ),
        )
        val records = api.botRunJournal("veille", limit = 50)
        assertEquals("/api/bots/veille/journal?limit=50", server.takeRequest().path)
        assertEquals(51000L, records[0].tokensIn)
    }

    @Test
    fun `dlq lists and replay accepts`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"dlq":[{"id":"7f3a2b10-4c5d","botId":"veille","trigger":"cron","prompt":"Résume","state":"dead",
                    "attempts":2,"createdAt":1792999000000,"reasonCode":"provider_rate_limit"}]}""",
            ),
        )
        val dlq = api.botDlq("veille")
        assertEquals("/api/bots/veille/dlq", server.takeRequest().path)
        assertEquals(2, dlq.dlq[0].attempts)

        server.enqueue(MockResponse().setBody("""{"accepted":true,"jobId":"7f3a2b10-4c5d","status":"accepted"}"""))
        val replayed = api.replayDlq("veille", "7f3a2b10-4c5d")
        val replayRequest = server.takeRequest()
        assertEquals("POST", replayRequest.method)
        assertEquals("/api/bots/veille/replay/7f3a2b10-4c5d", replayRequest.path)
        assertTrue(replayed.accepted)
    }

    @Test
    fun `a replay the daemon refuses is decoded, not thrown`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"accepted":false,"reasonCode":"not_found"}"""))

        val replayed = api.replayDlq("veille", "ghost")

        assertFalse(replayed.accepted)
        assertEquals("not_found", replayed.reasonCode)
    }

    @Test
    fun `clearing the dlq is fleet-wide unless a bot is named`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"cleared":3}"""))
        val fleet = api.clearDlq()
        val fleetRequest = server.takeRequest()
        assertEquals("DELETE", fleetRequest.method)
        assertEquals("/api/bots-dlq", fleetRequest.path)
        assertEquals(3, fleet.cleared)

        server.enqueue(MockResponse().setBody("""{"cleared":1}"""))
        api.clearDlq("veille")
        assertEquals("/api/bots-dlq?bot=veille", server.takeRequest().path)
    }
}
