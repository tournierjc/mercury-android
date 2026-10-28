package io.github.tournierjc.mercury.ui.graph

import kotlin.math.hypot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The graph screen's placement, without a screen.
 *
 * Three properties the drawing depends on. It is reproducible: a node that moves between two
 * replies has to be a change on the server, not in the maths. It never returns a NaN or a point
 * outside the surface: a node drawn off the canvas reads as a memory that vanished. And a link
 * means proximity: the whole point of the picture is that a tie is visible as closeness, so if
 * linked pairs end up no nearer than unlinked ones the screen is drawing noise.
 */
class GraphLayoutTest {

    private val nodes = listOf(
        "identity",
        "preference",
        "goal",
        "project",
        "habit",
        "decision",
        "episode",
        "reflection",
        "constraint",
        "relationship",
    )

    private val edges = listOf(
        GraphEdge("identity", "goal"),
        GraphEdge("goal", "project"),
        GraphEdge("goal", "habit"),
        GraphEdge("preference", "habit"),
        GraphEdge("decision", "episode"),
        GraphEdge("episode", "reflection"),
        GraphEdge("constraint", "goal"),
    )

    private val width = 1080f
    private val height = 1800f

    @Test
    fun `the same graph lands in the same place twice`() {
        val first = GraphLayout.layout(nodes, edges, width, height)
        val second = GraphLayout.layout(nodes, edges, width, height)

        assertEquals("a node went missing between two runs", first.size, second.size)
        assertEquals("the ids are not in the order they were given", nodes, first.map { it.id })
        first.zip(second).forEach { (a, b) ->
            assertEquals("x of ${a.id} moved", a.x, b.x, 0f)
            assertEquals("y of ${a.id} moved", a.y, b.y, 0f)
        }
    }

    @Test
    fun `the ids handed over in another order still land inside the surface`() {
        // The seed is per id but the starting circle is per index, so another order is another
        // drawing. What must hold either way is that nothing escapes the canvas or comes back NaN.
        val reversed = GraphLayout.layout(nodes.reversed(), edges, width, height).associateBy { it.id }
        val direct = GraphLayout.layout(nodes, edges, width, height).associateBy { it.id }

        assertTrue("the two orders placed nothing", reversed.isNotEmpty())
        reversed.values.forEach { point ->
            assertTrue("${point.id} is not finite", point.x.isFinite() && point.y.isFinite())
            assertTrue("${point.id} sits at ${point.x},${point.y}", point.x in 0f..width && point.y in 0f..height)
        }
        assertEquals(nodes.size, direct.size)
    }

    @Test
    fun `every coordinate is a finite number inside the surface`() {
        val placed = GraphLayout.layout(nodes, edges, width, height)

        assertEquals(nodes.size, placed.size)
        placed.forEach { point ->
            assertTrue("${point.id} has a NaN coordinate", point.x.isFinite() && point.y.isFinite())
            assertTrue(
                "${point.id} at ${point.x},${point.y} is outside ${width}x$height",
                point.x in 0f..width && point.y in 0f..height,
            )
            // Not just inside: inside the air the layout promises, so a circle is never cut.
            assertTrue("${point.id} touches the frame", point.x >= GraphLayout.PADDING - 0.01f)
            assertTrue("${point.id} touches the frame", point.x <= width - GraphLayout.PADDING + 0.01f)
            assertTrue("${point.id} touches the frame", point.y >= GraphLayout.PADDING - 0.01f)
            assertTrue("${point.id} touches the frame", point.y <= height - GraphLayout.PADDING + 0.01f)
        }
    }

    @Test
    fun `linked nodes end closer than unlinked ones on average`() {
        val placed = GraphLayout.layout(nodes, edges, width, height).associateBy { it.id }
        val linked = HashSet<Pair<String, String>>()
        edges.forEach { edge ->
            linked.add(edge.source to edge.target)
            linked.add(edge.target to edge.source)
        }

        val linkedDistances = mutableListOf<Double>()
        val apartDistances = mutableListOf<Double>()
        for (i in nodes.indices) {
            for (j in i + 1 until nodes.size) {
                val a = placed.getValue(nodes[i])
                val b = placed.getValue(nodes[j])
                val distance = hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble())
                if ((nodes[i] to nodes[j]) in linked) linkedDistances.add(distance) else apartDistances.add(distance)
            }
        }

        assertTrue("no link was measured", linkedDistances.isNotEmpty())
        assertTrue("no unlinked pair was measured", apartDistances.isNotEmpty())
        val linkedAverage = linkedDistances.average()
        val apartAverage = apartDistances.average()
        assertTrue(
            "linked pairs average $linkedAverage, unlinked $apartAverage — the drawing would show no tie",
            linkedAverage < apartAverage,
        )
    }

    @Test
    fun `asking for a spacing pushes the closest pair further apart`() {
        val loose = GraphLayout.layout(nodes, edges, width, height)
        val spaced = GraphLayout.layout(nodes, edges, width, height, minSpacing = 120f)

        val closestLoose = closest(loose)
        val closestSpaced = closest(spaced)
        assertTrue("the spacing pass left the closest pair at $closestSpaced", closestSpaced > closestLoose)
        spaced.forEach { point ->
            assertTrue("${point.id} left the surface", point.x in 0f..width && point.y in 0f..height)
        }
    }

    @Test
    fun `a lone node sits in the middle of the surface`() {
        val placed = GraphLayout.layout(listOf("identity"), emptyList(), width, height)

        assertEquals(1, placed.size)
        assertEquals(width / 2f, placed[0].x, 0.01f)
        assertEquals(height / 2f, placed[0].y, 0.01f)
    }

    @Test
    fun `an empty graph places nothing`() {
        assertTrue(GraphLayout.layout(emptyList(), emptyList(), width, height).isEmpty())
    }

    @Test
    fun `an edge naming a node that is not in the list is ignored`() {
        val placed = GraphLayout.layout(
            nodeIds = listOf("a", "b"),
            edges = listOf(GraphEdge("a", "ghost"), GraphEdge("ghost", "b")),
            width = width,
            height = height,
        )

        assertEquals(2, placed.size)
        placed.forEach { point ->
            assertTrue("${point.id} is not finite", point.x.isFinite() && point.y.isFinite())
        }
    }

    @Test
    fun `a surface with no size still yields finite points`() {
        val placed = GraphLayout.layout(nodes, edges, 0f, 0f)

        assertEquals(nodes.size, placed.size)
        placed.forEach { point ->
            assertTrue("${point.id} has a NaN coordinate", point.x.isFinite() && point.y.isFinite())
        }
    }

    private fun closest(placed: List<PlacedNode>): Double {
        var smallest = Double.MAX_VALUE
        for (i in placed.indices) {
            for (j in i + 1 until placed.size) {
                val distance = hypot(
                    (placed[i].x - placed[j].x).toDouble(),
                    (placed[i].y - placed[j].y).toDouble(),
                )
                if (distance < smallest) smallest = distance
            }
        }
        return smallest
    }
}
