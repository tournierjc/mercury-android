package io.github.tournierjc.mercury.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import io.github.tournierjc.mercury.R
import io.github.tournierjc.mercury.data.BotJson
import io.github.tournierjc.mercury.vm.BotsUiState
import io.github.tournierjc.mercury.vm.BotsViewModel
import io.github.tournierjc.mercury.vm.ConsoleUiState
import io.github.tournierjc.mercury.vm.ConsoleViewModel
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.github.tournierjc.mercury.data.BotOps
import io.github.tournierjc.mercury.data.dto.DeliverableDto
import io.github.tournierjc.mercury.ui.theme.LocalMercuryColors
import io.github.tournierjc.mercury.util.OutputPreviewState

/**
 * Livrables — the files a bot actually produced.
 *
 * One list, two callers: the bot sheet (one bot's folder) and the Console (the whole
 * fleet). Same rows, same three actions — aperçu, partager, supprimer — so a file
 * behaves the same wherever the owner finds it.
 */
@Composable
fun DeliverablesSection(
    title: String,
    outputs: List<DeliverableDto>,
    botNames: Map<String, String> = emptyMap(),
    /** The bots themselves, for the face and the monogram of each group. */
    bots: Map<String, kotlinx.serialization.json.JsonObject> = emptyMap(),
    avatars: Map<String, ByteArray> = emptyMap(),
    showOrigin: Boolean = false,
    busy: Boolean = false,
    emptyText: String,
    onPreview: (DeliverableDto) -> Unit,
    onShare: (DeliverableDto) -> Unit,
    onDelete: (DeliverableDto) -> Unit,
) {
    val colors = LocalMercuryColors.current
    val now = remember(outputs) { System.currentTimeMillis() }
    var pendingDelete by remember { mutableStateOf<DeliverableDto?>(null) }

    CollapsibleSection(title) {
        if (outputs.isEmpty()) {
            Text(
                text = emptyText,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
            return@CollapsibleSection
        }
        // Grouped by bot when the list is the fleet's: two files from Bricoleur and one from
        // Atelier are three names that look alike, and "who made this" is the first question.
        val groups = if (!showOrigin) {
            listOf("" to outputs)
        } else {
            outputs.groupBy { it.botId }.map { (botId, files) -> botId to files }
        }
        groups.forEach { (botId, files) ->
            if (showOrigin && botId.isNotBlank()) {
                val bot = bots[botId]
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
                ) {
                    if (bot != null) {
                        BotFace(bot, avatars[botId], small = true)
                    }
                    Text(
                        text = botNames[botId].orEmpty().ifBlank { botId },
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.textPrimary,
                        modifier = Modifier.padding(start = if (bot != null) 8.dp else 0.dp),
                    )
                    Text(
                        text = stringResource(R.string.out_n_files, files.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }
            files.forEach { output ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.extraSmall)
                        .clickable { onPreview(output) }
                        .padding(vertical = 6.dp),
                ) {
                    Icon(
                        // The glyph says what the file is: a picture, a document, anything else.
                        imageVector = when (BotOps.kind(output.name)) {
                            io.github.tournierjc.mercury.data.OutputKind.Image -> Icons.Rounded.Image
                            io.github.tournierjc.mercury.data.OutputKind.Text -> Icons.Rounded.Description
                            else -> Icons.Rounded.InsertDriveFile
                        },
                        contentDescription = null,
                        tint = colors.textSecondary,
                        modifier = Modifier.size(20.dp),
                    )
                    Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(
                            text = output.name,
                            style = MaterialTheme.typography.bodyLarge,
                            color = colors.textPrimary,
                            maxLines = 2,
                        )
                        Text(
                            text = listOfNotNull(
                                noticeText(BotOps.scopeNotice(output)),
                                BotOps.sizeNotice(output.bytes)?.let { noticeText(it) }.orEmpty(),
                                BotOps.ageNotice(output.mtimeMs, now)?.let { noticeText(it) },
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textSecondary,
                        )
                    }
                    IconButton(onClick = { onPreview(output) }, modifier = Modifier.size(38.dp)) {
                        Icon(
                            Icons.Rounded.Visibility,
                            contentDescription = stringResource(R.string.out_preview_of, output.name),
                            tint = colors.textSecondary,
                            modifier = Modifier.size(19.dp),
                        )
                    }
                    IconButton(onClick = { onShare(output) }, enabled = !busy, modifier = Modifier.size(38.dp)) {
                        Icon(
                            Icons.Rounded.Share,
                            contentDescription = stringResource(R.string.out_share_of, output.name),
                            tint = colors.textSecondary,
                            modifier = Modifier.size(19.dp),
                        )
                    }
                    IconButton(onClick = { pendingDelete = output }, enabled = !busy, modifier = Modifier.size(38.dp)) {
                        Icon(
                            Icons.Rounded.DeleteOutline,
                            contentDescription = stringResource(R.string.out_delete_of, output.name),
                            tint = colors.danger,
                            modifier = Modifier.size(19.dp),
                        )
                    }
                }
            }
        }
    }

    val target = pendingDelete
    if (target != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            containerColor = colors.surface,
            title = { Text(stringResource(R.string.out_delete_title), color = colors.textPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = target.name,
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        color = colors.textPrimary,
                    )
                    Text(
                        text = stringResource(R.string.out_delete_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    onDelete(target)
                }) { Text(stringResource(R.string.out_delete_confirm), color = colors.danger) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.out_cancel)) } },
        )
    }
}

/**
 * The preview: text inline (the route already caps at 64 kB), a small image decoded with
 * `BitmapFactory`, and a plain sentence for everything else — a phone is not a viewer for
 * every format, and the share sheet is one tap away.
 */
@Composable
fun OutputPreviewDialog(
    state: OutputPreviewState,
    onDismiss: () -> Unit,
    onShare: () -> Unit,
) {
    val colors = LocalMercuryColors.current
    val image = state.image
    val bitmap = remember(image) {
        image?.let { bytes -> BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = { Text(state.output.name, color = colors.textPrimary, style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                KeyValueRow(
                    stringResource(R.string.out_row_file),
                    listOf(
                        if (state.output.final) stringResource(R.string.out_kind_final) else stringResource(R.string.out_kind_work),
                        BotOps.sizeNotice(state.output.bytes)?.let { noticeText(it) }.orEmpty(),
                    ).joinToString(" · "),
                )
                when {
                    state.loading -> Text(
                        text = stringResource(R.string.common_loading),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                    )

                    bitmap != null -> Image(
                        bitmap = bitmap,
                        contentDescription = stringResource(R.string.out_preview_of, state.output.name),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    state.text != null -> {
                        Text(
                            text = state.text.ifBlank { stringResource(R.string.out_empty_file) },
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = colors.textPrimary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(colors.surfaceMuted, MaterialTheme.shapes.extraSmall)
                                .padding(10.dp),
                        )
                        if (state.truncated) {
                            Text(
                                text = stringResource(R.string.out_truncated),
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textSecondary,
                            )
                        }
                    }

                    else -> Text(
                        text = state.note?.let { noticeText(it) } ?: stringResource(R.string.out_unavailable),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.out_close)) } },
        dismissButton = { TextButton(onClick = onShare) { Text(stringResource(R.string.out_share)) } },
    )
}


/**
 * The fleet inbox, wired: same rows as the per-bot section, with the producing bot named.
 * One composable, two hosts — the Console section and the dedicated Livrables screen — so
 * the two never drift apart.
 */
@Composable
fun FleetDeliverablesSection(
    state: ConsoleUiState,
    viewModel: ConsoleViewModel,
    avatars: Map<String, ByteArray> = emptyMap(),
) {
    val names = state.bots.associate { BotJson.id(it) to BotJson.name(it) }
    val byId = state.bots.associateBy { BotJson.id(it) }
    DeliverablesSection(
        title = stringResource(R.string.out_title),
        outputs = state.outputs,
        botNames = names,
        bots = byId,
        avatars = avatars,
        showOrigin = true,
        busy = state.busy,
        emptyText = stringResource(R.string.out_none_fleet),
        onPreview = viewModel::previewOutput,
        onShare = viewModel::shareOutput,
        onDelete = viewModel::deleteOutput,
    )
}

/** The same dialog, for the per-bot view model the conversation screen holds. */
@Composable
fun BotOutputPreviewHost(state: BotsUiState, viewModel: BotsViewModel) {
    val preview = state.outputPreview ?: return
    OutputPreviewDialog(
        state = preview,
        onDismiss = viewModel::dismissPreview,
        onShare = {
            viewModel.dismissPreview()
            viewModel.shareOutput(preview.output)
        },
    )
}

/** The preview dialog, hosted once per screen that can open a deliverable. */
@Composable
fun OutputPreviewHost(state: ConsoleUiState, viewModel: ConsoleViewModel) {
    val preview = state.outputPreview ?: return
    OutputPreviewDialog(
        state = preview,
        onDismiss = viewModel::dismissPreview,
        onShare = {
            viewModel.dismissPreview()
            viewModel.shareOutput(preview.output)
        },
    )
}


/**
 * The bot's deliverable folder, opened from its conversation.
 *
 * The list is the same `DeliverablesSection` the console and the Livrables screen render —
 * what changes is where it comes from: here, only this bot's files, one tap from the
 * conversation that produced them. It is loaded when the sheet opens, so a bot that wrote
 * something a minute ago shows it without leaving the discussion.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BotDeliverablesSheet(
    botId: String,
    botName: String,
    outputs: List<DeliverableDto>,
    busy: Boolean,
    onPreview: (DeliverableDto) -> Unit,
    onShare: (DeliverableDto) -> Unit,
    onDelete: (DeliverableDto) -> Unit,
    onReload: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalMercuryColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.canvas,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 28.dp),
        ) {
            Text(
                text = stringResource(R.string.out_of_bot, botName),
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
            )
            Text(
                text = when (outputs.size) {
                    0 -> stringResource(R.string.out_none_bot)
                    1 -> stringResource(R.string.out_count_one)
                    else -> stringResource(R.string.out_count_many, outputs.size)
                },
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary,
            )
            DeliverablesSection(
                title = stringResource(R.string.out_files),
                outputs = outputs,
                botNames = mapOf(botId to botName),
                showOrigin = false,
                busy = busy,
                emptyText = stringResource(R.string.out_none_this),
                onPreview = onPreview,
                onShare = onShare,
                onDelete = onDelete,
            )
            IconActionBar(
                listOf(
                    QuickAction(
                        icon = Icons.Rounded.Refresh,
                        label = stringResource(R.string.out_refresh),
                        onClick = onReload,
                    ),
                ),
            )
        }
    }
}
