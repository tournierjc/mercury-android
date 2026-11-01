package io.github.tournierjc.mercury.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import io.github.tournierjc.mercury.R
import io.github.tournierjc.mercury.data.CodeMode
import io.github.tournierjc.mercury.ui.theme.LocalMercuryColors

/**
 * The programming mode, where the decision is actually made: above the field you are about to type in.
 *
 * It lived in the console, among the daemon's disk usage and its plugins — a supervision screen, for
 * a setting that changes what the agent does with the *next message*. Moved here, it reads as what it
 * is: a property of this conversation. Quiet when it is off, so it does not become furniture, and
 * coloured when a mode is engaged, because then it changes every reply.
 */
@Composable
fun CodeModeBar(
    mode: CodeMode,
    available: Boolean,
    onPick: (CodeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalMercuryColors.current
    var open by remember { mutableStateOf(false) }
    if (!available) return

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 6.dp, end = 6.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            // Le déclencheur repose à 48 dp : il est juste au-dessus du champ où le pouce se pose,
            // et le mode engagé se dit comme un état, pas seulement comme une couleur.
            val stateText = stringResource(if (mode.engaged) R.string.chat_mode_state_on else R.string.chat_mode_state_off)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(50))
                    .then(
                        if (mode.engaged) {
                            Modifier.background(colors.accent.copy(alpha = 0.14f))
                        } else {
                            Modifier.border(1.dp, colors.hairline, RoundedCornerShape(50))
                        },
                    )
                    .clickable { open = true }
                    .semantics { stateDescription = stateText }
                    .padding(horizontal = 9.dp, vertical = 4.dp),
            ) {
                Icon(
                    Icons.Rounded.Code,
                    contentDescription = null,
                    tint = if (mode.engaged) colors.accent else colors.textSecondary,
                    modifier = Modifier.size(13.dp),
                )
                Text(
                    text = modeLabel(mode),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (mode.engaged) colors.accent else colors.textSecondary,
                    modifier = Modifier.padding(start = 5.dp),
                )
                if (mode.engaged) {
                    Box(
                        modifier = Modifier
                            .padding(start = 6.dp)
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(colors.accent),
                    )
                }
            }
            DropdownMenu(
                expanded = open,
                onDismissRequest = { open = false },
                modifier = Modifier.widthIn(min = 260.dp, max = 320.dp),
            ) {
                Text(
                    text = stringResource(R.string.chat_mode_title),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(start = 14.dp, top = 6.dp, bottom = 2.dp),
                )
                CodeMode.entries.forEach { candidate ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = candidate == mode,
                                role = Role.RadioButton,
                            ) {
                                open = false
                                if (candidate != mode) onPick(candidate)
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = modeLabel(candidate),
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (candidate == mode) colors.accent else colors.textPrimary,
                            )
                            Text(
                                text = modeDescription(candidate),
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textSecondary,
                            )
                        }
                        if (candidate == mode) {
                            Icon(
                                Icons.Rounded.Check,
                                contentDescription = null,
                                tint = colors.accent,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** The four names, as the console translated them, plus the one the agent is in now. */
@Composable
fun modeLabel(mode: CodeMode): String = stringResource(
    when (mode) {
        CodeMode.Off -> R.string.chat_mode_off
        CodeMode.Auto -> R.string.chat_mode_auto
        CodeMode.Plan -> R.string.chat_mode_plan
        CodeMode.Execute -> R.string.chat_mode_execute
    },
)

@Composable
private fun modeDescription(mode: CodeMode): String = stringResource(
    when (mode) {
        CodeMode.Off -> R.string.chat_mode_off_hint
        CodeMode.Auto -> R.string.chat_mode_auto_hint
        CodeMode.Plan -> R.string.chat_mode_plan_hint
        CodeMode.Execute -> R.string.chat_mode_execute_hint
    },
)
