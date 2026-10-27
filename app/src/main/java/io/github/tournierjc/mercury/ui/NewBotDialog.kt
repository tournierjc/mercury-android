package io.github.tournierjc.mercury.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.tournierjc.mercury.R
import io.github.tournierjc.mercury.data.BotSlug
import io.github.tournierjc.mercury.ui.theme.LocalMercuryColors

/**
 * A new teammate, from the app.
 *
 * Until now a bot could only be created with `POST /api/bots` or by hand in a folder: the app
 * could start, stop, disable and even delete one, but not make one. The form is deliberately
 * short — name, id, tier, persona — because the manifest has defaults for everything else and a
 * first bot should not require reading a spec.
 *
 * The id is a proposal derived from the name ([BotSlug]) and stays editable: the daemon refuses
 * duplicates and malformed ids, and its message is what the owner sees.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NewBotDialog(
    busy: Boolean,
    onDismiss: () -> Unit,
    onCreate: (name: String, id: String, tier: String, persona: String) -> Unit,
    /** "Nouvel équipier" from the roster, "Nouveau membre" from a fleet. */
    title: String = stringResource(R.string.newbot_title),
    submitLabel: String = stringResource(R.string.newbot_submit),
) {
    var name by remember { mutableStateOf("") }
    var id by remember { mutableStateOf("") }
    // Once the owner edits the id, the name stops driving it: an id that keeps changing under
    // the caret is a form fighting its user.
    var idEdited by remember { mutableStateOf(false) }
    var tier by remember { mutableStateOf("readonly") }
    var persona by remember { mutableStateOf("") }

    // An empty name proposes nothing: the field shows its placeholder, not the fallback "bot"
    // the slug uses when there is nothing to derive from.
    val proposedId = remember(name) { if (name.isBlank()) "" else BotSlug.from(name) }
    val shownId = if (idEdited) id else proposedId

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.newbot_label_name)) },
                    placeholder = { Text(stringResource(R.string.newbot_placeholder_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = shownId,
                    onValueChange = {
                        idEdited = true
                        id = BotSlug.from(it)
                    },
                    label = { Text(stringResource(R.string.newbot_label_id)) },
                    supportingText = { Text(stringResource(R.string.newbot_supporting_id)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(R.string.newbot_label_tier),
                    style = MaterialTheme.typography.labelLarge,
                )
                // FlowRow, not Row: four chips do not fit on a narrow phone, and a chip cut
                // off at the edge is a choice the owner cannot make.
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        "readonly" to stringResource(R.string.newbot_tier_read_only),
                        "builder" to stringResource(R.string.newbot_tier_write),
                        "operator" to stringResource(R.string.newbot_tier_operator),
                        "full" to stringResource(R.string.newbot_tier_full),
                    ).forEach { (tierId, label) ->
                        FilterChip(
                            selected = tier == tierId,
                            onClick = { tier = tierId },
                            label = { Text(label) },
                        )
                    }
                }
                OutlinedTextField(
                    value = persona,
                    onValueChange = { persona = it },
                    label = { Text(stringResource(R.string.newbot_label_persona)) },
                    placeholder = { Text(stringResource(R.string.newbot_placeholder_persona)) },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy && name.isNotBlank(),
                onClick = { onCreate(name, shownId, tier, persona) },
            ) {
                Text(submitLabel)
            }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = onDismiss) { Text(stringResource(R.string.newbot_action_cancel)) }
        },
    )
}

/** Deleting a bot takes its profile with it: asked once, plainly. */
@Composable
fun ConfirmDeleteBotDialog(
    botName: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.newbot_confirm_delete_title, botName)) },
        text = {
            Text(stringResource(R.string.newbot_confirm_delete_body))
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.newbot_action_delete), color = LocalMercuryColors.current.danger)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.newbot_action_cancel)) }
        },
    )
}
