package io.github.tournierjc.mercury.graph

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two rules the webapp drew on its canvas — a query that dims what does not match, a selected
 * node that dims its non-neighbours — plus the legend and the connection count the phone has to put
 * in words. Values below are the kind the brain really sends (`#3b82f6`, accented French labels),
 * so an assertion that passes here is not passing on a default.
 */
class GraphFocusTest {

    private fun node(
        id: String,
        label: String,
        type: String = "fact",
        color: String = "#3b82f6",
        fullLabel: String = label,
    ) = FocusNode(id = id, label = label, fullLabel = fullLabel, type = type, color = color)

    private val links = listOf(
        GraphLink("a", "b"),
        GraphLink("b", "c"),
    )

    @Test
    fun anAccentFreeQueryFindsTheAccentedLabel() {
        val nodes = listOf(
            node("a", "Préférence : répondre en français"),
            node("b", "Le daemon écoute sur le port 8787"),
        )

        val matches = GraphFocus.matchingIds(nodes, "preference")

        assertEquals(setOf("a"), matches)
        assertTrue("a typed query is a filter", GraphFocus.of(nodes, links, "preference").filtering)
    }

    @Test
    fun aQueryFadesEveryNodeThatDoesNotMatch() {
        val nodes = listOf(
            node("a", "Préférence : répondre en français"),
            node("b", "Le daemon écoute sur le port 8787"),
        )

        val focus = GraphFocus.of(nodes, links, "français")

        assertEquals(NodeEmphasis.FULL, focus.emphasisOf("a"))
        assertEquals(NodeEmphasis.FAINT, focus.emphasisOf("b"))
    }

    @Test
    fun theFullLabelMatchesEvenWhenTheShortLabelDoesNot() {
        val nodes = listOf(
            node("a", "daemon", fullLabel = "Le daemon Mercury écoute sur le port 8787"),
        )

        assertEquals(setOf("a"), GraphFocus.matchingIds(nodes, "8787"))
    }

    @Test
    fun aSelectedNodeLightsItsNeighboursAndShadesTheRest() {
        val nodes = listOf(node("a", "un"), node("b", "deux"), node("c", "trois"), node("d", "quatre"))

        val focus = GraphFocus.of(nodes, links, query = "", focusId = "b")

        assertEquals(NodeEmphasis.FULL, focus.emphasisOf("b"))
        assertEquals(setOf("a", "c"), focus.neighbours)
        assertEquals(NodeEmphasis.FULL, focus.emphasisOf("a"))
        assertEquals(NodeEmphasis.FULL, focus.emphasisOf("c"))
        assertEquals(NodeEmphasis.DIMMED, focus.emphasisOf("d"))
        assertFalse(focus.filtering)
    }

    @Test
    fun theQueryWinsOverTheSelection() {
        val nodes = listOf(node("a", "Préférence"), node("b", "deux"))
        val focus = GraphFocus.of(nodes, links, query = "deux", focusId = "a")

        // `a` is the selected node and a direct neighbour of itself, but the query does not match it.
        assertEquals(NodeEmphasis.FAINT, focus.emphasisOf("a"))
        assertEquals(NodeEmphasis.FULL, focus.emphasisOf("b"))
    }

    @Test
    fun withNeitherQueryNorSelectionEveryNodeIsFull() {
        val focus = GraphFocus.of(listOf(node("a", "un"), node("b", "deux")), links, query = "   ")

        assertFalse("spaces are not a query", focus.filtering)
        assertEquals(NodeEmphasis.FULL, focus.emphasisOf("a"))
        assertEquals(NodeEmphasis.FULL, focus.emphasisOf("b"))
        assertTrue(focus.matches.isEmpty())
    }

    @Test
    fun connectionsCountBothEndsAndTheSelectedNodeAgain() {
        assertEquals(2, GraphFocus.connections(links, "b"))
        assertEquals(1, GraphFocus.connections(links, "a"))
        assertEquals(0, GraphFocus.connections(links, "zzz"))
    }

    @Test
    fun aSelfLinkIsOneConnectionNotTwo() {
        assertEquals(1, GraphFocus.connections(listOf(GraphLink("a", "a")), "a"))
    }

    @Test
    fun theLegendListsTheTypesPresentWithTheServersColours() {
        val nodes = listOf(
            node("a", "un", type = "fact", color = "#3b82f6"),
            node("b", "deux", type = "preference", color = "#a78bfa"),
            node("c", "trois", type = "fact", color = "#3b82f6"),
            node("d", "quatre", type = "  ", color = "#000000"),
        )

        assertEquals(
            listOf(
                LegendEntry("fact", "#3b82f6"),
                LegendEntry("preference", "#a78bfa"),
            ),
            GraphFocus.legend(nodes),
        )
    }

    @Test
    fun aTypeWhoseFirstNodeHadNoColourTakesTheNextOne() {
        val nodes = listOf(
            node("a", "un", type = "goal", color = ""),
            node("b", "deux", type = "goal", color = "#f59e0b"),
        )

        assertEquals(listOf(LegendEntry("goal", "#f59e0b")), GraphFocus.legend(nodes))
    }

    @Test
    fun foldingKeepsLettersAndDropsOnlyTheAccents() {
        assertEquals("preference : repondre en francais", GraphFocus.fold("  Préférence : répondre en français "))
    }
}
