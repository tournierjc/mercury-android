package io.github.tournierjc.mercury.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.tournierjc.mercury.MercuryApp
import io.github.tournierjc.mercury.data.BotJson
import io.github.tournierjc.mercury.data.GitJson
import io.github.tournierjc.mercury.data.SpotifyJson
import io.github.tournierjc.mercury.data.dto.ProviderTestResult
import io.github.tournierjc.mercury.ui.theme.LocalMercuryColors
import io.github.tournierjc.mercury.util.OutputSharing
import io.github.tournierjc.mercury.util.ShareText
import io.github.tournierjc.mercury.util.formatBytes
import io.github.tournierjc.mercury.util.isDestructiveCommand
import kotlinx.serialization.json.JsonObject
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
    var confirmShutdown by remember { mutableStateOf(false) }
    var showAddCrew by remember { mutableStateOf(false) }
    val botDetail = state.botDetail

    // The bundle is a document, not a screen: hand it to the share sheet as soon as it lands.
    val bundle = state.bundle
    LaunchedEffect(bundle) {
        if (bundle != null) {
            ShareText.bundle(context, "${state.bundleFor}-bundle.json", bundle)
            viewModel.clearBundle()
        }
    }

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

        // Same handoff as the bot sheet: bytes downloaded, FileProvider URI, share sheet.
        val pendingShare = state.pendingShare
        LaunchedEffect(pendingShare) {
            if (pendingShare != null) {
                runCatching { OutputSharing.share(context, pendingShare) }
                viewModel.consumeShare()
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp),
        ) {
            RuntimeSection(state)
            ProvidersSection(state, viewModel)
            // No Mémoire block: the memory screen owns it (souvenirs, croyances, quarantaine,
            // runtime), and this summary contradicted it on the pause state.
            CodeSection(state, viewModel)
            // No Équipiers block here: the fleet has its own screen (roster, sheets, dispatch),
            // and a second, read-only copy in the Console only ever disagreed with it.
            FleetSection(state, viewModel, onAddCrew = { showAddCrew = true })
            FleetDeliverablesSection(state, viewModel)
            CostSection(state)
            StorageSection(state)
            TerminalSection(state, viewModel)
            GitSection(state, viewModel)
            TasksSection(state, viewModel)
            SchedulesSection(state, viewModel)
            AgentsSection(state, viewModel)
            SkillsSection(state, viewModel)
            PluginsSection(state)
            McpSection(state)
            WorkspaceSection(state, viewModel)
            MusicSection(state, viewModel)

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
                Text(
                    text = "Arrêter le démon libère les canaux, consolide la mémoire et relâche ses fichiers. " +
                        "Mercury ne l'accepte que depuis la machine qui l'héberge.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(top = 12.dp),
                )
                TextButton(
                    onClick = { confirmShutdown = true },
                    enabled = !state.busy,
                    modifier = Modifier.padding(top = 6.dp),
                ) { Text("Arrêter Mercury", color = colors.danger) }
            }
            Text(
                text = " ",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(bottom = 24.dp),
            )
        }
    }

    if (showAddCrew) {
        AddCrewDialog(
            leads = state.bots,
            tiers = state.tiers,
            onDismiss = { showAddCrew = false },
            onConfirm = { leadId, id, name, description, persona, tier ->
                showAddCrew = false
                viewModel.addCrew(leadId, id, name, description, persona, tier)
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
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        val id = botDetail.bot?.get("id")
                            ?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull }
                            .orEmpty()
                        if (id.isNotBlank()) viewModel.exportBundle(id)
                    }) { Text("Exporter") }
                    TextButton(onClick = viewModel::closeBot) { Text("Fermer") }
                }
            },
        )
    }

    if (confirmShutdown) {
        AlertDialog(
            onDismissRequest = { confirmShutdown = false },
            containerColor = colors.surface,
            title = { Text("Arrêter le démon ?", color = colors.danger) },
            text = {
                Text(
                    "L'agent s'arrête proprement : les tours en cours sont interrompus et plus rien ne répond " +
                        "avant un redémarrage. Depuis le téléphone, Mercury peut refuser l'ordre (hôte et jeton local requis).",
                    color = colors.textSecondary,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmShutdown = false
                    viewModel.shutdown()
                }) { Text("Arrêter", color = colors.danger) }
            },
            dismissButton = { TextButton(onClick = { confirmShutdown = false }) { Text("Annuler") } },
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

    OutputPreviewHost(state, viewModel)
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
private fun ProvidersSection(state: ConsoleUiState, viewModel: ConsoleViewModel) {
    val colors = LocalMercuryColors.current
    var editing by remember { mutableStateOf<String?>(null) }
    SectionTitle("Fournisseurs")
    CardBlock {
        if (state.providers.isEmpty()) {
            Text(
                text = "Aucun fournisseur configuré sur ce runtime.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
            return@CardBlock
        }
        // One row per provider: name and model on the left, its two actions on the right. Two
        // text buttons under a two-line block made six providers fill the screen.
        state.providers.forEach { provider ->
            val name = provider.name.ifBlank { "—" }
            val ready = provider.enabled && provider.hasKey
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(
                            when {
                                !provider.enabled -> colors.textSecondary.copy(alpha = 0.35f)
                                provider.hasKey -> colors.accent
                                else -> colors.danger.copy(alpha = 0.7f)
                            },
                            CircleShape,
                        ),
                )
                Column(modifier = Modifier.weight(1f).padding(horizontal = 10.dp)) {
                    Text(
                        text = if (provider.recommended) "$name ★" else name,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (ready) colors.textPrimary else colors.textSecondary,
                        maxLines = 1,
                    )
                    Text(
                        text = when {
                            !provider.enabled -> "désactivé"
                            !provider.hasKey -> "sans clé"
                            provider.model.isNotBlank() -> provider.model
                            else -> "prêt"
                        } + provider.baseUrl.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty(),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = { editing = name }, enabled = !state.busy) {
                    Icon(
                        Icons.Rounded.Edit,
                        contentDescription = "Modifier $name",
                        tint = colors.textSecondary,
                        modifier = Modifier.size(19.dp),
                    )
                }
                IconButton(onClick = { viewModel.testProvider(name) }, enabled = !state.busy) {
                    Icon(
                        Icons.Rounded.Bolt,
                        contentDescription = "Tester $name",
                        tint = colors.accent,
                        modifier = Modifier.size(19.dp),
                    )
                }
            }
            if (state.providerTestFor == name) {
                state.providerTest?.let { result -> ProviderTestLine(result) }
            }
        }
        Text(
            text = "Une clé ou un modèle enregistré n'agit qu'au prochain démarrage du runtime.",
            style = MaterialTheme.typography.labelSmall,
            color = colors.textSecondary,
            modifier = Modifier.padding(top = 8.dp),
        )
    }

    editing?.let { id ->
        ProviderEditDialog(
            id = id,
            onDismiss = { editing = null },
            onConfirm = { apiKey, baseUrl, model, enabled ->
                editing = null
                viewModel.saveProvider(id, apiKey, baseUrl, model, enabled)
            },
        )
    }
}

/** The probe answers in place: success with its catalogue, or the daemon's own refusal. */
@Composable
private fun ProviderTestLine(result: ProviderTestResult) {
    val colors = LocalMercuryColors.current
    if (result.success) {
        val catalogue = result.models.take(4).joinToString(", ")
        Text(
            text = "Connecté · ${result.models.size} modèle(s)" +
                (if (catalogue.isNotBlank()) " : $catalogue" else "") +
                (if (result.recommendedModel.isNotBlank()) " · conseillé ${result.recommendedModel}" else ""),
            style = MaterialTheme.typography.labelSmall,
            color = colors.accent,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    } else {
        Text(
            text = "Échec · " + result.error.ifBlank { "connexion refusée" },
            style = MaterialTheme.typography.labelSmall,
            color = colors.danger,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

/**
 * `POST /api/providers/:id` takes `apiKey`, `baseUrl`, `model` and `enabled`, each optional:
 * an empty field is not sent, so the stored value survives.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProviderEditDialog(
    id: String,
    onDismiss: () -> Unit,
    onConfirm: (apiKey: String?, baseUrl: String?, model: String?, enabled: Boolean?) -> Unit,
) {
    var apiKey by remember { mutableStateOf("") }
    var baseUrl by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var enabled by remember { mutableStateOf<Boolean?>(null) }
    val colors = LocalMercuryColors.current

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = { Text("Fournisseur $id", color = colors.textPrimary) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "Seuls les champs remplis sont envoyés : les autres gardent leur valeur enregistrée.",
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.labelSmall,
                )
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("Clé API") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("URL de base") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    label = { Text("Modèle par défaut") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = enabled == true,
                        onClick = { enabled = if (enabled == true) null else true },
                        label = { Text("Activer") },
                    )
                    FilterChip(
                        selected = enabled == false,
                        onClick = { enabled = if (enabled == false) null else false },
                        label = { Text("Désactiver") },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        apiKey.trim().ifBlank { null },
                        baseUrl.trim().ifBlank { null },
                        model.trim().ifBlank { null },
                        enabled,
                    )
                },
                enabled = apiKey.isNotBlank() || baseUrl.isNotBlank() || model.isNotBlank() || enabled != null,
            ) { Text("Enregistrer") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SkillsSection(state: ConsoleUiState, viewModel: ConsoleViewModel) {
    val colors = LocalMercuryColors.current
    var installUrl by remember { mutableStateOf(false) }
    var installRegistry by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<String?>(null) }

    SectionTitle("Skills")
    CardBlock {
        state.skills.forEach { skill ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = skill.name,
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.textPrimary,
                    )
                    Text(
                        text = listOfNotNull(
                            skill.version?.takeIf { it.isNotBlank() },
                            skill.allowedTools.joinToString(", ").takeIf { it.isNotBlank() },
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                        maxLines = 2,
                    )
                }
                TextButton(onClick = { pendingDelete = skill.name }, enabled = !state.busy) {
                    Text("✕", color = colors.danger)
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
                color = colors.textSecondary,
            )
        }
        ButtonRow {
            TextButton(onClick = { installUrl = true }, enabled = !state.busy) { Text("Installer (URL)") }
            TextButton(onClick = { installRegistry = true }, enabled = !state.busy) { Text("Registre") }
        }
    }

    if (installUrl) {
        var url by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { installUrl = false },
            containerColor = colors.surface,
            title = { Text("Installer un skill", color = colors.textPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Mercury télécharge le paquet depuis une URL http(s). Un dossier local du serveur " +
                            "n'est pas accepté par cette route.",
                        color = colors.textSecondary,
                        style = MaterialTheme.typography.labelSmall,
                    )
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text("URL du paquet") },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val target = url.trim()
                        installUrl = false
                        viewModel.installSkill(target)
                    },
                    enabled = url.isNotBlank(),
                ) { Text("Installer") }
            },
            dismissButton = { TextButton(onClick = { installUrl = false }) { Text("Annuler") } },
        )
    }

    if (installRegistry) {
        var id by remember { mutableStateOf("") }
        var force by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { installRegistry = false },
            containerColor = colors.surface,
            title = { Text("Installer depuis le registre", color = colors.textPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Identifiant au format catégorie/nom, par exemple research/arxiv.",
                        color = colors.textSecondary,
                        style = MaterialTheme.typography.labelSmall,
                    )
                    OutlinedTextField(
                        value = id,
                        onValueChange = { id = it },
                        label = { Text("Identifiant") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    FilterChip(
                        selected = force,
                        onClick = { force = !force },
                        label = { Text("Forcer (réinstaller)") },
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val target = id.trim()
                        installRegistry = false
                        viewModel.installSkillFromRegistry(target, force)
                    },
                    enabled = id.isNotBlank(),
                ) { Text("Installer") }
            },
            dismissButton = { TextButton(onClick = { installRegistry = false }) { Text("Annuler") } },
        )
    }

    val victim = pendingDelete
    if (victim != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            containerColor = colors.surface,
            title = { Text("Supprimer le skill ?", color = colors.danger) },
            text = {
                Text(
                    "Le dossier de « $victim » est retiré du serveur, références et scripts compris. " +
                        "Cette action ne peut pas être annulée.",
                    color = colors.textSecondary,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    viewModel.deleteSkill(victim)
                }) { Text("Supprimer", color = colors.danger) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Annuler") } },
        )
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
private fun WorkspaceSection(state: ConsoleUiState, viewModel: ConsoleViewModel) {
    val workspace = state.workspace ?: return
    val colors = LocalMercuryColors.current
    var changing by remember { mutableStateOf(false) }
    SectionTitle("Espace de travail")
    CardBlock {
        KeyValueRow("Dossier", workspace.cwd.ifBlank { "—" })
        KeyValueRow("Projet", workspace.projectName.ifBlank { "—" })
        KeyValueRow("Git", if (workspace.isGit) workspace.branch.ifBlank { "dépôt" } else "aucun")
        ButtonRow {
            TextButton(onClick = { changing = true }, enabled = !state.busy) { Text("Changer la racine") }
        }
    }

    if (changing) {
        var path by remember { mutableStateOf(workspace.cwd) }
        AlertDialog(
            onDismissRequest = { changing = false },
            containerColor = colors.surface,
            title = { Text("Changer la racine du workspace", color = colors.textPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Le dossier doit déjà exister sur la machine de Mercury : le chemin est celui du serveur, " +
                            "pas celui du téléphone.",
                        color = colors.textSecondary,
                        style = MaterialTheme.typography.labelSmall,
                    )
                    OutlinedTextField(
                        value = path,
                        onValueChange = { path = it },
                        label = { Text("Chemin absolu") },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val target = path.trim()
                        changing = false
                        viewModel.setWorkspaceRoot(target)
                    },
                    enabled = path.isNotBlank(),
                ) { Text("Déplacer") }
            },
            dismissButton = { TextButton(onClick = { changing = false }) { Text("Annuler") } },
        )
    }
}

/** Where the tokens went: the fleet total and the bots that spend the most. */
@Composable
private fun CostSection(state: ConsoleUiState) {
    val colors = LocalMercuryColors.current
    SectionTitle("Coût")
    CardBlock {
        val cost = state.cost
        if (cost == null) {
            Text(
                text = "Dépense indisponible sur ce runtime.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
            return@CardBlock
        }
        KeyValueRow("Fenêtre", "${cost.days} jours")
        KeyValueRow("Tokens consommés", formatTokens(cost.windowTokens))
        KeyValueRow(
            label = "Plafond du jour",
            value = if (cost.fleetCap <= 0) "illimité" else "${formatTokens(cost.fleetToday)} / ${formatTokens(cost.fleetCap)}",
            highlight = cost.fleetPaused,
        )
        if (cost.fleetPaused) {
            KeyValueRow("Flotte", "en pause — plafond atteint", highlight = true)
        }
        val top = cost.topByTokens()
        if (top.isEmpty()) {
            Text(
                text = "Aucune dépense sur la période.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
        } else {
            top.forEach { row ->
                ActionRow(
                    title = row.name.ifBlank { row.id },
                    subtitle = listOfNotNull(
                        "${formatTokens(row.window.tokens)} tokens",
                        "${row.window.runs} passage(s)",
                        row.window.failed.takeIf { it > 0 }?.let { "$it échec(s)" },
                        row.cap.takeIf { it > 0 }?.let { "plafond ${formatTokens(it)}/j" },
                    ).joinToString(" · "),
                    trailing = if (row.paused) "en pause" else "",
                )
            }
        }
    }
}

/** What the fleet occupies on disk, journal included. */
@Composable
private fun StorageSection(state: ConsoleUiState) {
    val colors = LocalMercuryColors.current
    SectionTitle("Stockage")
    CardBlock {
        val storage = state.storage
        if (storage == null) {
            Text(
                text = "Occupation disque indisponible sur ce runtime.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
            return@CardBlock
        }
        KeyValueRow("Total", formatBytes(storage.totalBytes))
        KeyValueRow(
            label = "File",
            value = "${storage.queue.pending} en attente · ${storage.queue.claimed} en cours" +
                if (storage.queue.dlq > 0) " · ${storage.queue.dlq} en échec" else "",
        )
        val top = storage.topBySize()
        if (top.isEmpty()) {
            Text(
                text = "Aucun dossier d'équipier à mesurer.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
        } else {
            top.forEach { row ->
                ActionRow(
                    title = row.id,
                    subtitle = "${formatBytes(row.bytes)} dont journal ${formatBytes(row.journalBytes)}",
                    trailing = formatBytes(row.profileBytes),
                )
            }
        }
    }
}

private fun mcpStateLabel(state: String): String = when (state) {
    "connected" -> "connecté"
    "connecting" -> "connexion…"
    "reconnecting" -> "reconnexion…"
    "failed" -> "échec"
    "disabled" -> "désactivé"
    else -> state.ifBlank { "inconnu" }
}

/** `GET /api/mcp` — read-only: which servers the runtime knows and what they expose. */
@Composable
private fun McpSection(state: ConsoleUiState) {
    val colors = LocalMercuryColors.current
    SectionTitle("Serveurs MCP")
    CardBlock {
        val mcp = state.mcp
        if (mcp == null) {
            Text(
                text = "État MCP indisponible.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
            return@CardBlock
        }
        if (!mcp.available) {
            Text(
                text = "Ce runtime n'expose pas de gestion MCP.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
            return@CardBlock
        }
        if (mcp.servers.isEmpty()) {
            Text(
                text = "Aucun serveur MCP configuré.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
            return@CardBlock
        }
        mcp.servers.forEach { server ->
            KeyValueRow(
                label = server.id.ifBlank { "serveur" },
                value = mcpStateLabel(server.state),
                highlight = server.state == "connected",
            )
            KeyValueRow(
                label = "Transport",
                value = listOf(server.transport, server.target).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "—" },
            )
            KeyValueRow("Outils", "${server.toolCount} exposé(s)")
            server.error?.takeIf { it.isNotBlank() }?.let { message ->
                Text(message, style = MaterialTheme.typography.labelSmall, color = colors.danger)
            }
            if (server.tools.isNotEmpty()) {
                Text(
                    text = server.tools.joinToString("\n") { tool ->
                        val name = tool.exposedName.ifBlank { tool.name }
                        val flag = when {
                            !tool.allowed -> "bloqué"
                            tool.autoApprove -> "auto"
                            else -> ""
                        }
                        if (flag.isBlank()) "· $name" else "· $name ($flag)"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
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

/**
 * Fleet controls: promote a bot to lead, inspect its crew, add a member, set the
 * permission tier. A lead can also build its own crew (autocrew, 202 + background).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FleetSection(state: ConsoleUiState, viewModel: ConsoleViewModel, onAddCrew: () -> Unit) {
    val colors = LocalMercuryColors.current
    val context = LocalContext.current
    var selectedId by remember { mutableStateOf("") }
    var overwrite by remember { mutableStateOf(false) }
    var importError by remember { mutableStateOf<String?>(null) }
    val crew = state.crew

    // A bundle is a .json file; the picker reports some of them as plain text or octet-stream.
    val bundlePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
        }.getOrNull()
        if (text.isNullOrBlank()) {
            importError = "Fichier illisible"
        } else {
            importError = null
            viewModel.importBundle(text, overwrite)
        }
    }

    SectionTitle("Flotte")
    CardBlock {
        if (state.bots.isEmpty()) {
            Text(
                text = "Aucun équipier : créez-en un, ou importez un bundle partagé pour repartir d'une flotte entière.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
        }
        ButtonRow {
            TextButton(
                onClick = {
                    bundlePicker.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                },
                enabled = !state.busy,
            ) { Text("Importer un bundle") }
            FilterChip(
                selected = overwrite,
                onClick = { overwrite = !overwrite },
                label = { Text("Écraser") },
            )
        }
        importError?.let { message ->
            Text(message, style = MaterialTheme.typography.labelSmall, color = colors.danger)
        }
        Text(
            text = "Un bundle importé arrive désactivé : la flotte ne dépense rien avant que vous l'activiez.",
            style = MaterialTheme.typography.labelSmall,
            color = colors.textSecondary,
        )

        if (state.bots.isEmpty()) return@CardBlock
        Text(
            text = "Un chef de flotte peut engendrer ses propres spécialistes. Sélectionnez un équipier :",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            state.bots.forEach { bot ->
                val id = (bot["id"] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull.orEmpty()
                if (id.isNotBlank()) {
                    FilterChip(
                        selected = selectedId == id,
                        onClick = {
                            selectedId = id
                            viewModel.loadCrew(id)
                            viewModel.loadPermissions(id)
                        },
                        label = { Text(bot.titleHint()) },
                    )
                }
            }
        }
        if (selectedId.isNotBlank()) {
            ButtonRow {
                TextButton(onClick = { viewModel.promoteBot(selectedId) }) { Text("Promouvoir") }
                TextButton(onClick = { viewModel.autoCrew(selectedId) }) { Text("Flotte auto") }
                TextButton(onClick = onAddCrew) { Text("Ajouter un membre") }
            }
        }

        if (crew != null) {
            if (crew.error.isNotBlank()) {
                Text(crew.error, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
            } else {
                KeyValueRow("Chef", crew.lead?.titleHint()?.ifBlank { "—" } ?: "—")
                KeyValueRow("Membres", "${crew.crew.size} / ${crew.maxCrew}")
                crew.crew.forEach { member ->
                    ActionRow(
                        title = member.titleHint(),
                        subtitle = member.detailHint(),
                        trailing = "",
                        onClick = {},
                    )
                }
            }
        }

        state.permissions?.let { permissions ->
            val tier = (permissions["tier"] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull.orEmpty()
            if (tier.isNotBlank()) KeyValueRow("Palier", tier)
        }
        if (state.tiers.isNotEmpty() && selectedId.isNotBlank()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                state.tiers.forEach { tier ->
                    FilterChip(
                        selected = false,
                        onClick = { viewModel.setTier(selectedId, tier.id) },
                        label = { Text(tier.label) },
                    )
                }
            }
        }
    }
}

/**
 * Terminal: one command at a time, in the workspace, 30 s timeout.
 * Destructive patterns ask for a second confirmation — this executes on the host.
 */
@Composable
private fun TerminalSection(state: ConsoleUiState, viewModel: ConsoleViewModel) {
    val colors = LocalMercuryColors.current
    var command by remember { mutableStateOf("") }
    var cwd by remember { mutableStateOf("") }
    var pending by remember { mutableStateOf<String?>(null) }

    SectionTitle("Terminal")
    CardBlock {
        Text(
            text = "Exécute une commande shell dans l'espace de travail de Mercury (30 s max). " +
                "Elle tourne sur l'hôte, pas sur le téléphone.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
        )
        OutlinedTextField(
            value = command,
            onValueChange = { command = it },
            label = { Text("Commande") },
            singleLine = false,
            minLines = 1,
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = cwd,
            onValueChange = { cwd = it },
            label = { Text("Dossier (optionnel, relatif au workspace)") },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier.fillMaxWidth(),
        )
        ButtonRow {
            TextButton(
                onClick = {
                    val trimmed = command.trim()
                    if (trimmed.isNotEmpty()) {
                        if (isDestructiveCommand(trimmed)) pending = trimmed else viewModel.runCommand(trimmed, cwd)
                    }
                },
                enabled = !state.terminalBusy && command.isNotBlank(),
            ) { Text(if (state.terminalBusy) "Exécution…" else "Exécuter") }
            TextButton(onClick = { viewModel.clearTerminal() }, enabled = state.terminal != null) { Text("Effacer") }
        }
        state.terminal?.let { result ->
            KeyValueRow(
                "Sortie",
                "code ${result.exitCode}" + if (result.cwd.isNotBlank()) " · ${result.cwd}" else "",
                highlight = result.success,
            )
            Text(
                text = result.display,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = if (result.success) colors.textPrimary else colors.danger,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surfaceMuted, RoundedCornerShape(12.dp))
                    .padding(10.dp),
            )
        }
    }

    if (pending != null) {
        AlertDialog(
            onDismissRequest = { pending = null },
            containerColor = colors.surface,
            title = { Text("Commande destructive", color = colors.danger) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Cette commande peut supprimer des données ou arrêter un service :",
                        color = colors.textSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        pending.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        color = colors.textPrimary,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val cmd = pending
                    pending = null
                    if (cmd != null) viewModel.runCommand(cmd, cwd)
                }) { Text("Exécuter quand même", color = colors.danger) }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Annuler") } },
        )
    }
}

/**
 * What the Git card is about to do, once the owner confirms it. Kept local to the screen:
 * the pending action is UI state, not something the runtime should ever see.
 */
private class GitConfirm(
    val title: String,
    val body: String,
    val confirm: String,
    val danger: Boolean = false,
    val action: () -> Unit,
)

/**
 * Git: branch, upstream distance, both sides of the index, a commit box the runtime can
 * fill, push/pull, a branch switcher and the log.
 *
 * A refusal from git reaches the banner above this card verbatim — that text ("nothing to
 * commit", "non-fast-forward") is the only useful description of what went wrong.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GitSection(state: ConsoleUiState, viewModel: ConsoleViewModel) {
    val colors = LocalMercuryColors.current
    var confirm by remember { mutableStateOf<GitConfirm?>(null) }
    val git = state.git

    SectionTitle("Git")
    CardBlock {
        if (git == null) {
            Text(
                text = "Aucun dépôt git accessible dans l'espace de travail.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
            TextButton(onClick = viewModel::refreshGit) { Text("Vérifier à nouveau") }
            return@CardBlock
        }

        KeyValueRow("Branche", git.branch.ifBlank { "—" }, highlight = true)
        KeyValueRow("Amont", git.upstream.ifBlank { "aucun" })
        KeyValueRow("Synchronisation", GitJson.syncLabel(git.ahead, git.behind))
        Text(
            text = GitJson.summaryLabel(git),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
            modifier = Modifier.padding(top = 2.dp),
        )

        val stagedFiles = GitJson.staged(git)
        val unstagedFiles = GitJson.unstaged(git)

        if (stagedFiles.isNotEmpty()) {
            Text(
                text = "Indexé",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary,
                modifier = Modifier.padding(top = 10.dp),
            )
            stagedFiles.forEach { file ->
                ActionRow(
                    title = file.path,
                    subtitle = GitJson.changeLabel(file),
                    trailing = "désindexer",
                    enabled = !state.gitBusy,
                    onClick = { viewModel.unstage(listOf(file.path)) },
                )
            }
        }
        if (unstagedFiles.isNotEmpty()) {
            Text(
                text = "Modifié",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary,
                modifier = Modifier.padding(top = 10.dp),
            )
            unstagedFiles.forEach { file ->
                ActionRow(
                    title = file.path,
                    subtitle = GitJson.changeLabel(file),
                    trailing = "indexer",
                    enabled = !state.gitBusy,
                    onClick = { viewModel.stage(listOf(file.path)) },
                )
            }
        }
        if (stagedFiles.isEmpty() && unstagedFiles.isEmpty()) {
            Text(
                text = "Arbre propre : rien à indexer.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        ButtonRow {
            TextButton(
                onClick = viewModel::stageAll,
                enabled = !state.gitBusy && unstagedFiles.isNotEmpty(),
            ) { Text("Tout indexer") }
            TextButton(
                onClick = viewModel::unstageAll,
                enabled = !state.gitBusy && stagedFiles.isNotEmpty(),
            ) { Text("Tout désindexer") }
        }
        ButtonRow {
            TextButton(
                onClick = { viewModel.showDiff(null, false) },
                enabled = !state.gitBusy && unstagedFiles.isNotEmpty(),
            ) { Text("Diff non indexé") }
            TextButton(
                onClick = { viewModel.showDiff(null, true) },
                enabled = !state.gitBusy && stagedFiles.isNotEmpty(),
            ) { Text("Diff indexé") }
        }

        OutlinedTextField(
            value = state.commitDraft,
            onValueChange = viewModel::setCommitDraft,
            label = { Text("Message de commit") },
            minLines = 2,
            textStyle = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
        )
        ButtonRow {
            TextButton(
                onClick = viewModel::generateCommitMessage,
                enabled = !state.gitBusy,
            ) { Text(if (state.gitBusy) "…" else "Générer") }
            TextButton(
                onClick = { confirm = GitConfirm(
                    title = "Créer le commit ?",
                    body = "Le message part tel quel. Mercury ajoute lui-même la ligne " +
                        "« Co-authored-by » et ne commite que ce qui est indexé.",
                    confirm = "Committer",
                    action = viewModel::commit,
                ) },
                enabled = !state.gitBusy && state.commitDraft.isNotBlank(),
            ) { Text("Committer") }
        }
        ButtonRow {
            TextButton(
                onClick = { confirm = GitConfirm(
                    title = "Pousser la branche ?",
                    body = "Les commits partent sur origin. Rien n'est réécrit, rien n'est forcé.",
                    confirm = "Pousser",
                    action = viewModel::push,
                ) },
                enabled = !state.gitBusy,
            ) { Text("Pousser") }
            TextButton(
                onClick = { confirm = GitConfirm(
                    title = "Tirer les changements ?",
                    body = "git pull sur la branche courante : le répertoire de travail peut " +
                        "recevoir des commits distants.",
                    confirm = "Tirer",
                    action = viewModel::pull,
                ) },
                enabled = !state.gitBusy,
            ) { Text("Tirer") }
            TextButton(onClick = viewModel::refreshGit, enabled = !state.gitBusy) { Text("Rafraîchir") }
        }

        val branches = state.gitBranches.filter { it.name.isNotBlank() }
        if (branches.isNotEmpty()) {
            Text(
                text = "Branches",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary,
                modifier = Modifier.padding(top = 12.dp),
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                branches.forEach { branch ->
                    val current = branch.isCurrent || branch.name == git.branch
                    FilterChip(
                        selected = current,
                        enabled = !state.gitBusy && !current,
                        onClick = { confirm = GitConfirm(
                            title = "Changer de branche ?",
                            body = "Le répertoire de travail passe sur « ${branch.name} ». " +
                                "Un arbre modifié peut faire échouer le changement.",
                            confirm = "Changer",
                            action = { viewModel.checkout(branch.name) },
                        ) },
                        label = { Text(branch.name) },
                    )
                }
            }
        }

        if (state.gitLog.isNotEmpty()) {
            Text(
                text = "Derniers commits",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary,
                modifier = Modifier.padding(top = 12.dp),
            )
            state.gitLog.forEach { commit ->
                Text(
                    text = "${commit.short}  ${commit.message}",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = colors.textPrimary,
                    maxLines = 2,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Text(
                    text = listOf(commit.author, commit.date).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary,
                )
            }
        }
    }

    val pending = confirm
    if (pending != null) {
        AlertDialog(
            onDismissRequest = { confirm = null },
            containerColor = colors.surface,
            title = { Text(pending.title, color = colors.textPrimary) },
            text = { Text(pending.body, color = colors.textSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    confirm = null
                    pending.action()
                }) { Text(pending.confirm, color = if (pending.danger) colors.danger else colors.textPrimary) }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Annuler") } },
        )
    }

    val diff = state.gitDiff
    if (diff != null) {
        AlertDialog(
            onDismissRequest = viewModel::dismissDiff,
            containerColor = colors.surface,
            title = {
                Text(
                    text = state.gitDiffTitle.ifBlank { "Diff" },
                    color = colors.textPrimary,
                    style = MaterialTheme.typography.titleSmall,
                )
            },
            text = {
                // The patch as text: this branch has no diff renderer, and pretending to
                // colour one would be worse than showing git's own output.
                Text(
                    text = diff,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = colors.textPrimary,
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                )
            },
            confirmButton = { TextButton(onClick = viewModel::dismissDiff) { Text("Fermer") } },
        )
    }
}

/**
 * Musique: one compact card — what plays, transport, volume, shuffle and the devices.
 *
 * Spotify's `now-playing` route answers a formatted line, so progress comes from parsing
 * it; nothing here guesses a playback state it was not told.
 */
@Composable
private fun MusicSection(state: ConsoleUiState, viewModel: ConsoleViewModel) {
    val colors = LocalMercuryColors.current
    val music = state.music
    var volume by remember {
        // The volume Spotify reports for the active device is the only starting point the
        // routes give us; 50 is a neutral guess when no device answers.
        mutableStateOf((SpotifyJson.activeDevice(state.devices)?.volume ?: 50).toFloat())
    }
    var shuffle by remember { mutableStateOf(false) }

    SectionTitle("Musique")
    CardBlock {
        if (music == null || !music.available) {
            Text(
                text = "Spotify n'est pas configuré sur ce runtime.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
            return@CardBlock
        }
        if (!music.connected) {
            Text(
                text = "Compte Spotify non connecté. Lance « /spotify auth » sur le runtime, " +
                    "puis rafraîchis cette carte.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
            return@CardBlock
        }

        KeyValueRow(
            "Compte",
            listOf(music.accountName?.ifBlank { "—" } ?: "—", SpotifyJson.productLabel(music.product))
                .joinToString(" · "),
        )

        val track = state.nowPlaying
        if (track == null) {
            Text(
                text = "Rien en lecture.",
                style = MaterialTheme.typography.bodyLarge,
                color = colors.textPrimary,
                modifier = Modifier.padding(top = 6.dp),
            )
        } else {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyLarge,
                color = colors.textPrimary,
                maxLines = 2,
                modifier = Modifier.padding(top = 6.dp),
            )
            Text(
                text = track.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
                maxLines = 1,
            )
            ProgressLine(track.ratio)
            Text(
                text = track.progressLabel,
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        ButtonRow {
            TextButton(onClick = viewModel::previous, enabled = !state.musicBusy) { Text("Précédent") }
            TextButton(onClick = viewModel::play, enabled = !state.musicBusy) { Text("Lecture") }
            TextButton(onClick = viewModel::pause, enabled = !state.musicBusy) { Text("Pause") }
            TextButton(onClick = viewModel::next, enabled = !state.musicBusy) { Text("Suivant") }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        ) {
            Text(
                text = "Volume",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
                modifier = Modifier.weight(1f),
            )
            Slider(
                value = volume,
                onValueChange = { volume = it },
                // The route is called once the finger leaves the slider, not per pixel.
                onValueChangeFinished = { viewModel.setVolume(volume.toInt()) },
                valueRange = 0f..100f,
                enabled = !state.musicBusy,
                modifier = Modifier.weight(2f),
            )
            Text(
                text = "${volume.toInt()} %",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary,
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Aléatoire",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = shuffle,
                onCheckedChange = { checked ->
                    shuffle = checked
                    viewModel.setShuffle(checked)
                },
                enabled = !state.musicBusy,
            )
        }

        val devices = state.devices
        if (devices.size == 1) {
            KeyValueRow("Appareil", devices[0].name.ifBlank { devices[0].label })
        } else if (devices.size > 1) {
            Text(
                text = "Appareils",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary,
                modifier = Modifier.padding(top = 10.dp),
            )
            devices.forEach { device ->
                ActionRow(
                    title = device.name.ifBlank { device.id },
                    subtitle = device.label,
                    trailing = if (device.active) "actif" else null,
                    selected = device.active,
                )
            }
            Text(
                text = "Le runtime joue sur l'appareil qu'il a choisi (« /spotify device ») : " +
                    "cette liste le montre, elle ne le déplace pas.",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        ButtonRow {
            TextButton(onClick = viewModel::refreshMusic, enabled = !state.musicBusy) { Text("Rafraîchir") }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddCrewDialog(
    leads: List<JsonObject>,
    tiers: List<io.github.tournierjc.mercury.data.dto.BotTierDto>,
    onDismiss: () -> Unit,
    onConfirm: (leadId: String, id: String, name: String, description: String?, persona: String?, tier: String?) -> Unit,
) {
    val colors = LocalMercuryColors.current
    var leadId by remember { mutableStateOf("") }
    var id by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var persona by remember { mutableStateOf("") }
    var tier by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = { Text("Ajouter à une flotte", color = colors.textPrimary) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Chef de flotte", color = colors.textSecondary, style = MaterialTheme.typography.labelSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    leads.forEach { bot ->
                        val botId = (bot["id"] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull.orEmpty()
                        if (botId.isNotBlank()) {
                            FilterChip(
                                selected = leadId == botId,
                                onClick = { leadId = botId },
                                label = { Text(bot.titleHint()) },
                            )
                        }
                    }
                }
                OutlinedTextField(value = id, onValueChange = { id = it }, label = { Text("Identifiant") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nom") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Spécialité") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = persona, onValueChange = { persona = it }, label = { Text("Persona (optionnel)") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                if (tiers.isNotEmpty()) {
                    Text("Palier de permissions", color = colors.textSecondary, style = MaterialTheme.typography.labelSmall)
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
                onClick = {
                    onConfirm(
                        leadId,
                        id.trim(),
                        name.trim(),
                        description.trim().ifBlank { null },
                        persona.trim().ifBlank { null },
                        tier,
                    )
                },
                enabled = leadId.isNotBlank() && id.isNotBlank() && name.isNotBlank(),
            ) { Text("Ajouter") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}
