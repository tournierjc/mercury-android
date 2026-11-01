package io.github.tournierjc.mercury.data

import io.github.tournierjc.mercury.data.dto.BotsCostReport
import io.github.tournierjc.mercury.data.dto.BotsStorageReport
import io.github.tournierjc.mercury.data.dto.BundleImportResponse
import io.github.tournierjc.mercury.data.dto.McpServersResponse
import io.github.tournierjc.mercury.data.dto.ProviderTestResult
import io.github.tournierjc.mercury.data.dto.RegistrySkillInstallResult
import io.github.tournierjc.mercury.data.dto.ShutdownResult
import io.github.tournierjc.mercury.data.dto.WorkspaceRootResult
import io.github.tournierjc.mercury.util.formatBytes
import java.util.Locale
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Decoding of the console's new payloads, plus the ordering the two fleet sections rely on.
 *
 * Every numeric assertion is worth more than the non-default: a tolerant accessor reading the
 * wrong receiver returns 0 silently, and the screen would just show zeros.
 */
class ConsoleOpsContractTest {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    @Test
    fun `cost report ranks the bots that actually spend`() {
        val payload = """
            {"days":7,"bots":[
              {"id":"ceo","name":"Chef","today":{"runs":1,"completed":1,"failed":0,"noOutcome":0,"deliverables":1,"tokensIn":100,"tokensOut":50},"window":{"runs":4,"completed":3,"failed":1,"noOutcome":0,"deliverables":2,"tokensIn":900,"tokensOut":600},"cap":0,"paused":false},
              {"id":"idle","name":"Au repos","today":{"runs":0,"completed":0,"failed":0,"noOutcome":0,"deliverables":0,"tokensIn":0,"tokensOut":0},"window":{"runs":0,"completed":0,"failed":0,"noOutcome":0,"deliverables":0,"tokensIn":0,"tokensOut":0},"cap":0,"paused":false},
              {"id":"scout","name":"Éclaireur","parent":"ceo","today":{"runs":2,"completed":2,"failed":0,"noOutcome":0,"deliverables":0,"tokensIn":8000,"tokensOut":4000},"window":{"runs":6,"completed":6,"failed":0,"noOutcome":1,"deliverables":4,"tokensIn":24000,"tokensOut":16000},"cap":90000,"paused":true}
            ],"fleets":[{"id":"ceo","name":"Chef","members":2,"today":{"runs":3,"completed":3,"failed":0,"noOutcome":0,"deliverables":1,"tokensIn":8100,"tokensOut":4050},"window":{"runs":10,"completed":9,"failed":1,"noOutcome":1,"deliverables":6,"tokensIn":24900,"tokensOut":16600}}],"fleetToday":12150,"fleetCap":250000,"fleetPaused":true}
        """.trimIndent()

        val report = json.decodeFromString(BotsCostReport.serializer(), payload)

        assertEquals(7, report.days)
        assertEquals(3, report.bots.size)
        // Non-default on every field that a wrong receiver would read as 0/false.
        assertEquals(900L, report.bots[0].window.tokensIn)
        assertEquals(600L, report.bots[0].window.tokensOut)
        assertEquals(1500L, report.bots[0].window.tokens)
        assertEquals(3, report.bots[0].window.completed)
        assertEquals(1, report.bots[0].window.failed)
        assertEquals("ceo", report.bots[2].parent)
        assertEquals(90000L, report.bots[2].cap)
        assertTrue(report.bots[2].paused)
        assertEquals(2, report.fleets[0].members)
        assertEquals(24900L, report.fleets[0].window.tokensIn)
        assertEquals(12150L, report.fleetToday)
        assertEquals(250000L, report.fleetCap)
        assertTrue(report.fleetPaused)

        // Biggest window first, and a bot with nothing to show is left out.
        val top = report.topByTokens()
        assertEquals(listOf("scout", "ceo"), top.map { it.id })
        assertEquals(40000L, top[0].window.tokens)
        assertEquals(41500L, report.windowTokens)
    }

    @Test
    fun `an empty fleet decodes to empty sections instead of failing`() {
        val report = json.decodeFromString(
            BotsCostReport.serializer(),
            """{"days":1,"bots":[],"fleets":[],"fleetToday":0,"fleetCap":0,"fleetPaused":false}""",
        )
        assertEquals(1, report.days)
        assertTrue(report.bots.isEmpty())
        assertTrue(report.topByTokens().isEmpty())
        assertEquals(0L, report.windowTokens)
    }

    @Test
    fun `storage report splits the journal from the profile`() {
        val payload = """
            {"usage":[{"id":"ceo","bytes":5242880,"journalBytes":1048576},{"id":"scout","bytes":131072,"journalBytes":131072}],"queue":{"pending":4,"claimed":2,"dlq":1}}
        """.trimIndent()

        val report = json.decodeFromString(BotsStorageReport.serializer(), payload)

        assertEquals(2, report.usage.size)
        assertEquals(5242880L, report.usage[0].bytes)
        assertEquals(1048576L, report.usage[0].journalBytes)
        assertEquals(4194304L, report.usage[0].profileBytes)
        // A bot whose bytes are all journal has an empty profile, never a negative size.
        assertEquals(0L, report.usage[1].profileBytes)
        assertEquals(5373952L, report.totalBytes)
        assertEquals(4, report.queue.pending)
        assertEquals(2, report.queue.claimed)
        assertEquals(1, report.queue.dlq)
        assertEquals("ceo", report.topBySize()[0].id)
    }

    @Test
    fun `storage sizes read in the locale's units`() {
        assertEquals("512 o", formatBytes(512, Locale.FRENCH))
        assertEquals("2 ko", formatBytes(2048, Locale.FRENCH))
        assertEquals("5 Mo", formatBytes(5L * 1024 * 1024, Locale.FRENCH))
        assertEquals("1,5 Go", formatBytes((1.5 * 1024 * 1024 * 1024).toLong(), Locale.FRENCH))
        assertEquals("—", formatBytes(0, Locale.FRENCH))
    }

    @Test
    fun `mcp servers keep their state transport and tool gates`() {
        val payload = """
            {"available":true,"servers":[
              {"id":"fs","transport":"stdio","enabled":true,"state":"connected","target":"node fs.js","error":null,"toolCount":1,"tools":[{"name":"read_file","exposedName":"mcp_fs_read_file","allowed":true,"autoApprove":false,"skipped":null}]},
              {"id":"web","transport":"http","enabled":false,"state":"failed","target":"https://mcp.example.com","error":"ECONNREFUSED","toolCount":0,"tools":[]}
            ]}
        """.trimIndent()

        val response = json.decodeFromString(McpServersResponse.serializer(), payload)

        assertTrue(response.available)
        assertEquals("stdio", response.servers[0].transport)
        assertEquals("connected", response.servers[0].state)
        assertEquals(1, response.servers[0].toolCount)
        assertTrue(response.servers[0].tools[0].allowed)
        assertFalse(response.servers[0].tools[0].autoApprove)
        assertEquals("mcp_fs_read_file", response.servers[0].tools[0].exposedName)
        assertEquals("http", response.servers[1].transport)
        assertEquals("failed", response.servers[1].state)
        assertEquals("ECONNREFUSED", response.servers[1].error)
        assertFalse(response.servers[1].enabled)
    }

    @Test
    fun `provider probe and registry install decode their answers`() {
        val probe = json.decodeFromString(
            ProviderTestResult.serializer(),
            """{"success":true,"models":["a","b","c"],"recommendedModel":"b"}""",
        )
        assertTrue(probe.success)
        assertEquals(3, probe.models.size)
        assertEquals("b", probe.recommendedModel)

        val install = json.decodeFromString(
            RegistrySkillInstallResult.serializer(),
            """{"success":true,"id":"research/arxiv","version":"0.4.1","status":"installed","path":"/s/arxiv","webUrl":"https://w/research/arxiv"}""",
        )
        assertTrue(install.success)
        assertEquals("0.4.1", install.version)
        assertEquals("installed", install.status)
        assertEquals("https://w/research/arxiv", install.webUrl)
    }

    @Test
    fun `shutdown refusal and workspace root decode`() {
        val refused = json.decodeFromString(
            ShutdownResult.serializer(),
            """{"error":"Shutdown requires the local attach token"}""",
        )
        assertFalse(refused.ok)
        assertEquals(0, refused.pid)
        assertEquals("Shutdown requires the local attach token", refused.error)

        val accepted = json.decodeFromString(ShutdownResult.serializer(), """{"ok":true,"pid":91}""")
        assertTrue(accepted.ok)
        assertEquals(91, accepted.pid)

        val root = json.decodeFromString(
            WorkspaceRootResult.serializer(),
            """{"success":true,"workspace":"/home/node/work"}""",
        )
        assertTrue(root.success)
        assertEquals("/home/node/work", root.workspace)
    }

    @Test
    fun `bundle import report separates created bots from skipped ones`() {
        val response = json.decodeFromString(
            BundleImportResponse.serializer(),
            """{"report":{"created":["ceo"],"skipped":[{"id":"ops","reason":"already exists"}]}}""",
        )
        assertEquals(listOf("ceo"), response.report.created)
        assertEquals(1, response.report.skipped.size)
        assertEquals("ops", response.report.skipped[0].id)
        assertEquals("already exists", response.report.skipped[0].reason)

        val refused = json.decodeFromString(BundleImportResponse.serializer(), """{"error":"Invalid bundle format"}""")
        assertEquals("Invalid bundle format", refused.error)
        assertTrue(refused.report.created.isEmpty())
    }
}
