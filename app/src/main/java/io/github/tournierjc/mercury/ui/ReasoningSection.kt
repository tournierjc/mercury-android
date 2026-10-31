package io.github.tournierjc.mercury.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.github.tournierjc.mercury.R
import io.github.tournierjc.mercury.data.BotOps
import io.github.tournierjc.mercury.data.dto.ReasoningBlockDto
import io.github.tournierjc.mercury.ui.theme.LocalMercuryColors

/**
 * The bot's thinking, folded, wherever a turn is read: the run sheet and the conversation.
 *
 * Folded to the last two steps and 400 characters each, because the newest thinking is what explains
 * the outcome and an unfolded wall of text above every reply would bury the replies. Expanded shows
 * every step the daemon kept, each still capped — the daemon bounds the whole run, so the screen never
 * promises more than the transcript holds.
 */
@Composable
fun ReasoningSection(
    blocks: List<ReasoningBlockDto>,
    truncated: Boolean = false,
    initiallyExpanded: Boolean = false,
    modifier: Modifier = Modifier,
) {
    if (blocks.isEmpty()) return
    val colors = LocalMercuryColors.current
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.run_reasoning).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { expanded = !expanded }) {
                Text(
                    text = stringResource(if (expanded) R.string.run_reasoning_less else R.string.run_reasoning_more),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        BotOps.reasoningBlocks(blocks, expanded).forEach { block ->
            Text(
                text = stringResource(R.string.run_reasoning_step, block.step),
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                text = block.text,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = colors.textPrimary,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surfaceMuted, MaterialTheme.shapes.extraSmall)
                    .padding(10.dp),
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
