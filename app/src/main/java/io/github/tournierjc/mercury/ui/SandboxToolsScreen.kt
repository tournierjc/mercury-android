package io.github.tournierjc.mercury.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import io.github.tournierjc.mercury.MercuryApp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.tournierjc.mercury.R
import io.github.tournierjc.mercury.data.dto.ConnectFlowDto
import io.github.tournierjc.mercury.data.SandboxToolAction
import io.github.tournierjc.mercury.data.SandboxToolActions
import io.github.tournierjc.mercury.data.dto.SandboxToolDto
import io.github.tournierjc.mercury.ui.theme.LocalMercuryColors
import io.github.tournierjc.mercury.vm.SandboxToolsViewModel
import kotlinx.coroutines.delay
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton

/**
 * The tools the bots can run, and whether they are signed in.
 *
 * One truth the screen states once instead of per row: the sandbox confines paths, not processes, so a
 * tool installed here is shared by every bot and by the main agent. Installs land in
 * `~/.mercury/tools/`, which survives a container upgrade — otherwise "install it once" would mean
 * nothing.
 */
@Composable
fun SandboxToolsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val container = remember { (context.applicationContext as MercuryApp).container }
    val vm: SandboxToolsViewModel = viewModel(factory = SandboxToolsViewModel.factory(container))
    val colors = LocalMercuryColors.current
    val state by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { if (!state.loaded) vm.refresh() }
    // Installs are minutes and device flows end on another screen: follow them while they move, and
    // stop as soon as nothing is in flight (poll() returns immediately when nothing runs).
    val installsRunning = state.installs.values.any { it.state == "running" }
    val flowPending = state.flows.values.any { it.state == "pending" }
    LaunchedEffect(installsRunning to flowPending) {
        while (installsRunning || flowPending) {
            delay(3_000)
            vm.poll()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.canvas)
            .safeDrawingPadding(),
    ) {
        ScreenHeader(
            title = stringResource(R.string.sandbox_title),
            subtitle = stringResource(R.string.sandbox_shared_note),
            onBack = onBack,
        )

        when {
            !state.loaded -> Text(
                text = stringResource(R.string.common_loading),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
                modifier = Modifier.padding(16.dp),
            )

            state.tools.isEmpty() -> Text(
                text = state.error?.let { noticeText(it) } ?: stringResource(R.string.sandbox_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = if (state.error == null) colors.textSecondary else colors.danger,
                modifier = Modifier.padding(16.dp),
            )

            else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(state.tools, key = { it.id }) { tool ->
                    ToolRow(
                        tool = tool,
                        install = state.installs[tool.id],
                        flow = state.flows[tool.id],
                        busy = state.busy == tool.id,
                        onInstall = { vm.install(tool.id) },
                        onConnect = { vm.connect(tool.id) },
                        onDisconnect = { vm.disconnect(tool.id) },
                        onRemove = { vm.remove(tool.id) },
                    )
                }
                state.error?.let { error ->
                    item {
                        TextButton(onClick = { vm.clearError() }) {
                            Text(noticeText(error), color = colors.danger)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolRow(
    tool: SandboxToolDto,
    install: io.github.tournierjc.mercury.data.dto.InstallRecordDto?,
    flow: ConnectFlowDto?,
    busy: Boolean,
    onInstall: () -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onRemove: () -> Unit,
) {
    val colors = LocalMercuryColors.current
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var confirming by remember { mutableStateOf(false) }
    // Removing is destructive and shared, so it asks once — like signing out, and for the same
    // reason: every bot loses the tool at the same instant.
    var confirmingRemove by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val running = install?.state == "running" || busy
        Row(verticalAlignment = Alignment.Top) {
            // What the tool is, on the left: name, sign-in state, version and path.
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = tool.label,
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.textPrimary,
                    )
                    // The sign-in state is the second question an owner asks, after "is it there".
                    val authLabel = when {
                        !tool.present -> ""
                        tool.auth == "connected" -> stringResource(R.string.sandbox_connected)
                        tool.auth == "no" -> stringResource(R.string.sandbox_not_connected)
                        else -> ""
                    }
                    if (authLabel.isNotBlank()) {
                        Text(
                            text = authLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (tool.auth == "connected") colors.textSecondary else colors.danger,
                            modifier = Modifier.padding(start = 10.dp),
                        )
                    }
                }
                Text(
                    text = listOf(tool.version, tool.path).filter { it.isNotBlank() }
                        .joinToString(" · ").ifBlank { tool.description },
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!tool.present && tool.installBlocked.isNotBlank()) {
                    Text(
                        text = tool.installBlocked,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textSecondary,
                    )
                }
            }
            // The actions, in one column on the right edge: as text buttons they pushed the version
            // and the path into a narrow ribbon, and the widest labels wrapped. An icon column costs
            // 40 dp of width and keeps every row the same height whatever it offers.
            Column(horizontalAlignment = Alignment.End) {
                // One rule for the whole row, in :core and tested: which actions a tool offers is
                // independent of how they are drawn.
                SandboxToolActions.of(tool).forEach { action ->
                    when (action) {
                        SandboxToolAction.Install -> ToolActionButton(
                            icon = Icons.Rounded.Download,
                            label = stringResource(if (running) R.string.sandbox_installing else R.string.sandbox_install),
                            enabled = !running,
                            onClick = onInstall,
                        )

                        SandboxToolAction.Connect -> ToolActionButton(
                            icon = Icons.Rounded.Link,
                            label = stringResource(R.string.sandbox_connect),
                            enabled = flow?.state != "pending",
                            onClick = onConnect,
                        )

                        // Signing out affects every bot at once, so it asks once instead of sitting
                        // one tap away from an accidental revocation.
                        SandboxToolAction.Disconnect -> ToolActionButton(
                            icon = Icons.AutoMirrored.Rounded.Logout,
                            label = stringResource(R.string.sandbox_disconnect),
                            enabled = !busy,
                            onClick = { confirming = true },
                        )

                        // Deleting what we installed: the second question the owner asked on this
                        // screen, and the one that had no answer at all.
                        SandboxToolAction.Remove -> ToolActionButton(
                            icon = Icons.Rounded.DeleteOutline,
                            label = stringResource(R.string.sandbox_remove),
                            enabled = !busy,
                            destructive = true,
                            onClick = { confirmingRemove = true },
                        )
                    }
                }
                if (running) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(12.dp).size(18.dp),
                        strokeWidth = 2.dp,
                        color = colors.accent,
                    )
                }
            }
        }

        install?.takeIf { it.state == "failed" }?.let {
            Text(
                text = stringResource(R.string.sandbox_install_failed, it.log.takeLast(300)),
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = colors.danger,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // A pending device flow: the code is the whole feature, so it is large, monospace and one tap
        // from the clipboard, with the page to open next to it.
        flow?.takeIf { it.state == "pending" && it.code.isNotBlank() }?.let { live ->
            Text(
                text = stringResource(R.string.sandbox_code_hint),
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = live.code,
                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
                    color = colors.textPrimary,
                )
                TextButton(onClick = { clipboard.setText(AnnotatedString(live.code)) }) {
                    Text(stringResource(R.string.sandbox_copy_code))
                }
            }
            if (live.url.isNotBlank()) {
                // The code alone is useless without the page it is typed on: a URL printed in accent
                // colour that ignores a tap reads as a broken screen, so the tap opens the browser.
                Text(
                    text = live.url,
                    style = MaterialTheme.typography.bodySmall.copy(textDecoration = TextDecoration.Underline),
                    color = colors.accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(live.url)))
                            }
                        }
                        .padding(vertical = 4.dp),
                )
                Text(
                    text = stringResource(R.string.sandbox_open_browser),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary,
                )
            }
            Text(
                text = stringResource(R.string.sandbox_waiting),
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary,
            )
        }
        flow?.takeIf { it.state == "failed" }?.let {
            Text(
                text = stringResource(R.string.sandbox_connect_failed),
                style = MaterialTheme.typography.labelSmall,
                color = colors.danger,
            )
        }
    }

    if (confirmingRemove) {
        AlertDialog(
            onDismissRequest = { confirmingRemove = false },
            title = { Text(stringResource(R.string.sandbox_remove_question, tool.label)) },
            text = { Text(stringResource(R.string.sandbox_remove_warning)) },
            confirmButton = {
                TextButton(onClick = { confirmingRemove = false; onRemove() }) {
                    Text(stringResource(R.string.sandbox_remove), color = colors.danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingRemove = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }

    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text(stringResource(R.string.sandbox_disconnect_question, tool.label)) },
            text = { Text(stringResource(R.string.sandbox_disconnect_warning)) },
            confirmButton = {
                TextButton(onClick = { confirming = false; onDisconnect() }) {
                    Text(stringResource(R.string.sandbox_disconnect), color = colors.danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirming = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

/**
 * Une action de la ligne, en icône.
 *
 * Le libellé reste, mais il va à TalkBack (`contentDescription`) au lieu de tenir 90 dp de large sur
 * chaque rangée : quatre actions en texte poussaient la version et le chemin dans un ruban étroit, et
 * « Désinstaller » finissait sur deux lignes. La colonne d'icônes garde toutes les rangées de la même
 * hauteur, quelle que soit l'action qu'elles offrent.
 */
@Composable
private fun ToolActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = LocalMercuryColors.current
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(40.dp)) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = when {
                !enabled -> colors.textSecondary.copy(alpha = 0.4f)
                destructive -> colors.danger
                else -> colors.accent
            },
            modifier = Modifier.size(22.dp),
        )
    }
}
