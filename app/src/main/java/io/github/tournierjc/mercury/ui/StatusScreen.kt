package io.github.tournierjc.mercury.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.tournierjc.mercury.BuildConfig
import io.github.tournierjc.mercury.MercuryApp
import io.github.tournierjc.mercury.R
import io.github.tournierjc.mercury.ui.theme.LocalMercuryColors
import io.github.tournierjc.mercury.util.formatUptime
import io.github.tournierjc.mercury.vm.StatusUiState
import io.github.tournierjc.mercury.vm.StatusViewModel

/**
 * The dashboard the app was missing: what the daemon is doing, which providers can answer, what
 * the second brain holds, what today has cost, and which instance this phone is attached to.
 *
 * It also carries the facts that used to live under Mémoire → « À propos » (agent, owner, default
 * provider, budget, heartbeat, second brain, Telegram): they describe the runtime, not the
 * memories, and they were the only reason to open that tab. The prose is gone; only the values
 * the webapp's overview shows are here.
 */
@Composable
fun StatusScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val container = remember(context) { (context.applicationContext as MercuryApp).container }
    val viewModel: StatusViewModel = viewModel(factory = StatusViewModel.factory(container))
    val state by viewModel.state.collectAsState()
    val colors = LocalMercuryColors.current
    // Local configuration, not a route: the VM reads it from the container it was built with.
    val serverUrl = viewModel.serverUrl

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.canvas)
            .safeDrawingPadding(),
    ) {
        ScreenHeader(
            title = stringResource(R.string.nav_status),
            subtitle = state.status?.let { status ->
                listOf(
                    daemonStateLabel(status.state, status.running),
                    formatUptime(status.uptime),
                ).joinToString(" · ")
            },
            onBack = onBack,
            onRefresh = viewModel::refresh,
            refreshing = state.loading,
        )
        state.error?.let { notice -> NoticeBanner(notice, onDismiss = viewModel::clearBanner) }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp),
        ) {
            DaemonSection(state)
            InstanceSection(state, serverUrl)
            ProvidersSection(state)
            BrainSection(state)
            UsageSection(state)
            AgentSection(state)
            Text(
                text = " ",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(bottom = 24.dp),
            )
        }
    }
}

/** `GET /api/status` — the daemon itself: state, uptime, and what it admits to holding. */
@Composable
private fun DaemonSection(state: StatusUiState) {
    val colors = LocalMercuryColors.current
    CollapsibleSection(stringResource(R.string.status_section_daemon), defaultOpen = true) {
        val status = state.status
        if (status == null) {
            Text(
                text = stringResource(if (state.loading) R.string.common_loading else R.string.status_daemon_unavailable),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
            return@CollapsibleSection
        }
        KeyValueRow(
            label = stringResource(R.string.status_label_state),
            value = daemonStateLabel(status.state, status.running),
            highlight = status.running,
        )
        KeyValueRow(stringResource(R.string.status_label_uptime), formatUptime(status.uptime))
    }
}

/**
 * The app's own block: the build in the owner's hand, the host it talks to, and how long that
 * host has been up. All three are readable without a route, so a dead daemon still leaves them
 * on screen — and a header that says "?" instead of a state is the only thing that changes.
 */
@Composable
private fun InstanceSection(state: StatusUiState, serverUrl: String) {
    CollapsibleSection(stringResource(R.string.status_section_instance), defaultOpen = true) {
        KeyValueRow(stringResource(R.string.status_label_version), BuildConfig.VERSION_NAME)
        if (serverUrl.isNotBlank()) {
            KeyValueRow(stringResource(R.string.status_label_server), serverUrl)
        }
    }
}

/** The providers that can actually answer: enabled *and* holding a key. */
@Composable
private fun ProvidersSection(state: StatusUiState) {
    val colors = LocalMercuryColors.current
    val ready = state.readyProviders
    CollapsibleSection(stringResource(R.string.status_section_providers), defaultOpen = true) {
        if (state.status == null) {
            Text(
                text = stringResource(if (state.loading) R.string.common_loading else R.string.status_daemon_unavailable),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
            return@CollapsibleSection
        }
        Text(
            text = pluralStringResource(R.plurals.status_providers_ready, ready.size, ready.size),
            style = MaterialTheme.typography.bodyLarge,
            color = if (ready.isEmpty()) colors.textSecondary else colors.accent,
        )
        if (ready.isEmpty()) {
            Text(
                text = stringResource(R.string.status_providers_none),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        ready.forEach { name ->
            KeyValueRow(label = name, value = stringResource(R.string.status_provider_key_present))
        }
    }
}

/** `GET /api/brain/status` — how much the agent remembers, by kind, and whether it is learning. */
@Composable
private fun BrainSection(state: StatusUiState) {
    val colors = LocalMercuryColors.current
    CollapsibleSection(stringResource(R.string.status_section_brain)) {
        val brain = state.brain
        if (brain == null) {
            Text(
                text = stringResource(if (state.loading) R.string.common_loading else R.string.status_brain_unavailable),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
            return@CollapsibleSection
        }
        KeyValueRow(stringResource(R.string.status_label_memories), formatCount(brain.total.toLong()))
        KeyValueRow(stringResource(R.string.status_label_subconscious), formatCount(brain.subconsciousTotal.toLong()))
        if (brain.byType.isNotEmpty()) {
            brain.byType.toList()
                .sortedByDescending { it.second }
                .forEach { (type, count) -> KeyValueRow(label = rawLabel(type), value = formatCount(count.toLong())) }
        } else {
            Text(
                text = stringResource(R.string.status_memories_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
        }
        KeyValueRow(
            label = stringResource(R.string.status_label_learning),
            value = stringResource(if (brain.learningPaused) R.string.status_learning_paused else R.string.status_learning_active),
            highlight = brain.learningPaused,
        )
    }
}

/** `GET /api/usage` — today against the cap, with the bar the owner already knows from Réglages. */
@Composable
private fun UsageSection(state: StatusUiState) {
    val colors = LocalMercuryColors.current
    CollapsibleSection(stringResource(R.string.status_section_usage), defaultOpen = true) {
        val usage = state.usage
        if (usage == null) {
            Text(
                text = stringResource(if (state.loading) R.string.common_loading else R.string.status_usage_unavailable),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
            return@CollapsibleSection
        }
        Text(
            text = "${formatCompact(usage.dailyUsed)} / ${
                if (usage.dailyBudget > 0) formatCompact(usage.dailyBudget) else stringResource(R.string.status_unlimited)
            }",
            style = MaterialTheme.typography.bodyLarge,
            color = if (usage.dailyBudget > 0 && usage.dailyUsed >= usage.dailyBudget) colors.danger else colors.textPrimary,
        )
        ProgressLine(usage.ratio)
        KeyValueRow(
            label = stringResource(R.string.status_label_remaining),
            value = if (usage.dailyBudget <= 0) stringResource(R.string.status_unlimited) else formatCompact(usage.remaining),
            highlight = usage.remaining <= 0 && usage.dailyBudget > 0,
        )
    }
}

/**
 * `GET /api/config` — the runtime's identity and the switches the About tab described in prose.
 * A field the daemon leaves blank is not drawn: "—" is a value, absence is not.
 */
@Composable
private fun AgentSection(state: StatusUiState) {
    val colors = LocalMercuryColors.current
    val config = state.config
    if (config == null && !state.loading) {
        // The route failed: say so once, below the rest, without a section that pretends.
        CollapsibleSection(stringResource(R.string.status_section_agent)) {
            Text(
                text = stringResource(R.string.status_agent_unavailable),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
        }
        return
    }
    if (config == null) return
    CollapsibleSection(stringResource(R.string.status_section_agent)) {
        KeyValueRow(stringResource(R.string.status_label_agent_name), config.identity.name.ifBlank { PLACEHOLDER })
        KeyValueRow(stringResource(R.string.status_label_owner), config.identity.owner.ifBlank { PLACEHOLDER })
        KeyValueRow(stringResource(R.string.status_label_default_provider), config.defaultProvider.ifBlank { PLACEHOLDER })
        KeyValueRow(
            label = stringResource(R.string.status_label_token_budget),
            value = if (config.tokenBudget > 0) formatCompact(config.tokenBudget) else stringResource(R.string.status_unlimited),
        )
        if (config.heartbeatInterval > 0) {
            KeyValueRow(
                label = stringResource(R.string.status_label_heartbeat),
                value = stringResource(R.string.status_heartbeat_minutes, formatCount(config.heartbeatInterval)),
            )
        }
        KeyValueRow(
            label = stringResource(R.string.status_label_second_brain),
            value = stringResource(if (config.secondBrainEnabled) R.string.status_on else R.string.status_off),
            highlight = config.secondBrainEnabled,
        )
        KeyValueRow(
            label = stringResource(R.string.status_label_telegram),
            value = stringResource(if (config.telegramEnabled) R.string.status_on else R.string.status_off),
            highlight = config.telegramEnabled,
        )
    }
}

/** What a missing value reads as. Punctuation, not a word to translate. */
private const val PLACEHOLDER = "—"

/** The daemon's state in the owner's words; an unknown state is shown as the daemon names it. */
@Composable
private fun daemonStateLabel(state: String, running: Boolean): String = when (state.lowercase()) {
    "" -> stringResource(if (running) R.string.status_state_running else R.string.status_state_stopped)
    "running" -> stringResource(R.string.status_state_running)
    "idle" -> stringResource(R.string.status_state_idle)
    "stopped" -> stringResource(R.string.status_state_stopped)
    "booting", "starting" -> stringResource(R.string.status_state_starting)
    "paused" -> stringResource(R.string.status_state_paused)
    else -> state
}
