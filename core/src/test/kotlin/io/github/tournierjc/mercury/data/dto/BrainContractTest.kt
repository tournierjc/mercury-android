package io.github.tournierjc.mercury.data.dto

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Payloads below were captured from a live Mercury 1.4.2 brain, so the second-brain
 * screens decode for real instead of on a guess.
 */
class BrainContractTest {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    @Test
    fun `memory page decodes a stored preference`() {
        val payload = """
            {"memories":[{"id":"87fe0577-2d71-4000-9732-3e9047b4fd8a","type":"preference","summary":"Writes to the agent in French and expects French replies.","detail":null,"scope":"active","evidenceKind":"inferred","source":"conversation","confidence":0.6659166635763106,"importance":0.5,"durability":0.9,"evidenceCount":1,"dismissed":false,"createdAt":1792946256716,"updatedAt":1792946256716,"lastSeenAt":1792946256716}],"total":1,"available":true}
        """.trimIndent()

        val page = json.decodeFromString(MemoriesPage.serializer(), payload)

        assertEquals(1, page.total)
        val memory = page.memories.single()
        assertEquals("preference", memory.type)
        assertTrue(memory.summary.startsWith("Writes to the agent in French"))
        assertNull(memory.detail)
        assertEquals(0.666, memory.confidence, 0.001)
    }

    @Test
    fun `belief page decodes the evidence counts`() {
        val payload = """
            {"beliefs":[{"id":"87fe0577-2d71-4000-9732-3e9047b4fd8a","ownerKey":"user:owner","kind":"preference","dimension":null,"level":null,"statement":"Writes to the agent in French and expects French replies.","detail":null,"context":"*","support":1,"against":0,"supportSessions":0,"timescale":"stable","status":"candidate","ownerStated":false,"validFrom":1792946256716,"validTo":null,"supersededBy":null,"lastSupportedAt":1792946256716}],"total":1,"available":true}
        """.trimIndent()

        val page = json.decodeFromString(BeliefsPage.serializer(), payload)

        val belief = page.beliefs.single()
        assertEquals("candidate", belief.status)
        assertEquals(1.0, belief.netSupport, 0.0)
        assertNull(belief.validTo)
        assertFalse(belief.ownerStated)
    }

    @Test
    fun `graph counts nodes even when edges are absent`() {
        val payload = """
            {"nodes":[{"id":"n1","label":"French replies","fullLabel":"Writes in French","type":"preference","importance":0.5,"confidence":0.66,"color":"#febc2e","size":6}],"edges":[],"available":true}
        """.trimIndent()

        val graph = json.decodeFromString(BeliefGraphDto.serializer(), payload)

        assertEquals(1, graph.nodes.size)
        assertEquals(0, graph.edges.size)
        assertEquals("#febc2e", graph.nodes[0].color)
    }

    @Test
    fun `quarantine reuses the memory shape`() {
        val page = json.decodeFromString(
            MemoriesPage.serializer(),
            """{"memories":[],"total":0,"available":true}""",
        )
        assertTrue(page.memories.isEmpty())
        assertTrue(page.available)
    }

    @Test
    fun `config decodes the identity projection`() {
        val payload = """
            {"identity":{"name":"Mercury","owner":"JC","creator":"Cosmic Stack","about":""},"defaultProvider":"deepseek","tokenBudget":200000,"heartbeatInterval":60,"telegramEnabled":false,"secondBrainEnabled":true}
        """.trimIndent()

        val config = json.decodeFromString(InstanceConfig.serializer(), payload)

        assertEquals("JC", config.identity.owner)
        assertEquals(200_000L, config.tokenBudget)
        assertTrue(config.secondBrainEnabled)
        assertFalse(config.telegramEnabled)
    }

    @Test
    fun `the config write answers with the restart it asks for`() {
        // The route reads provider and budget at boot; the app must be able to say so instead of
        // implying the change is live.
        val saved = json.decodeFromString(InstanceConfigSave.serializer(), """{"success":true,"restartRequired":true}""")

        assertTrue(saved.success)
        assertTrue(saved.restartRequired)
    }

    @Test
    fun `memory types match what the route accepts`() {
        assertEquals(10, MEMORY_TYPES.size)
        assertTrue(MEMORY_TYPES.contains("preference"))
        assertTrue(MEMORY_TYPES.contains("episode"))
    }

    @Test
    fun `workspace file marks a refused path as unreadable`() {
        val missing = json.decodeFromString(
            WorkspaceFileDto.serializer(),
            """{"error":"File not found"}""",
        )
        assertFalse(missing.readable)

        val ok = json.decodeFromString(
            WorkspaceFileDto.serializer(),
            """{"path":"README.md","name":"README.md","content":"# hi","size":4,"binary":false,"truncated":false}""",
        )
        assertTrue(ok.readable)
        assertEquals("# hi", ok.content)
    }

    @Test
    fun `belief support is a weight, not a count — a fractional value decodes`() {
        // The instance sends 4.5 for a belief seen with a partial session: an Int here threw the
        // whole beliefs page away, and the Learned tab looked empty for a reason nobody could see.
        val payload = """
            {"beliefs":[{"id":"b1","ownerKey":"user:owner","kind":"preference","statement":"Likes terse replies.","support":4.5,"against":0.5,"supportSessions":2,"timescale":"stable","status":"active","ownerStated":true,"validFrom":1792946256716,"validTo":null,"supersededBy":null,"lastSupportedAt":1792946256716}],"total":1,"available":true}
        """.trimIndent()

        val belief = json.decodeFromString(BeliefsPage.serializer(), payload).beliefs.single()

        assertEquals(4.5, belief.support, 0.001)
        assertEquals(4.0, belief.netSupport, 0.001)
    }

}
