package io.github.tournierjc.mercury.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PlayArrow
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.tournierjc.mercury.MercuryApp
import io.github.tournierjc.mercury.R
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
import io.github.tournierjc.mercury.util.TerminalPaths
import io.github.tournierjc.mercury.vm.TerminalEntry
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
            title = stringResource(R.string.console_title),
            subtitle = state.status?.let { "${it.state.ifBlank { "?" }} · ${formatUptime(it.uptime)}" },
            onBack = onBack,
            onRefresh = viewModel::refresh,
        )
        state.message?.let { notice -> NoticeBanner(notice, onDismiss = viewModel::clearBanner) }
        state.error?.let { notice -> NoticeBanner(notice, onDismiss = viewModel::clearBanner) }

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
            // No Mémoire block: the memory screen owns it (souvenirs, croyances, quarantaine,
            // runtime), and this summary contradicted it on the pause state.
            CodeSection(state, viewModel)
            // No Équipiers block here: the fleet has its own screen (roster, sheets, dispatch),
            // and a second, read-only copy in the Console only ever disagreed with it.
            // La flotte vit dans l'écran Équipiers : l'import de bundle et les chefs y sont,
            // avec les visages et les fiches, au lieu d'une rangée de puces ici.
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

            SectionTitle(stringResource(R.string.console_section_system))
            CardBlock {
                Text(
                    text = stringResource(R.string.console_restart_required_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
                TextButton(
                    onClick = { confirmRestart = true },
                    enabled = !state.busy,
                    modifier = Modifier.padding(top = 6.dp),
                ) { Text(stringResource(R.string.console_restart_mercury), color = colors.danger) }
                Text(
                    text = stringResource(R.string.console_shutdown_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(top = 12.dp),
                )
                TextButton(
                    onClick = { confirmShutdown = true },
                    enabled = !state.busy,
                    modifier = Modifier.padding(top = 6.dp),
                ) { Text(stringResource(R.string.console_shutdown_mercury), color = colors.danger) }
            }
            Text(
                text = " ",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(bottom = 24.dp),
            )
        }
    }


    if (confirmShutdown) {
        AlertDialog(
            onDismissRequest = { confirmShutdown = false },
            containerColor = colors.surface,
            title = { Text(stringResource(R.string.console_shutdown_dialog_title), color = colors.danger) },
            text = {
                Text(
                    stringResource(R.string.console_shutdown_dialog_body),
                    color = colors.textSecondary,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmShutdown = false
                    viewModel.shutdown()
                }) { Text(stringResource(R.string.console_stop), color = colors.danger) }
            },
            dismissButton = { TextButton(onClick = { confirmShutdown = false }) { Text(stringResource(R.string.console_cancel)) } },
        )
    }

    if (confirmRestart) {
        AlertDialog(
            onDismissRequest = { confirmRestart = false },
            containerColor = colors.surface,
            title = { Text(stringResource(R.string.console_restart_dialog_title), color = colors.textPrimary) },
            text = {
                Text(
                    stringResource(R.string.console_restart_dialog_body),
                    color = colors.textSecondary,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmRestart = false
                    viewModel.restart()
                }) { Text(stringResource(R.string.console_restart), color = colors.danger) }
            },
            dismissButton = { TextButton(onClick = { confirmRestart = false }) { Text(stringResource(R.string.console_cancel)) } },
        )
    }

    OutputPreviewHost(state, viewModel)
}



@Composable
private fun RuntimeSection(state: ConsoleUiState) {
    SectionTitle(stringResource(R.string.console_section_runtime))
    CardBlock {
        val status = state.status
        val health = state.health
        if (status == null) {
            Text(stringResource(R.string.console_status_unavailable), style = MaterialTheme.typography.bodyMedium, color = LocalMercuryColors.current.textSecondary)
            return@CardBlock
        }
        KeyValueRow(stringResource(R.string.console_label_state), status.state.ifBlank { if (status.running) stringResource(R.string.console_active) else stringResource(R.string.console_stopped) }, highlight = status.running)
        KeyValueRow(stringResource(R.string.console_label_uptime), formatUptime(status.uptime))
        KeyValueRow(stringResource(R.string.console_label_daily_tokens), "${formatTokens(status.tokens.dailyUsed)} / ${formatTokens(status.tokens.dailyBudget)}")
        if (health != null) {
            KeyValueRow("PID", health.pid.toString())
            KeyValueRow(stringResource(R.string.console_label_loop_lag), "${health.loopLag.p99Ms} ms")
            KeyValueRow(stringResource(R.string.console_label_plugins), stringResource(R.string.console_plugins_summary, health.plugins.running, health.plugins.tools))
            if (health.plugins.safeMode) KeyValueRow(stringResource(R.string.console_label_safe_mode), stringResource(R.string.console_safe_mode_on), highlight = true)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CodeSection(state: ConsoleUiState, viewModel: ConsoleViewModel) {
    val code = state.code ?: return
    SectionTitle(stringResource(R.string.console_section_programming_mode))
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
                text = stringResource(R.string.console_unavailable_on_runtime),
                style = MaterialTheme.typography.bodyMedium,
                color = LocalMercuryColors.current.textSecondary,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TasksSection(state: ConsoleUiState, viewModel: ConsoleViewModel) {
    SectionTitle(stringResource(R.string.console_section_background_tasks))
    CardBlock {
        state.tasks.forEach { task ->
            val id = (task["id"] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull.orEmpty()
            ActionRow(
                title = task.titleHint(),
                subtitle = task.detailHint(),
                trailing = stringResource(R.string.console_trailing_cancel),
                onClick = { if (id.isNotBlank()) viewModel.cancelTask(id) },
            )
        }
        if (state.tasks.isEmpty()) {
            Text(
                text = stringResource(R.string.console_no_tasks),
                style = MaterialTheme.typography.bodyMedium,
                color = LocalMercuryColors.current.textSecondary,
            )
        } else {
            TextButton(onClick = viewModel::clearFinishedTasks) { Text(stringResource(R.string.console_clear_finished_tasks)) }
        }
    }
}

@Composable
private fun SchedulesSection(state: ConsoleUiState, viewModel: ConsoleViewModel) {
    SectionTitle(stringResource(R.string.console_section_schedules))
    CardBlock {
        state.schedules.forEach { schedule ->
            val id = (schedule["id"] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull.orEmpty()
            ActionRow(
                title = schedule.titleHint(),
                subtitle = schedule.detailHint(),
                trailing = stringResource(R.string.console_trailing_delete),
                onClick = { if (id.isNotBlank()) viewModel.deleteSchedule(id) },
            )
        }
        if (state.schedules.isEmpty()) {
            Text(
                text = stringResource(R.string.console_no_schedules),
                style = MaterialTheme.typography.bodyMedium,
                color = LocalMercuryColors.current.textSecondary,
            )
        }
    }
}

@Composable
private fun AgentsSection(state: ConsoleUiState, viewModel: ConsoleViewModel) {
    if (state.agents.isEmpty()) return
    SectionTitle(stringResource(R.string.console_section_agents))
    CardBlock {
        state.agents.forEach { agent ->
            ActionRow(title = agent.titleHint(), subtitle = agent.detailHint())
        }
        ButtonRow {
            TextButton(onClick = viewModel::haltAgents) { Text(stringResource(R.string.console_pause)) }
            TextButton(onClick = viewModel::stopAgents) { Text(stringResource(R.string.console_stop)) }
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

    SectionTitle(stringResource(R.string.console_section_skills))
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
                text = stringResource(R.string.console_no_skills),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
        }
        ButtonRow {
            TextButton(onClick = { installUrl = true }, enabled = !state.busy) { Text(stringResource(R.string.console_install_url)) }
            TextButton(onClick = { installRegistry = true }, enabled = !state.busy) { Text(stringResource(R.string.console_registry)) }
        }
    }

    if (installUrl) {
        var url by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { installUrl = false },
            containerColor = colors.surface,
            title = { Text(stringResource(R.string.console_install_skill_title), color = colors.textPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.console_install_url_body),
                        color = colors.textSecondary,
                        style = MaterialTheme.typography.labelSmall,
                    )
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text(stringResource(R.string.console_package_url_label)) },
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
                ) { Text(stringResource(R.string.console_install)) }
            },
            dismissButton = { TextButton(onClick = { installUrl = false }) { Text(stringResource(R.string.console_cancel)) } },
        )
    }

    if (installRegistry) {
        var id by remember { mutableStateOf("") }
        var force by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { installRegistry = false },
            containerColor = colors.surface,
            title = { Text(stringResource(R.string.console_install_from_registry_title), color = colors.textPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.console_registry_id_body),
                        color = colors.textSecondary,
                        style = MaterialTheme.typography.labelSmall,
                    )
                    OutlinedTextField(
                        value = id,
                        onValueChange = { id = it },
                        label = { Text(stringResource(R.string.console_identifier)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    FilterChip(
                        selected = force,
                        onClick = { force = !force },
                        label = { Text(stringResource(R.string.console_force_reinstall)) },
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
                ) { Text(stringResource(R.string.console_install)) }
            },
            dismissButton = { TextButton(onClick = { installRegistry = false }) { Text(stringResource(R.string.console_cancel)) } },
        )
    }

    val victim = pendingDelete
    if (victim != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            containerColor = colors.surface,
            title = { Text(stringResource(R.string.console_delete_skill_title), color = colors.danger) },
            text = {
                Text(
                    stringResource(R.string.console_delete_skill_body, victim),
                    color = colors.textSecondary,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    viewModel.deleteSkill(victim)
                }) { Text(stringResource(R.string.console_delete), color = colors.danger) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.console_cancel)) } },
        )
    }
}

@Composable
private fun PluginsSection(state: ConsoleUiState) {
    if (state.plugins.isEmpty()) return
    SectionTitle(stringResource(R.string.console_section_plugins))
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
    SectionTitle(stringResource(R.string.console_section_workspace))
    CardBlock {
        KeyValueRow(stringResource(R.string.console_label_folder), workspace.cwd.ifBlank { "—" })
        KeyValueRow(stringResource(R.string.console_label_project), workspace.projectName.ifBlank { "—" })
        KeyValueRow("Git", if (workspace.isGit) workspace.branch.ifBlank { stringResource(R.string.console_repository) } else stringResource(R.string.console_none))
        ButtonRow {
            TextButton(onClick = { changing = true }, enabled = !state.busy) { Text(stringResource(R.string.console_change_root)) }
        }
    }

    if (changing) {
        var path by remember { mutableStateOf(workspace.cwd) }
        AlertDialog(
            onDismissRequest = { changing = false },
            containerColor = colors.surface,
            title = { Text(stringResource(R.string.console_change_root_title), color = colors.textPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.console_change_root_body),
                        color = colors.textSecondary,
                        style = MaterialTheme.typography.labelSmall,
                    )
                    OutlinedTextField(
                        value = path,
                        onValueChange = { path = it },
                        label = { Text(stringResource(R.string.console_absolute_path)) },
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
                ) { Text(stringResource(R.string.console_move)) }
            },
            dismissButton = { TextButton(onClick = { changing = false }) { Text(stringResource(R.string.console_cancel)) } },
        )
    }
}

/** Where the tokens went: the fleet total and the bots that spend the most. */
@Composable
private fun CostSection(state: ConsoleUiState) {
    val colors = LocalMercuryColors.current
    SectionTitle(stringResource(R.string.console_section_cost))
    CardBlock {
        val cost = state.cost
        if (cost == null) {
            Text(
                text = stringResource(R.string.console_cost_unavailable),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
            return@CardBlock
        }
        KeyValueRow(stringResource(R.string.console_label_window), stringResource(R.string.console_window_days, cost.days))
        KeyValueRow(stringResource(R.string.console_label_tokens_used), formatTokens(cost.windowTokens))
        KeyValueRow(
            label = stringResource(R.string.console_label_daily_cap),
            value = if (cost.fleetCap <= 0) stringResource(R.string.console_unlimited) else "${formatTokens(cost.fleetToday)} / ${formatTokens(cost.fleetCap)}",
            highlight = cost.fleetPaused,
        )
        if (cost.fleetPaused) {
            KeyValueRow(stringResource(R.string.console_label_fleet), stringResource(R.string.console_fleet_paused_cap_reached), highlight = true)
        }
        val top = cost.topByTokens()
        if (top.isEmpty()) {
            Text(
                text = stringResource(R.string.console_no_spend),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
        } else {
            top.forEach { row ->
                ActionRow(
                    title = row.name.ifBlank { row.id },
                    subtitle = listOfNotNull(
                        "${formatTokens(row.window.tokens)} tokens",
                        stringResource(R.string.console_runs_count, row.window.runs),
                        row.window.failed.takeIf { it > 0 }?.let { stringResource(R.string.console_failures_count, it) },
                        row.cap.takeIf { it > 0 }?.let { stringResource(R.string.console_bot_cap, formatTokens(it)) },
                    ).joinToString(" · "),
                    trailing = if (row.paused) stringResource(R.string.console_paused) else "",
                )
            }
        }
    }
}

/** What the fleet occupies on disk, journal included. */
@Composable
private fun StorageSection(state: ConsoleUiState) {
    val colors = LocalMercuryColors.current
    SectionTitle(stringResource(R.string.console_section_storage))
    CardBlock {
        val storage = state.storage
        if (storage == null) {
            Text(
                text = stringResource(R.string.console_storage_unavailable),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
            return@CardBlock
        }
        KeyValueRow(stringResource(R.string.console_label_total), formatBytes(storage.totalBytes))
        KeyValueRow(
            label = stringResource(R.string.console_label_queue),
            value = stringResource(R.string.console_queue_pending, storage.queue.pending) + " · " +
                stringResource(R.string.console_queue_in_progress, storage.queue.claimed) +
                if (storage.queue.dlq > 0) " · " + stringResource(R.string.console_queue_failed, storage.queue.dlq) else "",
        )
        val top = storage.topBySize()
        if (top.isEmpty()) {
            Text(
                text = stringResource(R.string.console_no_teammate_dirs),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
        } else {
            top.forEach { row ->
                ActionRow(
                    title = row.id,
                    subtitle = stringResource(R.string.console_size_with_journal, formatBytes(row.bytes), formatBytes(row.journalBytes)),
                    trailing = formatBytes(row.profileBytes),
                )
            }
        }
    }
}

@Composable
private fun mcpStateLabel(state: String): String = when (state) {
    "connected" -> stringResource(R.string.console_mcp_state_connected)
    "connecting" -> stringResource(R.string.console_mcp_state_connecting)
    "reconnecting" -> stringResource(R.string.console_mcp_state_reconnecting)
    "failed" -> stringResource(R.string.console_mcp_state_failed)
    "disabled" -> stringResource(R.string.console_mcp_state_disabled)
    else -> state.ifBlank { stringResource(R.string.console_mcp_state_unknown) }
}

/** `GET /api/mcp` — read-only: which servers the runtime knows and what they expose. */
@Composable
private fun McpSection(state: ConsoleUiState) {
    val colors = LocalMercuryColors.current
    SectionTitle(stringResource(R.string.console_section_mcp))
    CardBlock {
        val mcp = state.mcp
        if (mcp == null) {
            Text(
                text = stringResource(R.string.console_mcp_unavailable),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
            return@CardBlock
        }
        if (!mcp.available) {
            Text(
                text = stringResource(R.string.console_mcp_not_exposed),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
            return@CardBlock
        }
        if (mcp.servers.isEmpty()) {
            Text(
                text = stringResource(R.string.console_no_mcp_servers),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
            return@CardBlock
        }
        mcp.servers.forEach { server ->
            KeyValueRow(
                label = server.id.ifBlank { stringResource(R.string.console_server) },
                value = mcpStateLabel(server.state),
                highlight = server.state == "connected",
            )
            KeyValueRow(
                label = stringResource(R.string.console_label_transport),
                value = listOf(server.transport, server.target).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "—" },
            )
            KeyValueRow(stringResource(R.string.console_label_tools), stringResource(R.string.console_tools_exposed, server.toolCount))
            server.error?.takeIf { it.isNotBlank() }?.let { message ->
                Text(message, style = MaterialTheme.typography.labelSmall, color = colors.danger)
            }
            if (server.tools.isNotEmpty()) {
                // Resolved here: joinToString's lambda is not a composable context.
                val blockedLabel = stringResource(R.string.console_blocked)
                Text(
                    text = server.tools.joinToString("\n") { tool ->
                        val name = tool.exposedName.ifBlank { tool.name }
                        val flag = when {
                            !tool.allowed -> blockedLabel
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
        title = { Text(stringResource(R.string.console_new_bot_title), color = colors.textPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = id,
                    onValueChange = { id = it.filter { c -> c.isLetterOrDigit() || c == '-' || c == '_' } },
                    label = { Text(stringResource(R.string.console_identifier)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.console_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.console_role_optional)) },
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
            ) { Text(stringResource(R.string.console_create)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.console_cancel)) } },
    )
}

/**
 * Fleet controls: promote a bot to lead, inspect its crew, add a member, set the
 * permission tier. A lead can also build its own crew (autocrew, 202 + background).
 */
@OptIn(ExperimentalLayoutApi::class)
/**
 * A terminal, not a form.
 *
 * The block used to be two text fields and two buttons that showed one command's answer and
 * replaced it on the next run: nothing to scroll back to, nothing that echoed what you typed, no
 * prompt. This is the shape a shell has — a prompt that says where you are, the command echoed as
 * you enter it, the output under it, and a session you can read from the top.
 *
 * `cd` is handled locally: it moves the prompt, it does not spend a request on the daemon. Every
 * other line goes to `POST /api/terminal/exec` (30 s ceiling, on the host — not on the phone).
 */
@Composable
private fun TerminalSection(state: ConsoleUiState, viewModel: ConsoleViewModel) {
    val colors = LocalMercuryColors.current
    var line by remember { mutableStateOf("") }
    var pending by remember { mutableStateOf<String?>(null) }
    var historyIndex by remember { mutableStateOf(-1) }
    val scroll = rememberScrollState()
    val log = state.terminalLog

    // New output: keep the prompt in view, the way a terminal does.
    LaunchedEffect(log.size, state.terminalBusy) {
        scroll.animateScrollTo(scroll.maxValue)
    }

    fun submit() {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return
        // `cd` is the prompt's business, not the daemon's.
        if (trimmed == "cd" || trimmed.startsWith("cd ")) {
            viewModel.setTerminalCwd(
                TerminalPaths.resolve(state.terminalCwd, trimmed.removePrefix("cd").trim()),
            )
            line = ""
            historyIndex = -1
            return
        }
        if (isDestructiveCommand(trimmed)) {
            pending = trimmed
            return
        }
        viewModel.runCommand(trimmed, state.terminalCwd)
        line = ""
        historyIndex = -1
    }

    CollapsibleSection(stringResource(R.string.console_section_terminal)) {
        // The screen itself: monospace on a sunken panel, prompt and transcript together.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surfaceMuted, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Text(
                text = stringResource(R.string.console_terminal_hint),
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary,
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 90.dp, max = 320.dp)
                    .verticalScroll(scroll)
                    .padding(top = 8.dp),
            ) {
                if (log.isEmpty() && !state.terminalBusy) {
                    Text(
                        text = stringResource(R.string.console_terminal_empty),
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = colors.textSecondary,
                    )
                }
                log.forEach { entry ->
                    Text(
                        text = "${entry.cwdPrompt(state.terminalCwd)} ${entry.command}",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = colors.accent,
                    )
                    Text(
                        text = entry.body,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = if (entry.failure != null || !entry.ok) colors.danger else colors.textPrimary,
                    )
                    entry.status?.let { notice ->
                        Text(
                            text = "— ${noticeText(notice)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textSecondary,
                        )
                    }
                }
                if (state.terminalBusy) {
                    Text(
                        text = "…",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = colors.textSecondary,
                    )
                }
            }
            // The prompt line: `mount~ $` then the field. Send on the keyboard's action, and the
            // glyph does the same for a thumb.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${TerminalPaths.prompt(state.terminalCwd)} $",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium,
                    ),
                    color = colors.textSecondary,
                    maxLines = 1,
                )
                BasicTextField(
                    value = line,
                    onValueChange = { line = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        color = colors.textPrimary,
                    ),
                    cursorBrush = SolidColor(colors.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { submit() }),
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp),
                )
                IconButton(
                    onClick = { submit() },
                    enabled = line.isNotBlank() && !state.terminalBusy,
                    modifier = Modifier.size(34.dp),
                ) {
                    Icon(
                        Icons.Rounded.PlayArrow,
                        contentDescription = stringResource(R.string.console_run),
                        tint = if (line.isNotBlank() && !state.terminalBusy) colors.accent else colors.textSecondary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
        IconActionBar(
            listOf(
                QuickAction(
                    icon = Icons.Rounded.History,
                    label = stringResource(R.string.console_previous_command),
                    enabled = log.isNotEmpty(),
                    onClick = {
                        // Walk back through the session, newest first, without leaving the field.
                        val next = (historyIndex + 1).coerceAtMost(log.size - 1)
                        historyIndex = next
                        line = log[log.size - 1 - next].command
                    },
                ),
                QuickAction(
                    icon = Icons.Rounded.DeleteSweep,
                    label = stringResource(R.string.console_clear),
                    tint = colors.danger,
                    enabled = log.isNotEmpty(),
                    onClick = viewModel::clearTerminal,
                ),
                QuickAction(
                    icon = Icons.Rounded.FolderOpen,
                    label = stringResource(R.string.console_label_folder),
                    onClick = { viewModel.setTerminalCwd("") },
                    enabled = state.terminalCwd.isNotBlank(),
                ),
            ),
        )
    }

    if (pending != null) {
        AlertDialog(
            onDismissRequest = { pending = null },
            containerColor = colors.surface,
            title = { Text(stringResource(R.string.console_destructive_command_title), color = colors.danger) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        stringResource(R.string.console_destructive_command_body),
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
                    if (cmd != null) viewModel.runCommand(cmd, state.terminalCwd)
                }) { Text(stringResource(R.string.console_run_anyway), color = colors.danger) }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text(stringResource(R.string.console_cancel)) } },
        )
    }
}

/** The prompt a command was typed at, kept for the transcript. */
private fun TerminalEntry.cwdPrompt(current: String): String = TerminalPaths.prompt(current)


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
                text = stringResource(R.string.console_no_repository),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
            TextButton(onClick = viewModel::refreshGit) { Text(stringResource(R.string.console_check_again)) }
            return@CardBlock
        }

        KeyValueRow(stringResource(R.string.console_label_branch), git.branch.ifBlank { "—" }, highlight = true)
        KeyValueRow(stringResource(R.string.console_label_upstream), git.upstream.ifBlank { stringResource(R.string.console_none) })
        KeyValueRow(stringResource(R.string.console_label_sync), GitJson.syncLabel(git.ahead, git.behind))
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
                text = stringResource(R.string.console_staged),
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary,
                modifier = Modifier.padding(top = 10.dp),
            )
            stagedFiles.forEach { file ->
                ActionRow(
                    title = file.path,
                    subtitle = GitJson.changeLabel(file),
                    trailing = stringResource(R.string.console_unstage),
                    enabled = !state.gitBusy,
                    onClick = { viewModel.unstage(listOf(file.path)) },
                )
            }
        }
        if (unstagedFiles.isNotEmpty()) {
            Text(
                text = stringResource(R.string.console_modified),
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary,
                modifier = Modifier.padding(top = 10.dp),
            )
            unstagedFiles.forEach { file ->
                ActionRow(
                    title = file.path,
                    subtitle = GitJson.changeLabel(file),
                    trailing = stringResource(R.string.console_stage),
                    enabled = !state.gitBusy,
                    onClick = { viewModel.stage(listOf(file.path)) },
                )
            }
        }
        if (stagedFiles.isEmpty() && unstagedFiles.isEmpty()) {
            Text(
                text = stringResource(R.string.console_clean_tree),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        ButtonRow {
            TextButton(
                onClick = viewModel::stageAll,
                enabled = !state.gitBusy && unstagedFiles.isNotEmpty(),
            ) { Text(stringResource(R.string.console_stage_all)) }
            TextButton(
                onClick = viewModel::unstageAll,
                enabled = !state.gitBusy && stagedFiles.isNotEmpty(),
            ) { Text(stringResource(R.string.console_unstage_all)) }
        }
        ButtonRow {
            TextButton(
                onClick = { viewModel.showDiff(null, false) },
                enabled = !state.gitBusy && unstagedFiles.isNotEmpty(),
            ) { Text(stringResource(R.string.console_diff_unstaged)) }
            TextButton(
                onClick = { viewModel.showDiff(null, true) },
                enabled = !state.gitBusy && stagedFiles.isNotEmpty(),
            ) { Text(stringResource(R.string.console_diff_staged)) }
        }

        OutlinedTextField(
            value = state.commitDraft,
            onValueChange = viewModel::setCommitDraft,
            label = { Text(stringResource(R.string.console_commit_message)) },
            minLines = 2,
            textStyle = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
        )
        // Resolved in the composable scope: the onClick lambdas below are not composable.
        val commitTitle = stringResource(R.string.console_create_commit_title)
        val commitBody = stringResource(R.string.console_create_commit_body)
        val commitConfirm = stringResource(R.string.console_commit)
        ButtonRow {
            TextButton(
                onClick = viewModel::generateCommitMessage,
                enabled = !state.gitBusy,
            ) { Text(if (state.gitBusy) "…" else stringResource(R.string.console_generate)) }
            TextButton(
                onClick = { confirm = GitConfirm(
                    title = commitTitle,
                    body = commitBody,
                    confirm = commitConfirm,
                    action = viewModel::commit,
                ) },
                enabled = !state.gitBusy && state.commitDraft.isNotBlank(),
            ) { Text(stringResource(R.string.console_commit)) }
        }
        val pushTitle = stringResource(R.string.console_push_branch_title)
        val pushBody = stringResource(R.string.console_push_body)
        val pushConfirm = stringResource(R.string.console_push)
        val pullTitle = stringResource(R.string.console_pull_title)
        val pullBody = stringResource(R.string.console_pull_body)
        val pullConfirm = stringResource(R.string.console_pull)
        ButtonRow {
            TextButton(
                onClick = { confirm = GitConfirm(
                    title = pushTitle,
                    body = pushBody,
                    confirm = pushConfirm,
                    action = viewModel::push,
                ) },
                enabled = !state.gitBusy,
            ) { Text(stringResource(R.string.console_push)) }
            TextButton(
                onClick = { confirm = GitConfirm(
                    title = pullTitle,
                    body = pullBody,
                    confirm = pullConfirm,
                    action = viewModel::pull,
                ) },
                enabled = !state.gitBusy,
            ) { Text(stringResource(R.string.console_pull)) }
            TextButton(onClick = viewModel::refreshGit, enabled = !state.gitBusy) { Text(stringResource(R.string.console_refresh)) }
        }

        val branches = state.gitBranches.filter { it.name.isNotBlank() }
        if (branches.isNotEmpty()) {
            Text(
                text = stringResource(R.string.console_label_branches),
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
                    val switchTitle = stringResource(R.string.console_switch_branch_title)
                    val switchBody = stringResource(R.string.console_switch_branch_body, branch.name)
                    val switchConfirm = stringResource(R.string.console_switch)
                    FilterChip(
                        selected = current,
                        enabled = !state.gitBusy && !current,
                        onClick = { confirm = GitConfirm(
                            title = switchTitle,
                            body = switchBody,
                            confirm = switchConfirm,
                            action = { viewModel.checkout(branch.name) },
                        ) },
                        label = { Text(branch.name) },
                    )
                }
            }
        }

        if (state.gitLog.isNotEmpty()) {
            Text(
                text = stringResource(R.string.console_recent_commits),
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
            dismissButton = { TextButton(onClick = { confirm = null }) { Text(stringResource(R.string.console_cancel)) } },
        )
    }

    val diff = state.gitDiff
    if (diff != null) {
        AlertDialog(
            onDismissRequest = viewModel::dismissDiff,
            containerColor = colors.surface,
            title = {
                Text(
                    // The dialog's own words come from the resources that already had them.
                    text = listOfNotNull(
                        stringResource(
                            if (state.gitDiffStaged) R.string.console_diff_staged else R.string.console_diff_unstaged,
                        ),
                        state.gitDiffPath,
                    ).joinToString(" · "),
                    color = colors.textPrimary,
                    style = MaterialTheme.typography.titleSmall,
                )
            },
            text = {
                // The patch as text: this branch has no diff renderer, and pretending to
                // colour one would be worse than showing git's own output.
                Text(
                    text = diff.ifBlank { stringResource(R.string.console_diff_vide) },
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = colors.textPrimary,
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                )
            },
            confirmButton = { TextButton(onClick = viewModel::dismissDiff) { Text(stringResource(R.string.console_close)) } },
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

    SectionTitle(stringResource(R.string.console_section_music))
    CardBlock {
        if (music == null || !music.available) {
            Text(
                text = stringResource(R.string.console_spotify_not_configured),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
            return@CardBlock
        }
        if (!music.connected) {
            Text(
                text = stringResource(R.string.console_spotify_not_connected),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
            return@CardBlock
        }

        KeyValueRow(
            stringResource(R.string.console_label_account),
            listOf(music.accountName?.ifBlank { "—" } ?: "—", SpotifyJson.productLabel(music.product))
                .joinToString(" · "),
        )

        val track = state.nowPlaying
        if (track == null) {
            Text(
                text = stringResource(R.string.console_nothing_playing),
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
            TextButton(onClick = viewModel::previous, enabled = !state.musicBusy) { Text(stringResource(R.string.console_previous_track)) }
            TextButton(onClick = viewModel::play, enabled = !state.musicBusy) { Text(stringResource(R.string.console_play)) }
            TextButton(onClick = viewModel::pause, enabled = !state.musicBusy) { Text(stringResource(R.string.console_pause)) }
            TextButton(onClick = viewModel::next, enabled = !state.musicBusy) { Text(stringResource(R.string.console_next_track)) }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        ) {
            Text(
                text = stringResource(R.string.console_label_volume),
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
                text = stringResource(R.string.console_shuffle),
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
            KeyValueRow(stringResource(R.string.console_label_device), devices[0].name.ifBlank { devices[0].label })
        } else if (devices.size > 1) {
            Text(
                text = stringResource(R.string.console_label_devices),
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary,
                modifier = Modifier.padding(top = 10.dp),
            )
            devices.forEach { device ->
                ActionRow(
                    title = device.name.ifBlank { device.id },
                    subtitle = device.label,
                    trailing = if (device.active) stringResource(R.string.console_active) else null,
                    selected = device.active,
                )
            }
            Text(
                text = stringResource(R.string.console_devices_note),
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        ButtonRow {
            TextButton(onClick = viewModel::refreshMusic, enabled = !state.musicBusy) { Text(stringResource(R.string.console_refresh)) }
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
        title = { Text(stringResource(R.string.console_add_to_fleet_title), color = colors.textPrimary) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(R.string.console_fleet_lead), color = colors.textSecondary, style = MaterialTheme.typography.labelSmall)
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
                OutlinedTextField(value = id, onValueChange = { id = it }, label = { Text(stringResource(R.string.console_identifier)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.console_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text(stringResource(R.string.console_specialty)) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = persona, onValueChange = { persona = it }, label = { Text(stringResource(R.string.console_persona_optional)) }, minLines = 2, modifier = Modifier.fillMaxWidth())
                if (tiers.isNotEmpty()) {
                    Text(stringResource(R.string.console_permission_tier), color = colors.textSecondary, style = MaterialTheme.typography.labelSmall)
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
            ) { Text(stringResource(R.string.console_add)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.console_cancel)) } },
    )
}
