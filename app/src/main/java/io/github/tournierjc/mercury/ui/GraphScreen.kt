package io.github.tournierjc.mercury.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.tournierjc.mercury.MercuryApp
import io.github.tournierjc.mercury.R
import io.github.tournierjc.mercury.data.dto.GraphNode
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

    // A node that vanished from a refresh must not stay in the panel.
    val shown = selected?.let { picked -> state.nodes.firstOrNull { it.id == picked.id } }

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
            Text(
                text = when {
                    state.loading -> stringResource(R.string.memory_chargement)
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
            GraphCanvas(
                state = state,
                onSelect = { node -> selected = node },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
        }

        shown?.let { node ->
            NodePanel(node = node, onClose = { selected = null })
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
    // Only the most important few get words: eight labels on a phone already overlap.
    val labelled = remember(state.nodes) {
        state.nodes.sortedByDescending { it.importance }.take(MAX_LABELS).map { it.id }.toHashSet()
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
                        color = colors.hairline,
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
                    maxLines = 1,
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
@Composable
private fun NodePanel(node: GraphNode, onClose: () -> Unit) {
    val colors = LocalMercuryColors.current
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier
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
            Text(
                text = buildString {
                    append(node.type)
                    if (node.importance > 0.0) append(" · ").append((node.importance * 100).toInt()).append(" %")
                },
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary,
                modifier = Modifier.padding(top = 4.dp),
            )
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
    return when (digits.length) {
        6 -> Color(0xFF000000L or value)
        8 -> Color(value)
        else -> fallback
    }
}

private const val MIN_RADIUS = 4f
private const val MAX_RADIUS = 16f
private const val MAX_SCALE = 6f
private const val MAX_LABELS = 8
private val NODE_SPACING = 40.dp
private val TOUCH_SLOP = 12.dp
private val HAIRLINE = 1.dp
private val LABEL_GAP = 3.dp
private val labelWidth = 108.dp
