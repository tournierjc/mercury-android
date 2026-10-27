package io.github.tournierjc.mercury.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.github.tournierjc.mercury.diff.Diff
import io.github.tournierjc.mercury.diff.DiffLine
import io.github.tournierjc.mercury.ui.theme.LocalMercuryColors

/** Green of a `+` line, on top of the palette's accent and danger. */
private val AddGreen = Color(0xFF3FB950)

/**
 * A ```diff fenced block drawn as a unified diff — the Compose half of the
 * `diff-view` plugin's renderer (`renderer/diff.html`), which claims the fence on the web.
 *
 * The classification lives in [io.github.tournierjc.mercury.diff.Diff] so both clients colour
 * the same lines the same way. Nothing here interprets the patch: a diff is text, and a
 * half-written one draws as a half-written one — no parse to fail, no reason to refuse.
 */
@Composable
fun DiffBlock(text: String, modifier: Modifier = Modifier) {
    val colors = LocalMercuryColors.current
    val document = remember(text) { Diff.parse(text) }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = Diff.FENCE,
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary,
            )
            Text(
                text = "+${document.added}  −${document.removed}",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary,
            )
        }
        SelectionContainer {
            Column(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                document.lines.forEach { line ->
                    DiffLineRow(line, colors.textPrimary, colors.textSecondary, colors.accent, colors.danger)
                }
            }
        }
    }
}

@Composable
private fun DiffLineRow(line: DiffLine, text: Color, muted: Color, accent: Color, danger: Color) {
    val color = when (line.kind) {
        DiffLine.Kind.ADD -> AddGreen
        DiffLine.Kind.DEL -> danger
        DiffLine.Kind.HUNK -> accent
        DiffLine.Kind.META -> muted
        DiffLine.Kind.CONTEXT -> text
    }
    // A tinted background is what makes a patch readable at a glance on a phone; the sign
    // column keeps the colour from being the only signal (colour-blind safe).
    val background = when (line.kind) {
        DiffLine.Kind.ADD -> AddGreen.copy(alpha = 0.12f)
        DiffLine.Kind.DEL -> danger.copy(alpha = 0.12f)
        else -> Color.Transparent
    }
    Row(modifier = Modifier.background(background).padding(vertical = 1.dp)) {
        Text(
            text = when (line.kind) {
                DiffLine.Kind.ADD -> "+"
                DiffLine.Kind.DEL -> "−"
                else -> " "
            },
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = color,
            modifier = Modifier.width(14.dp),
        )
        Text(
            text = line.text,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = color,
        )
    }
}
