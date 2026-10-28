package io.github.tournierjc.mercury.ui

import io.github.tournierjc.mercury.data.Notice

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.tournierjc.mercury.openui.OpenUi
import io.github.tournierjc.mercury.openui.OpenUiNode
import io.github.tournierjc.mercury.ui.theme.LocalMercuryColors
import io.github.tournierjc.mercury.R
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/** Tones the contract allows, on top of the palette's accent and danger. */
private val OkGreen = Color(0xFF3FB950)
private val WarnAmber = Color(0xFFD29922)

/**
 * A rendered `openui` block — the client half of the contract the `openui-contract`
 * plugin enforces (`CONTRACT.md`).
 *
 * Everything here draws only from [OpenUiNode], a tree the parser already vetted: no
 * string in it is a URL, a script or a style. A spec that fails to parse is shown as
 * code instead — a message is never lost, it is just not drawn.
 *
 * [streaming] is the answer still arriving: the block then draws whatever part of the
 * spec is already complete and shows `…` while nothing is — a half-written statement is
 * the normal state of a streamed fence, not something to report as an error.
 */
@Composable
fun OpenUiBlock(spec: String, streaming: Boolean = false, modifier: Modifier = Modifier) {
    val result = remember(spec, streaming) { OpenUi.parse(spec, partial = streaming) }
    when (result) {
        is OpenUi.Result.Rendered -> Column(modifier = modifier.fillMaxWidth()) {
            NodeView(result.root)
        }

        OpenUi.Result.Pending -> Text(
            text = "…",
            style = MaterialTheme.typography.bodyLarge,
            color = LocalMercuryColors.current.textSecondary,
            modifier = modifier.padding(top = 2.dp, bottom = 2.dp),
        )

        is OpenUi.Result.Refused -> RefusedSpec(spec, result.errors, modifier)
    }
}

@Composable
private fun RefusedSpec(spec: String, errors: List<Notice>, modifier: Modifier) {
    val colors = LocalMercuryColors.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(14.dp))
            .padding(14.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("openui", style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
            Text(stringResource(R.string.openui_not_rendered), style = MaterialTheme.typography.labelSmall, color = colors.danger)
        }
        Spacer(Modifier.height(8.dp))
        SelectionContainer {
            Text(
                text = spec,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = colors.textPrimary,
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = errors.firstOrNull()?.let { noticeText(it) }.orEmpty(),
            style = MaterialTheme.typography.labelSmall,
            color = colors.textSecondary,
        )
    }
}

@Composable
private fun NodeView(node: OpenUiNode, spacing: Int = 10) {
    val colors = LocalMercuryColors.current
    when (node) {
        is OpenUiNode.Stack -> Column(verticalArrangement = Arrangement.spacedBy(spacing.dp)) {
            node.children.forEach { child -> NodeView(child) }
        }

        is OpenUiNode.Paragraph -> Text(
            text = node.text,
            style = when (node.variant) {
                "h1" -> MaterialTheme.typography.headlineSmall
                "h2", "h3" -> MaterialTheme.typography.titleMedium
                "mono" -> MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                else -> MaterialTheme.typography.bodyLarge
            },
            fontWeight = if (node.variant == "h1" || node.variant == "h2") FontWeight.SemiBold else FontWeight.Normal,
            color = if (node.muted) colors.textSecondary else colors.textPrimary,
        )

        is OpenUiNode.Card -> Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .background(colors.surface, RoundedCornerShape(14.dp)),
        ) {
            // The tone is a stripe, not a tint: a red card would fight the palette.
            val tone = toneColor(node.tone)
            if (tone != null) {
                Box(
                    Modifier
                        .width(3.dp)
                        .fillMaxHeight()
                        .background(tone, RoundedCornerShape(topStart = 14.dp, bottomStart = 14.dp)),
                )
            }
            Column(modifier = Modifier.padding(14.dp)) {
                node.title?.let {
                    Text(it, style = MaterialTheme.typography.titleSmall, color = colors.textPrimary)
                    Spacer(Modifier.height(4.dp))
                }
                node.body?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
                }
            }
        }

        is OpenUiNode.KeyValue -> Column {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(node.label, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
                Text(
                    node.value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (node.highlight) colors.accent else colors.textPrimary,
                    fontWeight = if (node.highlight) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
            HorizontalDivider(color = colors.hairline, thickness = 1.dp)
        }

        is OpenUiNode.Table -> Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surface, RoundedCornerShape(14.dp))
                .padding(12.dp)
                .horizontalScroll(rememberScrollState()),
        ) {
            node.caption?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
                Spacer(Modifier.height(8.dp))
            }
            // One width for every cell keeps the columns aligned without measuring text.
            val cell = 132.dp
            Row {
                node.columns.forEachIndexed { index, title ->
                    val numeric = isNumericColumn(node, index)
                    Text(
                        text = title.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textSecondary,
                        textAlign = if (numeric) TextAlign.End else TextAlign.Start,
                        modifier = Modifier.width(cell).padding(end = 8.dp),
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            HorizontalDivider(color = colors.hairline, thickness = 1.dp)
            node.rows.forEach { row ->
                Row(modifier = Modifier.padding(top = 7.dp)) {
                    node.columns.indices.forEach { index ->
                        val numeric = isNumericColumn(node, index)
                        Text(
                            text = row.getOrNull(index).orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textPrimary,
                            textAlign = if (numeric) TextAlign.End else TextAlign.Start,
                            modifier = Modifier.width(cell).padding(end = 8.dp),
                        )
                    }
                }
            }
        }

        is OpenUiNode.Bullets -> Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            node.items.forEachIndexed { index, item ->
                Row {
                    Text(
                        text = if (node.ordered) "${index + 1}." else "•",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                        modifier = Modifier.width(20.dp),
                    )
                    Text(item, style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary)
                }
            }
        }

        is OpenUiNode.Stat -> Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surface, RoundedCornerShape(14.dp))
                .padding(14.dp),
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    node.value,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = toneColor(node.tone) ?: colors.textPrimary,
                )
                node.delta?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(start = 8.dp, bottom = 4.dp),
                    )
                }
            }
            Text(node.label, style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
        }

        is OpenUiNode.Snippet -> Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surface, RoundedCornerShape(14.dp)),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    node.title.orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary,
                )
                Text(
                    node.language,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = colors.textSecondary,
                )
            }
            HorizontalDivider(color = colors.hairline, thickness = 1.dp)
            SelectionContainer {
                Text(
                    text = node.code,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = colors.textPrimary,
                    modifier = Modifier.padding(12.dp).horizontalScroll(rememberScrollState()),
                )
            }
        }

        is OpenUiNode.Chart -> ChartView(node)
        is OpenUiNode.Pie -> PieView(node)
        is OpenUiNode.Gauge -> GaugeView(node)
    }
}

private fun toneColor(tone: String): Color? = when (tone) {
    "ok" -> OkGreen
    "warn" -> WarnAmber
    "danger" -> Color(0xFFFF6B6B)
    else -> null
}

private fun isNumericColumn(table: OpenUiNode.Table, index: Int): Boolean =
    table.rows.isNotEmpty() && table.rows.all { row ->
        row.getOrNull(index)?.trim()?.matches(Regex("^-?\\d+([.,]\\d+)?$")) == true
    }

/** Round tick steps (1, 2, 5 × 10^k): labels read 0/5/10, never 0/2/5/7/9. */
private fun niceStep(raw: Double): Double {
    if (raw <= 0.0) return 1.0
    val power = 10.0.pow(floor(log10(raw)))
    val fraction = raw / power
    val nice = when {
        fraction <= 1.0 -> 1.0
        fraction <= 2.0 -> 2.0
        fraction <= 5.0 -> 5.0
        else -> 10.0
    }
    return nice * power
}

@Composable
private fun ChartView(node: OpenUiNode.Chart) {
    val colors = LocalMercuryColors.current
    val dataMax = node.values.maxOrNull() ?: 1.0
    val step = niceStep(maxOf(dataMax / 4.0, 1.0))
    val max = ceil(dataMax / step) * step
    val ticks = generateSequence(0.0) { it + step }.takeWhile { it <= max + step / 2 }.toList()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(14.dp))
            .padding(14.dp),
    ) {
        node.title?.let {
            Text(it, style = MaterialTheme.typography.titleSmall, color = colors.textPrimary)
        }
        node.unit?.let {
            Text(stringResource(R.string.openui_in, it), style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
        }
        Spacer(Modifier.height(10.dp))
        Row {
            Column(modifier = Modifier.padding(end = 8.dp), horizontalAlignment = Alignment.End) {
                ticks.reversed().forEach { tick ->
                    Text(
                        text = tick.toInt().toString(),
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = colors.textSecondary,
                        modifier = Modifier.height(28.dp),
                    )
                }
            }
            Canvas(modifier = Modifier.weight(1f).height(28.dp * (ticks.size - 1))) {
                val height = size.height
                val width = size.width
                // Grid: one line per tick, the baseline included.
                ticks.forEachIndexed { index, _ ->
                    val y = height - (height / (ticks.size - 1).coerceAtLeast(1)) * index
                    drawLine(colors.hairline, Offset(0f, y), Offset(width, y), strokeWidth = 1f)
                }
                val values = node.values
                if (values.isEmpty()) return@Canvas
                if (node.kind == "bar") {
                    val slot = width / values.size
                    val barWidth = minOf(slot * 0.55f, 38.dp.toPx())
                    values.forEachIndexed { index, value ->
                        val barHeight = (value / max * height).toFloat().coerceAtLeast(1f)
                        drawRoundRect(
                            color = colors.accent,
                            topLeft = Offset(slot * index + (slot - barWidth) / 2f, height - barHeight),
                            size = Size(barWidth, barHeight),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()),
                        )
                    }
                } else {
                    val path = Path()
                    values.forEachIndexed { index, value ->
                        val x = if (values.size == 1) width / 2 else width / (values.size - 1) * index
                        val y = height - (value / max * height).toFloat()
                        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    drawPath(path, colors.accent, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
                    values.forEachIndexed { index, value ->
                        val x = if (values.size == 1) width / 2 else width / (values.size - 1) * index
                        val y = height - (value / max * height).toFloat()
                        drawCircle(colors.surface, radius = 4.dp.toPx(), center = Offset(x, y))
                        drawCircle(colors.accent, radius = 4.dp.toPx(), center = Offset(x, y), style = Stroke(2.dp.toPx()))
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            node.labels.forEach { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** One colour per slice, in the order the model listed them. */
private val PieColors = listOf(
    Color(0xFF00D4FF), Color(0xFF7BD88F), Color(0xFFFFB454), Color(0xFFFF6B6B),
    Color(0xFFB48CFF), Color(0xFF4DD0E1), Color(0xFFF78FB3), Color(0xFF9CCC65),
    Color(0xFFFFD54F), Color(0xFF90A4AE), Color(0xFF7986CB), Color(0xFF4DB6AC),
)

/**
 * Part-to-whole, drawn with arcs: a `donut` (the default) leaves the middle empty, a `pie`
 * fills it. Slices come from values the parser has already checked — same label count, no
 * negative, a total above zero — so there is always something to divide.
 */
@Composable
private fun PieView(node: OpenUiNode.Pie) {
    val colors = LocalMercuryColors.current
    val total = node.values.filter { it.isFinite() && it > 0 }.sum().takeIf { it > 0 } ?: 1.0
    val slices = node.values.mapIndexed { index, value ->
        val share = if (value.isFinite() && value > 0) value / total else 0.0
        Triple(index, share, node.labels.getOrNull(index).orEmpty())
    }.filter { it.second > 0 }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(14.dp))
            .padding(14.dp),
    ) {
        node.title?.let {
            Text(it, style = MaterialTheme.typography.titleSmall, color = colors.textPrimary)
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Canvas(modifier = Modifier.size(150.dp)) {
                val outer = size.minDimension / 2f - 2f
                val inner = if (node.kind == "pie") 0f else outer * 0.58f
                val box = Rect(center - Offset(outer, outer), Size(outer * 2f, outer * 2f))
                if (slices.size == 1) {
                    // One slice covering the circle: a 360° arc is degenerate, so draw the ring.
                    drawCircle(
                        color = PieColors[0],
                        radius = (outer + inner) / 2f,
                        style = if (inner > 0f) Stroke(width = outer - inner) else Fill,
                    )
                } else {
                    var start = -90f
                    slices.forEach { (index, share, _) ->
                        val sweep = (share * 360.0).toFloat()
                        val path = Path()
                        if (inner <= 0f) {
                            path.moveTo(center.x, center.y)
                            path.arcTo(box, start, sweep, forceMoveTo = false)
                        } else {
                            path.arcTo(box, start, sweep, forceMoveTo = true)
                            val holeBottomRight = Offset(center.x + inner, center.y + inner)
                            val hole = Rect(holeBottomRight - Offset(inner * 2f, inner * 2f), Size(inner * 2f, inner * 2f))
                            path.arcTo(hole, start + sweep, -sweep, forceMoveTo = false)
                        }
                        path.close()
                        drawPath(path, PieColors[index % PieColors.size])
                        start += sweep
                    }
                }
            }
            if (node.legend) {
                Column(modifier = Modifier.weight(1f).padding(start = 14.dp)) {
                    slices.forEach { (index, share, label) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(PieColors[index % PieColors.size], RoundedCornerShape(3.dp)),
                            )
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f).padding(start = 8.dp),
                            )
                            Text(
                                text = "${Math.round(share * 100)}%",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = colors.textSecondary,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** One value against its maximum, as a half ring with the percentage in the middle. */
@Composable
private fun GaugeView(node: OpenUiNode.Gauge) {
    val colors = LocalMercuryColors.current
    val ratio = (node.value / node.max).coerceIn(0.0, 1.0)
    val tint = toneColor(node.tone) ?: colors.accent

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(14.dp))
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        node.title?.let {
            Text(it, style = MaterialTheme.typography.titleSmall, color = colors.textPrimary)
        }
        Box(contentAlignment = Alignment.BottomCenter) {
            Canvas(modifier = Modifier.fillMaxWidth().height(96.dp)) {
                val stroke = 16.dp.toPx()
                val radius = minOf(size.width / 2f, size.height) - stroke / 2f - 1f
                val topLeft = Offset(center.x - radius, size.height - radius)
                val arcSize = Size(radius * 2f, radius * 2f)
                drawArc(
                    color = colors.hairline,
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
                if (ratio > 0.0) {
                    drawArc(
                        color = tint,
                        startAngle = 180f,
                        sweepAngle = (180.0 * ratio).toFloat(),
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                }
            }
            Text(
                text = "${Math.round(ratio * 100)}%",
                style = MaterialTheme.typography.headlineSmall,
                fontFamily = FontFamily.Monospace,
                color = colors.textPrimary,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        node.label?.let {
            Text(
                text = listOfNotNull(it, node.unit?.let { unit -> "${node.value.toInt()} $unit" }).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary,
            )
        }
    }
}
