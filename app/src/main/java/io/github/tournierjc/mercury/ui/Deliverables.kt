package io.github.tournierjc.mercury.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.text.style.TextOverflow
import io.github.tournierjc.mercury.data.DeliverableFilter
import io.github.tournierjc.mercury.data.DeliverableGroup
import io.github.tournierjc.mercury.data.matchingBots
import io.github.tournierjc.mercury.data.OutputKind
import io.github.tournierjc.mercury.data.filteredBy
import io.github.tournierjc.mercury.data.groupedByBot
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
 * The deliverables list: searchable, filterable, and folded per bot.
 *
 * It was a flat list under one heading — seventy-one files from ten bots in a single scroll, with
 * the bot's name repeated above each block and no way to put a block away. The three questions the
 * owner actually asks are on top now: a word to find a file, a kind to narrow it, a bot to see one
 * teammate's work; and each bot's block folds, so a fleet of ten fits on one screen.
 *
 * Filtering happens in [filteredBy] on plain data — the screen keeps only the controls' state.
 */
@Composable
fun DeliverablesSection(
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

    var query by rememberSaveable { mutableStateOf("") }
    var kindName by rememberSaveable { mutableStateOf<String?>(null) }
    var botFilter by rememberSaveable { mutableStateOf<String?>(null) }
    // Which bots' blocks are folded shut. Folded, not open: a fleet whose files you have already
    // read is noise, and the default has to fit ten groups on one screen.
    var folded by remember { mutableStateOf(emptySet<String>()) }

    val filter = DeliverableFilter(
        query = query,
        kind = kindName?.let { name -> OutputKind.entries.firstOrNull { it.name == name } },
        botId = botFilter,
    )
    val shown = remember(outputs, filter) { outputs.filteredBy(filter) }
    // The picker lists every bot that has files, never the filter's own result: a filter that
    // shrank its own menu to the one bot already chosen would be a filter you cannot change.
    val botChoices = remember(outputs, botNames, showOrigin) {
        if (!showOrigin) emptyList() else outputs.groupedByBot(botNames).filter { it.botId.isNotBlank() }
    }
    val groups = remember(shown, botNames, showOrigin) {
        if (showOrigin) shown.groupedByBot(botNames) else listOf(DeliverableGroup("", "", shown.sortedByDescending { it.mtimeMs }))
    }

    // The list is the screen: it had a heading of its own above the search field, while the bar
    // already says « Livrables ». One less level to unfold before reading a file.
    Column(modifier = Modifier.fillMaxWidth()) {
        if (outputs.isEmpty()) {
            Text(
                text = emptyText,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
            return@Column
        }

        DeliverableSearch(
            query = query,
            onQuery = { query = it },
            filter = filter,
            onKind = { picked -> kindName = picked?.name },
            bots = botChoices,
            botFilter = botFilter,
            onBot = { botFilter = it },
        )

        if (shown.isEmpty()) {
            Text(
                text = stringResource(R.string.out_no_match),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
                modifier = Modifier.padding(top = 10.dp),
            )
            TextButton(onClick = { query = ""; kindName = null; botFilter = null }) {
                Text(stringResource(R.string.out_filters_clear), color = colors.accent)
            }
            return@Column
        }

        // Only worth saying when the three controls actually narrowed something.
        if (!filter.isOff) {
            Text(
                text = stringResource(R.string.out_showing, shown.size, outputs.size),
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary,
                modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
            )
        }

        groups.forEach { group ->
            if (showOrigin && group.botId.isNotBlank()) {
                val open = group.botId !in folded
                val bot = bots[group.botId]
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.extraSmall)
                        .clickable { folded = if (open) folded + group.botId else folded - group.botId }
                        .padding(top = 8.dp, bottom = 4.dp),
                ) {
                    if (bot != null) {
                        BotFace(bot, avatars[group.botId], small = true)
                    }
                    Text(
                        text = group.name,
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        // The name takes whatever the count and the chevron leave: pinning them to
                        // the right edge is what makes the column read as one, instead of every
                        // count starting wherever its own folder name happens to end.
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = if (bot != null) 8.dp else 0.dp),
                    )
                    Text(
                        text = stringResource(R.string.out_n_files, group.files.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                    Icon(
                        imageVector = if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = stringResource(
                            if (open) R.string.out_fold_group else R.string.out_unfold_group,
                            group.name,
                        ),
                        tint = colors.textSecondary,
                        // 2 dp of air, then the same 4 dp the section headings above use, so every
                        // chevron in the column sits on one vertical line.
                        modifier = Modifier.padding(start = 2.dp, end = 4.dp).size(18.dp),
                    )
                }
                if (!open) return@forEach
            }
            group.files.forEach { output ->
                DeliverableRow(
                    output = output,
                    now = now,
                    busy = busy,
                    onPreview = onPreview,
                    onShare = onShare,
                    onDelete = { pendingDelete = it },
                )
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
 * The three controls above the list: a word, a kind, a bot.
 *
 * The kinds and the bots are chips rather than dropdowns because there are four kinds and a
 * handful of bots, and a row you can read at a glance beats a menu you have to open to remember
 * what is inside it.
 */
@Composable
private fun DeliverableSearch(
    query: String,
    onQuery: (String) -> Unit,
    filter: DeliverableFilter,
    onKind: (OutputKind?) -> Unit,
    bots: List<DeliverableGroup>,
    botFilter: String?,
    onBot: (String?) -> Unit,
) {
    val colors = LocalMercuryColors.current
    var pickingBot by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .clip(MaterialTheme.shapes.small)
            .background(colors.surfaceMuted)
            .padding(horizontal = 10.dp, vertical = 2.dp),
    ) {
        Icon(
            Icons.Rounded.Search,
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier.size(18.dp),
        )
        Box(modifier = Modifier.weight(1f).padding(start = 8.dp, top = 9.dp, bottom = 9.dp)) {
            if (query.isEmpty()) {
                Text(
                    text = stringResource(R.string.out_search_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary.copy(alpha = 0.7f),
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.textPrimary),
                cursorBrush = SolidColor(colors.accent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (query.isNotEmpty()) {
            IconButton(onClick = { onQuery("") }, modifier = Modifier.size(ComposerControlSize)) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.out_search_clear),
                    tint = colors.textSecondary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(top = 8.dp),
    ) {
        FilterChip(selected = filter.kind == null, label = stringResource(R.string.out_filter_all)) { onKind(null) }
        FilterChip(
            selected = filter.kind == OutputKind.Text,
            label = stringResource(R.string.out_filter_text),
        ) { onKind(OutputKind.Text) }
        FilterChip(
            selected = filter.kind == OutputKind.Image,
            label = stringResource(R.string.out_filter_images),
        ) { onKind(OutputKind.Image) }
        FilterChip(
            selected = filter.kind == OutputKind.Binary,
            label = stringResource(R.string.out_filter_other),
        ) { onKind(OutputKind.Binary) }
        if (bots.size > 1) {
            // One chip, not one per bot: ten teammates turned this line into a scroll nobody could
            // search, and the chip that was already on could scroll out of reach. It opens a list
            // with a field; the selected bot's name is the label, and its × clears the filter.
            val picked = bots.firstOrNull { it.botId == botFilter }
            FilterChip(
                selected = picked != null,
                label = picked?.name ?: stringResource(R.string.out_filter_bot_all),
                accent = true,
                onClear = if (picked != null) ({ onBot(null) }) else null,
            ) { pickingBot = true }
        }
    }

    if (pickingBot) {
        BotFilterDialog(
            bots = bots,
            selected = botFilter,
            onPick = { picked ->
                // Tapping the bot already chosen takes the filter off: the same gesture as the ×,
                // for the hand that expects a second tap to undo the first.
                onBot(if (picked != null && picked == botFilter) null else picked)
                pickingBot = false
            },
            onDismiss = { pickingBot = false },
        )
    }
}

/**
 * The bots that have files, with a field to find one.
 *
 * A list, not a row of chips: the fleet of ten that made this screen worth grouping is already
 * more names than a phone shows on one line, and what the owner knows is a fragment of the name.
 * Matching forgives case and accents, like the search above it.
 */
@Composable
private fun BotFilterDialog(
    bots: List<DeliverableGroup>,
    selected: String?,
    onPick: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalMercuryColors.current
    var query by remember { mutableStateOf("") }
    val matches = remember(bots, query) { bots.matchingBots(query) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = { Text(stringResource(R.string.out_filter_bot_title), color = colors.textPrimary) },
        text = {
            Column(modifier = Modifier.heightIn(max = 380.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(stringResource(R.string.out_filter_bot_search)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Column(modifier = Modifier.padding(top = 8.dp).verticalScroll(rememberScrollState())) {
                    BotFilterRow(
                        name = stringResource(R.string.out_filter_bot_all),
                        detail = null,
                        selected = selected == null,
                        onTap = { onPick(null) },
                    )
                    matches.forEach { group ->
                        BotFilterRow(
                            name = group.name,
                            detail = stringResource(R.string.out_n_files, group.files.size),
                            selected = group.botId == selected,
                            onTap = { onPick(group.botId) },
                        )
                    }
                    if (matches.isEmpty()) {
                        Text(
                            text = stringResource(R.string.out_filter_bot_none),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textSecondary,
                            modifier = Modifier.padding(vertical = 10.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.out_filter_bot_close)) }
        },
    )
}

@Composable
private fun BotFilterRow(name: String, detail: String?, selected: Boolean, onTap: () -> Unit) {
    val colors = LocalMercuryColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraSmall)
            .clickable(onClick = onTap)
            .padding(vertical = 10.dp, horizontal = 4.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (detail != null) {
                Text(text = detail, style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
            }
        }
        if (selected) {
            Icon(
                Icons.Rounded.Check,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun FilterChip(
    selected: Boolean,
    label: String,
    accent: Boolean = false,
    /** Drawn inside the chip when a value is on: one tap takes the filter off. */
    onClear: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val colors = LocalMercuryColors.current
    val tint = if (accent) colors.accent else colors.textPrimary
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .then(
                if (selected) Modifier.background((if (accent) colors.accent else colors.textPrimary).copy(alpha = 0.14f))
                else Modifier.border(1.dp, colors.hairline, RoundedCornerShape(50)),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 5.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) tint else colors.textSecondary,
            maxLines = 1,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (onClear != null) {
            Box(
                modifier = Modifier
                    .padding(start = 6.dp)
                    .size(16.dp)
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onClear),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.out_filter_bot_clear),
                    tint = tint,
                    modifier = Modifier.size(13.dp),
                )
            }
        }
    }
}

/** One file: its glyph, its name, what it is and when, and the three things you can do with it. */
@Composable
private fun DeliverableRow(
    output: DeliverableDto,
    now: Long,
    busy: Boolean,
    onPreview: (DeliverableDto) -> Unit,
    onShare: (DeliverableDto) -> Unit,
    onDelete: (DeliverableDto) -> Unit,
) {
    val colors = LocalMercuryColors.current
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
                OutputKind.Image -> Icons.Rounded.Image
                OutputKind.Text -> Icons.Rounded.Description
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
        IconButton(onClick = { onDelete(output) }, enabled = !busy, modifier = Modifier.size(38.dp)) {
            Icon(
                Icons.Rounded.DeleteOutline,
                contentDescription = stringResource(R.string.out_delete_of, output.name),
                tint = colors.danger,
                modifier = Modifier.size(19.dp),
            )
        }
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
 *
 * It took a console view model — the screen loaded fourteen routes to draw a file list. It takes
 * the files and the roster now, and the caller owns the rest.
 */
@Composable
fun FleetDeliverablesSection(
    outputs: List<DeliverableDto>,
    bots: List<kotlinx.serialization.json.JsonObject>,
    avatars: Map<String, ByteArray> = emptyMap(),
    busy: Boolean = false,
    onPreview: (DeliverableDto) -> Unit,
    onShare: (DeliverableDto) -> Unit,
    onDelete: (DeliverableDto) -> Unit,
) {
    val names = bots.associate { BotJson.id(it) to BotJson.name(it) }
    val byId = bots.associateBy { BotJson.id(it) }
    DeliverablesSection(
        outputs = outputs,
        botNames = names,
        bots = byId,
        avatars = avatars,
        showOrigin = true,
        busy = busy,
        emptyText = stringResource(R.string.out_none_fleet),
        onPreview = onPreview,
        onShare = onShare,
        onDelete = onDelete,
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
fun OutputPreviewHost(state: OutputPreviewState?, onDismiss: () -> Unit, onShare: () -> Unit) {
    val preview = state ?: return
    OutputPreviewDialog(state = preview, onDismiss = onDismiss, onShare = onShare)
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
            // The title and its refresh share one row: the button at the bottom of the sheet was
            // below the list it reloads, so the further down the folder you had scrolled, the
            // further the control was from the count it changes.
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
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
                }
                IconButton(onClick = onReload, enabled = !busy, modifier = Modifier.size(ComposerControlSize)) {
                    if (busy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = colors.accent,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(
                            Icons.Rounded.Refresh,
                            contentDescription = stringResource(R.string.out_refresh),
                            tint = colors.textSecondary,
                            modifier = Modifier.size(ComposerControlIcon),
                        )
                    }
                }
            }
            DeliverablesSection(
                outputs = outputs,
                botNames = mapOf(botId to botName),
                showOrigin = false,
                busy = busy,
                emptyText = stringResource(R.string.out_none_this),
                onPreview = onPreview,
                onShare = onShare,
                onDelete = onDelete,
            )
        }
    }
}
