package io.github.tournierjc.mercury.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.tournierjc.mercury.R
import io.github.tournierjc.mercury.data.BotConfigPatch
import io.github.tournierjc.mercury.data.BotJson
import io.github.tournierjc.mercury.ui.theme.LocalMercuryColors
import io.github.tournierjc.mercury.vm.BotsUiState
import io.github.tournierjc.mercury.vm.BotsViewModel
import kotlinx.serialization.json.JsonObject

/**
 * What a bot remembers and what it keeps: the `memory` and `retention` objects of its manifest.
 *
 * The daemon replaces those objects whole on a `PATCH`, so every control here writes the object
 * back complete — [BotConfigPatch] does the merge, and each section only names the field that
 * changed. An empty field is the daemon's default, which is what the placeholder says: the sheet
 * never invents a number the instance did not choose.
 */
@Composable
fun BotMemorySection(state: BotsUiState, viewModel: BotsViewModel) {
    val colors = LocalMercuryColors.current
    val bot = state.manifest ?: return
    val id = state.selectedId.orEmpty()
    val scope = BotConfigPatch.text(bot, BotConfigPatch.MEMORY, BotConfigPatch.SCOPE).orEmpty()
    val learn = BotConfigPatch.flag(bot, BotConfigPatch.MEMORY, BotConfigPatch.LEARN) ?: true
    val recall = BotConfigPatch.strings(bot, BotConfigPatch.MEMORY, BotConfigPatch.ALLOW_CROSS_BOT_RECALL)

    CollapsibleSection(stringResource(R.string.bots_section_memory)) {
        Text(
            text = stringResource(R.string.bots_memory_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        // The three scopes are a row of cells, not a dropdown: the sentence under it says what
        // the chosen one does, which a three-word label cannot.
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            listOf(
                BotConfigPatch.SCOPE_NONE to stringResource(R.string.bots_memory_scope_none),
                BotConfigPatch.SCOPE_OWN to stringResource(R.string.bots_memory_scope_own),
                BotConfigPatch.SCOPE_SHARED_READ to stringResource(R.string.bots_memory_scope_shared),
            ).forEach { (value, label) ->
                ScopeCell(
                    label = label,
                    selected = scope == value,
                    modifier = Modifier.weight(1f),
                    onClick = { if (scope != value) viewModel.setMemoryScope(id, value) },
                )
            }
        }
        // No verb when the manifest holds no scope at all: the daemon's own default is unknown to
        // the phone, so it says nothing rather than guessing "none".
        verbFor(scope)?.let { verb ->
            Text(
                text = verb,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        // `learn` defaults to true on the daemon's side, so an absent key reads as on.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 10.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.bots_memory_learn),
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.textPrimary,
                )
                Text(
                    text = stringResource(R.string.bots_memory_learn_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
            }
            Switch(
                checked = learn,
                enabled = !state.busy,
                onCheckedChange = { viewModel.setMemoryLearn(id, it) },
            )
        }

        if (scope == BotConfigPatch.SCOPE_SHARED_READ) {
            Text(
                text = stringResource(R.string.bots_memory_recall),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.textPrimary,
                modifier = Modifier.padding(top = 12.dp),
            )
            Text(
                text = stringResource(R.string.bots_memory_recall_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
            RecallPicker(
                roster = state.roster,
                self = id,
                selected = recall,
                enabled = !state.busy,
                onToggle = { target ->
                    viewModel.setMemoryRecall(id, if (target in recall) recall - target else recall + target)
                },
            )
        }
    }
}

/**
 * What the daemon keeps for one bot, and for how long.
 *
 * The six numbers and the janitor's two durations are fields, not pickers: the instance's fair
 * values depend on its disk and its traffic, and the phone has no basis to propose one.
 */
@Composable
fun BotRetentionSection(state: BotsUiState, viewModel: BotsViewModel) {
    val colors = LocalMercuryColors.current
    val bot = state.manifest ?: return
    val id = state.selectedId.orEmpty()

    CollapsibleSection(stringResource(R.string.bots_section_retention)) {
        Text(
            text = stringResource(R.string.bots_retention_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        retentionFields.forEach { field ->
            ConfigNumberField(
                label = stringResource(field.label),
                hint = stringResource(field.hint),
                value = BotConfigPatch.number(bot, BotConfigPatch.RETENTION, field.key),
                onApply = { viewModel.setRetentionLimit(id, field.key, it) },
            )
        }

        val janitorOn = BotConfigPatch.janitorEnabled(bot) ?: false
        val hotDays = BotConfigPatch.janitorDays(bot, BotConfigPatch.HOT_DAYS)
        val archiveDays = BotConfigPatch.janitorDays(bot, BotConfigPatch.ARCHIVE_DAYS)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 14.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.bots_label_janitor),
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.textPrimary,
                )
                Text(
                    text = stringResource(R.string.bots_hint_janitor),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
            }
            Switch(
                checked = janitorOn,
                enabled = !state.busy,
                onCheckedChange = { on ->
                    // The two durations travel with the switch: the sub-object is replaced whole.
                    viewModel.setSandboxJanitor(id, enabled = on, hotDays = hotDays, archiveDays = archiveDays)
                },
            )
        }
        // With the janitor off there is no window to set: the fields stay visible, greyed, so the
        // values are not lost from view — and they are still whatever the manifest holds.
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            ConfigNumberField(
                label = stringResource(R.string.bots_label_janitor_hot),
                hint = stringResource(R.string.bots_hint_janitor_hot),
                value = hotDays,
                enabled = janitorOn && !state.busy,
                modifier = Modifier.weight(1f),
                onApply = { viewModel.setSandboxJanitor(id, janitorOn, it, archiveDays) },
            )
            ConfigNumberField(
                label = stringResource(R.string.bots_label_janitor_archive),
                hint = stringResource(R.string.bots_hint_janitor_archive),
                value = archiveDays,
                enabled = janitorOn && !state.busy,
                modifier = Modifier.weight(1f).padding(start = 8.dp),
                onApply = { viewModel.setSandboxJanitor(id, janitorOn, hotDays, it) },
            )
        }
    }
}

/** One retention key, with the strings that name it. */
private data class RetentionField(val key: String, val label: Int, val hint: Int)

private val retentionFields = listOf(
    RetentionField(BotConfigPatch.TRANSCRIPT_RUNS, R.string.bots_label_transcript_runs, R.string.bots_hint_transcript_runs),
    RetentionField(BotConfigPatch.JOURNAL_ROTATE_BYTES, R.string.bots_label_journal_rotate, R.string.bots_hint_journal_rotate),
    RetentionField(BotConfigPatch.JOURNAL_KEEP_ROTATIONS, R.string.bots_label_journal_keep, R.string.bots_hint_journal_keep),
    RetentionField(BotConfigPatch.MAILBOX_TTL_HOURS, R.string.bots_label_mailbox_ttl, R.string.bots_hint_mailbox_ttl),
    RetentionField(BotConfigPatch.DLQ_CAP, R.string.bots_label_dlq_cap, R.string.bots_hint_dlq_cap),
    RetentionField(BotConfigPatch.ARTIFACT_QUOTA_BYTES, R.string.bots_label_artifact_quota, R.string.bots_hint_artifact_quota),
)

/**
 * One cell of the scope row: filled and ticked when it is the bot's scope, outlined otherwise.
 * A tinted label alone is unreadable at arm's length on a phone.
 */
@Composable
private fun ScopeCell(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = LocalMercuryColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = modifier
            .clip(MaterialTheme.shapes.extraSmall)
            .background(if (selected) colors.accent.copy(alpha = 0.16f) else Color.Transparent)
            .then(if (selected) Modifier else Modifier.border(1.dp, colors.hairlineStrong, MaterialTheme.shapes.extraSmall))
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
    ) {
        if (selected) {
            Icon(Icons.Rounded.Check, contentDescription = null, tint = colors.accent, modifier = Modifier.padding(end = 4.dp).size(13.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) colors.accent else colors.textSecondary,
            maxLines = 1,
        )
    }
}

/**
 * The bots whose learned context this one may re-read.
 *
 * The list is the roster minus the bot itself: the daemon has no use for a self-grant, and
 * offering it would only let the owner create one.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecallPicker(
    roster: List<JsonObject>,
    self: String,
    selected: List<String>,
    enabled: Boolean,
    onToggle: (String) -> Unit,
) {
    val colors = LocalMercuryColors.current
    val others = roster.filter { BotJson.id(it) != self && BotJson.id(it).isNotBlank() }
    if (others.isEmpty()) {
        Text(
            text = stringResource(R.string.bots_memory_recall_empty),
            style = MaterialTheme.typography.labelSmall,
            color = colors.textSecondary,
            modifier = Modifier.padding(top = 6.dp),
        )
        return
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        others.forEach { bot ->
            val id = BotJson.id(bot)
            FilterChip(
                selected = id in selected,
                enabled = enabled,
                onClick = { onToggle(id) },
                label = { Text(BotJson.name(bot), style = MaterialTheme.typography.labelSmall) },
            )
        }
    }
}

/**
 * A retention number: digits only, applied on the keyboard's "done" or the tick.
 *
 * Same shape as `LimitField` in BotsScreen, widened to `Long` — a byte quota (gigabytes) does
 * not fit an `Int`, and an `Int` overflow would silently write a truncated cap.
 */
@Composable
private fun ConfigNumberField(
    label: String,
    hint: String,
    value: Long?,
    onApply: (Long?) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = LocalMercuryColors.current
    var text by remember(value) { mutableStateOf(value?.toString().orEmpty()) }
    val apply = { onApply(text.trim().toLongOrNull()) }
    OutlinedTextField(
        value = text,
        onValueChange = { typed -> text = typed.filter { it.isDigit() } },
        label = { Text(label) },
        // The daemon's own default, not a number this screen made up.
        placeholder = { Text(stringResource(R.string.bots_config_default)) },
        singleLine = true,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { apply() }),
        shape = MaterialTheme.shapes.extraSmall,
        trailingIcon = {
            if (enabled && text != value?.toString().orEmpty()) {
                IconButton(onClick = { apply() }) {
                    Icon(Icons.Rounded.Check, contentDescription = label, tint = colors.accent)
                }
            }
        },
        modifier = modifier.fillMaxWidth().padding(top = 4.dp),
    )
}

/** The sentence for the selected scope; null when the manifest names none. */
@Composable
private fun verbFor(scope: String): String? = when (scope) {
    BotConfigPatch.SCOPE_NONE -> stringResource(R.string.bots_memory_scope_none_verb)
    BotConfigPatch.SCOPE_OWN -> stringResource(R.string.bots_memory_scope_own_verb)
    BotConfigPatch.SCOPE_SHARED_READ -> stringResource(R.string.bots_memory_scope_shared_verb)
    else -> null
}
