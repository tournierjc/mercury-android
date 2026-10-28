package io.github.tournierjc.mercury.ui.graph

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * One link of the brain graph, by node id. The payload names its two ends `source` and `target`;
 * an edge whose ends are not both in the node list draws nothing, so the drawing code never has to
 * carry a dangling id.
 */
data class GraphEdge(val source: String, val target: String)

/** A node placed on the surface, in pixels of that surface. */
data class PlacedNode(val id: String, val x: Float, val y: Float)

/**
 * Where the nodes of the brain graph go, without Compose and without a `Random`.
 *
 * The layout is Fruchterman-Reingold: every pair of nodes pushes the other away (`k² / d`), every
 * link pulls its two ends together (`d² / k`), a weak gravity keeps a lone cluster from drifting,
 * and a cooling step caps how far a node may move per iteration so the last pass settles instead
 * of oscillating. Nodes start on a circle, in the order the server sent them, each nudged by an
 * angle derived from its own id — so the same graph lands in the same place twice, and a node that
 * moves between replies is the server's change, not the layout's.
 *
 * The forces are computed in an arbitrary unit space (`k = 1`) and the result is scaled into the
 * surface at the end: clamping every node to the canvas *during* the simulation is what pins a
 * graph to its own border, which reads as a broken drawing rather than as a layout.
 *
 * Pure on purpose: [GraphLayoutTest] runs it on the JVM.
 */
object GraphLayout {

    /** Enough passes for ~40 nodes to settle; beyond that the boundary never moves again. */
    const val ITERATIONS: Int = 300

    /** Air between the graph and the edge of the surface, so a node is never cut by the frame. */
    const val PADDING: Float = 24f

    /** Past this many nodes the repulsion pass is the cost of the screen, so it is shortened. */
    private const val DENSE_GRAPH = 400

    /** Bounded, so a graph too dense for its canvas cannot spin at full cost. */
    private const val SPACING_PASSES = 40

    private const val START_TEMPERATURE = 0.6

    private const val END_TEMPERATURE = 0.02

    /** Weak pull to the origin: without it, a graph in two clusters drifts apart forever. */
    private const val GRAVITY = 0.02

    /**
     * Places [nodeIds] on a [width] × [height] surface. Every returned point lies inside the
     * surface, inset by [padding] (rounded down when the surface is smaller than two paddings).
     *
     * [minSpacing] is the smallest distance the caller is willing to see between two centres, in
     * the same pixels — the radius of a drawn node, plus air. At zero the simulation's own spacing
     * is used; a caller that draws circles passes its diameter.
     */
    fun layout(
        nodeIds: List<String>,
        edges: List<GraphEdge>,
        width: Float,
        height: Float,
        iterations: Int = ITERATIONS,
        padding: Float = PADDING,
        minSpacing: Float = 0f,
    ): List<PlacedNode> {
        val n = nodeIds.size
        if (n == 0) return emptyList()

        val w = if (width.isFinite() && width > 0f) width.toDouble() else 1.0
        val h = if (height.isFinite() && height > 0f) height.toDouble() else 1.0
        val pad = padding.coerceAtLeast(0f).toDouble().coerceAtMost(min(w, h) / 2.0)

        val xs = DoubleArray(n)
        val ys = DoubleArray(n)
        // Seeded on a circle, in server order, each turned by a fraction of a slot read from its id.
        for (i in 0 until n) {
            val slot = if (n == 1) 0.0 else 2.0 * PI * i / n
            val angle = slot + seedOf(nodeIds[i]) * 2.0 * PI / max(n, 1)
            xs[i] = cos(angle)
            ys[i] = sin(angle)
        }

        // First index wins: a duplicated id is one node, not two that repel each other at zero
        // distance (which is exactly the division the guard below would have to absorb).
        val index = HashMap<String, Int>(n * 2)
        nodeIds.forEachIndexed { i, id -> if (!index.containsKey(id)) index[id] = i }
        val springs = ArrayList<Pair<Int, Int>>(edges.size)
        edges.forEach { edge ->
            val a = index[edge.source]
            val b = index[edge.target]
            if (a != null && b != null && a != b) springs.add(a to b)
        }

        if (n == 1) return listOf(PlacedNode(nodeIds[0], (w / 2f).toFloat(), (h / 2f).toFloat()))

        val steps = if (n <= DENSE_GRAPH) iterations.coerceAtLeast(1) else min(iterations, 60).coerceAtLeast(1)
        val dx = DoubleArray(n)
        val dy = DoubleArray(n)

        for (step in 0 until steps) {
            dx.fill(0.0)
            dy.fill(0.0)

            // Repulsion, every pair once.
            for (i in 0 until n) {
                for (j in i + 1 until n) {
                    var vx = xs[i] - xs[j]
                    var vy = ys[i] - ys[j]
                    var d2 = vx * vx + vy * vy
                    if (d2 < 1e-6) {
                        // Two seeds on the same point: a fixed, tiny offset, never a random one.
                        vx = 0.01 * (1 + i)
                        vy = 0.01 * (1 + j)
                        d2 = vx * vx + vy * vy
                    }
                    val d = sqrt(d2)
                    val force = 1.0 / d
                    val ux = vx / d
                    val uy = vy / d
                    dx[i] += ux * force
                    dy[i] += uy * force
                    dx[j] -= ux * force
                    dy[j] -= uy * force
                }
            }

            // Attraction along the links.
            springs.forEach { (a, b) ->
                var vx = xs[a] - xs[b]
                var vy = ys[a] - ys[b]
                var d2 = vx * vx + vy * vy
                if (d2 < 1e-6) {
                    vx = 0.01
                    vy = 0.01
                    d2 = vx * vx + vy * vy
                }
                val d = sqrt(d2)
                val force = d2
                val ux = vx / d
                val uy = vy / d
                dx[a] -= ux * force
                dy[a] -= uy * force
                dx[b] += ux * force
                dy[b] += uy * force
            }

            // Gravity, so a graph made of several clusters stays one drawing.
            for (i in 0 until n) {
                dx[i] -= xs[i] * GRAVITY
                dy[i] -= ys[i] * GRAVITY
            }

            val temperature = START_TEMPERATURE +
                (END_TEMPERATURE - START_TEMPERATURE) * step.toDouble() / (steps - 1).coerceAtLeast(1)
            for (i in 0 until n) {
                val d = hypot(dx[i], dy[i])
                if (d > 1e-9) {
                    val stepLength = min(d, temperature)
                    xs[i] += dx[i] / d * stepLength
                    ys[i] += dy[i] / d * stepLength
                }
            }
        }

        val fitted = fit(
            nodeIds = nodeIds,
            xs = xs,
            ys = ys,
            width = w,
            height = h,
            padding = pad,
        )
        if (minSpacing <= 0f || n < 2) return fitted
        return spread(fitted, minSpacing.toDouble(), w, h, pad)
    }

    /**
     * After the fit, pushes overlapping centres apart until the caller's spacing is met, then
     * puts them back inside the surface. The forces above spread a *cluster* well and an isolated
     * node far from it; scaled to one canvas, the cluster can end up narrower than a drawn circle,
     * and two memories become one blob. Deterministic, like everything else here: the pair order
     * and the push direction are fixed.
     */
    private fun spread(
        nodes: List<PlacedNode>,
        spacing: Double,
        width: Double,
        height: Double,
        padding: Double,
    ): List<PlacedNode> {
        val n = nodes.size
        val xs = DoubleArray(n) { nodes[it].x.toDouble() }
        val ys = DoubleArray(n) { nodes[it].y.toDouble() }
        val lowX = padding
        val highX = max(width - padding, padding)
        val lowY = padding
        val highY = max(height - padding, padding)

        var pass = 0
        while (pass < SPACING_PASSES) {
            var moved = false
            for (i in 0 until n) {
                for (j in i + 1 until n) {
                    var vx = xs[i] - xs[j]
                    var vy = ys[i] - ys[j]
                    var d = hypot(vx, vy)
                    if (d >= spacing) continue
                    if (d < 1e-6) {
                        vx = 0.5
                        vy = 0.5
                        d = hypot(vx, vy)
                    }
                    val push = (spacing - d) / 2.0
                    val ux = vx / d
                    val uy = vy / d
                    xs[i] += ux * push
                    ys[i] += uy * push
                    xs[j] -= ux * push
                    ys[j] -= uy * push
                    moved = true
                }
            }
            for (i in 0 until n) {
                xs[i] = xs[i].coerceIn(lowX, highX)
                ys[i] = ys[i].coerceIn(lowY, highY)
            }
            if (!moved) break
            pass++
        }
        return nodes.mapIndexed { i, node -> PlacedNode(node.id, xs[i].toFloat(), ys[i].toFloat()) }
    }

    /**
     * Scales the settled points into the surface, keeping their proportions: the layout's own
     * scale is arbitrary, the drawing area is not.
     */
    private fun fit(
        nodeIds: List<String>,
        xs: DoubleArray,
        ys: DoubleArray,
        width: Double,
        height: Double,
        padding: Double,
    ): List<PlacedNode> {
        var minX = xs[0]
        var maxX = xs[0]
        var minY = ys[0]
        var maxY = ys[0]
        for (i in 1 until xs.size) {
            minX = min(minX, xs[i])
            maxX = max(maxX, xs[i])
            minY = min(minY, ys[i])
            maxY = max(maxY, ys[i])
        }
        val spanX = max(maxX - minX, 1e-6)
        val spanY = max(maxY - minY, 1e-6)
        val availableWidth = max(width - 2 * padding, 1.0)
        val availableHeight = max(height - 2 * padding, 1.0)
        val scale = min(availableWidth / spanX, availableHeight / spanY)
        val offsetX = (width - spanX * scale) / 2.0 - minX * scale
        val offsetY = (height - spanY * scale) / 2.0 - minY * scale

        val lowX = padding
        val highX = max(width - padding, padding)
        val lowY = padding
        val highY = max(height - padding, padding)
        return nodeIds.mapIndexed { i, id ->
            PlacedNode(
                id = id,
                x = (xs[i] * scale + offsetX).coerceIn(lowX, highX).toFloat(),
                y = (ys[i] * scale + offsetY).coerceIn(lowY, highY).toFloat(),
            )
        }
    }

    /**
     * A number in `[0, 1)` read from the id, used to turn each node a little off its slot on the
     * starting circle. `String.hashCode` is spelled out rather than called so the seed cannot
     * change with the Kotlin version.
     */
    private fun seedOf(id: String): Double {
        var hash = 0
        for (char in id) {
            hash = hash * 31 + char.code
        }
        val positive = hash.toLong() and 0xFFFFFFFFL
        return (positive % 1000L).toDouble() / 1000.0
    }
}
