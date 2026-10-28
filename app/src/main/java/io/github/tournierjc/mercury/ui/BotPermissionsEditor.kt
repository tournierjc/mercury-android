package io.github.tournierjc.mercury.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.github.tournierjc.mercury.R
import io.github.tournierjc.mercury.data.BotPermissionsDoc
import io.github.tournierjc.mercury.ui.theme.LocalMercuryColors
import io.github.tournierjc.mercury.vm.BotsUiState
import io.github.tournierjc.mercury.vm.BotsViewModel
import kotlinx.serialization.json.JsonObject

/**
 * The bot's permissions document, edited key by key: the paths it may read, write or execute under,
 * the commands it runs without asking and the ones it must never run, and the tools it may or may not
 * call.
 *
 * Every control writes one key and sends the whole document back — `PUT /api/bots/:id/permissions`
 * replaces the file, so each edit is merged into the document already loaded rather than composed
 * from scratch. That is what keeps `tier` and the keys this screen does not show alive.
 */
@Composable
fun BotPermissionsEditor(state: BotsUiState, viewModel: BotsViewModel) {
    // A bot is selected; without one there is nothing to edit or to warn about.
    state.selectedId ?: return
    // No document, no editor: without the loaded file there is nothing to merge into, and a fresh
    // document would erase the tier and every grant it does not mention.
    val doc = state.permissionsDraft ?: state.permissions ?: return
    val colors = LocalMercuryColors.current
    // Edits are local until the header's save button: a tap on a chip is a decision, not a write.
    val enabled = !state.busy
    val commit: (JsonObject) -> Unit = { next -> viewModel.draftPermissions(next) }

    SectionTitle(stringResource(R.string.bots_perm_paths_title))
    val grants = BotPermissionsDoc.paths(doc)
    if (grants.isEmpty()) {
        Text(
            text = stringResource(R.string.bots_perm_no_path),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
        )
    }
    grants.forEach { grant ->
        PathRow(
            grant = grant,
            enabled = enabled,
            onChange = { next -> commit(BotPermissionsDoc.withPath(doc, next)) },
            // A rename is one key change, not a removal and a write: `renamePath` keeps the row in
            // place, with its flags, instead of dropping it to the bottom of the list.
            onRename = { old, next -> commit(BotPermissionsDoc.renamePath(doc, old, next.scope)) },
            onRemove = { commit(BotPermissionsDoc.withoutPath(doc, grant.scope)) },
        )
    }
    AddFolderField(
        enabled = enabled,
        onAdd = { scope ->
            // Read only: a folder added by hand grants what the owner then lights up, and a new row
            // with nothing granted read as a bug.
            commit(BotPermissionsDoc.withPath(doc, BotPermissionsDoc.PathGrant(scope, read = true)))
        },
    )

    SectionTitle(stringResource(R.string.bots_perm_commands_group))
    CommaListField(
        label = stringResource(R.string.bots_perm_auto_title),
        hint = stringResource(R.string.bots_perm_commands_hint),
        stored = BotPermissionsDoc.commands(doc, BotPermissionsDoc.AUTO_APPROVE_COMMANDS),
        enabled = enabled,
        onCommit = { values -> commit(BotPermissionsDoc.withCommands(doc, BotPermissionsDoc.AUTO_APPROVE_COMMANDS, values)) },
    )

    CommaListField(
        label = stringResource(R.string.bots_perm_blocked_title),
        hint = stringResource(R.string.bots_perm_commands_hint),
        stored = BotPermissionsDoc.commands(doc, BotPermissionsDoc.BLOCKED_COMMANDS),
        enabled = enabled,
        onCommit = { values -> commit(BotPermissionsDoc.withCommands(doc, BotPermissionsDoc.BLOCKED_COMMANDS, values)) },
    )

    SectionTitle(stringResource(R.string.bots_perm_tools_group))
    CommaListField(
        label = stringResource(R.string.bots_perm_tools_allow),
        hint = stringResource(R.string.bots_perm_tools_hint),
        stored = BotPermissionsDoc.tools(doc, BotPermissionsDoc.ALLOW),
        enabled = enabled,
        onCommit = { values -> commit(BotPermissionsDoc.withTools(doc, BotPermissionsDoc.ALLOW, values)) },
    )
    CommaListField(
        label = stringResource(R.string.bots_perm_tools_deny),
        hint = stringResource(R.string.bots_perm_tools_hint),
        stored = BotPermissionsDoc.tools(doc, BotPermissionsDoc.DENY),
        enabled = enabled,
        onCommit = { values -> commit(BotPermissionsDoc.withTools(doc, BotPermissionsDoc.DENY, values)) },
    )

    Text(
        text = stringResource(R.string.bots_perm_note),
        style = MaterialTheme.typography.bodyMedium,
        color = colors.textSecondary,
        modifier = Modifier.padding(top = 12.dp, start = 4.dp),
    )
}

/** One `paths` entry: its folder, the three grants as chips, and a way out. */
@Composable
private fun PathRow(
    grant: BotPermissionsDoc.PathGrant,
    enabled: Boolean,
    onChange: (BotPermissionsDoc.PathGrant) -> Unit,
    onRename: (String, BotPermissionsDoc.PathGrant) -> Unit,
    onRemove: () -> Unit,
) {
    val colors = LocalMercuryColors.current
    // Keyed on the stored scope: a save elsewhere re-reads the document and must not leave a stale
    // buffer claiming a name the file no longer holds.
    var scope by remember(grant.scope) { mutableStateOf(grant.scope) }
    val applyRename = {
        val next = scope.trim()
        if (next.isNotEmpty() && next != grant.scope) onRename(grant.scope, grant.copy(scope = next)) else scope = grant.scope
    }

    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = scope,
                onValueChange = { scope = it },
                label = { Text(stringResource(R.string.bots_perm_folder)) },
                singleLine = true,
                enabled = enabled,
                textStyle = MaterialTheme.typography.bodyMedium,
                shape = MaterialTheme.shapes.extraSmall,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { applyRename() }),
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = onRemove,
                enabled = enabled,
                modifier = Modifier.size(ComposerControlSize),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.bots_perm_cd_remove_path, grant.scope),
                    tint = colors.textSecondary,
                    modifier = Modifier.size(ComposerControlIcon),
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp, start = 4.dp),
        ) {
            GrantChip(stringResource(R.string.bots_perm_read), grant.read, enabled) {
                onChange(grant.copy(read = it))
            }
            GrantChip(stringResource(R.string.bots_perm_write), grant.write, enabled) {
                onChange(grant.copy(write = it))
            }
            GrantChip(stringResource(R.string.bots_perm_execute), grant.execute, enabled) {
                onChange(grant.copy(execute = it))
            }
        }
    }
}

/**
 * A grant as a lit or outlined word — the vocabulary the tier cells already use.
 *
 * Three `Switch`es do not fit beside a folder field on a phone, and an unlabelled one is a riddle:
 * these are 30 dp high, tappable, and say what they grant.
 */
@Composable
private fun GrantChip(label: String, checked: Boolean, enabled: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val colors = LocalMercuryColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(MaterialTheme.shapes.extraSmall)
            .background(if (checked) colors.accent.copy(alpha = 0.16f) else Color.Transparent)
            .then(
                if (checked) Modifier
                else Modifier.border(1.dp, colors.hairlineStrong, MaterialTheme.shapes.extraSmall),
            )
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(horizontal = 10.dp, vertical = 7.dp),
    ) {
        if (checked) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = if (enabled) colors.accent else colors.accent.copy(alpha = 0.4f),
                modifier = Modifier.size(13.dp),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = when {
                !enabled -> colors.textSecondary.copy(alpha = 0.4f)
                checked -> colors.accent
                else -> colors.textSecondary
            },
            modifier = Modifier.padding(start = if (checked) 4.dp else 0.dp),
        )
    }
}

/** The folder to add: emptied once written, so the next one does not append to the last. */
@Composable
private fun AddFolderField(enabled: Boolean, onAdd: (String) -> Unit) {
    val colors = LocalMercuryColors.current
    var folder by remember { mutableStateOf("") }
    val add = {
        val scope = folder.trim()
        if (scope.isNotEmpty()) {
            onAdd(scope)
            folder = ""
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
    ) {
        OutlinedTextField(
            value = folder,
            onValueChange = { folder = it },
            label = { Text(stringResource(R.string.bots_perm_add_folder)) },
            placeholder = { Text(stringResource(R.string.bots_perm_folder_hint)) },
            singleLine = true,
            enabled = enabled,
            textStyle = MaterialTheme.typography.bodyMedium,
            shape = MaterialTheme.shapes.extraSmall,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { add() }),
            trailingIcon = {
                if (folder.isNotBlank()) {
                    IconButton(onClick = { add() }, enabled = enabled) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = stringResource(R.string.bots_perm_cd_add_folder),
                            tint = colors.accent,
                            modifier = Modifier.size(ComposerControlIcon),
                        )
                    }
                }
            },
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * A list the daemon stores as an array, edited as one comma-separated line.
 *
 * A line rather than a chip editor: these lists are short, and the daemon owns the vocabulary — a
 * hand-typed name that does not exist is the daemon's to refuse, not this field's to hide. Newlines
 * are accepted too, because pasting a list from the daemon's own file is the common case.
 */
@Composable
private fun CommaListField(
    label: String,
    hint: String,
    stored: List<String>,
    enabled: Boolean,
    onCommit: (List<String>) -> Unit,
) {
    val colors = LocalMercuryColors.current
    val joined = stored.joinToString(", ")
    // Keyed on what the file holds: after a save the field shows the normalised list, not the raw
    // commas that were typed.
    var text by remember(label, joined) { mutableStateOf(joined) }
    val commit = { onCommit(BotPermissionsDoc.splitList(text)) }

    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        label = { Text(label) },
        placeholder = { Text(hint) },
        singleLine = true,
        enabled = enabled,
        textStyle = MaterialTheme.typography.bodyMedium,
        shape = MaterialTheme.shapes.extraSmall,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { commit() }),
        trailingIcon = {
            if (text != joined) {
                IconButton(onClick = { commit() }, enabled = enabled) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = stringResource(R.string.bots_perm_cd_apply),
                        tint = colors.accent,
                        modifier = Modifier.size(ComposerControlIcon),
                    )
                }
            }
        },
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
    )
}
