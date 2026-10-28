package io.github.tournierjc.mercury.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.tournierjc.mercury.MercuryApp
import io.github.tournierjc.mercury.R
import io.github.tournierjc.mercury.data.Notice
import io.github.tournierjc.mercury.ui.theme.LocalMercuryColors
import io.github.tournierjc.mercury.vm.ConfigureUiState
import io.github.tournierjc.mercury.vm.ConfigureSlice
import io.github.tournierjc.mercury.vm.ConfigureViewModel

/**
 * The Configure group: seven read-only surfaces, one per capability, in the webapp's order.
 *
 * None of them writes anything. Each answers "what is this instance made of" — which providers
 * can answer, what the agent can do, what it is allowed to touch, what it is told to run, what it
 * has spent — and points at Réglages for the things the owner can change from the phone. That
 * split is deliberate: a second set of switches here would be a second source of truth.
 *
 * They share one [ConfigureViewModel], so walking the group costs one round of requests rather
 * than seven, and every screen still refreshes on demand from its header.
 */

// ---------------------------------------------------------------------------
// Providers
// ---------------------------------------------------------------------------

@Composable
fun ProvidersScreen(onBack: () -> Unit) {
    val (viewModel, state) = rememberConfigure()
    ConfigureScaffold(
        title = stringResource(R.string.configure_providers_title),
        subtitle = if (ConfigureSlice.Providers in state.failed) {
            null
        } else {
            pluralStringResource(
                R.plurals.configure_providers_count,
                state.providers.size,
                state.providers.size,
            )
        },
        onBack = onBack,
        onRefresh = viewModel::refresh,
        error = state.error,
        onDismissError = viewModel::clearBanner,
    ) {
        CollapsibleSection(stringResource(R.string.configure_providers_title), defaultOpen = true) {
            if (state.providers.isEmpty()) {
                EmptyLine(state.loading, R.string.configure_providers_empty, ConfigureSlice.Providers in state.failed)
            }
            state.providers.forEach { provider ->
                // The app already has exactly one provider row (Réglages). `busy` greys its two
                // glyphs — edit and probe — because writing a key is Réglages' job, not this
                // screen's; the row itself, dots, model and base URL included, is not rebuilt.
                ProviderRow(provider = provider, busy = true, onEdit = {}, onTest = {})
            }
            Text(
                text = stringResource(R.string.configure_providers_edit_note),
                style = MaterialTheme.typography.labelSmall,
                color = LocalMercuryColors.current.textSecondary,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Skills
// ---------------------------------------------------------------------------

@Composable
fun SkillsScreen(onBack: () -> Unit) {
    val (viewModel, state) = rememberConfigure()
    val colors = LocalMercuryColors.current
    ConfigureScaffold(
        title = stringResource(R.string.configure_skills_title),
        subtitle = pluralStringResource(R.plurals.configure_skills_count, state.skills.size, state.skills.size)
            .takeIf { state.skills.isNotEmpty() },
        onBack = onBack,
        onRefresh = viewModel::refresh,
        error = state.error,
        onDismissError = viewModel::clearBanner,
    ) {
        CollapsibleSection(stringResource(R.string.configure_skills_title), defaultOpen = true) {
            if (state.skills.isEmpty()) {
                EmptyLine(state.loading, R.string.configure_skills_empty, ConfigureSlice.Skills in state.failed)
            }
            state.skills.forEach { skill ->
                // "v—" is what a prefix in front of an unknown version produces: drop the prefix.
                val version = skill.version?.takeIf { it.isNotBlank() }
                    ?.let { stringResource(R.string.configure_version, it) }
                val activity = stringResource(if (skill.active) R.string.configure_active else R.string.configure_inactive)
                val tools = pluralStringResource(
                    R.plurals.configure_skills_tools,
                    skill.allowedTools.size,
                    skill.allowedTools.size,
                )
                val flags = listOfNotNull(
                    stringResource(R.string.configure_skills_scripts).takeIf { skill.hasScripts },
                    stringResource(R.string.configure_skills_references).takeIf { skill.hasReferences },
                )
                DetailCard(
                    label = "$version · $activity",
                    labelTint = if (skill.active) colors.accent else colors.textSecondary,
                    title = skill.name,
                    body = skill.description,
                    meta = (listOf(tools) + flags).joinToString(" · "),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// MCP
// ---------------------------------------------------------------------------

@Composable
fun McpScreen(onBack: () -> Unit) {
    val (viewModel, state) = rememberConfigure()
    val colors = LocalMercuryColors.current
    ConfigureScaffold(
        title = stringResource(R.string.configure_mcp_title),
        subtitle = pluralStringResource(
            R.plurals.configure_mcp_servers,
            state.mcpServers.size,
            state.mcpServers.size,
        ),
        onBack = onBack,
        onRefresh = viewModel::refresh,
        error = state.error,
        onDismissError = viewModel::clearBanner,
    ) {
        CollapsibleSection(stringResource(R.string.configure_mcp_title), defaultOpen = true) {
            when {
                !state.mcpAvailable -> Text(
                    text = stringResource(R.string.configure_mcp_unavailable),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )

                state.mcpServers.isEmpty() -> EmptyLine(state.loading, R.string.configure_mcp_empty, ConfigureSlice.Mcp in state.failed)
            }
            state.mcpServers.forEach { server ->
                val tools = pluralStringResource(R.plurals.configure_mcp_tools, server.toolCount, server.toolCount)
                DetailCard(
                    label = configureMcpStateLabel(server.state),
                    labelTint = when (server.state) {
                        "connected" -> colors.accent
                        "failed" -> colors.danger
                        else -> colors.textSecondary
                    },
                    title = server.id.ifBlank { stringResource(R.string.configure_mcp_unnamed) },
                    body = server.error,
                    meta = listOf(server.transport, server.target, tools)
                        .filter { it.isNotBlank() }
                        .joinToString(" · "),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Plugins
// ---------------------------------------------------------------------------

@Composable
fun PluginsScreen(onBack: () -> Unit) {
    val (viewModel, state) = rememberConfigure()
    val colors = LocalMercuryColors.current
    ConfigureScaffold(
        title = stringResource(R.string.configure_plugins_title),
        subtitle = if (ConfigureSlice.Plugins in state.failed) {
            null
        } else {
            pluralStringResource(
                R.plurals.configure_plugins_count,
                state.plugins.size,
                state.plugins.size,
            )
        },
        onBack = onBack,
        onRefresh = viewModel::refresh,
        error = state.error,
        onDismissError = viewModel::clearBanner,
    ) {
        CollapsibleSection(stringResource(R.string.configure_plugins_title), defaultOpen = true) {
            if (state.plugins.isEmpty()) {
                EmptyLine(state.loading, R.string.configure_plugins_empty, ConfigureSlice.Plugins in state.failed)
            }
            state.plugins.forEach { plugin ->
                val version = plugin.version.takeIf { it.isNotBlank() }
                    ?.let { stringResource(R.string.configure_version, it) }
                val family = listOf(rawLabel(plugin.kind), rawLabel(plugin.tier)).filter { it.isNotBlank() }.joinToString(" · ")
                val renderers = if (plugin.renderers.isEmpty()) {
                    null
                } else {
                    pluralStringResource(
                        R.plurals.configure_plugin_renderers,
                        plugin.renderers.size,
                        plugin.renderers.size,
                    )
                }
                DetailCard(
                    label = configurePluginStatusLabel(plugin.status),
                    labelTint = when {
                        plugin.running -> colors.accent
                        plugin.status.equals("failed", ignoreCase = true) -> colors.danger
                        else -> colors.textSecondary
                    },
                    title = plugin.name.ifBlank { plugin.id },
                    body = plugin.description,
                    meta = listOfNotNull(version, family.ifBlank { null }, renderers).joinToString(" · "),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Permissions
// ---------------------------------------------------------------------------

@Composable
fun PermissionsScreen(onBack: () -> Unit) {
    val (viewModel, state) = rememberConfigure()
    val colors = LocalMercuryColors.current
    ConfigureScaffold(
        title = stringResource(R.string.configure_permissions_title),
        subtitle = null,
        onBack = onBack,
        onRefresh = viewModel::refresh,
        error = state.error,
        onDismissError = viewModel::clearBanner,
    ) {
        val permissions = state.permissions
        CollapsibleSection(stringResource(R.string.configure_permissions_filesystem), defaultOpen = true) {
            if (permissions == null) {
                EmptyLine(state.loading, R.string.configure_permissions_unavailable, ConfigureSlice.Permissions in state.failed)
            } else {
                KeyValueRow(
                    label = stringResource(R.string.configure_permissions_enabled),
                    value = onOffLabel(permissions.filesystemEnabled),
                    highlight = permissions.filesystemEnabled,
                )
                if (permissions.scopes.isEmpty()) {
                    Text(
                        text = stringResource(R.string.configure_permissions_no_scope),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                    )
                }
                permissions.scopes.forEach { scope ->
                    val rights = listOfNotNull(
                        stringResource(R.string.configure_permissions_read).takeIf { scope.read },
                        stringResource(R.string.configure_permissions_write).takeIf { scope.write },
                    ).joinToString(" · ").ifBlank { stringResource(R.string.configure_permissions_none) }
                    KeyValueRow(label = scope.path, value = rights)
                }
            }
        }
        CollapsibleSection(stringResource(R.string.configure_permissions_shell), defaultOpen = true) {
            if (permissions == null) {
                EmptyLine(state.loading, R.string.configure_permissions_unavailable, ConfigureSlice.Permissions in state.failed)
            } else {
                KeyValueRow(
                    label = stringResource(R.string.configure_permissions_enabled),
                    value = onOffLabel(permissions.shellEnabled),
                    highlight = permissions.shellEnabled,
                )
                KeyValueRow(
                    label = stringResource(R.string.configure_permissions_blocked_label),
                    value = pluralStringResource(
                        R.plurals.configure_permissions_blocked,
                        permissions.blockedCount,
                        permissions.blockedCount,
                    ),
                )
            }
        }
        Text(
            text = stringResource(R.string.configure_permissions_readonly),
            style = MaterialTheme.typography.labelSmall,
            color = colors.textSecondary,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

// ---------------------------------------------------------------------------
// Schedules
// ---------------------------------------------------------------------------

@Composable
fun SchedulesScreen(onBack: () -> Unit) {
    val (viewModel, state) = rememberConfigure()
    val colors = LocalMercuryColors.current
    ConfigureScaffold(
        title = stringResource(R.string.configure_schedules_title),
        subtitle = if (ConfigureSlice.Schedules in state.failed) {
            null
        } else {
            pluralStringResource(
                R.plurals.configure_schedules_count,
                state.schedules.size,
                state.schedules.size,
            )
        },
        onBack = onBack,
        onRefresh = viewModel::refresh,
        error = state.error,
        onDismissError = viewModel::clearBanner,
    ) {
        CollapsibleSection(stringResource(R.string.configure_schedules_title), defaultOpen = true) {
            if (state.schedules.isEmpty()) {
                EmptyLine(state.loading, R.string.configure_schedules_empty, ConfigureSlice.Schedules in state.failed)
            }
            state.schedules.forEach { schedule ->
                val bot = schedule.botId.ifBlank { stringResource(R.string.configure_schedules_mercury) }
                // A raw ISO string answers neither "when is it" nor "how soon".
                val next = formatWhen(schedule.nextRun)
                    ?.let { stringResource(R.string.configure_schedules_next, it) }
                val created = formatWhen(schedule.createdAt)
                    ?.let { stringResource(R.string.configure_schedules_created, it) }
                DetailCard(
                    // The cadence in words where we can read the expression; the expression itself
                    // moves to the meta, next to the rest of the identifiers.
                    label = cronCadenceLabel(schedule.cron) ?: schedule.cron.ifBlank { UNNAMED },
                    labelTint = colors.accent,
                    title = schedule.description.ifBlank { schedule.prompt }.ifBlank { schedule.id },
                    meta = listOfNotNull(schedule.cron.takeIf { it.isNotBlank() }, bot, next, created)
                        .joinToString(" · "),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Usage
// ---------------------------------------------------------------------------

@Composable
fun UsageScreen(onBack: () -> Unit) {
    val (viewModel, state) = rememberConfigure()
    val colors = LocalMercuryColors.current
    ConfigureScaffold(
        title = stringResource(R.string.configure_usage_title),
        subtitle = state.usage?.let { usage ->
            "${formatCompact(usage.dailyUsed)} / ${
                if (usage.dailyBudget > 0) formatCompact(usage.dailyBudget) else stringResource(R.string.configure_usage_unlimited)
            }"
        },
        onBack = onBack,
        onRefresh = viewModel::refresh,
        error = state.error,
        onDismissError = viewModel::clearBanner,
    ) {
        CollapsibleSection(stringResource(R.string.configure_usage_today), defaultOpen = true) {
            val usage = state.usage
            if (usage == null) {
                EmptyLine(state.loading, R.string.configure_usage_unavailable, ConfigureSlice.Usage in state.failed)
            } else {
                Text(
                    text = "${formatCompact(usage.dailyUsed)} / ${
                        if (usage.dailyBudget > 0) formatCompact(usage.dailyBudget) else stringResource(R.string.configure_usage_unlimited)
                    }",
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (usage.dailyBudget > 0 && usage.dailyUsed >= usage.dailyBudget) {
                        colors.danger
                    } else {
                        colors.textPrimary
                    },
                )
                ProgressLine(usage.ratio)
                Text(
                    text = stringResource(R.string.configure_usage_percent, (usage.ratio * 100).toInt()),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(top = 6.dp),
                )
                KeyValueRow(
                    label = stringResource(R.string.configure_usage_remaining),
                    value = if (usage.dailyBudget <= 0) {
                        stringResource(R.string.configure_usage_unlimited)
                    } else {
                        formatCompact(usage.remaining)
                    },
                )
                KeyValueRow(
                    label = stringResource(R.string.configure_usage_reset),
                    value = usage.lastResetDate.ifBlank { UNNAMED },
                )
            }
        }

        CollapsibleSection(stringResource(R.string.configure_usage_by_model)) {
            if (state.byModel.isEmpty()) {
                EmptyLine(state.loading, R.string.configure_usage_no_requests)
            }
            state.byModel.forEach { slice ->
                KeyValueRow(
                    label = slice.label,
                    value = "${formatCompact(slice.tokens)} · ${stringResource(R.string.configure_usage_share, slice.share)}",
                )
            }
        }

        CollapsibleSection(stringResource(R.string.configure_usage_by_channel)) {
            if (state.byChannel.isEmpty()) {
                EmptyLine(state.loading, R.string.configure_usage_no_requests)
            }
            state.byChannel.forEach { slice ->
                KeyValueRow(
                    label = slice.label,
                    value = "${formatCompact(slice.tokens)} · ${stringResource(R.string.configure_usage_share, slice.share)}",
                )
            }
        }

        CollapsibleSection(stringResource(R.string.configure_usage_recent)) {
            if (state.recent.isEmpty()) {
                EmptyLine(state.loading, R.string.configure_usage_no_requests)
            }
            state.recent.forEach { request ->
                val where = listOf(request.provider, request.channel)
                    .filter { it.isNotBlank() }
                    .joinToString(" · ")
                val counts = stringResource(
                    R.string.configure_usage_counts,
                    formatCompact(request.input),
                    formatCompact(request.output),
                )
                ActionRow(
                    title = request.model.ifBlank { request.provider }.ifBlank { UNNAMED },
                    subtitle = listOf(where, counts).filter { it.isNotBlank() }.joinToString(" · "),
                    trailing = request.time.ifBlank { null },
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Shared furniture for the group
// ---------------------------------------------------------------------------

/** The seven screens share one state object; the factory is keyed to the activity. */
@Composable
private fun rememberConfigure(): Pair<ConfigureViewModel, ConfigureUiState> {
    val context = LocalContext.current
    val container = remember(context) { (context.applicationContext as MercuryApp).container }
    val viewModel: ConfigureViewModel = viewModel(factory = ConfigureViewModel.factory(container))
    val state by viewModel.state.collectAsState()
    return viewModel to state
}

/**
 * One shape for all seven: a header with a refresh, the failure banner, and a scrolling body.
 * Repeating it per screen would guarantee that one of them eventually forgets the banner.
 */
@Composable
private fun ConfigureScaffold(
    title: String,
    subtitle: String?,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    error: Notice?,
    onDismissError: () -> Unit,
    content: @Composable () -> Unit,
) {
    val colors = LocalMercuryColors.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.canvas)
            .safeDrawingPadding(),
    ) {
        ScreenHeader(title = title, subtitle = subtitle, onBack = onBack, onRefresh = onRefresh)
        error?.let { notice -> NoticeBanner(notice, onDismiss = onDismissError) }
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp),
        ) {
            content()
            Text(
                text = " ",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(bottom = 24.dp),
            )
        }
    }
}

/**
 * A dense row: a small label on top, the name, an optional description, then the facts.
 * The same shape as a memory card — these lists are read, not tapped.
 */
@Composable
private fun DetailCard(
    label: String,
    title: String,
    body: String? = null,
    meta: String? = null,
    labelTint: Color? = null,
) {
    val colors = LocalMercuryColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(colors.surfaceMuted, MaterialTheme.shapes.extraSmall)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = labelTint ?: colors.accent,
        )
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = colors.textPrimary,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
        if (!body.isNullOrBlank()) {
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        if (!meta.isNullOrBlank()) {
            Text(
                text = meta,
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

/** The one empty/loading line every section falls back to — never a blank screen. */
@Composable
private fun EmptyLine(loading: Boolean, @StringRes emptyState: Int, failed: Boolean = false) {
    Text(
        // Three states, three sentences: still loading, we could not ask, and the server answered
        // that there is nothing. Collapsing the middle one into the last one lies about the server.
        text = stringResource(
            when {
                loading -> R.string.common_loading
                failed -> R.string.configure_unavailable
                else -> emptyState
            },
        ),
        style = MaterialTheme.typography.bodyMedium,
        color = LocalMercuryColors.current.textSecondary,
    )
}

@Composable
private fun onOffLabel(value: Boolean): String =
    stringResource(if (value) R.string.status_on else R.string.status_off)

@Composable
private fun configureMcpStateLabel(state: String): String = when (state) {
    "connected" -> stringResource(R.string.configure_mcp_connected)
    "connecting" -> stringResource(R.string.configure_mcp_connecting)
    "reconnecting" -> stringResource(R.string.configure_mcp_reconnecting)
    "failed" -> stringResource(R.string.configure_mcp_failed)
    "disabled" -> stringResource(R.string.configure_mcp_disabled)
    else -> state.ifBlank { stringResource(R.string.configure_mcp_unknown) }
}

@Composable
private fun configurePluginStatusLabel(status: String): String = when (status.lowercase()) {
    "enabled" -> stringResource(R.string.configure_plugin_enabled)
    "available" -> stringResource(R.string.configure_plugin_available)
    "disabled" -> stringResource(R.string.configure_plugin_disabled)
    "failed" -> stringResource(R.string.configure_plugin_failed)
    else -> status.ifBlank { stringResource(R.string.configure_plugin_unknown) }
}

/** What a value the server left empty reads as. Punctuation, not a word to translate. */
private const val UNNAMED = "—"
