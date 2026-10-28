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
import io.github.tournierjc.mercury.util.formatTokens
import io.github.tournierjc.mercury.util.formatUptime
import io.github.tournierjc.mercury.vm.MemoryTab
import io.github.tournierjc.mercury.vm.MemoryUiState
import io.github.tournierjc.mercury.vm.MemoryViewModel

/**
 * What the agent remembers and believes, from the phone: search, add, correct,
 * forget, and inspect what it set aside.
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
            subtitle = stringResource(R.string.memory_sous_titre, state.total, state.beliefs.size),
            onBack = onBack,
            onRefresh = viewModel::refresh,
        )
        state.message?.let { notice -> NoticeBanner(notice, onDismiss = viewModel::clearBanner) }
        state.error?.let { notice -> NoticeBanner(notice, onDismiss = viewModel::clearBanner) }

        // One segmented row: four tabs on a phone, the active one underlined in the accent.
        // Chips wrapped onto a second line and read as filters, not as sections.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            MemoryTab.entries.forEach { tab ->
                val selected = state.tab == tab
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { viewModel.selectTab(tab) }
                        .padding(vertical = 8.dp),
                ) {
                    Text(
                        text = tab.label(),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) colors.accent else colors.textSecondary,
                        maxLines = 1,
                    )
                    Box(
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .width(18.dp)
                            .height(2.dp)
                            .clip(RoundedCornerShape(1.dp))
                            .background(if (selected) colors.accent else Color.Transparent),
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp),
        ) {
            when (state.tab) {
                MemoryTab.Memories -> MemoriesTab(state, viewModel, onAdd = { showAdd = true })
                MemoryTab.Beliefs -> BeliefsTab(state, viewModel, onCorrect = { correcting = it })
                MemoryTab.Quarantine -> QuarantineTab(state, viewModel)
                MemoryTab.About -> AboutTab(state)
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

@Composable
private fun MemoriesTab(state: MemoryUiState, viewModel: MemoryViewModel, onAdd: () -> Unit) {
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
        if (state.memories.isEmpty()) {
            Text(
                text = if (state.loading) {
                    stringResource(R.string.memory_chargement)
                } else {
                    stringResource(R.string.memory_aucun_souvenir)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
        }
        state.memories.forEach { memory ->
            MemoryRow(memory = memory, onForget = { viewModel.forgetMemory(memory.id) })
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

@Composable
private fun MemoryRow(memory: MemoryDto, onForget: () -> Unit) {
    val colors = LocalMercuryColors.current
    // Même forme que les croyances : le texte respire à gauche, les verbes vivent en colonne à
    // droite. Un bouton posé sous la dernière ligne se lisait comme une note de bas de carte.
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
                text = memory.type.uppercase(),
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
            IconButton(onClick = onForget, modifier = Modifier.size(38.dp)) {
                Icon(
                    Icons.Rounded.DeleteOutline,
                    contentDescription = stringResource(R.string.memory_oublier_souvenir),
                    tint = colors.danger,
                    modifier = Modifier.size(19.dp),
                )
            }
        }
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
                    .padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${belief.kind.uppercase()} · ${belief.statusLabel()}",
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
                        text = memory.type.uppercase(),
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

@Composable
private fun AboutTab(state: MemoryUiState) {
    val colors = LocalMercuryColors.current
    CollapsibleSection(stringResource(R.string.memory_runtime)) {
        val config = state.config
        if (config == null) {
            Text(
                stringResource(R.string.memory_config_indisponible),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
        } else {
            KeyValueRow(stringResource(R.string.memory_agent), config.identity.name.ifBlank { "—" })
            KeyValueRow(stringResource(R.string.memory_proprietaire), config.identity.owner.ifBlank { "—" })
            KeyValueRow(stringResource(R.string.memory_fournisseur), config.defaultProvider.ifBlank { "—" })
            KeyValueRow(stringResource(R.string.memory_budget), formatTokens(config.tokenBudget))
            KeyValueRow(stringResource(R.string.memory_battement), formatUptime(config.heartbeatInterval * 60))
            KeyValueRow(
                stringResource(R.string.memory_second_cerveau),
                if (config.secondBrainEnabled) {
                    stringResource(R.string.memory_active)
                } else {
                    stringResource(R.string.memory_desactive)
                },
            )
            KeyValueRow(
                stringResource(R.string.memory_telegram),
                if (config.telegramEnabled) {
                    stringResource(R.string.memory_active)
                } else {
                    stringResource(R.string.memory_desactive)
                },
            )
        }
    }
    CollapsibleSection(stringResource(R.string.memory_graphe)) {
        val graph = state.graph
        if (graph == null) {
            Text(
                stringResource(R.string.memory_graphe_indisponible),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
        } else {
            KeyValueRow(stringResource(R.string.memory_noeuds), graph.nodes.size.toString())
            KeyValueRow(stringResource(R.string.memory_liens), graph.edges.size.toString())
            graph.nodes.take(6).forEach { node ->
                ActionRow(title = node.fullLabel.ifBlank { node.label }, subtitle = node.type)
            }
        }
    }
    CollapsibleSection(stringResource(R.string.memory_personnes)) {
        if (state.persons.isEmpty()) {
            Text(
                text = stringResource(R.string.memory_aucune_personne),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
        }
        state.persons.forEach { person -> ActionRow(title = person.titleHint(), subtitle = person.detailHint()) }
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
    MemoryTab.Memories -> stringResource(R.string.memory_souvenirs)
    MemoryTab.Beliefs -> stringResource(R.string.memory_croyances)
    MemoryTab.Quarantine -> stringResource(R.string.memory_quarantaine)
    MemoryTab.About -> stringResource(R.string.memory_a_propos)
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
