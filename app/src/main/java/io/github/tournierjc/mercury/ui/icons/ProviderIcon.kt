package io.github.tournierjc.mercury.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * A provider, as a glyph: a chip with its pins.
 *
 * « Fournisseurs » borrowed the memory icon — the brain's own glyph, for the models the brain calls
 * out to — which made two different destinations wear the same mark. Extension and grid look like
 * what they mean for skills and plugins; providers had nothing.
 */
val ProviderGlyph: ImageVector by lazy {
    ImageVector.Builder(
        name = "ProviderGlyph",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        // The body: a rounded square, drawn as a stroked path so it stays a line at any size.
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.6f,
            strokeLineCap = StrokeCap.Round,
        ) {
            moveTo(8.4f, 7.6f)
            lineTo(15.6f, 7.6f)
            arcToRelative(1.6f, 1.6f, 0f, false, true, 1.6f, 1.6f)
            lineTo(17.2f, 14.8f)
            arcToRelative(1.6f, 1.6f, 0f, false, true, -1.6f, 1.6f)
            lineTo(8.4f, 16.4f)
            arcToRelative(1.6f, 1.6f, 0f, false, true, -1.6f, -1.6f)
            lineTo(6.8f, 9.2f)
            arcToRelative(1.6f, 1.6f, 0f, false, true, 1.6f, -1.6f)
            close()
            // Four pins, two a side.
            moveTo(9.6f, 7.6f)
            lineTo(9.6f, 4.8f)
            moveTo(14.4f, 7.6f)
            lineTo(14.4f, 4.8f)
            moveTo(9.6f, 16.4f)
            lineTo(9.6f, 19.2f)
            moveTo(14.4f, 16.4f)
            lineTo(14.4f, 19.2f)
        }
        // The core: a filled dot in the middle, so the chip reads as a chip and not a box.
        path(fill = SolidColor(Color.Black)) {
            moveTo(13.2f, 12f)
            arcToRelative(1.2f, 1.2f, 0f, true, true, -2.4f, 0f)
            arcToRelative(1.2f, 1.2f, 0f, true, true, 2.4f, 0f)
        }
    }.build()
}
