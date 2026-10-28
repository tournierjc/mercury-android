package io.github.tournierjc.mercury.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * The graph, as a glyph: three linked dots.
 *
 * The drawer borrowed `Share` for this destination — the icon of "send to another app", which says
 * the opposite of "here is what the brain connects". The material core set has no graph glyph, so
 * this one is drawn: the same shape the screen draws.
 */
val GraphGlyph: ImageVector by lazy {
    ImageVector.Builder(
        name = "GraphGlyph",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        // The two edges, drawn first so the dots sit on top of them.
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.6f,
            strokeLineCap = StrokeCap.Round,
        ) {
            moveTo(7.2f, 8.2f)
            lineTo(16.8f, 15.8f)
            moveTo(16.8f, 8.2f)
            lineTo(7.2f, 15.8f)
        }
        // Three dots at the ends.
        path(fill = SolidColor(Color.Black)) {
            moveTo(9f, 4.6f)
            arcToRelative(2.4f, 2.4f, 0f, true, true, -4.8f, 0f)
            arcToRelative(2.4f, 2.4f, 0f, true, true, 4.8f, 0f)
            moveTo(19.8f, 4.6f)
            arcToRelative(2.4f, 2.4f, 0f, true, true, -4.8f, 0f)
            arcToRelative(2.4f, 2.4f, 0f, true, true, 4.8f, 0f)
            moveTo(14.4f, 19.4f)
            arcToRelative(2.4f, 2.4f, 0f, true, true, -4.8f, 0f)
            arcToRelative(2.4f, 2.4f, 0f, true, true, 4.8f, 0f)
        }
    }.build()
}
