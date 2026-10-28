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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Terminal
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.tournierjc.mercury.R
import io.github.tournierjc.mercury.data.ToolStep
import io.github.tournierjc.mercury.ui.theme.LocalMercuryColors

/**
 * What the agent did with its tools, as a framed block of its own — not as prose, and not as chips
 * glued to the answer.
 *
 * The daemon stores one summary per turn, written as `- run_command … → ok` for the model to read;
 * the phone printed it verbatim in a reopened thread and showed a strip of pills during the turn.
 * One component now serves both: a bounded block, monospace, with a header that counts what ran and
 * what failed — a turn that went wrong is visible while folded.
 *
 * [folded] says whether it starts closed: history is context (closed), the turn in progress is the
 * thing you are watching (open).
 */
@Composable
fun ToolTraceBlock(
    steps: List<ToolStep>,
    folded: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val colors = LocalMercuryColors.current
    var open by rememberSaveable { mutableStateOf(!folded) }
    val failures = steps.count { it.summary?.startsWith("✗") == true }

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
            Text(
                text = stringResource(if (open) R.string.chat_trace_hide else R.string.chat_trace_show),
                style = MaterialTheme.typography.labelSmall,
                color = colors.accent,
            )
        }
        if (open) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                steps.forEach { step -> ToolTraceRow(step) }
            }
        }
    }
}

@Composable
private fun ToolTraceRow(step: ToolStep) {
    val colors = LocalMercuryColors.current
    val failed = step.summary?.startsWith("✗") == true
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = step.tool,
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
            color = if (failed) colors.danger else colors.accent,
            maxLines = 1,
            // The tool's name anchors the row: it may not be squeezed to nothing by its arguments.
            modifier = Modifier.widthIn(max = 110.dp),
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = step.label,
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
            color = colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(start = 6.dp),
        )
        step.summary?.takeIf { it.isNotBlank() }?.let { outcome ->
            Text(
                text = outcome,
                style = MaterialTheme.typography.labelSmall,
                color = if (failed) colors.danger else colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .widthIn(max = 96.dp)
                    .padding(start = 6.dp),
            )
        }
    }
}
