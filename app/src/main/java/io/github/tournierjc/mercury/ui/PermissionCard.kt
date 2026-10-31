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
import io.github.tournierjc.mercury.data.ChoiceOption
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
    // DEC-9 (SPECS/115 §16, PARITE 28): a request with nothing to press keeps its card. The daemon
    // holds the turn open until its own timeout, so an early `return` here was the screen going
    // mute for two minutes. No button is drawn — the app invents no value to post — and the state
    // line takes the footnote's place, because a footnote asking to answer would be the lie.
    val hasOptions = interaction.options.isNotEmpty()
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
        if (hasOptions) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                interaction.options.forEach { option ->
                    // The route is keyed on the value, so that is what we post; the label is only
                    // ever what the button says.
                    val primary = interaction.kind != InteractionKind.Choice && isPrimary(option.value)
                    if (interaction.kind != InteractionKind.Choice && isSecondary(option.value)) {
                        OutlinedButton(
                            onClick = { onAction(option.value) },
                            shape = MaterialTheme.shapes.medium,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.textSecondary),
                        ) { Text(optionText(interaction.kind, option)) }
                    } else {
                        Button(
                            onClick = { onAction(option.value) },
                            shape = MaterialTheme.shapes.medium,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (primary) colors.textPrimary else colors.surfaceMuted,
                                contentColor = if (primary) colors.canvas else colors.textPrimary,
                            ),
                        ) { Text(optionText(interaction.kind, option)) }
                    }
                }
            }
            Text(
                text = footnote(interaction.kind),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
                modifier = Modifier.padding(top = 12.dp),
            )
        } else {
            // Nothing to press: one state line where the footnote was, muted like every other
            // status line of the app, and no button at all (DEC-9).
            Text(
                text = stringResource(R.string.perm_no_options),
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary,
            )
        }
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

/**
 * The button's wording: the server's [ChoiceOption.label] when it wrote one, our own copy
 * otherwise.
 *
 * A choice is the agent's own vocabulary, so its value is shown untouched — rewriting `yes` into
 * "Autoriser" or capitalising `src/main.kt` would answer a question the agent did not ask. The
 * approval prompts keep the localised mapping of their documented actions.
 */
@Composable
private fun optionText(kind: InteractionKind, option: ChoiceOption): String {
    val serverLabel = option.label
    val value = option.value
    return serverLabel ?: if (kind == InteractionKind.Choice) value else actionLabel(value)
}

@Composable
private fun actionLabel(action: String): String = when (action) {
    "yes", "confirm", "ok" -> stringResource(R.string.perm_action_allow)
    "always" -> stringResource(R.string.perm_action_always)
    "no", "cancel", "stop" -> stringResource(R.string.perm_action_deny)
    "ask-me" -> stringResource(R.string.perm_action_keep_asking)
    "allow-all" -> stringResource(R.string.perm_action_allow_all)
    else -> action.replaceFirstChar { it.uppercase() }
}

private fun isPrimary(action: String): Boolean = action in setOf("yes", "confirm", "ok", "always", "allow-all")

private fun isSecondary(action: String): Boolean = action in setOf("no", "cancel", "stop", "ask-me")
