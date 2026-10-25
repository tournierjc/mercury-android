package io.github.tournierjc.mercury.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.tournierjc.mercury.MercuryApp
import io.github.tournierjc.mercury.data.BoardJson
import io.github.tournierjc.mercury.ui.theme.LocalMercuryColors
import io.github.tournierjc.mercury.vm.BoardsUiState
import io.github.tournierjc.mercury.vm.BoardsViewModel
import io.github.tournierjc.mercury.vm.CardDetail
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.canvas)
            .safeDrawingPadding(),
    ) {
        ScreenHeader(
            title = state.openBoardId?.let { state.boardName.ifBlank { "Plateau" } } ?: "Plateaux",
            subtitle = if (state.openBoardId == null) {
                "${state.boards.size} plateaux"
            } else {
                "${state.cards.size} cartes"
            },
            onBack = { if (state.openBoardId == null) onBack() else viewModel.close() },
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
            if (state.openBoardId == null) {
                BoardList(state, onOpen = viewModel::open, onDelete = viewModel::deleteBoard) {
                    showCreateBoard = true
                }
            } else {
                BoardActions(
                    state = state,
                    onActivate = viewModel::activate,
                    onDeactivate = viewModel::deactivate,
                    onRunAll = viewModel::runAll,
                    onHaltAll = viewModel::haltAll,
                    onPlan = { viewModel.loadPlan(); showPlan = true },
                    onContext = { viewModel.loadContext(); showContext = true },
                    onClearDone = viewModel::clearDone,
                    onGenerate = { viewModel.generate(null) },
                )
                CardColumns(
                    state = state,
                    onRun = viewModel::runCard,
                    onHalt = viewModel::haltCard,
                    onCascade = viewModel::cascadeCard,
                    onDelete = viewModel::deleteCard,
                    onOpen = viewModel::openCard,
                    onNewCard = { showNewCard = true },
                )
                if (state.plan.isNotEmpty()) {
                    SectionTitle("Plan d'exécution")
                    CardBlock {
                        state.plan.forEachIndexed { index, batch ->
                            KeyValueRow("Lot ${index + 1}", batch.joinToString(" · "))
                        }
                    }
                }
            }
            Text(" ", modifier = Modifier.padding(bottom = 24.dp))
        }
    }

    if (showCreateBoard) {
        TextPromptDialog(
            title = "Nouveau plateau",
            label = "Nom",
            secondLabel = "Description (optionnel)",
            confirm = "Créer",
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
            title = "Nouvelle carte",
            label = "Tâche",
            confirm = "Ajouter",
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
            title = { Text("Plan d'exécution", color = colors.textPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (state.plan.isEmpty()) {
                        Text("Aucun lot calculable (pas de dépendances).", color = colors.textSecondary)
                    }
                    state.plan.forEachIndexed { index, batch ->
                        Text("Lot ${index + 1}", color = colors.accent, style = MaterialTheme.typography.labelSmall)
                        Text(batch.joinToString(" · "), color = colors.textPrimary, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPlan = false; viewModel.smartExecute() }) {
                    Text("Exécuter dans cet ordre")
                }
            },
            dismissButton = { TextButton(onClick = { showPlan = false }) { Text("Fermer") } },
        )
    }

    if (showContext) {
        ContextDialog(state = state, viewModel = viewModel, onDismiss = { showContext = false })
    }

    state.cardDetail?.let { detail ->
        CardDetailDialog(state = state, detail = detail, viewModel = viewModel, onDismiss = viewModel::closeCard)
    }
}

@Composable
private fun BoardList(
    state: BoardsUiState,
    onOpen: (String) -> Unit,
    onDelete: (String) -> Unit,
    onCreate: () -> Unit,
) {
    val colors = LocalMercuryColors.current
    SectionTitle("Plateaux")
    CardBlock {
        if (state.boards.isEmpty()) {
            Text(
                text = if (state.loading) "Chargement…" else "Aucun plateau. Un plateau est une file de tâches que le runtime exécute carte par carte.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
        }
        state.boards.forEach { board ->
            val id = BoardJson.id(board)
            ActionRow(
                title = BoardJson.title(board),
                subtitle = listOfNotNull(
                    BoardJson.intField(board, "cardCount")?.let { "$it cartes" },
                    BoardJson.intField(board, "pendingCount")?.let { "$it en attente" },
                    BoardJson.intField(board, "runningCount")?.takeIf { it > 0 }?.let { "$it en cours" },
                    if (BoardJson.boolField(board, "active") == true) "actif" else null,
                ).joinToString(" · "),
                trailing = "ouvrir",
                onClick = { if (id.isNotBlank()) onOpen(id) },
            )
            if (id.isNotBlank()) {
                ButtonRow { TextButton(onClick = { onDelete(id) }) { Text("Supprimer", color = colors.danger) } }
            }
        }
        ButtonRow { TextButton(onClick = onCreate) { Text("Nouveau plateau") } }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BoardActions(
    state: BoardsUiState,
    onActivate: () -> Unit,
    onDeactivate: () -> Unit,
    onRunAll: () -> Unit,
    onHaltAll: () -> Unit,
    onPlan: () -> Unit,
    onContext: () -> Unit,
    onClearDone: () -> Unit,
    onGenerate: () -> Unit,
) {
    SectionTitle("Exécution")
    CardBlock {
        val active = BoardJson.boolField(state.board, "active") == true
        KeyValueRow("État", if (active) "actif" else "inactif", highlight = active)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            FilterChip(selected = false, onClick = { if (active) onDeactivate() else onActivate() }, label = {
                Text(if (active) "Désactiver" else "Activer")
            })
            FilterChip(selected = false, onClick = onRunAll, label = { Text("Tout lancer") })
            FilterChip(selected = false, onClick = onHaltAll, label = { Text("Tout arrêter") })
            FilterChip(selected = false, onClick = onPlan, label = { Text("Plan") })
            FilterChip(selected = false, onClick = onContext, label = { Text("Contexte") })
            FilterChip(selected = false, onClick = onClearDone, label = { Text("Nettoyer") })
            FilterChip(selected = false, onClick = onGenerate, label = { Text("Générer") })
        }
    }
}

@Composable
private fun CardColumns(
    state: BoardsUiState,
    onRun: (String) -> Unit,
    onHalt: (String) -> Unit,
    onCascade: (String) -> Unit,
    onDelete: (String) -> Unit,
    onOpen: (String) -> Unit,
    onNewCard: () -> Unit,
) {
    val colors = LocalMercuryColors.current
    if (state.grouped.isEmpty()) {
        SectionTitle("Cartes")
        CardBlock {
            Text(
                text = "Aucune carte sur ce plateau.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
            ButtonRow { TextButton(onClick = onNewCard) { Text("Nouvelle carte") } }
        }
        return
    }
    state.grouped.forEach { (status, cards) ->
        SectionTitle("$status · ${cards.size}")
        CardBlock {
            cards.forEach { card ->
                val cardId = BoardJson.id(card)
                ActionRow(
                    title = BoardJson.title(card),
                    subtitle = listOfNotNull(
                        BoardJson.priority(card).takeIf { it.isNotBlank() }?.let { "priorité $it" },
                        BoardJson.labels(card).size.takeIf { it > 0 }?.let { "$it étapes" },
                        BoardJson.intField(card, "tokenBudget")?.let { "$it tokens" },
                    ).joinToString(" · "),
                    trailing = "détails",
                    onClick = { if (cardId.isNotBlank()) onOpen(cardId) },
                )
                if (cardId.isNotBlank()) {
                    ButtonRow {
                        TextButton(onClick = { onRun(cardId) }) { Text("Lancer") }
                        TextButton(onClick = { onHalt(cardId) }) { Text("Arrêter") }
                        TextButton(onClick = { onCascade(cardId) }) { Text("Cascade") }
                        TextButton(onClick = { onDelete(cardId) }) { Text("Suppr.", color = colors.danger) }
                    }
                }
            }
        }
    }
    ButtonRow { TextButton(onClick = onNewCard) { Text("Nouvelle carte") } }
}

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
        title = { Text(BoardJson.title(card).ifBlank { "Carte" }, color = colors.textPrimary) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (BoardJson.description(card).isNotBlank()) {
                    Text(BoardJson.description(card), color = colors.textSecondary, style = MaterialTheme.typography.bodyMedium)
                }
                KeyValueRow("Statut", BoardJson.status(card))
                KeyValueRow("Commentaires", detail.comments.size.toString())
                KeyValueRow("Pièces jointes", detail.attachments.size.toString())
                KeyValueRow("Étiquettes", detail.labels.size.toString())
                KeyValueRow("Dépendances", detail.dependencies.size.toString())

                SectionTitle("Commentaires")
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
                PromptRow(value = comment, onValue = { comment = it }, label = "Ajouter un commentaire") {
                    if (comment.isNotBlank()) {
                        viewModel.addComment(comment)
                        comment = ""
                    }
                }

                SectionTitle("Étiquettes")
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
                PromptRow(value = labelName, onValue = { labelName = it }, label = "Nom d'étiquette", trailing = {
                    PromptRowInline(value = labelColor, onValue = { labelColor = it }, label = "Couleur")
                }) {
                    if (labelName.isNotBlank()) {
                        viewModel.addLabel(labelName, labelColor)
                        labelName = ""
                    }
                }

                SectionTitle("Pièces jointes")
                detail.attachments.forEach { attachment ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = BoardJson.title(attachment),
                            color = colors.textPrimary,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        val attachmentId = BoardJson.id(attachment)
                        if (attachmentId.isNotBlank()) {
                            TextButton(onClick = { viewModel.deleteAttachment(attachmentId) }) {
                                Text("×", color = colors.danger)
                            }
                        }
                    }
                }
                PromptRow(value = attachmentPath, onValue = { attachmentPath = it }, label = "Chemin dans le workspace") {
                    if (attachmentPath.isNotBlank()) {
                        val name = attachmentPath.substringAfterLast('/').ifBlank { attachmentPath }
                        viewModel.addAttachment(name, attachmentPath)
                        attachmentPath = ""
                    }
                }

                SectionTitle("Dépendances")
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
                PromptRow(value = dependencyId, onValue = { dependencyId = it }, label = "Identifiant de carte requise") {
                    if (dependencyId.isNotBlank()) {
                        viewModel.addDependency(dependencyId)
                        dependencyId = ""
                    }
                }
                PromptRow(value = parentId, onValue = { parentId = it }, label = "Carte parente (vide = détacher)") {
                    viewModel.setParent(parentId.ifBlank { null })
                    parentId = ""
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { viewModel.runCard(detail.cardId) }) { Text("Lancer") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { viewModel.haltCard(detail.cardId) }) { Text("Arrêter", color = colors.danger) }
                TextButton(onClick = onDismiss) { Text("Fermer") }
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
        title = { Text("Contexte du plateau", color = colors.textPrimary) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Ce que chaque carte reçoit avant de démarrer.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
                OutlinedTextField(
                    value = instructions,
                    onValueChange = { instructions = it },
                    label = { Text("Consignes") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = directory,
                    onValueChange = { directory = it },
                    label = { Text("Dossier de travail") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = knowledge,
                    onValueChange = { knowledge = it },
                    label = { Text("Connaissances") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                PromptRow(value = variableKey, onValue = { variableKey = it }, label = "Variable (clé)") {
                    if (variableKey.isNotBlank()) {
                        viewModel.setVariable(variableKey, variableValue)
                        variableKey = ""; variableValue = ""
                    }
                }
        PromptRow(value = variableValue, onValue = { variableValue = it }, label = "Valeur") {}
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (instructions.isNotBlank()) viewModel.setInstructions(instructions)
                if (directory.isNotBlank()) viewModel.setDirectory(directory)
                if (knowledge.isNotBlank()) viewModel.setKnowledge(knowledge)
                onDismiss()
            }) { Text("Enregistrer") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Fermer") } },
    )
}

private fun stringOf(context: JsonObject?, key: String): String =
    (context?.get(key) as? JsonPrimitive)?.contentOrNull.orEmpty()

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
            TextButton(onClick = onConfirm) { Text("OK") }
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
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}
