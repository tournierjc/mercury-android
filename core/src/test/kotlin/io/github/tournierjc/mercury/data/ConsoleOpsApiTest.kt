package io.github.tournierjc.mercury.data

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Routes the console gained: skills install/delete, provider write + probe, MCP status,
 * shutdown, the workspace root and the fleet's money/disk/bundle surface.
 *
 * Every path and body below was read off the daemon's handler (system.ts, providers.ts,
 * mcp.ts, lifecycle.ts, chat.ts, bots.ts) — the request assertions are the contract.
 */
class ConsoleOpsApiTest {

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

    private fun bodyOf(json: String): JsonObject = Json.parseToJsonElement(json) as JsonObject

    // --- Skills ----------------------------------------------------------

    @Test
    fun `installing a skill posts the url and decodes the folder`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody("""{"success":true,"name":"podcast","path":"/home/node/.mercury/skills/podcast"}"""),
        )

        val result = api.installSkill("  https://example.com/podcast.tar.gz  ")

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/skills/install", request.path)
        val body = bodyOf(request.body.readUtf8())
        assertEquals(setOf("url"), body.keys)
        assertEquals("https://example.com/podcast.tar.gz", (body["url"] as JsonPrimitive).content)
        assertTrue(result.success)
        assertEquals("podcast", result.name)
        assertEquals("/home/node/.mercury/skills/podcast", result.path)
    }

    @Test
    fun `a refused url surfaces the daemon reason`() {
        server.enqueue(
            MockResponse().setResponseCode(400)
                .setBody("""{"success":false,"error":"url must start with http:// or https://"}"""),
        )
        val failure = assertThrows(MercuryException::class.java) {
            runBlocking { api.installSkill("/opt/data/skills/podcast") }
        }
        assertEquals("url must start with http:// or https://", failure.message)
    }

    @Test
    fun `registry install sends the category slash slug and force`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"success":true,"id":"research/arxiv","version":"1.2.0","status":"installed","path":"/skills/arxiv","webUrl":"https://skills.mercury.dev/research/arxiv"}""",
            ),
        )

        val result = api.installSkillFromRegistry(" research/arxiv ", force = true)

        val request = server.takeRequest()
        assertEquals("/api/skills/install-from-registry", request.path)
        val body = bodyOf(request.body.readUtf8())
        assertEquals("research/arxiv", (body["id"] as JsonPrimitive).content)
        assertEquals(true, (body["force"] as JsonPrimitive).booleanOrNull)
        assertEquals("1.2.0", result.version)
        assertEquals("installed", result.status)
        assertEquals("https://skills.mercury.dev/research/arxiv", result.webUrl)
    }

    @Test
    fun `registry install without force omits the flag`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"success":true,"id":"a/b"}"""))

        api.installSkillFromRegistry("a/b")

        val body = bodyOf(server.takeRequest().body.readUtf8())
        // Absent, not false: the daemon defaults it with Boolean(body?.force).
        assertFalse(body.containsKey("force"))
    }

    @Test
    fun `deleting a skill uses DELETE on the encoded name`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"success":true}"""))

        val result = api.deleteSkill("pdf tools")

        val request = server.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals("/api/skills/pdf%20tools", request.path)
        assertTrue(result.success)
    }

    // --- Providers -------------------------------------------------------

    @Test
    fun `saving a provider posts only the fields that were filled`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"success":true,"restartRequired":true}"""))

        val result = api.saveProvider(
            "deepseek",
            io.github.tournierjc.mercury.data.dto.ProviderUpdateRequest(apiKey = "sk-new", enabled = true),
        )

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/providers/deepseek", request.path)
        val body = bodyOf(request.body.readUtf8())
        // An explicit null would overwrite nothing but is still a shape the daemon never sees.
        assertEquals(setOf("apiKey", "enabled"), body.keys)
        assertEquals("sk-new", (body["apiKey"] as JsonPrimitive).content)
        assertEquals(true, (body["enabled"] as JsonPrimitive).booleanOrNull)
        assertTrue(result.success)
        assertTrue(result.restartRequired)
    }

    @Test
    fun `testing a provider decodes the catalogue it reports`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"success":true,"models":["deepseek-flash","deepseek-pro"],"recommendedModel":"deepseek-flash"}""",
            ),
        )

        val result = api.testProvider("deepseek")

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/providers/deepseek/test", request.path)
        assertTrue(result.success)
        assertEquals(listOf("deepseek-flash", "deepseek-pro"), result.models)
        assertEquals("deepseek-flash", result.recommendedModel)
    }

    @Test
    fun `a failing probe is an answer, not an exception`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(400)
                .setBody("""{"error":"No API key configured"}"""),
        )

        val result = api.testProvider("openai")

        assertFalse(result.success)
        assertEquals("No API key configured", result.error)
        assertTrue(result.models.isEmpty())
    }

    @Test
    fun `an expired session on a probe still raises auth`() {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":"Unauthorized"}"""))
        assertThrows(AuthException::class.java) { runBlocking { api.testProvider("openai") } }
    }

    // --- MCP -------------------------------------------------------------

    @Test
    fun `mcp status decodes the servers and their tools`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"available":true,"servers":[{"id":"fs","transport":"stdio","enabled":true,"state":"connected","target":"node fs-server.js","error":null,"toolCount":2,"tools":[{"name":"read_file","exposedName":"mcp_fs_read_file","allowed":true,"autoApprove":false,"skipped":null},{"name":"write_file","exposedName":"mcp_fs_write_file","allowed":false,"autoApprove":true,"skipped":"denied by policy"}]}]}""",
            ),
        )

        val result = api.mcpServers()

        assertEquals("/api/mcp", server.takeRequest().path)
        assertTrue(result.available)
        assertEquals(1, result.servers.size)
        val fs = result.servers[0]
        assertEquals("fs", fs.id)
        assertEquals("stdio", fs.transport)
        assertEquals("connected", fs.state)
        assertEquals("node fs-server.js", fs.target)
        assertEquals(2, fs.toolCount)
        assertEquals("mcp_fs_read_file", fs.tools[0].exposedName)
        assertTrue(fs.tools[0].allowed)
        assertFalse(fs.tools[0].autoApprove)
        assertEquals("denied by policy", fs.tools[1].skipped)
    }

    @Test
    fun `a runtime without an mcp manager answers an empty list`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"available":false,"servers":[]}"""))

        val result = api.mcpServers()

        assertFalse(result.available)
        assertTrue(result.servers.isEmpty())
    }

    // --- Daemon ----------------------------------------------------------

    @Test
    fun `shutdown decodes the pid of the acknowledged daemon`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(202).setBody("""{"ok":true,"pid":4242}"""))

        val result = api.shutdown()

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/shutdown", request.path)
        assertTrue(result.ok)
        assertEquals(4242, result.pid)
    }

    @Test
    fun `a shutdown refused off-host keeps the daemon message`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(403)
                .setBody("""{"error":"Shutdown is only accepted from this machine"}"""),
        )

        val result = api.shutdown()

        // Not an auth failure: the session is fine, the phone is simply not the host.
        assertFalse(result.ok)
        assertEquals("Shutdown is only accepted from this machine", result.error)
    }

    @Test
    fun `changing the workspace root puts the path`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"success":true,"workspace":"/work/app"}"""))

        val result = api.setWorkspaceRoot(" /work/app ")

        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals("/api/workspace/root", request.path)
        val body = bodyOf(request.body.readUtf8())
        assertEquals("""{"path":"/work/app"}""", body.toString())
        assertTrue(result.success)
        assertEquals("/work/app", result.workspace)
    }

    @Test
    fun `a missing directory keeps the daemon reason`() {
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"error":"Directory does not exist"}"""))
        val failure = assertThrows(MercuryException::class.java) {
            runBlocking { api.setWorkspaceRoot("/nope") }
        }
        assertEquals("Directory does not exist", failure.message)
    }

    // --- Fleet money and disk -------------------------------------------

    @Test
    fun `bots cost asks for the window and decodes spend`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"days":7,"bots":[{"id":"ceo","name":"Chef","today":{"runs":2,"completed":2,"failed":0,"noOutcome":0,"deliverables":1,"tokensIn":1200,"tokensOut":800},"window":{"runs":9,"completed":8,"failed":1,"noOutcome":0,"deliverables":3,"tokensIn":4300,"tokensOut":2700},"cap":50000,"paused":false}],"fleets":[{"id":"ceo","name":"Chef","members":2,"today":{"runs":2,"completed":2,"failed":0,"noOutcome":0,"deliverables":1,"tokensIn":1200,"tokensOut":800},"window":{"runs":9,"completed":8,"failed":1,"noOutcome":0,"deliverables":3,"tokensIn":4300,"tokensOut":2700}}],"fleetToday":2000,"fleetCap":120000,"fleetPaused":false}""",
            ),
        )

        val report = api.botsCost(days = 14)

        assertEquals("/api/bots-cost?days=14", server.takeRequest().path)
        assertEquals(7, report.days)
        assertEquals(1, report.bots.size)
        assertEquals(4300L, report.bots[0].window.tokensIn)
        assertEquals(7000L, report.bots[0].window.tokens)
        assertEquals(50000L, report.bots[0].cap)
        assertEquals(2, report.fleets[0].members)
        assertEquals(2000L, report.fleetToday)
        assertEquals(120000L, report.fleetCap)
    }

    @Test
    fun `bots cost clamps the window the daemon accepts`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"days":90,"bots":[],"fleets":[],"fleetToday":0,"fleetCap":0,"fleetPaused":false}"""))

        api.botsCost(days = 500)

        assertEquals("/api/bots-cost?days=90", server.takeRequest().path)
    }

    @Test
    fun `bots storage decodes per-bot bytes and the queue`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"usage":[{"id":"ceo","bytes":5242880,"journalBytes":1048576},{"id":"scout","bytes":2048,"journalBytes":512}],"queue":{"pending":3,"claimed":1,"dlq":2}}""",
            ),
        )

        val report = api.botsStorage()

        assertEquals("/api/bots-storage", server.takeRequest().path)
        assertEquals(2, report.usage.size)
        assertEquals(5242880L, report.usage[0].bytes)
        assertEquals(1048576L, report.usage[0].journalBytes)
        assertEquals(4194304L, report.topBySize()[0].profileBytes)
        assertEquals(3, report.queue.pending)
        assertEquals(2, report.queue.dlq)
        assertEquals(5244928L, report.totalBytes)
    }

    @Test
    fun `bundle download returns the daemon document verbatim`() = runBlocking {
        val document = """{"format":"mercury-bot-bundle","version":1,"kind":"bot","root":"ceo","exportedAt":"2026-10-27T09:00:00Z","bots":[]}"""
        server.enqueue(
            MockResponse().setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .addHeader("Content-Disposition", "attachment; filename=\"ceo-bundle.json\"")
                .setBody(document),
        )

        val bundle = api.botBundle("ceo")

        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/api/bots/ceo/bundle", request.path)
        assertEquals(document, bundle)
    }

    @Test
    fun `import posts the bundle as the body and decodes the report`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"report":{"created":["ceo","scout"],"skipped":[{"id":"ops","reason":"already exists (use overwrite to refresh persona/permissions)"}]}}""",
            ),
        )
        val document = """{"format":"mercury-bot-bundle","version":1,"kind":"fleet","root":"ceo","bots":[]}"""

        val result = api.importBotBundle(document, overwrite = true)

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/bots/import?overwrite=1", request.path)
        // The document IS the body, not a wrapper object around it.
        assertEquals(document, request.body.readUtf8())
        assertEquals(listOf("ceo", "scout"), result.report.created)
        assertEquals("ops", result.report.skipped[0].id)
        assertTrue(result.report.skipped[0].reason.startsWith("already exists"))
    }

    @Test
    fun `import without overwrite leaves the query empty`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"report":{"created":[],"skipped":[]}}"""))

        api.importBotBundle("""{"format":"mercury-bot-bundle","version":1,"kind":"bot","root":"x","bots":[]}""")

        assertEquals("/api/bots/import", server.takeRequest().path)
    }

    @Test
    fun `an invalid bundle keeps the daemon reason`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":"Invalid bundle format"}"""))

        val result = api.importBotBundle("""{"hello":"world"}""")

        assertEquals("Invalid bundle format", result.error)
        assertTrue(result.report.created.isEmpty())
    }

    @Test
    fun `a bot id with a slash survives the bundle path`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"format":"x"}"""))

        api.botBundle("team/ceo")

        assertEquals("/api/bots/team%2Fceo/bundle", server.takeRequest().path)
    }
}
