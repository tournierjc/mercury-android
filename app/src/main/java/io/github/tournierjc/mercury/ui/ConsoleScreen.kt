package io.github.tournierjc.mercury.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.tournierjc.mercury.MercuryApp
import io.github.tournierjc.mercury.ui.theme.LocalMercuryColors
import io.github.tournierjc.mercury.util.formatTokens
import io.github.tournierjc.mercury.util.formatUptime
import io.github.tournierjc.mercury.vm.ConsoleUiState
import io.github.tournierjc.mercury.vm.ConsoleViewModel
import kotlinx.serialization.json.contentOrNull

/**
 * Supervision of the daemon from the phone: runtime health, memory, programming
 * mode, teammates, background tasks, schedules, agents, skills and plugins.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConsoleScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val container = remember { (context.applicationContext as MercuryApp).container }
    val viewModel: ConsoleViewModel = viewModel(factory = ConsoleViewModel.factory(container))
    val state by viewModel.state.collectAsState()
    val colors = LocalMercuryColors.current
    var confirmRestart by remember { mutableStateOf(false) }
    var showCreateBot by remember { mutableStateOf(false) }
    val botDetail = state.botDetail

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.canvas)
            .safeDrawingPadding(),
    ) {
        ScreenHeader(
            title = "Console",
            subtitle = state.status?.let { "${it.state.ifBlank { "?" }} · ${formatUptime(it.uptime)}" },
            onBack = onBack,
            onRefresh = viewModel::refresh,
        )
        state.message?.let { InlineBanner(it, isError = false, onDismiss = viewModel::clearBanner) }
        state.error?.let { InlineBanner(it, isError = true, onDismiss = viewModel::clearBanner) }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp),
        ) {
            RuntimeSection(state)
            BrainSection(state)
            CodeSection(state, viewModel)
            BotsSection(state, viewModel, onCreate = { showCreateBot = true }, onOpen = { id -> viewModel.openBot(id) })
            TasksSection(state, viewModel)
            SchedulesSection(state, viewModel)
            AgentsSection(state, viewModel)
            SkillsSection(state, viewModel)
            PluginsSection(state)
            WorkspaceSection(state)

            SectionTitle("Système")
            CardBlock {
                Text(
                    text = "Redémarrer le runtime réinitialise les sessions en cours. Le pont appareil n'est pas affecté.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
                TextButton(
                    onClick = { confirmRestart = true },
                    enabled = !state.busy,
                    modifier = Modifier.padding(top = 6.dp),
                ) { Text("Redémarrer Mercury", color = colors.danger) }
            }
            Text(
                text = " ",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(bottom = 24.dp),
            )
        }
    }

    if (showCreateBot) {
        CreateBotDialog(
            tiers = state.tiers,
            onDismiss = { showCreateBot = false },
            onConfirm = { id, name, description, tier ->
                showCreateBot = false
                viewModel.createBot(id, name, description, tier)
            },
        )
    }

    if (botDetail != null) {
        AlertDialog(
            onDismissRequest = viewModel::closeBot,
            containerColor = colors.surface,
            title = {
                Text(
                    botDetail.bot?.titleHint()?.ifBlank { "Équipier" } ?: "Équipier",
                    color = colors.textPrimary,
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val manifest = botDetail.bot
                    if (manifest != null) {
                        KeyValueRow("Modèle", (manifest["model"] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull.orEmpty().ifBlank { "défaut" })
                        KeyValueRow("Palier", (manifest["tier"] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull.orEmpty().ifBlank { "—" })
                        KeyValueRow("Activé", (manifest["enabled"] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull ?: "—")
                    }
                    KeyValueRow("Journal", "${botDetail.journal.size} dernières entrées")
                    botDetail.journal.take(4).forEach { entry ->
                        Text(
                            text = "· " + entry.titleHint(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textSecondary,
                            maxLines = 2,
                        )
                    }
                }
            },
            confirmButton = {
                val id = botDetail.bot?.get("id")?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull }.orEmpty()
                TextButton(onClick = {
                    viewModel.closeBot()
                    if (id.isNotBlank()) viewModel.runBot(id)
                }) { Text("Exécuter") }
            },
            dismissButton = { TextButton(onClick = viewModel::closeBot) { Text("Fermer") } },
        )
    }

    if (confirmRestart) {
        AlertDialog(
            onDismissRequest = { confirmRestart = false },
            containerColor = colors.surface,
            title = { Text("Redémarrer le runtime ?", color = colors.textPrimary) },
            text = {
                Text(
                    "Les tours en cours sont interrompus, le conteneur revient en quelques secondes.",
                    color = colors.textSecondary,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmRestart = false
                    viewModel.restart()
                }) { Text("Redémarrer", color = colors.danger) }
            },
            dismissButton = { TextButton(onClick = { confirmRestart = false }) { Text("Annuler") } },
        )
    }
}

@Composable
private fun RuntimeSection(state: ConsoleUiState) {
    SectionTitle("Runtime")
    CardBlock {
        val status = state.status
        val health = state.health
        if (status == null) {
            Text("État indisponible.", style = MaterialTheme.typography.bodyMedium, color = LocalMercuryColors.current.textSecondary)
            return@CardBlock
        }
        KeyValueRow("État", status.state.ifBlank { if (status.running) "actif" else "arrêté" }, highlight = status.running)
        KeyValueRow("Uptime", formatUptime(status.uptime))
        KeyValueRow("Tokens du jour", "${formatTokens(status.tokens.dailyUsed)} / ${formatTokens(status.tokens.dailyBudget)}")
        status.providers.forEach { (name, provider) ->
            KeyValueRow(
                label = name,
                value = when {
                    !provider.enabled -> "désactivé"
                    !provider.hasKey -> "sans clé"
                    else -> "prêt"
                },
            )
        }
        if (health != null) {
            KeyValueRow("PID", health.pid.toString())
            KeyValueRow("Boucle (p99)", "${health.loopLag.p99Ms} ms")
            KeyValueRow("Plugins", "${health.plugins.running} actifs · ${health.plugins.tools} outils")
            if (health.plugins.safeMode) KeyValueRow("Mode sûr", "activé", highlight = true)
        }
    }
}

@Composable
private fun BrainSection(state: ConsoleUiState) {
    val brain = state.brain ?: return
    SectionTitle("Mémoire")
    CardBlock {
        KeyValueRow("Souvenirs", brain.total.toString())
        KeyValueRow("Apprentissage", if (brain.learningPaused) "en pause" else "actif")
        if (brain.byType.isNotEmpty()) {
            Text(
                text = brain.byType.entries.joinToString(" · ") { "${it.key} ${it.value}" },
                style = MaterialTheme.typography.bodyMedium,
                color = LocalMercuryColors.current.textSecondary,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        if (brain.activeSummary.isNotBlank()) {
            Text(
                text = brain.activeSummary,
                style = MaterialTheme.typography.bodyMedium,
                color = LocalMercuryColors.current.textPrimary,
                modifier = Modifier.padding(top = 10.dp),
                maxLines = 6,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CodeSection(state: ConsoleUiState, viewModel: ConsoleViewModel) {
    val code = state.code ?: return
    SectionTitle("Mode programmation")
    CardBlock {
        Text(
            text = code.statusText.ifBlank { code.state },
            style = MaterialTheme.typography.bodyLarge,
            color = LocalMercuryColors.current.textPrimary,
        )
        if (code.available) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("off", "auto", "plan", "execute").forEach { mode ->
                    FilterChip(
                        selected = code.state == mode,
                        onClick = { viewModel.setCodeMode(mode) },
                        label = { Text(mode) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = LocalMercuryColors.current.accent,
                        ),
                    )
                }
            }
        } else {
            Text(
                text = "Indisponible sur ce runtime.",
                style = MaterialTheme.typography.bodyMedium,
                color = LocalMercuryColors.current.textSecondary,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BotsSection(
    state: ConsoleUiState,
    viewModel: ConsoleViewModel,
    onCreate: () -> Unit,
    onOpen: (String) -> Unit,
) {
    val colors = LocalMercuryColors.current
    SectionTitle("Équipiers")
    CardBlock {
        KeyValueRow("File d'attente", "${state.queue.pending} en attente · ${state.queue.claimed} en cours")
        if (state.queue.dlq > 0) KeyValueRow("Échecs", state.queue.dlq.toString(), highlight = true)
        if (state.bots.isEmpty()) {
            Text(
                text = "Aucun bot configuré. Un équipier tourne en boucle : il réveille l'agent selon son planning, avec ses propres permissions.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
        }
        state.bots.forEach { bot ->
            val id = (bot["id"] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull.orEmpty()
            val enabled = (bot["enabled"] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
            ActionRow(
                title = bot.titleHint(),
                subtitle = listOf(bot.detailHint(), if (enabled == "false") "désactivé" else null)
                    .filterNotNull().joinToString(" · "),
                trailing = "détails",
                onClick = { if (id.isNotBlank()) onOpen(id) },
            )
            if (id.isNotBlank()) {
                ButtonRow {
                    TextButton(onClick = { viewModel.startBot(id) }) { Text("Démarrer") }
                    TextButton(onClick = { viewModel.stopBot(id) }) { Text("Arrêter") }
                    TextButton(onClick = { viewModel.runBot(id) }) { Text("Exécuter") }
                    TextButton(onClick = { viewModel.setBotEnabled(id, enabled != "true") }) {
                        Text(if (enabled == "true") "Désactiver" else "Activer")
                    }
                }
            }
        }
        ButtonRow {
            TextButton(onClick = onCreate) { Text("Nouvel équipier") }
            if (state.bots.isNotEmpty()) {
                TextButton(onClick = viewModel::startAllBots) { Text("Tout démarrer") }
                TextButton(onClick = viewModel::stopAllBots) { Text("Tout arrêter") }
            }
        }
    }
}

@Composable
private fun TasksSection(state: ConsoleUiState, viewModel: ConsoleViewModel) {
    SectionTitle("Tâches de fond")
    CardBlock {
        state.tasks.forEach { task ->
            val id = (task["id"] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull.orEmpty()
            ActionRow(
                title = task.titleHint(),
                subtitle = task.detailHint(),
                trailing = "annuler",
                onClick = { if (id.isNotBlank()) viewModel.cancelTask(id) },
            )
        }
        if (state.tasks.isEmpty()) {
            Text(
                text = "Aucune tâche en cours.",
                style = MaterialTheme.typography.bodyMedium,
                color = LocalMercuryColors.current.textSecondary,
            )
        } else {
            TextButton(onClick = viewModel::clearFinishedTasks) { Text("Effacer les tâches terminées") }
        }
    }
}

@Composable
private fun SchedulesSection(state: ConsoleUiState, viewModel: ConsoleViewModel) {
    SectionTitle("Automatisations")
    CardBlock {
        state.schedules.forEach { schedule ->
            val id = (schedule["id"] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull.orEmpty()
            ActionRow(
                title = schedule.titleHint(),
                subtitle = schedule.detailHint(),
                trailing = "supprimer",
                onClick = { if (id.isNotBlank()) viewModel.deleteSchedule(id) },
            )
        }
        if (state.schedules.isEmpty()) {
            Text(
                text = "Aucune automatisation programmée.",
                style = MaterialTheme.typography.bodyMedium,
                color = LocalMercuryColors.current.textSecondary,
            )
        }
    }
}

@Composable
private fun AgentsSection(state: ConsoleUiState, viewModel: ConsoleViewModel) {
    if (state.agents.isEmpty()) return
    SectionTitle("Agents")
    CardBlock {
        state.agents.forEach { agent ->
            ActionRow(title = agent.titleHint(), subtitle = agent.detailHint())
        }
        ButtonRow {
            TextButton(onClick = viewModel::haltAgents) { Text("Pause") }
            TextButton(onClick = viewModel::stopAgents) { Text("Arrêter") }
        }
    }
}

@Composable
private fun SkillsSection(state: ConsoleUiState, viewModel: ConsoleViewModel) {
    SectionTitle("Skills")
    CardBlock {
        state.skills.forEach { skill ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = skill.name,
                        style = MaterialTheme.typography.bodyLarge,
                        color = LocalMercuryColors.current.textPrimary,
                    )
                    Text(
                        text = listOfNotNull(
                            skill.version?.takeIf { it.isNotBlank() },
                            skill.allowedTools.joinToString(", ").takeIf { it.isNotBlank() },
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = LocalMercuryColors.current.textSecondary,
                        maxLines = 2,
                    )
                }
                Switch(
                    checked = skill.active,
                    onCheckedChange = { active -> viewModel.toggleSkill(skill.name, active) },
                    enabled = !state.busy,
                )
            }
        }
        if (state.skills.isEmpty()) {
            Text(
                text = "Aucun skill installé.",
                style = MaterialTheme.typography.bodyMedium,
                color = LocalMercuryColors.current.textSecondary,
            )
        }
    }
}

@Composable
private fun PluginsSection(state: ConsoleUiState) {
    if (state.plugins.isEmpty()) return
    SectionTitle("Plugins")
    CardBlock {
        state.plugins.forEach { plugin ->
            ActionRow(
                title = plugin.name.ifBlank { plugin.id },
                subtitle = listOf(plugin.kind, plugin.tier).filter { it.isNotBlank() }.joinToString(" · "),
                trailing = plugin.status.ifBlank { plugin.installedVersion },
            )
        }
    }
}

@Composable
private fun WorkspaceSection(state: ConsoleUiState) {
    val workspace = state.workspace ?: return
    SectionTitle("Espace de travail")
    CardBlock {
        KeyValueRow("Dossier", workspace.cwd.ifBlank { "—" })
        KeyValueRow("Projet", workspace.projectName.ifBlank { "—" })
        KeyValueRow("Git", if (workspace.isGit) workspace.branch.ifBlank { "dépôt" } else "aucun")
    }
}

/** `POST /api/bots` only needs an id and a name; tier is a permission preset. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CreateBotDialog(
    tiers: List<io.github.tournierjc.mercury.data.dto.BotTierDto>,
    onDismiss: () -> Unit,
    onConfirm: (id: String, name: String, description: String, tier: String?) -> Unit,
) {
    var id by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var tier by remember { mutableStateOf<String?>(null) }
    val colors = LocalMercuryColors.current

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = { Text("Nouvel équipier", color = colors.textPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = id,
                    onValueChange = { id = it.filter { c -> c.isLetterOrDigit() || c == '-' || c == '_' } },
                    label = { Text("Identifiant") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nom") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Rôle (optionnel)") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (tiers.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        tiers.forEach { candidate ->
                            FilterChip(
                                selected = tier == candidate.id,
                                onClick = { tier = if (tier == candidate.id) null else candidate.id },
                                label = { Text(candidate.label) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(id.trim(), name.trim(), description, tier) },
                enabled = id.isNotBlank() && name.isNotBlank(),
            ) { Text("Créer") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}
