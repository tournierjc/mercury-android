package io.github.tournierjc.mercury.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.tournierjc.mercury.MercuryApp
import io.github.tournierjc.mercury.R
import io.github.tournierjc.mercury.data.BotOps
import io.github.tournierjc.mercury.data.dto.GraphNode
import io.github.tournierjc.mercury.data.dto.MemoryDto
import io.github.tournierjc.mercury.data.dto.Notice
import io.github.tournierjc.mercury.graph.FocusNode
import io.github.tournierjc.mercury.graph.GraphFocus
import io.github.tournierjc.mercury.graph.GraphLink
import io.github.tournierjc.mercury.graph.LegendEntry
import io.github.tournierjc.mercury.graph.NodeEmphasis
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
    val colors = LocalMercuryColors.current

    var selected by remember { mutableStateOf<GraphNode?>(null) }
    var query by rememberSaveable { mutableStateOf("") }

    // A node that vanished from a refresh must not stay in the panel.
    val shown = selected?.let { picked -> state.nodes.firstOrNull { it.id == picked.id } }

    // The system back closes the panel before it leaves the screen: the header arrow is not the
    // only way out of a memory, and a back press that jumps to the previous screen loses the graph.
    BackHandler(enabled = shown != null) {
        selected = null
        viewModel.clearDetail()
    }

    // Which node stays lit and which fades is decided in :core (GraphFocus), not here: the canvas
    // only applies the shade, so the rule is testable without Android.
    val nodes = remember(state.nodes) {
        state.nodes.map { FocusNode(it.id, it.label, it.fullLabel, it.type, it.color) }
    }
    val links = remember(state.edges) { state.edges.map { GraphLink(it.source, it.target) } }
    val legend = remember(nodes) { GraphFocus.legend(nodes) }
    val focus = remember(nodes, links, query, shown?.id) {
        GraphFocus.of(nodes = nodes, links = links, query = query, focusId = shown?.id)
    }

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
                stringResource(
                    R.string.brain_graphe_comptes,
                    pluralStringResource(R.plurals.brain_nb_noeuds, state.nodes.size, state.nodes.size),
                    pluralStringResource(R.plurals.brain_nb_liens, state.edges.size, state.edges.size),
                )
            },
            onBack = onBack,
            onRefresh = viewModel::refresh,
        )
        state.error?.let { notice -> NoticeBanner(notice, onDismiss = viewModel::clearBanner) }

        if (state.nodes.isEmpty()) {
            when {
                state.loading -> Text(
                    text = stringResource(R.string.common_loading),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                )
                // An unexposed graph is not an empty brain: the route said `available: false`, and
                // that answer carries a technical motif and deserves a retry of its own.
                !state.available -> GraphUnavailable(
                    motif = state.unavailableMotif,
                    onRetry = viewModel::refresh,
                )
                else -> Text(
                    text = stringResource(R.string.brain_graphe_vide),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                )
            }
        } else {
            Text(
                text = stringResource(R.string.brain_graphe_aide),
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary,
                modifier = Modifier.padding(horizontal = 18.dp),
            )
            GraphFilter(query = query, onQueryChange = { query = it }, legend = legend)
            if (focus.filtering && focus.matches.isEmpty()) {
                Text(
                    text = stringResource(R.string.brain_graphe_aucun_noeud),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 2.dp),
                )
            }
            // The panel used to be a sibling, so opening it shrank the canvas and the layout was
            // recomputed under the finger that had just picked a node. It floats now.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                GraphCanvas(
                    state = state,
                    focus = focus,
                    onSelect = { node ->
                        selected = node
                        // The graph payload carries a label and a size; the memory itself is a
                        // second route, so every pick asks for it.
                        if (node == null) viewModel.clearDetail() else viewModel.readNode(node.id)
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                shown?.let { node ->
                    NodePanel(
                        node = node,
                        detail = state.detail.takeIf { state.detailId == node.id },
                        loading = state.detailLoading && state.detailId == node.id,
                        error = state.detailError.takeIf { state.detailId == node.id },
                        connections = GraphFocus.connections(links, node.id),
                        onClose = {
                            selected = null
                            viewModel.clearDetail()
                        },
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
    focus: GraphFocus,
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

    // One alpha per node, computed once per filter change rather than per drawn circle.
    val shadeOf = remember(focus, state.nodes) {
        state.nodes.associate { it.id to emphasisAlpha(focus.emphasisOf(it.id)) }
    }

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
                    // A link fades with its dimmer end: a lit node never drags a dark line with it.
                    val shade = minOf(shadeOf[edge.source] ?: 1f, shadeOf[edge.target] ?: 1f)
                    drawLine(
                        color = colors.graphEdge.copy(alpha = colors.graphEdge.alpha * shade),
                        start = Offset(from.x, from.y),
                        end = Offset(to.x, to.y),
                        strokeWidth = HAIRLINE.toPx(),
                    )
                }
                for (point in placed) {
                    val radius = radii[point.id] ?: continue
                    drawCircle(
                        color = nodeColor(byId[point.id]?.color, colors.accent)
                            .copy(alpha = shadeOf[point.id] ?: 1f),
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
                    color = colors.textSecondary.copy(alpha = shadeOf[point.id] ?: 1f),
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

/**
 * The memory behind a dot, at the bottom, where a thumb can read it.
 *
 * The graph payload only carries a label, a type and a size; the summary, the scope, the evidence
 * and the dates come from `GET /api/brain/memory/{id}` and arrive a moment later. The panel says so
 * while it waits, and says what the route answered when it fails — an empty row would read as a
 * memory that holds nothing.
 */
@Composable
private fun NodePanel(
    node: GraphNode,
    detail: MemoryDto?,
    loading: Boolean,
    error: Notice?,
    connections: Int,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalMercuryColors.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .background(colors.surface, MaterialTheme.shapes.extraSmall)
            .padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Text(
                text = node.fullLabel.ifBlank { node.label },
                style = MaterialTheme.typography.bodyLarge,
                color = colors.textPrimary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onClose, modifier = Modifier.size(38.dp)) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.brain_noeud_fermer),
                    tint = colors.textSecondary,
                    modifier = Modifier.size(19.dp),
                )
            }
        }
        Column(
            modifier = Modifier
                // A tall memory must not swallow the drawing: past this the rows scroll.
                .heightIn(max = PANEL_CONTENT_LIMIT)
                .verticalScroll(rememberScrollState())
                .padding(top = 4.dp, end = 10.dp),
        ) {
            KeyValueRow(stringResource(R.string.brain_graphe_detail_type), rawLabel(node.type))
            // The webapp shows this on hover; a phone has no hover, so it is written down.
            KeyValueRow(
                label = stringResource(R.string.brain_graphe_detail_connexions),
                value = connections.toString(),
            )
            val memory = detail
            if (memory != null) {
                // The title above is the node's label; the memory's own sentence is only worth a
                // row when it says something else.
                if (memory.summary.isNotBlank() && memory.summary != node.fullLabel.ifBlank { node.label }) {
                    KeyValueRow(
                        label = stringResource(R.string.brain_graphe_detail_resume),
                        value = memory.summary,
                    )
                }
                if (memory.scope.isNotBlank()) {
                    KeyValueRow(
                        label = stringResource(R.string.brain_graphe_detail_portee),
                        value = rawLabel(memory.scope),
                    )
                }
                KeyValueRow(
                    label = stringResource(R.string.brain_graphe_detail_importance),
                    value = stringResource(
                        R.string.brain_graphe_detail_pourcent,
                        (memory.importance * 100).roundToInt(),
                    ),
                )
                KeyValueRow(
                    label = stringResource(R.string.brain_graphe_detail_confiance),
                    value = stringResource(
                        R.string.brain_graphe_detail_pourcent,
                        (memory.confidence * 100).roundToInt(),
                    ),
                )
                if (memory.evidenceCount > 0 || memory.evidenceKind.isNotBlank()) {
                    KeyValueRow(
                        label = stringResource(R.string.brain_graphe_detail_preuves),
                        value = if (memory.evidenceKind.isBlank()) {
                            memory.evidenceCount.toString()
                        } else {
                            stringResource(
                                R.string.brain_graphe_detail_preuve_compte,
                                memory.evidenceCount,
                                rawLabel(memory.evidenceKind),
                            )
                        },
                    )
                }
                dateRow(memory.lastSeenAt, R.string.brain_graphe_detail_vu)
                dateRow(memory.updatedAt, R.string.brain_graphe_detail_maj)
                dateRow(memory.createdAt, R.string.brain_graphe_detail_cree)
                memory.detail?.takeIf { it.isNotBlank() }?.let { prose ->
                    Text(
                        text = prose,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            } else if (loading) {
                Text(
                    text = stringResource(R.string.common_loading),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            } else if (error != null) {
                Text(
                    text = noticeText(error),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.danger,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

/** A date row, drawn only when the server really sent one. */
@Composable
private fun dateRow(epochMs: Long, label: Int) {
    if (epochMs > 0) {
        KeyValueRow(label = stringResource(label), value = BotOps.dateLabel(epochMs))
    }
}

/**
 * The route answered `available: false`: the graph is not exposed on this instance.
 *
 * The motif under the sentence is technical and stays as the server spelled it
 * (`SQLITE_DEPENDENCY_ERROR` today) — it is what the owner will search for, or paste to whoever
 * runs the instance. Retry is offered because installing the missing dependency and retrying is a
 * real fix, and because the route may come back without the app being restarted.
 */
@Composable
private fun GraphUnavailable(motif: String?, onRetry: () -> Unit) {
    val colors = LocalMercuryColors.current
    Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
        Text(
            text = stringResource(R.string.brain_graphe_indisponible),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
        )
        if (!motif.isNullOrBlank()) {
            Text(
                text = stringResource(R.string.brain_graphe_indisponible_motif, motif),
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        IconActionBar(
            listOf(
                QuickAction(
                    icon = Icons.Rounded.Refresh,
                    label = stringResource(R.string.brain_graphe_indisponible_reessayer),
                    tint = colors.accent,
                    onClick = onRetry,
                ),
            ),
        )
    }
}

/**
 * The webapp's filter box, plus the legend the phone needs because it has no hover.
 *
 * Typing dims what does not match — the rule itself lives in [io.github.tournierjc.mercury.graph.GraphFocus],
 * this only draws the box. The legend lists the types the drawing really contains, each with the
 * colour its nodes carry, so a dot's colour means something without pointing at it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GraphFilter(
    query: String,
    onQueryChange: (String) -> Unit,
    legend: List<LegendEntry>,
) {
    val colors = LocalMercuryColors.current
    val clear: (@Composable () -> Unit)? = if (query.isEmpty()) {
        null
    } else {
        {
            IconButton(onClick = { onQueryChange("") }) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.brain_graphe_filtre_effacer),
                    tint = colors.textSecondary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
    Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            label = {
                Text(
                    text = stringResource(R.string.brain_graphe_filtrer),
                    style = MaterialTheme.typography.labelSmall,
                )
            },
            trailingIcon = clear,
            modifier = Modifier.fillMaxWidth(),
        )
        if (legend.isNotEmpty()) {
            // Four types already overflow a 360 dp phone: the legend wraps, it does not clip.
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(top = 8.dp),
            ) {
                for (entry in legend) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(LEGEND_DOT)
                                .background(nodeColor(entry.color, colors.accent), CircleShape),
                        )
                        Text(
                            text = rawLabel(entry.type),
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textSecondary,
                        )
                    }
                }
            }
        }
    }
}

/** One labelled value of the panel: the same left column for every row, so the values line up. */
@Composable
private fun KeyValueRow(label: String, value: String) {
    val colors = LocalMercuryColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = colors.textSecondary,
            modifier = Modifier.width(KEY_COLUMN),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = colors.textPrimary,
        )
    }
}

/**
 * Only the shade changes: a dimmed node keeps its drawn position, radius and label, so a filter
 * never moves the drawing under the finger that typed it.
 */
private fun emphasisAlpha(emphasis: NodeEmphasis): Float = when (emphasis) {
    NodeEmphasis.FULL -> 1f
    NodeEmphasis.DIMMED -> 0.45f
    NodeEmphasis.FAINT -> 0.15f
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
private val LEGEND_DOT = 8.dp
/** Left column of the panel: wide enough for «Importance», narrow enough to leave a sentence. */
private val KEY_COLUMN = 104.dp
/** Past this a tall memory scrolls inside the panel instead of eating the drawing. */
private val PANEL_CONTENT_LIMIT = 220.dp
private val labelWidth = 108.dp
