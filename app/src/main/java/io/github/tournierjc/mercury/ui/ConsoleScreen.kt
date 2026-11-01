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
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.FactCheck
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.SkipNext
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
import io.github.tournierjc.mercury.data.Notice
import io.github.tournierjc.mercury.data.NoticeKey
import io.github.tournierjc.mercury.data.BotJson
import io.github.tournierjc.mercury.data.GitJson
import io.github.tournierjc.mercury.data.SpotifyJson
import io.github.tournierjc.mercury.data.dto.ProviderTestResult
import io.github.tournierjc.mercury.ui.theme.LocalMercuryColors
import io.github.tournierjc.mercury.util.OutputSharing
import io.github.tournierjc.mercury.util.ShareText
import io.github.tournierjc.mercury.util.formatBytes
import kotlinx.serialization.json.JsonObject
import io.github.tournierjc.mercury.util.formatTokens
import io.github.tournierjc.mercury.util.formatUptime
import io.github.tournierjc.mercury.vm.ConsoleUiState
import io.github.tournierjc.mercury.vm.ConsoleViewModel
import io.github.tournierjc.mercury.util.TerminalPaths
import kotlinx.serialization.json.contentOrNull

/**
 * The host: what the daemon is playing, and the two verbs that stop or restart it.
 *
 * The screen used to be a second copy of the deliverables, the cost, the disk, the terminal, git,
 * the tasks, the schedules, the agents, the skills, the plugins, MCP and the workspace. Each of
 * those has a destination of its own in the drawer, and a read-only copy here only ever disagreed
 * with it — the web app has no such page at all.
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.canvas)
            .safeDrawingPadding(),
    ) {
        ScreenHeader(
            title = stringResource(R.string.host_title),
            subtitle = state.status?.let { "${it.state.ifBlank { "?" }} · ${formatUptime(it.uptime)}" },
            onBack = onBack,
            onRefresh = viewModel::refresh,
            refreshing = state.loading,
        )
        state.message?.let { notice -> NoticeBanner(notice, onDismiss = viewModel::clearBanner) }
        state.error?.let { notice -> NoticeBanner(notice, onDismiss = viewModel::clearBanner) }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp),
        ) {
            // What is left is what has no other screen: the daemon's playback, and the two verbs
            // that stop or restart it. Everything else this screen used to repeat — the deliverables,
            // the cost, the disk, the terminal, git, the tasks, the schedules, the agents, the
            // skills, the plugins, MCP, the workspace — has its own destination in the drawer, and
            // a second copy only ever disagreed with it.
            MusicSection(state, viewModel)

            CollapsibleSection(stringResource(R.string.console_section_system)) {
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
            ScreenBottomSpacer()
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
private fun MusicSection(state: ConsoleUiState, viewModel: ConsoleViewModel) {
    val colors = LocalMercuryColors.current
    val music = state.music
    var volume by remember {
        // The volume Spotify reports for the active device is the only starting point the
        // routes give us; 50 is a neutral guess when no device answers.
        mutableStateOf((SpotifyJson.activeDevice(state.devices)?.volume ?: 50).toFloat())
    }
    var shuffle by remember { mutableStateOf(false) }

    CollapsibleSection(stringResource(R.string.console_section_music)) {
        if (music == null || !music.available) {
            Text(
                text = stringResource(R.string.console_spotify_not_configured),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
            return@CollapsibleSection
        }
        if (!music.connected) {
            Text(
                text = stringResource(R.string.console_spotify_not_connected),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
            return@CollapsibleSection
        }

        KeyValueRow(
            stringResource(R.string.console_label_account),
            listOf(music.accountName?.ifBlank { "—" } ?: "—", SpotifyJson.productNotice(music.product)?.let { noticeText(it) } ?: "—")
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
                text = track.title.ifBlank { noticeText(Notice(NoticeKey.SPOTIFY_UNKNOWN_TITLE)) },
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

        IconActionBar(
            listOf(
                QuickAction(
                    icon = Icons.Rounded.SkipPrevious,
                    label = stringResource(R.string.console_previous_track),
                    enabled = !state.musicBusy,
                    onClick = viewModel::previous,
                ),
                QuickAction(
                    icon = Icons.Rounded.PlayArrow,
                    label = stringResource(R.string.console_play),
                    enabled = !state.musicBusy,
                    onClick = viewModel::play,
                ),
                QuickAction(
                    icon = Icons.Rounded.Pause,
                    label = stringResource(R.string.console_pause),
                    enabled = !state.musicBusy,
                    onClick = viewModel::pause,
                ),
                QuickAction(
                    icon = Icons.Rounded.SkipNext,
                    label = stringResource(R.string.console_next_track),
                    enabled = !state.musicBusy,
                    onClick = viewModel::next,
                ),
            ),
        )

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
            KeyValueRow(stringResource(R.string.console_label_device), devices[0].name.ifBlank { noticeText(devices[0].typeNotice) })
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
                    subtitle = noticeText(device.typeNotice),
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

        IconActionBar(
            listOf(
                QuickAction(
                    icon = Icons.Rounded.Refresh,
                    label = stringResource(R.string.console_refresh),
                    enabled = !state.musicBusy,
                    onClick = viewModel::refreshMusic,
                ),
            ),
        )
    }
}
