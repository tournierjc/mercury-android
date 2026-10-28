package io.github.tournierjc.mercury.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.ErrorOutline
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.tournierjc.mercury.R
import io.github.tournierjc.mercury.data.ToolStep
import io.github.tournierjc.mercury.ui.theme.LocalMercuryColors

/**
 * What a reopened conversation shows for a turn that used tools: a folded block, not a paragraph.
 *
 * The daemon stores one entry per turn summarising the calls, written as `- run_command … → ok` for
 * the model to read. Rendered verbatim, a reopened thread showed that block as a bubble of dashes
 * and arrows — the same facts the live turn had shown as neat chips, minus the shape.
 *
 * Folded by default, because history is context and not the answer; the header says how many calls
 * and how many failed, so a turn that went wrong is visible without opening anything.
 */
@Composable
fun ToolTraceBlock(steps: List<ToolStep>, modifier: Modifier = Modifier) {
    val colors = LocalMercuryColors.current
    var open by rememberSaveable { mutableStateOf(false) }
    val failures = steps.count { it.summary?.startsWith("✗") == true }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .clip(MaterialTheme.shapes.extraSmall)
            .background(colors.surface)
            .clickable { open = !open }
            .padding(horizontal = 10.dp, vertical = 7.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Rounded.Build,
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = stringResource(R.string.chat_trace_count, steps.size),
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary,
                modifier = Modifier.padding(start = 6.dp),
            )
            if (failures > 0) {
                // A turn that failed somewhere says so while folded.
                Text(
                    text = " · " + stringResource(R.string.chat_trace_failures, failures),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.danger,
                )
            }
            Text(
                text = stringResource(if (open) R.string.chat_trace_hide else R.string.chat_trace_show),
                style = MaterialTheme.typography.labelSmall,
                color = colors.accent,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (open) {
            steps.forEach { step ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                ) {
                    val failed = step.summary?.startsWith("✗") == true
                    if (failed) {
                        Icon(
                            Icons.Rounded.ErrorOutline,
                            contentDescription = null,
                            tint = colors.danger,
                            modifier = Modifier.size(12.dp),
                        )
                    }
                    Text(
                        text = step.tool,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (failed) colors.danger else colors.accent,
                        modifier = Modifier.padding(start = if (failed) 4.dp else 0.dp),
                    )
                    Text(
                        text = step.label,
                        style = MaterialTheme.typography.labelSmall,
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
                            color = colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .widthIn(max = 120.dp)
                                .padding(start = 8.dp),
                        )
                    }
                }
            }
        }
    }
}
