package io.github.tournierjc.mercury.graph

import io.github.tournierjc.mercury.data.dto.GraphNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The type legend as a filter — the one rule of the design sheet that can be broken silently.
 *
 * "The filter masks, it does not relaunch" is not a claim about a composable here: it is the fact
 * that this object answers with a set of ids and never with positions. The layout is computed on
 * the whole graph, and masking is a subtraction applied at draw time.
 */
class GraphMaskTest {

    private val reference = listOf("preference", "decision", "fait", "personne", "projet")

    private fun node(id: String, type: String) = GraphNode(id = id, label = id, type = type)

    private val graph = listOf(
        node("a", "preference"),
        node("b", "preference"),
        node("c", "decision"),
        node("d", "fait"),
    )

    private val links = listOf(
        GraphLink("a", "c"),
        GraphLink("a", "d"),
        GraphLink("c", "d"),
    )

    @Test
    fun `present types follow the reference order, not the payload order`() {
        // The payload names "fait" before "preference": the chips must not.
        assertEquals(listOf("preference", "decision", "fait"), GraphMask.presentTypes(graph, reference))
    }

    @Test
    fun `a type the reference does not know is kept, last and sorted`() {
        val withUnknown = graph + node("e", "souvenir") + node("f", "anecdote")

        assertEquals(
            listOf("preference", "decision", "fait", "anecdote", "souvenir"),
            GraphMask.presentTypes(withUnknown, reference),
        )
    }

    @Test
    fun `turning a chip over hides its type, turning it back shows it`() {
        val hidden = GraphMask.toggle(emptySet(), "decision", listOf("preference", "decision", "fait"))
        assertEquals(setOf("decision"), hidden)

        assertEquals(emptySet<String>(), GraphMask.toggle(hidden, "decision", listOf("preference", "decision", "fait")))
    }

    @Test
    fun `switching the last lit chip off clears the mask instead of emptying the canvas`() {
        val types = listOf("preference", "decision", "fait")
        var hidden = GraphMask.toggle(emptySet(), "decision", types)
        hidden = GraphMask.toggle(hidden, "fait", types)
        assertEquals(setOf("decision", "fait"), hidden)

        // Hiding "preference" as well would leave nothing on the canvas: the mask clears, "Tous"
        // comes back, and the row is reachable again.
        hidden = GraphMask.toggle(hidden, "preference", types)
        assertEquals(emptySet<String>(), hidden)
        assertEquals(3, GraphMask.visibleNodes(graph, GraphMask.hiddenIds(graph, hidden)).size)
    }

    @Test
    fun `a hidden node takes its links with it, both ends`() {
        val hiddenIds = GraphMask.hiddenIds(graph, setOf("fait"))
        val visible = GraphMask.visibleLinks(links, hiddenIds)

        assertEquals(setOf("d"), hiddenIds)
        // a-c survives; a-d and c-d both touched "d".
        assertEquals(listOf(GraphLink("a", "c")), visible)
    }

    @Test
    fun `masking everything leaves only the links between two visible nodes`() {
        val hiddenIds = GraphMask.hiddenIds(graph, setOf("decision"))
        val visibleIds = GraphMask.visibleNodes(graph, hiddenIds).map { it.id }.toSet()

        assertTrue(GraphMask.visibleLinks(links, hiddenIds).all { it.source in visibleIds && it.target in visibleIds })
    }

    @Test
    fun `an empty mask removes nothing at all`() {
        assertEquals(links, GraphMask.visibleLinks(links, emptySet()))
        assertEquals(graph, GraphMask.visibleNodes(graph, emptySet()))
    }

    @Test
    fun `degree counts every link that touches the node, on the whole graph`() {
        assertEquals(mapOf("a" to 2, "c" to 2, "d" to 2), GraphMask.degrees(links))
    }

    @Test
    fun `degree ignores a dangling link the parser already dropped`() {
        // The parser removes links to unknown nodes, so this is a guard, not a case: a degree map
        // that invented an entry for "ghost" would put a number on a node nobody can open.
        assertEquals(mapOf("a" to 1, "b" to 1), GraphMask.degrees(listOf(GraphLink("a", "b"))))
    }
}
