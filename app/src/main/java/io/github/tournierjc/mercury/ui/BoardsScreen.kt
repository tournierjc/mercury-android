package io.github.tournierjc.mercury.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.tournierjc.mercury.MercuryApp
import io.github.tournierjc.mercury.R
import io.github.tournierjc.mercury.data.BoardJson
import io.github.tournierjc.mercury.data.BoardMetricsOf
import io.github.tournierjc.mercury.ui.theme.LocalMercuryColors
import io.github.tournierjc.mercury.util.AttachmentFiles
import io.github.tournierjc.mercury.vm.AttachmentExport
import io.github.tournierjc.mercury.vm.AttachmentPreview
import io.github.tournierjc.mercury.vm.BoardsUiState
import io.github.tournierjc.mercury.vm.BoardsViewModel
import io.github.tournierjc.mercury.vm.CardDetail
import io.github.tournierjc.mercury.data.Notice
import io.github.tournierjc.mercury.data.NoticeKey
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Mercury's boards: a kanban the runtime executes card by card. Everything on this
 * screen maps to one of the 43 `/api/boards` routes.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BoardsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val container = remember { (context.applicationContext as MercuryApp).container }
    val viewModel: BoardsViewModel = viewModel(factory = BoardsViewModel.factory(container))
    val state by viewModel.state.collectAsState()
    val colors = LocalMercuryColors.current

    var showCreateBoard by remember { mutableStateOf(false) }
    var showNewCard by remember { mutableStateOf(false) }
    var showPlan by remember { mutableStateOf(false) }
    var showContext by remember { mutableStateOf(false) }

    // The board stream lives exactly as long as the board is on screen: opening a board
    // subscribes, going back to the list — or leaving the screen — unsubscribes.
    DisposableEffect(state.openBoardId) {
        state.openBoardId?.let(viewModel::startLive)
        onDispose { viewModel.stopLive() }
    }

    // Nothing on the phone writes into shared storage on its own: a download asks the owner
    // where to put the file, and the bytes are handed over once the location is known.
    var pendingSave by remember { mutableStateOf<AttachmentExport?>(null) }
    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { target ->
        val export = pendingSave
        pendingSave = null
        if (target != null && export != null) {
            if (!AttachmentFiles.save(context, target, export.file.bytes)) {
                viewModel.report(Notice(NoticeKey.BOARD_WRITE_FAILED))
            }
        }
    }

    LaunchedEffect(state.export) {
        val export = state.export ?: return@LaunchedEffect
        if (export.download) {
            pendingSave = export
            saveLauncher.launch(export.file.name)
        } else if (!AttachmentFiles.open(context, export.file)) {
            viewModel.report(Notice(NoticeKey.NO_APP_FOR_TYPE, args = listOf(export.file.name)))
        }
        viewModel.consumeExport()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.canvas)
            .safeDrawingPadding(),
    ) {
        ScreenHeader(
            title = state.openBoardId?.let {
                state.boardName.ifBlank { stringResource(R.string.boards_titre_plateau) }
            } ?: stringResource(R.string.boards_titre_plateaux),
            subtitle = if (state.openBoardId == null) {
                // At zero the message speaks, not a counter: "0 plateau" is noise above "no board".
                if (state.boards.isEmpty() && !state.loading) {
                    ""
                } else {
                    pluralStringResource(
                        R.plurals.boards_sous_titre_nb_plateaux,
                        state.boards.size,
                        state.boards.size,
                    )
                }
            } else {
                listOfNotNull(
                    pluralStringResource(R.plurals.boards_nb_cartes, state.cards.size, state.cards.size),
                    stringResource(if (state.live) R.string.boards_live_direct else R.string.boards_live_auto),
                ).joinToString(" · ")
            },
            onBack = { if (state.openBoardId == null) onBack() else viewModel.close() },
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
            if (state.openBoardId == null) {
                BoardList(
                    state,
                    onOpen = viewModel::open,
                    onDelete = viewModel::deleteBoard,
                    onCreate = { showCreateBoard = true },
                    onRetry = viewModel::refresh,
                    onSetActive = { id, active -> viewModel.setBoardActive(id, active) },
                )
            } else {
                val metrics = BoardMetricsOf.of(state.cards)
                BoardMetricsStrip(metrics)
                BoardToolbar(
                    state = state,
                    onSetActive = { active -> viewModel.setActive(active) },
                    onRunAll = viewModel::runAll,
                    onHaltAll = viewModel::haltAll,
                    onPlan = { viewModel.loadPlan(); showPlan = true },
                    onContext = { viewModel.loadContext(); showContext = true },
                    onClearDone = viewModel::clearDone,
                    onGenerate = { viewModel.generate(null) },
                    onNewCard = { showNewCard = true },
                )
                if (metrics.needsInput > 0) {
                    AttentionBanner(metrics.needsInput)
                }
                state.grouped.forEach { (status, cards) ->
                    StatusLane(
                        status = status,
                        cards = cards,
                        onRun = viewModel::runCard,
                        onHalt = viewModel::haltCard,
                        onOpen = viewModel::openCard,
                        onDelete = viewModel::deleteCard,
                    )
                }
                if (state.grouped.isEmpty()) {
                    Text(
                        text = stringResource(R.string.boards_aucune_carte),
                        style = MaterialTheme.typography.bodyMedium,
                        color = LocalMercuryColors.current.textSecondary,
                    )
                }
                if (state.plan.isNotEmpty()) {
                    CollapsibleSection(stringResource(R.string.boards_plan_titre), defaultOpen = true) {
                        state.plan.forEachIndexed { index, batch ->
                            KeyValueRow(stringResource(R.string.boards_lot, index + 1), batch.joinToString(" · "))
                        }
                    }
                }
            }
            Text(" ", modifier = Modifier.padding(bottom = 24.dp))
        }
    }

    if (showCreateBoard) {
        TextPromptDialog(
            title = stringResource(R.string.boards_nouveau_plateau),
            label = stringResource(R.string.boards_nom),
            secondLabel = stringResource(R.string.boards_description_optionnelle),
            confirm = stringResource(R.string.boards_creer),
            onDismiss = { showCreateBoard = false },
            onConfirm = { name, description ->
                showCreateBoard = false
                viewModel.createBoard(name, description)
            },
        )
    }

    if (showNewCard) {
        var priority by remember { mutableStateOf<String?>(null) }
        TextPromptDialog(
            title = stringResource(R.string.boards_nouvelle_carte),
            label = stringResource(R.string.boards_tache),
            confirm = stringResource(R.string.boards_ajouter),
            onDismiss = { showNewCard = false },
            extra = {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("low", "normal", "high").forEach { candidate ->
                        FilterChip(
                            selected = priority == candidate,
                            onClick = { priority = if (priority == candidate) null else candidate },
                            label = { Text(candidate) },
                        )
                    }
                }
            },
            onConfirm = { task, _ ->
                showNewCard = false
                viewModel.addCard(task, priority)
            },
        )
    }

    if (showPlan) {
        AlertDialog(
            onDismissRequest = { showPlan = false },
            containerColor = colors.surface,
            title = { Text(stringResource(R.string.boards_plan_titre), color = colors.textPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (state.plan.isEmpty()) {
                        Text(stringResource(R.string.boards_plan_vide), color = colors.textSecondary)
                    }
                    state.plan.forEachIndexed { index, batch ->
                        Text(stringResource(R.string.boards_lot, index + 1), color = colors.accent, style = MaterialTheme.typography.labelSmall)
                        Text(batch.joinToString(" · "), color = colors.textPrimary, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPlan = false; viewModel.smartExecute() }) {
                    Text(stringResource(R.string.boards_plan_executer))
                }
            },
            dismissButton = { TextButton(onClick = { showPlan = false }) { Text(stringResource(R.string.boards_fermer)) } },
        )
    }

    if (showContext) {
        ContextDialog(state = state, viewModel = viewModel, onDismiss = { showContext = false })
    }

    state.cardDetail?.let { detail ->
        CardDetailDialog(state = state, detail = detail, viewModel = viewModel, onDismiss = viewModel::closeCard)
    }

    state.attachment?.let { preview ->
        AttachmentDialog(
            preview = preview,
            onOpen = { viewModel.exportAttachment(download = false) },
            onDownload = { viewModel.exportAttachment(download = true) },
            onDismiss = viewModel::closeAttachment,
        )
    }
}

/**
 * Reads one card attachment in place: an image is drawn from its bytes, a text file from
 * the `/content` route, and anything else is handed to the app that reads its type. The
 * whole file — never the preview — is what "Télécharger" saves.
 */
@Composable
private fun AttachmentDialog(
    preview: AttachmentPreview,
    onOpen: () -> Unit,
    onDownload: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalMercuryColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = { Text(preview.name, color = colors.textPrimary) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                when {
                    preview.loading -> Text(stringResource(R.string.boards_lecture), color = colors.textSecondary)
                    preview.error != null -> Text(noticeText(preview.error), color = colors.danger)
                    preview.isImage -> {
                        val bitmap = remember(preview.bytes) {
                            preview.bytes?.let { AttachmentFiles.decode(it) }
                        }
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = preview.name,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 320.dp),
                            )
                        } else {
                            Text(
                                stringResource(R.string.boards_image_illisible),
                                color = colors.textSecondary,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                    else -> Column {
                        Text(
                            text = preview.text?.ifBlank { stringResource(R.string.boards_fichier_vide) }
                                ?: stringResource(R.string.boards_apercu_indisponible),
                            color = if (preview.text == null) colors.textSecondary else colors.textPrimary,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = if (preview.text != null) FontFamily.Monospace else FontFamily.Default,
                        )
                        if (preview.truncated) {
                            Text(
                                text = stringResource(R.string.boards_apercu_tronque),
                                color = colors.textSecondary,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                }
                if (!preview.loading && preview.error == null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = onOpen) { Text(stringResource(R.string.boards_ouvrir)) }
                        TextButton(onClick = onDownload) { Text(stringResource(R.string.boards_telecharger)) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.boards_fermer)) } },
    )
}

/**
 * The one place a status becomes a word. Called from the section header and from the card sheet —
 * the API key never reaches the screen, except when the status is one Mercury does not know yet: it
 * then keeps its raw key, which is the only exploitable fact about it (SPECS/111 §1.5).
 */
@Composable
private fun CardDetailDialog(
    state: BoardsUiState,
    detail: CardDetail,
    viewModel: BoardsViewModel,
    onDismiss: () -> Unit,
) {
    val colors = LocalMercuryColors.current
    var comment by remember { mutableStateOf("") }
    var labelName by remember { mutableStateOf("") }
    var labelColor by remember { mutableStateOf("#00d4ff") }
    var attachmentPath by remember { mutableStateOf("") }
    var dependencyId by remember { mutableStateOf("") }
    var parentId by remember { mutableStateOf("") }
    val card = state.cards.firstOrNull { BoardJson.id(it) == detail.cardId }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = { Text(BoardJson.title(card).ifBlank { stringResource(R.string.boards_carte) }, color = colors.textPrimary) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (BoardJson.description(card).isNotBlank()) {
                    Text(BoardJson.description(card), color = colors.textSecondary, style = MaterialTheme.typography.bodyMedium)
                }
                KeyValueRow(stringResource(R.string.boards_statut), statusLabel(BoardJson.status(card)))
                KeyValueRow(stringResource(R.string.boards_commentaires), detail.comments.size.toString())
                KeyValueRow(stringResource(R.string.boards_pieces_jointes), detail.attachments.size.toString())
                KeyValueRow(stringResource(R.string.boards_etiquettes), detail.labels.size.toString())
                KeyValueRow(stringResource(R.string.boards_dependances), detail.dependencies.size.toString())

                CollapsibleSection(stringResource(R.string.boards_commentaires), defaultOpen = true) {
                detail.comments.forEach { entry ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = BoardJson.title(entry),
                            color = colors.textPrimary,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        val commentId = BoardJson.id(entry)
                        if (commentId.isNotBlank()) {
                            TextButton(onClick = { viewModel.deleteComment(commentId) }) {
                                Text("×", color = colors.danger)
                            }
                        }
                    }
                }
                PromptRow(
                    value = comment,
                    onValue = { comment = it },
                    label = stringResource(R.string.boards_ajouter_commentaire),
                ) {
                    if (comment.isNotBlank()) {
                        viewModel.addComment(comment)
                        comment = ""
                    }
                }
                }

                CollapsibleSection(stringResource(R.string.boards_etiquettes)) {
                detail.labels.forEach { label ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${BoardJson.title(label)} · ${(label["color"] as? JsonPrimitive)?.contentOrNull.orEmpty()}",
                            color = colors.textPrimary,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        val labelId = BoardJson.id(label)
                        if (labelId.isNotBlank()) {
                            TextButton(onClick = { viewModel.removeLabel(labelId) }) { Text("×", color = colors.danger) }
                        }
                    }
                }
                PromptRow(
                    value = labelName,
                    onValue = { labelName = it },
                    label = stringResource(R.string.boards_nom_etiquette),
                    trailing = {
                        PromptRowInline(
                            value = labelColor,
                            onValue = { labelColor = it },
                            label = stringResource(R.string.boards_couleur),
                        )
                    },
                ) {
                    if (labelName.isNotBlank()) {
                        viewModel.addLabel(labelName, labelColor)
                        labelName = ""
                    }
                }
                }

                CollapsibleSection(stringResource(R.string.boards_pieces_jointes)) {
                detail.attachments.forEach { attachment ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.openAttachment(attachment) },
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = BoardJson.title(attachment),
                                color = colors.textPrimary,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                text = listOfNotNull(
                                    BoardJson.attachmentType(attachment).takeIf { it.isNotBlank() },
                                    BoardJson.attachmentSize(attachment)?.let(::formatSize),
                                ).joinToString(" · ").ifBlank { stringResource(R.string.boards_action_lire) },
                                color = colors.textSecondary,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                        val attachmentId = BoardJson.id(attachment)
                        if (attachmentId.isNotBlank()) {
                            TextButton(onClick = { viewModel.deleteAttachment(attachmentId) }) {
                                Text("×", color = colors.danger)
                            }
                        }
                    }
                }
                PromptRow(
                    value = attachmentPath,
                    onValue = { attachmentPath = it },
                    label = stringResource(R.string.boards_chemin_workspace),
                ) {
                    if (attachmentPath.isNotBlank()) {
                        val name = attachmentPath.substringAfterLast('/').ifBlank { attachmentPath }
                        viewModel.addAttachment(name, attachmentPath)
                        attachmentPath = ""
                    }
                }
                }

                CollapsibleSection(stringResource(R.string.boards_dependances)) {
                detail.dependencies.forEach { dependency ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = BoardJson.title(dependency),
                            color = colors.textPrimary,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        val dependencyIdValue = BoardJson.id(dependency)
                        if (dependencyIdValue.isNotBlank()) {
                            TextButton(onClick = { viewModel.removeDependency(dependencyIdValue) }) {
                                Text("×", color = colors.danger)
                            }
                        }
                    }
                }
                PromptRow(
                    value = dependencyId,
                    onValue = { dependencyId = it },
                    label = stringResource(R.string.boards_dependance_identifiant),
                ) {
                    if (dependencyId.isNotBlank()) {
                        viewModel.addDependency(dependencyId)
                        dependencyId = ""
                    }
                }
                }

                PromptRow(
                    value = parentId,
                    onValue = { parentId = it },
                    label = stringResource(R.string.boards_carte_parente),
                ) {
                    viewModel.setParent(parentId.ifBlank { null })
                    parentId = ""
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { viewModel.runCard(detail.cardId) }) { Text(stringResource(R.string.boards_lancer)) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { viewModel.haltCard(detail.cardId) }) {
                    Text(stringResource(R.string.boards_arreter), color = colors.danger)
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.boards_fermer)) }
            }
        },
    )
}

@Composable
private fun ContextDialog(state: BoardsUiState, viewModel: BoardsViewModel, onDismiss: () -> Unit) {
    val colors = LocalMercuryColors.current
    val context = state.context
    var instructions by remember { mutableStateOf(stringOf(context, "instructions")) }
    var directory by remember { mutableStateOf(stringOf(context, "directory")) }
    var knowledge by remember { mutableStateOf(stringOf(context, "knowledge")) }
    var variableKey by remember { mutableStateOf("") }
    var variableValue by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = { Text(stringResource(R.string.boards_contexte_titre), color = colors.textPrimary) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.boards_contexte_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
                OutlinedTextField(
                    value = instructions,
                    onValueChange = { instructions = it },
                    label = { Text(stringResource(R.string.boards_consignes)) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = directory,
                    onValueChange = { directory = it },
                    label = { Text(stringResource(R.string.boards_dossier_travail)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = knowledge,
                    onValueChange = { knowledge = it },
                    label = { Text(stringResource(R.string.boards_connaissances)) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                PromptRow(
                    value = variableKey,
                    onValue = { variableKey = it },
                    label = stringResource(R.string.boards_variable_cle),
                ) {
                    if (variableKey.isNotBlank()) {
                        viewModel.setVariable(variableKey, variableValue)
                        variableKey = ""; variableValue = ""
                    }
                }
        PromptRow(
            value = variableValue,
            onValue = { variableValue = it },
            label = stringResource(R.string.boards_valeur),
        ) {}
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (instructions.isNotBlank()) viewModel.setInstructions(instructions)
                if (directory.isNotBlank()) viewModel.setDirectory(directory)
                if (knowledge.isNotBlank()) viewModel.setKnowledge(knowledge)
                onDismiss()
            }) { Text(stringResource(R.string.boards_enregistrer)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.boards_fermer)) } },
    )
}

private fun stringOf(context: JsonObject?, key: String): String =
    (context?.get(key) as? JsonPrimitive)?.contentOrNull.orEmpty()

/** A file size a phone can read: `812 o`, `12 ko`, `1,4 Mo`. */
private fun formatSize(bytes: Long): String {
    val units = listOf("o", "ko", "Mo", "Go")
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }
    if (unit == 0) return "$bytes o"
    val rounded = (value * 10).toLong()
    val whole = rounded / 10
    val tenth = rounded % 10
    return if (tenth == 0L) "$whole ${units[unit]}" else "$whole,$tenth ${units[unit]}"
}

@Composable
private fun PromptRow(
    value: String,
    onValue: (String) -> Unit,
    label: String,
    trailing: (@Composable () -> Unit)? = null,
    onConfirm: () -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = value,
                onValueChange = onValue,
                label = { Text(label) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.boards_ok)) }
        }
        trailing?.invoke()
    }
}

@Composable
private fun PromptRowInline(value: String, onValue: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun TextPromptDialog(
    title: String,
    label: String,
    confirm: String,
    onDismiss: () -> Unit,
    secondLabel: String? = null,
    extra: (@Composable () -> Unit)? = null,
    onConfirm: (first: String, second: String) -> Unit,
) {
    var first by remember { mutableStateOf("") }
    var second by remember { mutableStateOf("") }
    val colors = LocalMercuryColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = { Text(title, color = colors.textPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = first,
                    onValueChange = { first = it },
                    label = { Text(label) },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (secondLabel != null) {
                    OutlinedTextField(
                        value = second,
                        onValueChange = { second = it },
                        label = { Text(secondLabel) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                extra?.invoke()
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(first, second) }, enabled = first.isNotBlank()) { Text(confirm) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.boards_annuler)) } },
    )
}
