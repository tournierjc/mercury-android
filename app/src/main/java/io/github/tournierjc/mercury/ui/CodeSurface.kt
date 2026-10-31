package io.github.tournierjc.mercury.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.tournierjc.mercury.ui.theme.LocalMercuryColors

/**
 * A run of code, drawn the way an answer's fenced block is drawn: monospace on a card of its own,
 * scrolling sideways instead of wrapping a shell command into a paragraph.
 *
 * One composable for every piece of code on screen — the fence of an answer and the command a tool
 * call ran — because two renderings of the same thing drift apart, and they had: the trace squeezed
 * its command into a single truncated row while a fence ten lines below got a block of its own.
 *
 * [oneLine] is for the folded trace's preview, where three calls must fit in a thumb's width: the
 * line is cut with an ellipsis rather than scrolled, since nothing there is meant to be read in
 * full — opening the block is.
 */
@Composable
fun CodeSurface(
    content: String,
    modifier: Modifier = Modifier,
    oneLine: Boolean = false,
) {
    val colors = LocalMercuryColors.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surface, MaterialTheme.shapes.extraSmall)
            .padding(horizontal = 10.dp, vertical = 7.dp),
    ) {
        val text: @Composable () -> Unit = {
            Text(
                text = content,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                ),
                color = colors.textPrimary,
                maxLines = if (oneLine) 1 else Int.MAX_VALUE,
                overflow = if (oneLine) TextOverflow.Ellipsis else TextOverflow.Clip,
                modifier = if (oneLine) Modifier else Modifier.horizontalScroll(rememberScrollState()),
            )
        }
        if (oneLine) text() else SelectionContainer { text() }
    }
}
