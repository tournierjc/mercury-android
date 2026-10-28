package io.github.tournierjc.mercury.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.tournierjc.mercury.MercuryApp
import io.github.tournierjc.mercury.R
import io.github.tournierjc.mercury.data.dto.MEMORY_TYPES
import io.github.tournierjc.mercury.data.dto.BeliefDto
import io.github.tournierjc.mercury.data.dto.MemoryDto
import io.github.tournierjc.mercury.ui.theme.LocalMercuryColors
import io.github.tournierjc.mercury.vm.MemoryTab
import io.github.tournierjc.mercury.vm.MemoryUiState
import io.github.tournierjc.mercury.vm.MemoryViewModel

/**
 * What the agent remembers and believes, from the phone: search, add, correct, forget, and inspect
 * what it set aside.
 *
 * Four sections, the webapp's four: what it keeps conscious, what it only reaches by association,
 * the beliefs it drew from both, and what it quarantined. There used to be a fifth, « À propos »,
 * holding the runtime's identity and a count of the graph nodes — facts about the *instance*, not
 * about its memory, and now the Status screen's.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MemoryScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val container = remember { (context.applicationContext as MercuryApp).container }
    val viewModel: MemoryViewModel = viewModel(factory = MemoryViewModel.factory(container))
    val state by viewModel.state.collectAsState()
    val colors = LocalMercuryColors.current

    var showAdd by remember { mutableStateOf(false) }
    var correcting by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.canvas)
            .safeDrawingPadding(),
    ) {
        ScreenHeader(
            title = stringResource(R.string.memory_titre),
            subtitle = stringResource(
                R.string.memory_sous_titre,
                state.consciousTotal + state.subconsciousTotal,
                state.beliefs.size,
            ),
            onBack = onBack,
            onRefresh = viewModel::refresh,
            refreshing = state.loading,
        )
        state.message?.let { notice -> NoticeBanner(notice, onDismiss = viewModel::clearBanner) }
        state.error?.let { notice -> NoticeBanner(notice, onDismiss = viewModel::clearBanner) }

        SegmentedTabs(
            labels = MemoryTab.entries.map { it.label() },
            selected = state.tab.ordinal,
            onSelect = { index -> viewModel.selectTab(MemoryTab.entries[index]) },
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp),
        ) {
            when (state.tab) {
                MemoryTab.Conscious -> ConsciousTab(state, viewModel, onAdd = { showAdd = true })
                MemoryTab.Subconscious -> SubconsciousTab(state, viewModel)
                MemoryTab.Beliefs -> BeliefsTab(state, viewModel, onCorrect = { correcting = it })
                MemoryTab.Quarantine -> QuarantineTab(state, viewModel)
            }
            Text(
                text = " ",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(bottom = 24.dp),
            )
        }
    }

    if (showAdd) {
        AddMemoryDialog(
            onDismiss = { showAdd = false },
            onConfirm = { type, summary, detail ->
                showAdd = false
                viewModel.addMemory(type, summary, detail)
            },
        )
    }

    correcting?.let { id ->
        CorrectBeliefDialog(
            onDismiss = { correcting = null },
            onConfirm = { statement ->
                correcting = null
                viewModel.correctBelief(id, statement)
            },
        )
    }
}

/** What the brain calls on without being asked — the memories it acts from. */
@Composable
private fun ConsciousTab(state: MemoryUiState, viewModel: MemoryViewModel, onAdd: () -> Unit) {
    val colors = LocalMercuryColors.current
    OutlinedTextField(
        value = state.query,
        onValueChange = viewModel::search,
        label = { Text(stringResource(R.string.memory_rechercher)) },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
    )
    CollapsibleSection(stringResource(R.string.memory_souvenirs), defaultOpen = true) {
        Text(
            text = stringResource(R.string.brain_conscient_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
        )
        if (state.conscious.isEmpty()) {
            Text(
                text = if (state.loading) {
                    stringResource(R.string.common_loading)
                } else {
                    stringResource(R.string.memory_aucun_souvenir)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        state.conscious.forEach { memory ->
            MemoryRow(
                memory = memory,
                deleteLabel = stringResource(R.string.memory_oublier_souvenir),
                onForget = { viewModel.forgetMemory(memory.id) },
            )
        }
        IconActionBar(
            listOf(
                QuickAction(
                    icon = Icons.Rounded.Add,
                    label = stringResource(R.string.memory_ajouter),
                    tint = colors.accent,
                    onClick = onAdd,
                ),
            ),
        )
    }
}

/**
 * What the brain keeps below the threshold: present, but only recalled when a conversation touches
 * it. Nothing is added from here — a memory does not choose to be subconscious.
 */
@Composable
private fun SubconsciousTab(state: MemoryUiState, viewModel: MemoryViewModel) {
    val colors = LocalMercuryColors.current
    CollapsibleSection(stringResource(R.string.brain_sous_seuil_titre), defaultOpen = true) {
        Text(
            text = stringResource(R.string.brain_sous_seuil_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
        )
        if (state.subconscious.isEmpty()) {
            Text(
                text = if (state.loading) {
                    stringResource(R.string.common_loading)
                } else {
                    stringResource(R.string.memory_aucun_souvenir)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        state.subconscious.forEach { memory ->
            MemoryRow(
                memory = memory,
                deleteLabel = stringResource(R.string.memory_oublier_souvenir),
                onForget = { viewModel.forgetMemory(memory.id) },
            )
        }
    }
}

/**
 * One memory card, shared by the memory screen and the goals screen: a type label, the summary,
 * the detail, the two scores, and the bin. Text breathes on the left, verbs live in a column on
 * the right — a button under the last line reads as a footnote to the card.
 *
 * [deleteLabel] is said out loud for a screen reader, so a goal is not announced as a memory.
 */
@Composable
internal fun MemoryRow(memory: MemoryDto, deleteLabel: String, onForget: () -> Unit) {
    val colors = LocalMercuryColors.current
    // Forgetting is not undoable, and the glyph sat two dp from the card's edge: one stray thumb
    // erased a memory for good. Ask first.
    var confirming by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .background(colors.surfaceMuted, MaterialTheme.shapes.extraSmall)
            .padding(start = 12.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = rawLabel(memory.type),
                style = MaterialTheme.typography.labelSmall,
                color = colors.accent,
            )
            Text(
                text = memory.summary,
                style = MaterialTheme.typography.bodyLarge,
                color = colors.textPrimary,
                modifier = Modifier.padding(top = 4.dp),
            )
            memory.detail?.takeIf { it.isNotBlank() }?.let { detail ->
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(top = 4.dp),
                    maxLines = 4,
                )
            }
            Text(
                text = stringResource(
                    R.string.memory_souvenir_scores,
                    (memory.confidence * 100).toInt(),
                    (memory.importance * 100).toInt(),
                    memory.scope,
                ),
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            IconButton(onClick = { confirming = true }, modifier = Modifier.size(48.dp)) {
                Icon(
                    Icons.Rounded.DeleteOutline,
                    contentDescription = deleteLabel,
                    tint = colors.danger,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }

    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            containerColor = colors.surface,
            title = { Text(stringResource(R.string.memory_forget_title), color = colors.textPrimary) },
            text = { Text(memory.summary, color = colors.textSecondary, maxLines = 3) },
            confirmButton = {
                TextButton(onClick = {
                    confirming = false
                    onForget()
                }) {
                    Text(deleteLabel, color = colors.danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirming = false }) {
                    Text(stringResource(R.string.memory_forget_cancel), color = colors.textSecondary)
                }
            },
        )
    }
}

@Composable
private fun BeliefsTab(state: MemoryUiState, viewModel: MemoryViewModel, onCorrect: (String) -> Unit) {
    val colors = LocalMercuryColors.current
    CollapsibleSection(stringResource(R.string.memory_croyances), defaultOpen = true) {
        if (state.beliefs.isEmpty()) {
            Text(
                text = stringResource(R.string.memory_aucune_croyance),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
        }
        state.beliefs.forEach { belief ->
            // The three verbs sit in a column on the right, as glyphs: the card is a claim, and
            // three labelled buttons under it were a third of its height for no information.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .background(colors.surfaceMuted, MaterialTheme.shapes.extraSmall)
                    .padding(start = 12.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.belief_kind_and_status, rawLabel(belief.kind), belief.statusLabel()),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.accent,
                    )
                    Text(
                        text = belief.statement,
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.textPrimary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    Text(
                        text = stringResource(
                            R.string.memory_croyance_scores,
                            belief.support,
                            belief.against,
                            belief.timescale.ifBlank { "—" },
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    // Same glyph vocabulary as the bot sheet: tick to confirm, pencil to correct,
                    // bin to forget. A claim is confirmed or replaced, never edited in place.
                    IconButton(onClick = { viewModel.confirmBelief(belief.id) }, modifier = Modifier.size(38.dp)) {
                        Icon(
                            Icons.Rounded.Check,
                            contentDescription = stringResource(R.string.memory_confirmer_croyance),
                            tint = colors.accent,
                            modifier = Modifier.size(19.dp),
                        )
                    }
                    IconButton(onClick = { onCorrect(belief.id) }, modifier = Modifier.size(38.dp)) {
                        Icon(
                            Icons.Rounded.Edit,
                            contentDescription = stringResource(R.string.memory_corriger_croyance_action),
                            tint = colors.textSecondary,
                            modifier = Modifier.size(19.dp),
                        )
                    }
                    IconButton(onClick = { viewModel.forgetBelief(belief.id) }, modifier = Modifier.size(38.dp)) {
                        Icon(
                            Icons.Rounded.DeleteOutline,
                            contentDescription = stringResource(R.string.memory_oublier_croyance),
                            tint = colors.danger,
                            modifier = Modifier.size(19.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuarantineTab(state: MemoryUiState, viewModel: MemoryViewModel) {
    val colors = LocalMercuryColors.current
    CollapsibleSection(stringResource(R.string.memory_quarantaine_titre)) {
        Text(
            text = stringResource(R.string.memory_quarantaine_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
        )
        if (state.quarantined.isEmpty()) {
            Text(
                text = stringResource(R.string.memory_quarantaine_vide),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        state.quarantined.forEach { memory ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .background(colors.surfaceMuted, MaterialTheme.shapes.extraSmall)
                    .padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = rawLabel(memory.type),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textSecondary,
                    )
                    Text(
                        text = memory.summary,
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.textPrimary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    memory.detail?.takeIf { it.isNotBlank() }?.let { detail ->
                        Text(
                            text = detail,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textSecondary,
                            modifier = Modifier.padding(top = 4.dp),
                            maxLines = 3,
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = { viewModel.restoreQuarantined(memory.id) },
                        modifier = Modifier.size(38.dp),
                    ) {
                        Icon(
                            Icons.Rounded.Restore,
                            contentDescription = stringResource(R.string.memory_reintegrer),
                            tint = colors.accent,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * The belief lifecycle, in the words the owner thinks in.
 *
 * `candidate` → `held` → `confirmed` / `contested` → `retired` / `forgotten` is the daemon's
 * state machine (ADR-026). Shown in English it reads as jargon; the badge is the one place that
 * says whether a claim is being acted on.
 */
@Composable
private fun BeliefDto.statusLabel(): String = when (status.lowercase()) {
    "candidate" -> stringResource(R.string.memory_statut_candidate)
    "held" -> stringResource(R.string.memory_statut_held)
    "confirmed" -> stringResource(R.string.memory_statut_confirmed)
    "contested" -> stringResource(R.string.memory_statut_contested)
    "retired" -> stringResource(R.string.memory_statut_retired)
    "forgotten" -> stringResource(R.string.memory_statut_forgotten)
    else -> status
}

@Composable
private fun MemoryTab.label(): String = when (this) {
    MemoryTab.Conscious -> stringResource(R.string.brain_onglet_conscient)
    MemoryTab.Subconscious -> stringResource(R.string.brain_onglet_subconscient)
    MemoryTab.Beliefs -> stringResource(R.string.brain_onglet_apprises)
    MemoryTab.Quarantine -> stringResource(R.string.brain_onglet_quarantaine)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddMemoryDialog(
    onDismiss: () -> Unit,
    onConfirm: (type: String, summary: String, detail: String) -> Unit,
) {
    var type by remember { mutableStateOf(MEMORY_TYPES.first()) }
    var summary by remember { mutableStateOf("") }
    var detail by remember { mutableStateOf("") }
    val colors = LocalMercuryColors.current

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = { Text(stringResource(R.string.memory_nouveau_souvenir), color = colors.textPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MEMORY_TYPES.forEach { candidate ->
                        FilterChip(
                            selected = type == candidate,
                            onClick = { type = candidate },
                            label = { Text(candidate) },
                        )
                    }
                }
                OutlinedTextField(
                    value = summary,
                    onValueChange = { summary = it },
                    label = { Text(stringResource(R.string.memory_resume)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = detail,
                    onValueChange = { detail = it },
                    label = { Text(stringResource(R.string.memory_detail_optionnel)) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(type, summary, detail) }, enabled = summary.isNotBlank()) {
                Text(stringResource(R.string.memory_ajouter))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.memory_annuler)) } },
    )
}

@Composable
private fun CorrectBeliefDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var statement by remember { mutableStateOf("") }
    val colors = LocalMercuryColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = { Text(stringResource(R.string.memory_corriger_croyance), color = colors.textPrimary) },
        text = {
            OutlinedTextField(
                value = statement,
                onValueChange = { statement = it },
                label = { Text(stringResource(R.string.memory_formulation)) },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(statement) }, enabled = statement.trim().length >= 3) {
                Text(stringResource(R.string.memory_enregistrer))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.memory_annuler)) } },
    )
}
