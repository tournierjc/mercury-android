package io.github.tournierjc.mercury.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.ArrowDropUp
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.tournierjc.mercury.R
import io.github.tournierjc.mercury.data.ToolGlyph
import io.github.tournierjc.mercury.data.ToolStep
import io.github.tournierjc.mercury.data.TraceLine
import io.github.tournierjc.mercury.ui.theme.LocalMercuryColors

/**
 * What the agent did with its tools, as a framed block of its own — not as prose, and not as chips
 * glued to the answer.
 *
 * Each call is one line the way a terminal conversation shows it: an emoji for the kind of call
 * (💻 a command, 📄 a read, 📚 a skill, 📦 a delivery), the tool's name, and its argument as the
 * command it ran, in a code block. A turn that ran twenty calls is unreadable as a wall of
 * monospace names, and unreadable as chips that truncate the command: the emoji says what kind of
 * thing happened without reading a word, and the command is the only part worth reading in full.
 *
 * [folded] says whether it starts closed: history is context (closed, with the last calls still
 * legible), the turn in progress is the thing you are watching (open).
 */
@Composable
fun ToolTraceBlock(
    steps: List<ToolStep>,
    folded: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val colors = LocalMercuryColors.current
    var open by rememberSaveable { mutableStateOf(!folded) }
    val lines = remember(steps) { steps.map { ToolGlyph.lineOf(it) } }
    val failures = lines.count { it.failed }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surfaceMuted)
            .border(1.dp, colors.hairlineStrong, RoundedCornerShape(12.dp))
            .clickable { open = !open }
            .padding(horizontal = 10.dp, vertical = 7.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Rounded.Terminal,
                contentDescription = null,
                tint = if (failures > 0) colors.danger else colors.accent,
                modifier = Modifier.size(13.dp),
            )
            Text(
                text = stringResource(R.string.chat_trace_count, steps.size).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (failures > 0) colors.danger else colors.textSecondary,
                modifier = Modifier.padding(start = 6.dp),
            )
            if (failures > 0) {
                Text(
                    text = " · " + stringResource(R.string.chat_trace_failures, failures),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.danger,
                )
            }
            Box(modifier = Modifier.weight(1f))
            // A triangle, not the word « déplier »: the whole header is the target, and a label
            // there competed with the count on its left. The words survive as the tap's
            // description, where a screen reader still needs them.
            Icon(
                imageVector = if (open) Icons.Rounded.ArrowDropUp else Icons.Rounded.ArrowDropDown,
                contentDescription = stringResource(
                    if (open) R.string.chat_trace_hide else R.string.chat_trace_show,
                ),
                tint = colors.textSecondary,
                modifier = Modifier.size(20.dp),
            )
        }
        // Folded is not mute: the last calls stay on screen, one line each, so a closed block still
        // says what the turn was doing — and a failure is visible without opening anything.
        val shown = if (open) lines else lines.takeLast(FOLDED_PREVIEW)
        if (shown.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                verticalArrangement = Arrangement.spacedBy(if (open) 6.dp else 2.dp),
            ) {
                shown.forEach { line -> ToolTraceRow(line, compact = !open) }
            }
        }
    }
}

/** How many calls a closed trace still shows. Three fits above the fold on a phone. */
private const val FOLDED_PREVIEW = 3

@Composable
private fun ToolTraceRow(line: TraceLine, compact: Boolean) {
    val colors = LocalMercuryColors.current
    val tint = if (line.failed) colors.danger else colors.accent
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // The emoji is the row's icon, so no dependency on an icon font per tool family.
            Text(
                text = line.glyph,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(end = 6.dp),
            )
            Text(
                text = line.name,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = tint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (compact && line.detail.isNotEmpty()) {
                // One row when folded: name then command, cut at the edge. Opening the block is
                // what a truncated command invites, and the ellipsis says it was cut.
                Text(
                    text = "  " + line.detail,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
        }
        if (!compact && line.detail.isNotEmpty()) {
            CodeSurface(
                content = line.detail,
                modifier = Modifier.padding(top = 3.dp, start = 2.dp),
            )
        }
        // The outcome is the daemon's word on the call; a failure keeps it in the danger colour so
        // the opened block says what went wrong, which is the only reason to open it.
        line.outcome?.let { outcome ->
            Text(
                text = outcome.removePrefix("\u2717").trim(),
                style = MaterialTheme.typography.labelSmall,
                color = if (line.failed) colors.danger else colors.textSecondary,
                maxLines = if (compact) 1 else 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 2.dp, top = 1.dp),
            )
        }
    }
}
