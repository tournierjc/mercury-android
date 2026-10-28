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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Create
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.tournierjc.mercury.R
import io.github.tournierjc.mercury.data.BotRoutines
import io.github.tournierjc.mercury.ui.theme.LocalMercuryColors
import io.github.tournierjc.mercury.vm.BotsUiState
import io.github.tournierjc.mercury.vm.BotsViewModel

/** The name key of the add form: a routine name is the identity, so `""` is free to mean "new". */
private const val NEW_ROUTINE = ""

/**
 * Les routines d'un équipier : les créneaux cron du manifeste, réglés depuis sa fiche.
 *
 * Chaque écriture repart de la liste déjà chargée et renvoie le tableau entier — un PATCH remplace
 * `schedules`, donc envoyer une seule entrée effacerait les autres (cf. [BotRoutines]).
 */
@Composable
fun BotRoutinesSection(state: BotsUiState, viewModel: BotsViewModel) {
    val colors = LocalMercuryColors.current
    val botId = state.selectedId.orEmpty()
    val routines = BotRoutines.list(state.manifest)
    // Le nom de la routine dont le formulaire est ouvert ; null : aucun, "" : une nouvelle.
    var editing by remember(botId) { mutableStateOf<String?>(null) }
    var confirming by remember(botId) { mutableStateOf<String?>(null) }

    CollapsibleSection(stringResource(R.string.routines_section_title)) {
        // L'état vide explique à quoi sert un créneau ; il s'efface dès qu'on en écrit un.
        if (routines.isEmpty() && editing != NEW_ROUTINE) {
            Text(
                text = stringResource(R.string.routines_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }

        routines.forEach { stored ->
            val routine = BotRoutines.read(stored)
            RoutineRow(
                routine = routine,
                onEdit = { editing = if (editing == routine.name) null else routine.name },
                onDelete = { confirming = routine.name },
            )
            if (editing == routine.name) {
                RoutineForm(
                    initial = routine,
                    busy = state.busy,
                    onCancel = { editing = null },
                    onSave = { edited ->
                        editing = null
                        viewModel.saveRoutine(botId, edited)
                    },
                )
            }
        }

        when (editing) {
            null -> TextButton(onClick = { editing = NEW_ROUTINE }, enabled = !state.busy) {
                Icon(
                    Icons.Rounded.Add,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(ComposerControlIcon),
                )
                Text(
                    text = stringResource(R.string.routines_action_add),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.accent,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
            NEW_ROUTINE -> RoutineForm(
                initial = null,
                busy = state.busy,
                onCancel = { editing = null },
                onSave = { added ->
                    editing = null
                    viewModel.saveRoutine(botId, added)
                },
            )
        }
    }

    // La suppression est irréversible côté manifeste : elle demande, ceci retient la question.
    confirming?.let { name ->
        AlertDialog(
            onDismissRequest = { confirming = null },
            title = { Text(stringResource(R.string.routines_confirm_delete_title, name)) },
            text = { Text(stringResource(R.string.routines_confirm_delete_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirming = null
                        viewModel.deleteRoutine(botId, name)
                    },
                ) {
                    Text(stringResource(R.string.routines_action_delete), color = colors.danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirming = null }) {
                    Text(stringResource(R.string.routines_action_cancel))
                }
            },
        )
    }
}

/** Une routine telle qu'elle est stockée : nom, cron, badges, puis le prompt sur demande. */
@Composable
private fun RoutineRow(
    routine: BotRoutines.Routine,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = LocalMercuryColors.current
    // Le prompt peut faire plusieurs paragraphes : il reste replié tant qu'on ne le demande pas.
    var showPrompt by rememberSaveable(routine.name) { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = routine.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = listOfNotNull(routine.cron, cronCadenceLabel(routine.cron)).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary,
                )
            }
            IconButton(onClick = onEdit, modifier = Modifier.size(ComposerControlSize)) {
                Icon(
                    Icons.Rounded.Create,
                    contentDescription = stringResource(R.string.routines_cd_edit, routine.name),
                    tint = colors.accent,
                    modifier = Modifier.size(ComposerControlIcon),
                )
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(ComposerControlSize)) {
                Icon(
                    Icons.Rounded.DeleteOutline,
                    contentDescription = stringResource(R.string.routines_cd_delete, routine.name),
                    tint = colors.danger,
                    modifier = Modifier.size(ComposerControlIcon),
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(top = 2.dp),
        ) {
            RoutineBadge(deliverLabel(routine.deliver))
            RoutineBadge(expectsLabel(routine.expects))
            routine.minIntervalMinutes?.let { minutes ->
                RoutineBadge(stringResource(R.string.routines_badge_interval, minutes))
            }
        }
        TextButton(onClick = { showPrompt = !showPrompt }) {
            Text(
                text = stringResource(
                    if (showPrompt) R.string.routines_action_hide_prompt else R.string.routines_action_show_prompt,
                ),
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary,
            )
        }
        if (showPrompt) {
            Text(
                text = routine.prompt,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = colors.textPrimary,
                modifier = Modifier.padding(start = 12.dp, bottom = 6.dp),
            )
        }
    }
}

/**
 * Le formulaire, en ligne sous la routine qu'il édite.
 *
 * Il ne peut pas enregistrer un nom vide, un prompt vide ou un cron qui n'a pas cinq champs : le
 * refus est visible ici plutôt que dans le silence du démon, qui stocke un cron invalide et ne le
 * déclenche jamais.
 */
@Composable
private fun RoutineForm(
    initial: BotRoutines.Routine?,
    busy: Boolean,
    onCancel: () -> Unit,
    onSave: (BotRoutines.Routine) -> Unit,
) {
    val colors = LocalMercuryColors.current
    var name by remember(initial) { mutableStateOf(initial?.name.orEmpty()) }
    var cron by remember(initial) { mutableStateOf(initial?.cron.orEmpty()) }
    var prompt by remember(initial) { mutableStateOf(initial?.prompt.orEmpty()) }
    var deliver by remember(initial) { mutableStateOf(initial?.deliver ?: BotRoutines.DELIVER_BOT_CHAT) }
    var expects by remember(initial) { mutableStateOf(initial?.expects ?: BotRoutines.EXPECTS_WORK) }
    var interval by remember(initial) { mutableStateOf(initial?.minIntervalMinutes?.toString().orEmpty()) }
    var gate by remember(initial) { mutableStateOf(initial?.gateScript.orEmpty()) }

    val cronOk = BotRoutines.isValidCron(cron)
    val ready = !busy && name.isNotBlank() && prompt.isNotBlank() && cronOk

    Column(modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 10.dp)) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(stringResource(R.string.routines_label_name)) },
            singleLine = true,
            shape = MaterialTheme.shapes.extraSmall,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = cron,
            onValueChange = { cron = it },
            label = { Text(stringResource(R.string.routines_label_cron)) },
            placeholder = { Text(stringResource(R.string.routines_cron_example)) },
            singleLine = true,
            // Le cron est une expression, pas une phrase : la chasse fixe aligne les champs.
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            isError = cron.isNotBlank() && !cronOk,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            shape = MaterialTheme.shapes.extraSmall,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        )
        if (cron.isNotBlank() && !cronOk) {
            Text(
                text = stringResource(R.string.routines_error_cron),
                style = MaterialTheme.typography.labelSmall,
                color = colors.danger,
                modifier = Modifier.padding(start = 4.dp, top = 2.dp),
            )
        }
        OutlinedTextField(
            value = prompt,
            onValueChange = { prompt = it },
            label = { Text(stringResource(R.string.routines_label_prompt)) },
            minLines = 3,
            textStyle = MaterialTheme.typography.bodySmall,
            shape = MaterialTheme.shapes.extraSmall,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        )

        FormLabel(stringResource(R.string.routines_label_deliver))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            BotRoutines.delivers.forEach { value ->
                Pill(
                    label = deliverLabel(value),
                    selected = deliver == value,
                    onClick = { deliver = value },
                )
            }
        }

        FormLabel(stringResource(R.string.routines_label_expects))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            BotRoutines.allExpects.forEach { value ->
                Pill(
                    label = expectsLabel(value),
                    selected = expects == value,
                    onClick = { expects = value },
                )
            }
        }

        OutlinedTextField(
            value = interval,
            onValueChange = { typed -> interval = typed.filter { it.isDigit() } },
            label = { Text(stringResource(R.string.routines_label_interval)) },
            placeholder = { Text(stringResource(R.string.routines_hint_interval)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
            shape = MaterialTheme.shapes.extraSmall,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        )
        OutlinedTextField(
            value = gate,
            onValueChange = { gate = it },
            label = { Text(stringResource(R.string.routines_label_gate)) },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            shape = MaterialTheme.shapes.extraSmall,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        )

        Row(
            horizontalArrangement = Arrangement.End,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        ) {
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.routines_action_cancel), style = MaterialTheme.typography.labelSmall)
            }
            TextButton(
                enabled = ready,
                onClick = {
                    onSave(
                        BotRoutines.Routine(
                            name = name.trim(),
                            cron = cron.trim(),
                            prompt = prompt.trim(),
                            deliver = deliver,
                            expects = expects,
                            // Zéro n'est pas un intervalle : c'est l'absence de minimum.
                            minIntervalMinutes = interval.trim().toIntOrNull()?.takeIf { it > 0 },
                            gateScript = gate.trim().ifBlank { null },
                        ),
                    )
                },
            ) {
                Text(
                    text = stringResource(R.string.routines_action_save),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (ready) colors.accent else colors.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun FormLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = LocalMercuryColors.current.textSecondary,
        modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 4.dp),
    )
}

/** Une pastille de choix : remplie quand elle est active, cerclée sinon. */
@Composable
private fun Pill(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalMercuryColors.current
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = if (selected) colors.accent else colors.textSecondary,
        modifier = Modifier
            .clip(MaterialTheme.shapes.extraSmall)
            .background(if (selected) colors.accent.copy(alpha = 0.16f) else Color.Transparent)
            .then(
                if (selected) Modifier
                else Modifier.border(1.dp, colors.hairlineStrong, MaterialTheme.shapes.extraSmall),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

@Composable
private fun RoutineBadge(text: String) {
    val colors = LocalMercuryColors.current
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = colors.textSecondary,
        modifier = Modifier
            .clip(MaterialTheme.shapes.extraSmall)
            .background(colors.surfaceMuted)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
private fun deliverLabel(value: String): String = when (value) {
    BotRoutines.DELIVER_TELEGRAM -> stringResource(R.string.routines_value_deliver_telegram)
    BotRoutines.DELIVER_NONE -> stringResource(R.string.routines_value_deliver_none)
    else -> stringResource(R.string.routines_value_deliver_bot_chat)
}

@Composable
private fun expectsLabel(value: String): String = when (value) {
    BotRoutines.EXPECTS_CHECK -> stringResource(R.string.routines_value_expects_check)
    else -> stringResource(R.string.routines_value_expects_work)
}
