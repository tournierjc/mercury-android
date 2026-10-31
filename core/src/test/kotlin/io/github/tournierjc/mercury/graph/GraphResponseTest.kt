package io.github.tournierjc.mercury.graph

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The reader of `GET /api/brain/graph`, on the bytes the route really sends.
 *
 * The issues are asserted on purpose, not just "it did not crash": a 503 whose body names the
 * missing dependency must become [GraphOutcome.Unavailable] with that motif, and a body nobody can
 * read must become [GraphOutcome.Error] — never an empty graph, which is the one thing a
 * brain-with-no-memory and a brain-with-no-database have in common.
 */
class GraphResponseTest {

    /** `src/web/api/brain.ts:269`, the "no SQLite" branch, verbatim. */
    private val noDatabase =
        """{"nodes":[],"edges":[],"available":false,"error":"SQLITE_DEPENDENCY_ERROR"}"""

    /** What the route draws on a healthy instance: one node, its type, its colour, its weight. */
    private val twoNodes = """
        {
          "nodes": [
            {"id":"mem-1","label":"Aline préfère le thé","fullLabel":"Aline préfère le thé vert le matin",
             "type":"preference","importance":0.82,"confidence":0.66,"color":"#3f8cff","size":18.5,
             "seenAt":"2026-10-01T08:12:00.000Z"},
            {"id":"mem-2","label":"Bureau à Lyon","fullLabel":"Le bureau est à Lyon, rue de la Paix",
             "type":"place","importance":0.31,"confidence":0.4,"color":"#aabbcc","size":9}
          ],
          "edges": [{"source":"mem-1","target":"mem-2","type":"related"}],
          "available": true
        }
    """.trimIndent()

    @Test
    fun `a 503 carrying available false is an absent dependency, with the server's motif`() {
        val outcome = parseGraphResponse(503, noDatabase)
        assertEquals(GraphOutcome.Unavailable("SQLITE_DEPENDENCY_ERROR"), outcome)
    }

    @Test
    fun `the same flag on a 200 lands on the same state`() {
        // The daemon answers 503 today. A patch that turns it into a 200 {available:false} must
        // not move the client: the flag is read before the status.
        assertEquals(
            GraphOutcome.Unavailable("SQLITE_DEPENDENCY_ERROR"),
            parseGraphResponse(200, noDatabase),
        )
    }

    @Test
    fun `an empty error field does not hide the status code`() {
        val outcome = parseGraphResponse(503, """{"nodes":[],"edges":[],"available":false,"error":""}""")
        assertEquals(GraphOutcome.Unavailable("HTTP 503"), outcome)
    }

    @Test
    fun `a graph carries the server's own nodes and links`() {
        val outcome = parseGraphResponse(200, twoNodes)
        assertTrue("expected a graph, got $outcome", outcome is GraphOutcome.Ok)
        val ok = outcome as GraphOutcome.Ok

        assertEquals(listOf("mem-1", "mem-2"), ok.nodes.map { it.id })
        // Non-default values on purpose: a reader that fell back to the DTO defaults would pass
        // a "size == 2" check and still draw two grey circles.
        assertEquals("preference", ok.nodes[0].type)
        assertEquals("#3f8cff", ok.nodes[0].color)
        assertEquals(0.82, ok.nodes[0].importance, 0.0)
        assertEquals("Aline préfère le thé", ok.nodes[0].label)
        assertEquals(listOf(GraphLink("mem-1", "mem-2")), ok.links)
    }

    @Test
    fun `a body nobody can read is a failure, not an empty graph`() {
        val outcome = parseGraphResponse(200, "<html>proxy error</html>")
        assertEquals(GraphOutcome.Error(GraphMotifs.UNREADABLE_BODY), outcome)
    }

    @Test
    fun `a null body is a failure too`() {
        assertEquals(GraphOutcome.Error(GraphMotifs.UNREADABLE_BODY), parseGraphResponse(200, null))
    }

    @Test
    fun `nodes that are not an array are a failure`() {
        assertEquals(
            GraphOutcome.Error(GraphMotifs.NODES_NOT_AN_ARRAY),
            parseGraphResponse(200, """{"nodes":{"mem-1":"Aline"},"edges":[]}"""),
        )
        assertEquals(
            GraphOutcome.Error(GraphMotifs.NODES_NOT_AN_ARRAY),
            parseGraphResponse(200, """{"edges":[]}"""),
        )
    }

    @Test
    fun `nodes present but none readable is a failure`() {
        // Two objects, neither carrying an id: skipping both would draw an empty graph and claim
        // the brain holds nothing, which is not what the route said.
        val outcome = parseGraphResponse(200, """{"nodes":[{"label":"A"},{"label":"B"}],"edges":[]}""")
        assertEquals(GraphOutcome.Error(GraphMotifs.NO_READABLE_NODE), outcome)
    }

    @Test
    fun `an empty nodes array is a real, empty graph`() {
        assertEquals(
            GraphOutcome.Ok(nodes = emptyList(), links = emptyList()),
            parseGraphResponse(200, """{"nodes":[],"edges":[],"available":true}"""),
        )
    }

    @Test
    fun `a 500 keeps the error text the route sent`() {
        assertEquals(
            GraphOutcome.Error("SQLITE_READ_FAILED"),
            parseGraphResponse(500, """{"error":"SQLITE_READ_FAILED"}"""),
        )
    }

    @Test
    fun `a 500 with an unreadable body still names the status`() {
        assertEquals(GraphOutcome.Error("HTTP 500"), parseGraphResponse(500, "boom"))
    }

    @Test
    fun `the link array is accepted under the name links`() {
        val body = """
            {"nodes":[{"id":"a","label":"A"},{"id":"b","label":"B"}],
             "links":[{"source":"a","target":"b"}],"available":true}
        """.trimIndent()
        val ok = parseGraphResponse(200, body) as GraphOutcome.Ok
        assertEquals(listOf(GraphLink("a", "b")), ok.links)
    }

    @Test
    fun `an end named by an object is resolved like a bare id`() {
        val body = """
            {"nodes":[{"id":"a","label":"A"},{"id":"b","label":"B"}],
             "edges":[{"source":{"id":"a"},"target":{"id":"b"}}],"available":true}
        """.trimIndent()
        val ok = parseGraphResponse(200, body) as GraphOutcome.Ok
        assertEquals(listOf(GraphLink("a", "b")), ok.links)
    }

    @Test
    fun `the same pair named both ways stays one link`() {
        val body = """
            {"nodes":[{"id":"a","label":"A"},{"id":"b","label":"B"}],
             "edges":[{"source":"a","target":"b"},{"source":"b","target":"a"}],"available":true}
        """.trimIndent()
        val ok = parseGraphResponse(200, body) as GraphOutcome.Ok
        assertEquals(1, ok.links.size)
    }

    @Test
    fun `a link to a node that is not in the drawing is dropped`() {
        val body = """
            {"nodes":[{"id":"a","label":"A"},{"id":"b","label":"B"}],
             "edges":[{"source":"a","target":"b"},{"source":"a","target":"ghost"}],"available":true}
        """.trimIndent()
        val ok = parseGraphResponse(200, body) as GraphOutcome.Ok
        assertEquals(listOf(GraphLink("a", "b")), ok.links)
    }

    @Test
    fun `a node without a label borrows the full one`() {
        val body = """{"nodes":[{"id":"a","fullLabel":"Le bureau est à Lyon"}],"edges":[]}"""
        val ok = parseGraphResponse(200, body) as GraphOutcome.Ok
        assertEquals("Le bureau est à Lyon", ok.nodes.single().label)
        assertEquals("Le bureau est à Lyon", ok.nodes.single().fullLabel)
    }
}
