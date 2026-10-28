package io.github.tournierjc.mercury.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.tournierjc.mercury.R
import io.github.tournierjc.mercury.data.Interaction
import io.github.tournierjc.mercury.data.InteractionKind
import io.github.tournierjc.mercury.ui.theme.LocalMercuryColors

/**
 * One card for every question the runtime can block on: tool approval, loop
 * "keep going?", free-form choice and auto-approval mode. Mercury answers all of
 * them through `POST /api/chat/permission/:id`, so only the copy varies.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InteractionCard(
    interaction: Interaction,
    onAction: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalMercuryColors.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surface, MaterialTheme.shapes.medium)
            .padding(18.dp),
    ) {
        Text(
            text = title(interaction.kind),
            style = MaterialTheme.typography.labelSmall,
            color = colors.accent,
        )
        Text(
            text = interaction.notice?.let { noticeText(it) } ?: interaction.text,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textPrimary,
            modifier = Modifier.padding(top = 8.dp, bottom = 14.dp),
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            interaction.options.forEach { option ->
                if (isSecondary(option)) {
                    OutlinedButton(
                        onClick = { onAction(option) },
                        shape = MaterialTheme.shapes.medium,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.textSecondary),
                    ) { Text(label(option)) }
                } else {
                    Button(
                        onClick = { onAction(option) },
                        shape = MaterialTheme.shapes.medium,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isPrimary(option)) colors.textPrimary else colors.surfaceMuted,
                            contentColor = if (isPrimary(option)) colors.canvas else colors.textPrimary,
                        ),
                    ) { Text(label(option)) }
                }
            }
        }
        Text(
            text = footnote(interaction.kind),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

@Composable
private fun title(kind: InteractionKind): String = when (kind) {
    InteractionKind.Permission -> stringResource(R.string.perm_title_permission)
    InteractionKind.Continue -> stringResource(R.string.perm_title_continue)
    InteractionKind.Choice -> stringResource(R.string.perm_title_choice)
    InteractionKind.Mode -> stringResource(R.string.perm_title_mode)
}

@Composable
private fun footnote(kind: InteractionKind): String = when (kind) {
    InteractionKind.Permission -> stringResource(R.string.perm_footnote_permission)
    InteractionKind.Continue -> stringResource(R.string.perm_footnote_continue)
    InteractionKind.Choice -> stringResource(R.string.perm_footnote_choice)
    InteractionKind.Mode -> stringResource(R.string.perm_footnote_mode)
}

@Composable
private fun label(action: String): String = when (action) {
    "yes", "confirm", "ok" -> stringResource(R.string.perm_action_allow)
    "always" -> stringResource(R.string.perm_action_always)
    "no", "cancel", "stop" -> stringResource(R.string.perm_action_deny)
    "ask-me" -> stringResource(R.string.perm_action_keep_asking)
    "allow-all" -> stringResource(R.string.perm_action_allow_all)
    else -> action.replaceFirstChar { it.uppercase() }
}

private fun isPrimary(action: String): Boolean = action in setOf("yes", "confirm", "ok", "always", "allow-all")

private fun isSecondary(action: String): Boolean = action in setOf("no", "cancel", "stop", "ask-me")
