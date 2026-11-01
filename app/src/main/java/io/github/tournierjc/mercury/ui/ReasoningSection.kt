package io.github.tournierjc.mercury.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.tournierjc.mercury.R
import io.github.tournierjc.mercury.data.BotOps
import io.github.tournierjc.mercury.data.dto.ReasoningBlockDto
import io.github.tournierjc.mercury.ui.theme.LocalMercuryColors

/**
 * The bot's thinking, folded, wherever a turn is read: the conversation and the run sheet.
 *
 * **One framed block, its header inside**, exactly like [ToolTraceBlock] beside it: the thinking is
 * something the turn *did*, so it is drawn as an object and not as a caption sitting over a
 * paragraph. The whole card is the toggle — the header carries the triangle, and a tap anywhere
 * opens it. Drawn the other way (a header on the bare background, then a tinted box per step) the
 * block read as if the panel had not taken the thinking in, which is what the owner reported.
 *
 * Folded to the last two steps and 400 characters each, because the newest thinking is what explains
 * the outcome and an unfolded wall of text above every reply would bury the replies. Expanded shows
 * every step the daemon kept, each still capped — the daemon bounds the whole run, so the screen
 * never promises more than the transcript holds.
 */
@Composable
fun ReasoningSection(
    blocks: List<ReasoningBlockDto>,
    truncated: Boolean = false,
    modifier: Modifier = Modifier,
) {
    if (blocks.isEmpty()) return
    val colors = LocalMercuryColors.current
    var open by rememberSaveable { mutableStateOf(false) }

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
                Icons.Rounded.Psychology,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(13.dp),
            )
            Text(
                text = stringResource(R.string.run_reasoning).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = colors.textSecondary,
                modifier = Modifier.padding(start = 6.dp),
            )
            Box(modifier = Modifier.weight(1f))
            Icon(
                imageVector = if (open) Icons.Rounded.ArrowDropUp else Icons.Rounded.ArrowDropDown,
                contentDescription = stringResource(
                    if (open) R.string.chat_trace_hide else R.string.chat_trace_show,
                ),
                tint = colors.textSecondary,
                modifier = Modifier.size(20.dp),
            )
        }
        // A step can think twice: the daemon appends a block per spurt. `reasoningBlocks` joins the
        // spurts of one step before folding, so one step reads as one label and one paragraph.
        BotOps.reasoningBlocks(blocks, open).forEach { block ->
            Text(
                text = stringResource(R.string.run_reasoning_step, block.step),
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary,
                modifier = Modifier.padding(top = 6.dp),
            )
            Text(
                text = block.text,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = colors.textPrimary,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        if (truncated) {
            Text(
                text = stringResource(R.string.run_reasoning_truncated),
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
