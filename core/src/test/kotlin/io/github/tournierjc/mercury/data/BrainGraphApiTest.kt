package io.github.tournierjc.mercury.data

import io.github.tournierjc.mercury.graph.GraphLink
import io.github.tournierjc.mercury.graph.GraphOutcome
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

/**
 * `GET /api/brain/graph` when SQLite is missing.
 *
 * The route answers `503` **with a body** (`src/web/api/brain.ts:269`) — an absent dependency is
 * an HTTP error on purpose. What the client owes that answer is to read it: the call must come back
 * as [GraphOutcome.Unavailable] with the server's motif, not throw, or the screen shows
 * `SQLITE_DEPENDENCY_ERROR` as a failure instead of the "graph not exposed" state with a Retry.
 */
class BrainGraphApiTest {

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

    private fun respond(code: Int, body: String): MockResponse =
        MockResponse().setResponseCode(code).setBody(body)

    @Test
    fun `a 503 with a body becomes an absent dependency, it does not throw`() {
        server.enqueue(
            respond(503, """{"nodes":[],"edges":[],"available":false,"error":"SQLITE_DEPENDENCY_ERROR"}"""),
        )

        val outcome = runBlocking { api.brainGraph() }

        assertEquals(GraphOutcome.Unavailable("SQLITE_DEPENDENCY_ERROR"), outcome)
        // The route is asked once, without a query string: this read has no parameters.
        val request = server.takeRequest()
        assertEquals("/api/brain/graph", request.path)
        assertEquals("GET", request.method)
    }

    @Test
    fun `a healthy graph comes back drawn, with the server's colour and type`() {
        server.enqueue(
            respond(
                200,
                """
                {"nodes":[
                   {"id":"mem-1","label":"Aline préfère le thé","type":"preference",
                    "importance":0.82,"confidence":0.66,"color":"#3f8cff","size":18.5},
                   {"id":"mem-2","label":"Bureau à Lyon","type":"place",
                    "importance":0.31,"confidence":0.4,"color":"#aabbcc","size":9}],
                 "edges":[{"source":"mem-1","target":"mem-2","type":"related"}],
                 "available":true}
                """.trimIndent(),
            ),
        )

        val outcome = runBlocking { api.brainGraph() }

        assertTrue("expected a graph, got $outcome", outcome is GraphOutcome.Ok)
        val ok = outcome as GraphOutcome.Ok
        assertEquals(2, ok.nodes.size)
        assertEquals("#3f8cff", ok.nodes[0].color)
        assertEquals("preference", ok.nodes[0].type)
        assertEquals(0.82, ok.nodes[0].importance, 0.0)
        assertEquals(listOf(GraphLink("mem-1", "mem-2")), ok.links)
    }

    @Test
    fun `a 200 carrying available false lands on the same state as the 503`() {
        server.enqueue(
            respond(200, """{"nodes":[],"edges":[],"available":false,"error":"SQLITE_DEPENDENCY_ERROR"}"""),
        )

        assertEquals(
            GraphOutcome.Unavailable("SQLITE_DEPENDENCY_ERROR"),
            runBlocking { api.brainGraph() },
        )
    }

    @Test
    fun `an expired session still throws, like every other route`() {
        server.enqueue(respond(401, """{"error":"unauthorized"}"""))

        assertThrows(AuthException::class.java) {
            runBlocking { api.brainGraph() }
        }
    }

    @Test
    fun `a 500 keeps the route's own failure text`() {
        server.enqueue(respond(500, """{"error":"SQLITE_READ_FAILED"}"""))

        assertEquals(GraphOutcome.Error("SQLITE_READ_FAILED"), runBlocking { api.brainGraph() })
    }
}
