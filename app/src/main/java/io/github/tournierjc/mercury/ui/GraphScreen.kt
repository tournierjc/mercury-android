package io.github.tournierjc.mercury.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.tournierjc.mercury.MercuryApp
import io.github.tournierjc.mercury.R
import io.github.tournierjc.mercury.data.dto.GraphNode
import io.github.tournierjc.mercury.ui.graph.nodeBody
import io.github.tournierjc.mercury.ui.graph.NodeFacts
import io.github.tournierjc.mercury.ui.graph.factsOf
import io.github.tournierjc.mercury.data.dto.MemoryDto
import io.github.tournierjc.mercury.ui.graph.GraphLayout
import io.github.tournierjc.mercury.ui.graph.PlacedNode
import io.github.tournierjc.mercury.ui.theme.LocalMercuryColors
import io.github.tournierjc.mercury.vm.GraphUiState
import io.github.tournierjc.mercury.vm.GraphViewModel
import kotlin.math.roundToInt

/**
 * The brain's graph, drawn rather than listed: what the agent has tied together, at a glance.
 *
 * A text list of seven nodes and their edges is a table nobody reads. The daemon sends positions
 * only as sizes and colours, so the layout is computed here ([GraphLayout]), the canvas draws
 * links and nodes, and the phone does the rest: pinch to zoom, drag to pan, tap a node to read the
 * sentence it stands for.
 */
@Composable
fun GraphScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val container = remember { (context.applicationContext as MercuryApp).container }
    val viewModel: GraphViewModel = viewModel(factory = GraphViewModel.factory(container))
    val state by viewModel.state.collectAsState()

    // The node panel is the screen's inner depth: it closes before the screen does.
    BackHandler(enabled = state.detailId.isNotBlank()) { viewModel.clearSelection() }
    val colors = LocalMercuryColors.current

    // A node that vanished from a refresh must not stay in the panel.
    val shown = state.detailId.takeIf { it.isNotBlank() }?.let { id -> state.nodes.firstOrNull { it.id == id } }
    val drawn = visible(state.nodes, state.query)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.canvas)
            .safeDrawingPadding(),
    ) {
        ScreenHeader(
            title = stringResource(R.string.memory_graphe),
            subtitle = if (state.nodes.isEmpty()) {
                null
            } else {
                // With a search running, the header counts what is drawn — not the whole graph.
                val nodes = if (state.query.isBlank()) state.nodes else drawn
                stringResource(
                    R.string.brain_graphe_comptes,
                    pluralStringResource(R.plurals.brain_nb_noeuds, nodes.size, nodes.size),
                    pluralStringResource(R.plurals.brain_nb_liens, state.edges.size, state.edges.size),
                )
            },
            onBack = onBack,
            onRefresh = viewModel::refresh,
            refreshing = state.loading,
        )
        state.error?.let { notice -> NoticeBanner(notice, onDismiss = viewModel::clearBanner) }

        if (state.nodes.isEmpty()) {
            Text(
                text = when {
                    state.loading -> stringResource(R.string.common_loading)
                    !state.available -> stringResource(R.string.brain_graphe_indisponible)
                    else -> stringResource(R.string.brain_graphe_vide)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
            )
        } else {
            Text(
                text = stringResource(R.string.brain_graphe_aide),
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary,
                modifier = Modifier.padding(horizontal = 18.dp),
            )
            // The webapp narrows the drawing by a search field; so does the phone, and the layout
            // is recomputed on the few nodes left rather than dimming a hairball of them.
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::search,
                label = { Text(stringResource(R.string.brain_graphe_rechercher)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            )
            Legend(state.nodes)
            // The panel used to be a sibling, so opening it shrank the canvas and the layout was
            // recomputed under the finger that had just picked a node. It floats now.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                GraphCanvas(
                    state = state.copy(nodes = drawn, edges = state.edges),
                    // A tap on empty canvas closes the panel; a tap on a node opens its memory.
                    onSelect = { node ->
                        if (node == null) viewModel.clearSelection() else viewModel.selectNode(node.id)
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                shown?.let { node ->
                    NodePanel(
                        node = node,
                        memory = state.detailMemory,
                        connections = state.edges.count { it.source == node.id || it.target == node.id },
                        loading = state.detailLoading,
                        failed = state.detailFailed,
                        onClose = viewModel::clearSelection,
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }
        }
    }
}

/**
 * The drawing surface: one [Canvas] for links and nodes, the labels of the biggest few as text
 * over it, and a scale/translate layer over both so a pinch enlarges the labels as well.
 */
@Composable
private fun GraphCanvas(
    state: GraphUiState,
    onSelect: (GraphNode?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalMercuryColors.current
    val density = LocalDensity.current

    var scale by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var canvas by remember { mutableStateOf(IntSize.Zero) }

    val placed = remember(state.nodes, state.edges, canvas, density) {
        GraphLayout.layout(
            nodeIds = state.nodes.map { it.id },
            edges = state.edges,
            width = canvas.width.toFloat(),
            height = canvas.height.toFloat(),
            // Centres stay at least a node's width plus air apart, so two memories never draw as
            // one blob when the graph is small.
            minSpacing = with(density) { NODE_SPACING.toPx() },
        )
    }
    val byId = remember(state.nodes) { state.nodes.associateBy { it.id } }
    // Radius in pixels, from the server's own size, clamped so a huge memory is still a dot and a
    // negligible one is still touchable.
    val radii = remember(state.nodes, density) {
        state.nodes.associate { node ->
            node.id to with(density) { node.size.toFloat().coerceIn(MIN_RADIUS, MAX_RADIUS).dp.toPx() }
        }
    }
    // Only the most important few get words, and how few depends on the width: eight 19-character
    // labels never fit a 360 dp phone, five do. The room is measured, not assumed.
    val labelRoom = with(density) { (canvas.width / labelWidth.toPx()).toInt().coerceIn(2, MAX_LABELS) }
    val labelled = remember(state.nodes, labelRoom) {
        state.nodes.sortedByDescending { it.importance }.take(labelRoom).map { it.id }.toHashSet()
    }
    val touchSlop = with(density) { TOUCH_SLOP.toPx() }

    Box(
        modifier = modifier
            // A zoomed node must never spill over the header above the canvas.
            .clipToBounds()
            .onSizeChanged { canvas = it }
            // Outside the layer: the gesture sees screen pixels and the pan/zoom maths below is
            // the one the layer applies, so a tap lands on the node it was aimed at at any scale.
            .pointerInput(Unit) {
                detectTransformGestures { centroid, panChange, zoom, _ ->
                    val next = (scale * zoom).coerceIn(1f, MAX_SCALE)
                    val anchor = Offset((centroid.x - pan.x) / scale, (centroid.y - pan.y) / scale)
                    val moved = Offset(centroid.x - anchor.x * next, centroid.y - anchor.y * next) + panChange
                    scale = next
                    pan = clampPan(moved, next, canvas)
                }
            }
            .pointerInput(state.nodes, placed) {
                detectTapGestures { tap ->
                    // Nearest node inside its own radius, not the first one found: two overlapping
                    // circles are one tap, and the finger meant the closer of the two.
                    val hit = placed
                        .mapNotNull { point ->
                            val radius = radii[point.id] ?: return@mapNotNull null
                            val dx = tap.x - (point.x * scale + pan.x)
                            val dy = tap.y - (point.y * scale + pan.y)
                            val distance = kotlin.math.hypot(dx, dy)
                            if (distance <= radius + touchSlop) point.id to distance else null
                        }
                        .minByOrNull { it.second }
                        ?.first
                    onSelect(hit?.let { byId[it] })
                }
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = pan.x
                    translationY = pan.y
                    transformOrigin = TransformOrigin(0f, 0f)
                },
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val points = HashMap<String, PlacedNode>(placed.size * 2)
                for (point in placed) points[point.id] = point

                for (edge in state.edges) {
                    val from = points[edge.source] ?: continue
                    val to = points[edge.target] ?: continue
                    drawLine(
                        color = colors.graphEdge,
                        start = Offset(from.x, from.y),
                        end = Offset(to.x, to.y),
                        strokeWidth = HAIRLINE.toPx(),
                    )
                }
                for (point in placed) {
                    val radius = radii[point.id] ?: continue
                    drawCircle(
                        color = nodeColor(byId[point.id]?.color, colors.accent),
                        radius = radius,
                        center = Offset(point.x, point.y),
                    )
                }
            }

            for (point in placed) {
                if (point.id !in labelled) continue
                Text(
                    text = byId[point.id]?.label.orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .offset {
                            val radius = radii[point.id] ?: 0f
                            IntOffset(
                                (point.x - labelWidth.toPx() / 2f).roundToInt(),
                                (point.y + radius + LABEL_GAP.toPx()).roundToInt(),
                            )
                        }
                        .width(labelWidth),
                )
            }
        }
    }
}

/** The sentence behind a dot, at the bottom, where a thumb can read it. */
/**
 * The nodes whose label holds the query, and only the links between them. Empty query, untouched
 * graph; a query that matches nothing leaves the empty state to say so.
 */
internal fun visible(nodes: List<GraphNode>, query: String): List<GraphNode> {
    val needle = query.trim()
    if (needle.isBlank()) return nodes
    return nodes.filter { node ->
        node.fullLabel.contains(needle, ignoreCase = true) || node.label.contains(needle, ignoreCase = true)
    }
}

/** One chip per memory type drawn, in its colour: the graph's colours are not self-evident. */
@Composable
private fun Legend(nodes: List<GraphNode>) {
    val colors = LocalMercuryColors.current
    val types = nodes.map { it.type }.filter { it.isNotBlank() }.distinct().take(6)
    if (types.isEmpty()) return
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 4.dp),
    ) {
        types.forEach { type ->
            val sample = nodes.firstOrNull { it.type == type }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .background(nodeColor(sample?.color, colors.accent), CircleShape),
                )
                Text(
                    text = rawLabel(type),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun NodePanel(
    node: GraphNode,
    memory: MemoryDto?,
    connections: Int,
    loading: Boolean,
    failed: Boolean,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalMercuryColors.current
    Row(
        verticalAlignment = Alignment.Top,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .background(colors.surface, MaterialTheme.shapes.extraSmall)
            .padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = node.fullLabel.ifBlank { node.label },
                style = MaterialTheme.typography.bodyLarge,
                color = colors.textPrimary,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
            // The kind alone: the weight has its own row in the facts grid below, and printing the
            // same number in two places is the kind of duplication this panel was just rid of.
            Text(
                text = rawLabel(node.type),
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary,
                modifier = Modifier.padding(top = 4.dp),
            )
            when {
                loading -> Text(
                    text = stringResource(R.string.common_loading),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(top = 8.dp),
                )

                failed -> Text(
                    text = stringResource(R.string.brain_noeud_illisible),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(top = 8.dp),
                )

                memory == null -> Unit

                else -> {
                    // The title already is the summary on this fleet, so the body only prints what
                    // the title does not say: otherwise the sentence appeared twice.
                    nodeBody(node.fullLabel.ifBlank { node.label }, memory.summary, memory.detail)
                        .forEachIndexed { index, line ->
                            Text(
                                text = line,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (index == 0) colors.textPrimary else colors.textSecondary,
                                modifier = Modifier.padding(top = if (index == 0) 8.dp else 4.dp),
                            )
                        }
                    NodeFactsGrid(
                        facts = factsOf(memory, connections),
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
            }
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.Top) {
            IconButton(onClick = onClose, modifier = Modifier.size(38.dp)) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.brain_noeud_fermer),
                    tint = colors.textSecondary,
                    modifier = Modifier.size(19.dp),
                )
            }
        }
    }
}

/**
 * The facts the web app shows for a node, in the same order: the phone was showing the sentence and
 * a weight while the browser showed scope, evidence, source and the dates.
 *
 * Rows with nothing to say are left out rather than filled with zeros, and the block scrolls: a
 * memory with every field set is taller than the strip the panel may take over the canvas.
 */
@Composable
private fun NodeFactsGrid(facts: NodeFacts, modifier: Modifier = Modifier) {
    val colors = LocalMercuryColors.current
    val rows = buildList<Pair<Int, String>> {
        facts.scope?.let { add(R.string.graph_meta_scope to it) }
        facts.importancePercent?.let { add(R.string.graph_meta_importance to stringResource(R.string.graph_meta_percent, it)) }
        facts.confidencePercent?.let { add(R.string.graph_meta_confidence to stringResource(R.string.graph_meta_percent, it)) }
        facts.evidence?.let { add(R.string.graph_meta_evidence to it.toString()) }
        add(R.string.graph_meta_connections to facts.connections.toString())
        facts.source?.let { add(R.string.graph_meta_source to it) }
        formatStamp(facts.created)?.let { add(R.string.graph_meta_created to it) }
        formatStamp(facts.updated)?.let { add(R.string.graph_meta_updated to it) }
        formatStamp(facts.lastSeen)?.let { add(R.string.graph_meta_seen to it) }
        add(R.string.graph_meta_id to facts.id)
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 200.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        rows.forEach { (label, value) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
            ) {
                Text(
                    text = stringResource(label),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 10.dp),
                )
            }
        }
    }
}

/**
 * Keeps the zoomed drawing over the surface: at scale 1 it cannot move, and past 1 its edges may
 * not come inside the frame — a graph half off-screen with nothing to pull it back reads as a bug.
 */
private fun clampPan(pan: Offset, scale: Float, size: IntSize): Offset {
    val minX = size.width - size.width * scale
    val minY = size.height - size.height * scale
    return Offset(
        x = pan.x.coerceIn(minOf(minX, 0f), 0f),
        y = pan.y.coerceIn(minOf(minY, 0f), 0f),
    )
}

/**
 * The server's own colour (`#febc2e`), or the accent when the field is missing or unreadable. This
 * is the one colour literal the screen is allowed: it comes from the data, not from the design.
 */
private fun nodeColor(hex: String?, fallback: Color): Color {
    val digits = hex?.trim()?.removePrefix("#") ?: return fallback
    val value = digits.toLongOrNull(16) ?: return fallback
    val parsed = when (digits.length) {
        6 -> Color(0xFF000000L or value)
        8 -> Color(value)
        else -> return fallback
    }
    // The colour comes from the server, and a `#333333` belief is 1,7:1 on a black canvas: an
    // invisible node. Lift the dark ones towards white until they read.
    val luminance = 0.2126 * parsed.red + 0.7152 * parsed.green + 0.0722 * parsed.blue
    return if (luminance >= MIN_LUMINANCE) parsed else lerp(parsed, Color.White, 0.45f)
}

private fun lerp(from: Color, to: Color, t: Float): Color = Color(
    red = from.red + (to.red - from.red) * t,
    green = from.green + (to.green - from.green) * t,
    blue = from.blue + (to.blue - from.blue) * t,
    alpha = from.alpha,
)

private const val MIN_RADIUS = 4f
private const val MAX_RADIUS = 16f
private const val MAX_SCALE = 6f
private const val MAX_LABELS = 8
/** Below this relative luminance a node colour is not visible on either canvas. */
private const val MIN_LUMINANCE = 0.22
private val NODE_SPACING = 40.dp
private val TOUCH_SLOP = 12.dp
private val HAIRLINE = 1.dp
private val LABEL_GAP = 3.dp
private val labelWidth = 108.dp
