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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.tournierjc.mercury.MercuryApp
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
            title = "Mémoire",
            subtitle = "${state.total} souvenirs · ${state.beliefs.size} croyances",
            onBack = onBack,
            onRefresh = viewModel::refresh,
        )
        state.message?.let { InlineBanner(it, isError = false, onDismiss = viewModel::clearBanner) }
        state.error?.let { InlineBanner(it, isError = true, onDismiss = viewModel::clearBanner) }

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
        label = { Text("Rechercher dans la mémoire") },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
    )
    SectionTitle("Souvenirs")
    CardBlock {
        if (state.memories.isEmpty()) {
            Text(
                text = if (state.loading) "Chargement…" else "Rien en mémoire pour cette recherche.",
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
                    label = "Ajouter",
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .background(colors.surfaceMuted, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
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
            text = "confiance ${(memory.confidence * 100).toInt()} % · importance ${(memory.importance * 100).toInt()} % · ${memory.scope}",
            style = MaterialTheme.typography.labelSmall,
            color = colors.textSecondary,
            modifier = Modifier.padding(top = 4.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            IconButton(onClick = onForget) {
                Icon(
                    Icons.Rounded.DeleteOutline,
                    contentDescription = "Oublier ce souvenir",
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
    SectionTitle("Croyances")
    CardBlock {
        if (state.beliefs.isEmpty()) {
            Text(
                text = "Aucune croyance extraite pour l'instant.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
        }
        state.beliefs.forEach { belief ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .background(colors.surfaceMuted, RoundedCornerShape(14.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
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
                    text = "support ${belief.support} · contre ${belief.against} · ${belief.timescale.ifBlank { "—" }}",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(top = 4.dp),
                )
                // Same glyph vocabulary as the bot sheet: tick to confirm, pencil to correct,
                // bin to forget. A claim is confirmed or replaced, never edited in place.
                IconActionBar(
                    listOf(
                        QuickAction(
                            icon = Icons.Rounded.Check,
                            label = "Confirmer",
                            tint = colors.accent,
                            onClick = { viewModel.confirmBelief(belief.id) },
                        ),
                        QuickAction(
                            icon = Icons.Rounded.Edit,
                            label = "Corriger",
                            onClick = { onCorrect(belief.id) },
                        ),
                        QuickAction(
                            icon = Icons.Rounded.DeleteOutline,
                            label = "Oublier",
                            tint = colors.danger,
                            onClick = { viewModel.forgetBelief(belief.id) },
                        ),
                    ),
                )
            }
        }
    }
}

@Composable
private fun QuarantineTab(state: MemoryUiState, viewModel: MemoryViewModel) {
    val colors = LocalMercuryColors.current
    SectionTitle("Écartés par le cerveau")
    CardBlock {
        Text(
            text = "Souvenirs mis de côté parce qu'ils contredisaient quelque chose de plus solide. Les réintégrer les remet dans le circuit.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
        )
        if (state.quarantined.isEmpty()) {
            Text(
                text = "Rien en quarantaine.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        state.quarantined.forEach { memory ->
            Column(modifier = Modifier.padding(top = 10.dp)) {
                Text(
                    text = memory.summary,
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.textPrimary,
                )
                IconButton(onClick = { viewModel.restoreQuarantined(memory.id) }) {
                    Icon(
                        Icons.Rounded.Restore,
                        contentDescription = "Réintégrer",
                        tint = colors.accent,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun AboutTab(state: MemoryUiState) {
    val colors = LocalMercuryColors.current
    SectionTitle("Runtime")
    CardBlock {
        val config = state.config
        if (config == null) {
            Text("Configuration indisponible.", style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
        } else {
            KeyValueRow("Agent", config.identity.name.ifBlank { "—" })
            KeyValueRow("Propriétaire", config.identity.owner.ifBlank { "—" })
            KeyValueRow("Fournisseur", config.defaultProvider.ifBlank { "—" })
            KeyValueRow("Budget", formatTokens(config.tokenBudget))
            KeyValueRow("Battement", formatUptime(config.heartbeatInterval * 60))
            KeyValueRow("Second cerveau", if (config.secondBrainEnabled) "activé" else "désactivé")
            KeyValueRow("Telegram", if (config.telegramEnabled) "activé" else "désactivé")
        }
    }
    SectionTitle("Graphe")
    CardBlock {
        val graph = state.graph
        if (graph == null) {
            Text("Graphe indisponible.", style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
        } else {
            KeyValueRow("Nœuds", graph.nodes.size.toString())
            KeyValueRow("Liens", graph.edges.size.toString())
            graph.nodes.take(6).forEach { node ->
                ActionRow(title = node.fullLabel.ifBlank { node.label }, subtitle = node.type)
            }
        }
    }
    SectionTitle("Personnes")
    CardBlock {
        if (state.persons.isEmpty()) {
            Text(
                text = "Le cerveau n'a encore identifié personne.",
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
private fun BeliefDto.statusLabel(): String = when (status.lowercase()) {
    "candidate" -> "candidate"
    "held" -> "tenue"
    "confirmed" -> "confirmée"
    "contested" -> "contestée"
    "retired" -> "retirée"
    "forgotten" -> "oubliée"
    else -> status
}

private fun MemoryTab.label(): String = when (this) {
    MemoryTab.Memories -> "Souvenirs"
    MemoryTab.Beliefs -> "Croyances"
    MemoryTab.Quarantine -> "Quarantaine"
    MemoryTab.About -> "À propos"
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
        title = { Text("Nouveau souvenir", color = colors.textPrimary) },
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
                    label = { Text("Résumé") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = detail,
                    onValueChange = { detail = it },
                    label = { Text("Détail (optionnel)") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(type, summary, detail) }, enabled = summary.isNotBlank()) {
                Text("Ajouter")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

@Composable
private fun CorrectBeliefDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var statement by remember { mutableStateOf("") }
    val colors = LocalMercuryColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = { Text("Corriger la croyance", color = colors.textPrimary) },
        text = {
            OutlinedTextField(
                value = statement,
                onValueChange = { statement = it },
                label = { Text("Formulation correcte") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(statement) }, enabled = statement.trim().length >= 3) {
                Text("Enregistrer")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}
