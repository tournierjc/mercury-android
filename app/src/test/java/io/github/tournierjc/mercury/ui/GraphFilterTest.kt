package io.github.tournierjc.mercury.ui

import io.github.tournierjc.mercury.data.dto.GraphNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The graph's search field narrows what is drawn, on the label the node actually carries. */
class GraphFilterTest {

    private val nodes = listOf(
        GraphNode(id = "a", label = "Camille", fullLabel = "Camille, sœur, Lyon"),
        GraphNode(id = "b", label = "Marché", fullLabel = "Le marché du samedi"),
        GraphNode(id = "c", label = "Objectif", fullLabel = "Objectif : déménager"),
    )

    @Test
    fun `a blank query leaves the graph untouched`() {
        assertEquals(nodes, visible(nodes, ""))
        assertEquals(nodes, visible(nodes, "   "))
    }

    @Test
    fun `the query matches the short and the full label, whatever the case`() {
        assertEquals(listOf("a"), visible(nodes, "camille").map { it.id })
        assertEquals(listOf("a"), visible(nodes, "LYON").map { it.id })
        assertEquals(listOf("b"), visible(nodes, "samedi").map { it.id })
    }

    @Test
    fun `a query that matches nothing draws nothing`() {
        assertTrue(visible(nodes, "zzz").isEmpty())
    }
}
