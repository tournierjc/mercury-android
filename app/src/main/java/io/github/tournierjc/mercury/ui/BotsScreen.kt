package io.github.tournierjc.mercury.ui

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.tournierjc.mercury.MercuryApp
import io.github.tournierjc.mercury.bots.BotWatchState
import io.github.tournierjc.mercury.data.BotJson
import io.github.tournierjc.mercury.ui.theme.LocalMercuryColors
import io.github.tournierjc.mercury.data.Attachments
import io.github.tournierjc.mercury.data.PendingFile
import io.github.tournierjc.mercury.util.MediaPicker
import io.github.tournierjc.mercury.vm.BotsUiState
import io.github.tournierjc.mercury.vm.BotsViewModel
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Teammates: the roster with live activity and passive state, plus a detail view for
 * customization (persona, tier, model, budget), fleet control and background dispatch.
 *
 * A bot with a custom avatar shows it; the monogram (colour derived from the id) is the
 * fallback, not the default. `hasAvatar` on the roster tells us whether to fetch at all.
 */
@Composable
fun BotsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val container = remember { (context.applicationContext as MercuryApp).container }
    val viewModel: BotsViewModel = viewModel(factory = BotsViewModel.factory(container))
    val watch by viewModel.watcher.state.collectAsState()
    val state by viewModel.state.collectAsState()
    val avatars by viewModel.avatars.collectAsState()
    // Faces are fetched only for bots whose roster entry says they have one.
    LaunchedEffect(watch.bots) { viewModel.loadAvatars(watch.bots) }
    val colors = LocalMercuryColors.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.canvas)
            .safeDrawingPadding(),
    ) {
        val selected = state.selectedId?.let { id -> watch.bots.firstOrNull { BotJson.id(it) == id } }
        ScreenHeader(
            title = selected?.let { BotJson.name(it) } ?: "Équipiers",
            subtitle = if (selected == null) {
                "${watch.bots.size} équipiers · ${watch.queue.pending} en file" +
                    if (!watch.connected) " · hors ligne" else ""
            } else {
                BotJson.stateLabel(BotJson.state(selected))
            },
            onBack = { if (state.selectedId == null) onBack() else viewModel.select(null) },
            onRefresh = viewModel::refreshRoster,
        )
        state.message?.let { InlineBanner(it, isError = false, onDismiss = viewModel::clearBanner) }
        state.error?.let { InlineBanner(it, isError = true, onDismiss = viewModel::clearBanner) }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp),
        ) {
            if (state.selectedId == null) {
                RosterList(watch, onSelect = viewModel::select, avatars = avatars)
                if (watch.recent.isNotEmpty()) {
                    SectionTitle("Activité récente")
                    CardBlock {
                        watch.recent.take(12).forEach { line ->
                            val bot = watch.bots.firstOrNull { BotJson.id(it) == line.botId }
                            KeyValueRow(
                                bot?.let { BotJson.name(it) } ?: line.botId,
                                line.label.ifBlank { line.kind },
                            )
                        }
                    }
                }
            } else {
                BotDetail(state, watch, viewModel, avatars)
            }
            Text(" ", modifier = Modifier.padding(bottom = 28.dp))
        }
    }
}

@Composable
private fun RosterList(
    watch: BotWatchState,
    onSelect: (String) -> Unit,
    avatars: Map<String, ByteArray>,
) {
    val colors = LocalMercuryColors.current
    val needsYou = watch.needsYou()
    if (needsYou.isNotEmpty()) {
        SectionTitle("En attente de vous · ${needsYou.size}")
        CardBlock {
            needsYou.forEach { bot ->
                ActionRow(
                    title = BotJson.name(bot),
                    subtitle = BotJson.activity(bot).ifBlank { "Il attend une réponse avant de continuer." },
                    trailing = "ouvrir",
                    onClick = { onSelect(BotJson.id(bot)) },
                )
            }
        }
    }

    SectionTitle("Équipiers")
    CardBlock {
        if (watch.bots.isEmpty()) {
            Text(
                text = "Aucun équipier. Créez-en un depuis la Console.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
            return@CardBlock
        }
        watch.bots.forEach { bot ->
            val id = BotJson.id(bot)
            val live = watch.activity[id].orEmpty().ifBlank { BotJson.activity(bot) }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(id) }
                    .padding(vertical = 6.dp),
            ) {
                BotFace(bot, avatars[BotJson.id(bot)])
                Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = BotJson.name(bot),
                            color = colors.textPrimary,
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                        )
                        if (BotJson.isLead(bot)) {
                            Text(
                                text = "  chef" + (BotJson.crewWorking(bot)?.takeIf { it > 0 }?.let { " · $it en cours" } ?: ""),
                                color = colors.accent,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                        if (BotJson.needsYou(bot)) {
                            Text("  ⚠", color = colors.danger, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    Text(
                        text = listOf(
                            BotJson.stateLabel(BotJson.state(bot)),
                            live.takeIf { it.isNotBlank() },
                            BotJson.parent(bot).takeIf { it.isNotBlank() }?.let { "crew de $it" },
                        ).filterNotNull().joinToString(" · "),
                        color = colors.textSecondary,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BotDetail(
    state: BotsUiState,
    watch: BotWatchState,
    viewModel: BotsViewModel,
    avatars: Map<String, ByteArray>,
) {
    val colors = LocalMercuryColors.current
    // Captured at composition: `LocalContext.current` is a composable getter and the
    // picker callback below is not one.
    val context = LocalContext.current
    val id = state.selectedId.orEmpty()
    val bot = (state.manifest ?: watch.bots.firstOrNull { BotJson.id(it) == id }) ?: return
    val live = watch.activity[id].orEmpty().ifBlank { BotJson.activity(bot) }

    var dispatchText by remember { mutableStateOf("") }
    var dispatchFiles by remember(id) { mutableStateOf<List<PendingFile>>(emptyList()) }
    var dispatchFileError by remember(id) { mutableStateOf<String?>(null) }

    // 1600 px / JPEG q80 like the chat composer: a photo from a 50 MP sensor must not
    // travel at full size, and the server's 8 MB image cap is a guard, not a routine stop.
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        MediaPicker.read(context, uri, maxDimension = 1600)
            .onSuccess { file ->
                val batch = dispatchFiles + file
                val problem = Attachments.problemWith(batch)
                if (problem != null) {
                    dispatchFileError = problem
                } else {
                    dispatchFiles = batch
                    dispatchFileError = null
                }
            }
            .onFailure { error -> dispatchFileError = error.message ?: "Fichier illisible" }
    }
    var personaText by remember(state.personaLoaded, id) { mutableStateOf(state.persona) }
    var showAddCrew by remember { mutableStateOf(false) }
    var modelText by remember(id) { mutableStateOf("") }
    var budgetText by remember(id) { mutableStateOf("") }
    var avatarError by remember(id) { mutableStateOf<String?>(null) }

    // 512 px is plenty for a face in a roster, and the bytes leave the phone encoded.
    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        MediaPicker.read(context, uri, maxDimension = 512)
            .onSuccess { file -> avatarError = null; viewModel.uploadAvatar(id, file.bytes) }
            .onFailure { error -> avatarError = error.message ?: "Photo illisible" }
    }

    SectionTitle("Aperçu")
    CardBlock {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BotFace(bot, avatars[BotJson.id(bot)])
            Column(modifier = Modifier.padding(start = 10.dp)) {
                Text(BotJson.name(bot), color = colors.textPrimary, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = listOf(
                        BotJson.stateLabel(BotJson.state(bot)),
                        BotJson.fleetRole(bot).ifBlank { "solo" },
                        BotJson.enabled(bot).let { if (it) "activé" else "désactivé" },
                    ).joinToString(" · "),
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        if (live.isNotBlank()) KeyValueRow("Activité", live, highlight = true)
        KeyValueRow("Dernier passage", BotJson.lastRunState(bot).ifBlank { "—" })
        BotJson.description(bot).takeIf { it.isNotBlank() }?.let { KeyValueRow("Rôle", it) }
        KeyValueRow("Modèle", BotJson.model(bot).ifBlank { "défaut" })
        ButtonRow {
            TextButton(
                onClick = {
                    avatarPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
            ) { Text(if (avatars[id] != null) "Changer la photo" else "Photo") }
            if (avatars[id] != null) {
                TextButton(onClick = { viewModel.removeAvatar(id) }) { Text("Retirer la photo") }
            }
        }
        avatarError?.let { message ->
            Text(message, color = colors.danger, style = MaterialTheme.typography.labelSmall)
        }
        ButtonRow {
            TextButton(onClick = { viewModel.start(id) }) { Text("Démarrer") }
            TextButton(onClick = { viewModel.stop(id) }) { Text("Arrêter") }
            TextButton(onClick = { viewModel.run(id) }) { Text("Exécuter") }
            TextButton(onClick = { viewModel.setEnabled(id, !BotJson.enabled(bot)) }) {
                Text(if (BotJson.enabled(bot)) "Désactiver" else "Activer")
            }
        }
    }

    SectionTitle("Lancer en arrière-plan")
    CardBlock {
        Text(
            text = "La tâche est mise en file : la réponse revient immédiatement (202 + jobId) et " +
                "l'équipier travaille sans bloquer son fil. La file s'affiche en haut de la liste.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
        )
        OutlinedTextField(
            value = dispatchText,
            onValueChange = { dispatchText = it },
            label = { Text("Tâche à confier") },
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )
        if (dispatchFiles.isNotEmpty()) {
            ButtonRow {
                dispatchFiles.forEach { file ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "📎 ${file.name}",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textSecondary,
                        )
                        TextButton(onClick = { dispatchFiles = dispatchFiles - file }) {
                            Text("✕", color = colors.danger)
                        }
                    }
                }
            }
        }
        dispatchFileError?.let { message ->
            Text(message, color = colors.danger, style = MaterialTheme.typography.labelSmall)
        }
        state.dispatchFeedback?.let { feedback ->
            Text(
                text = feedback,
                style = MaterialTheme.typography.labelSmall,
                color = if (feedback.startsWith("Refusé")) colors.danger else colors.accent,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        ButtonRow {
            TextButton(onClick = { filePicker.launch(arrayOf("image/*", "application/pdf")) }) {
                Text("Joindre")
            }
            TextButton(
                onClick = {
                    viewModel.dispatch(dispatchText, dispatchFiles)
                    // Cleared at once: a refusal comes back in the feedback line with its
                    // reason, and re-picking a file is cheaper than a stale batch.
                    dispatchText = ""
                    dispatchFiles = emptyList()
                    dispatchFileError = null
                },
                enabled = !state.busy && (dispatchText.isNotBlank() || dispatchFiles.isNotEmpty()),
            ) { Text("Mettre en file") }
        }
    }

    SectionTitle("Persona")
    CardBlock {
        Text(
            text = "Qui il est et comment il travaille — c'est ce que Mercury relit à chaque réveil.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
        )
        OutlinedTextField(
            value = personaText,
            onValueChange = { personaText = it },
            label = { Text("Persona") },
            minLines = 4,
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier.fillMaxWidth(),
        )
        ButtonRow {
            TextButton(
                onClick = { viewModel.savePersona(personaText) },
                enabled = !state.busy && personaText.isNotBlank() && personaText != state.persona,
            ) { Text("Enregistrer le persona") }
        }
    }

    SectionTitle("Permissions")
    CardBlock {
        val tier = state.permissions?.let { permissions ->
            (permissions["tier"] as? JsonPrimitive)?.contentOrNull.orEmpty()
        }.orEmpty()
        KeyValueRow("Palier actuel", tier.ifBlank { BotJson.tier(bot).ifBlank { "—" } })
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("readonly" to "Lecture", "builder" to "Écriture", "operator" to "Opérateur", "full" to "Complet")
                .forEach { (tierId, label) ->
                    FilterChip(
                        selected = tier == tierId,
                        onClick = { viewModel.setTier(id, tierId) },
                        label = { Text(label) },
                    )
                }
        }
    }

    SectionTitle("Modèle et budget")
    CardBlock {
        PromptField(
            value = modelText,
            onValueChange = { modelText = it },
            label = "Modèle (vide = défaut)",
            confirmLabel = "Appliquer",
            onConfirm = { if (modelText.isNotBlank()) viewModel.setModel(id, modelText.trim()) },
        )
        PromptField(
            value = budgetText,
            onValueChange = { budgetText = it },
            label = "Budget quotidien (tokens, 0 = illimité)",
            confirmLabel = "Appliquer",
            onConfirm = { budgetText.trim().toIntOrNull()?.let { viewModel.setDailyBudget(id, it) } },
        )
    }

    SectionTitle("Flotte")
    CardBlock {
        val crew = state.crew
        KeyValueRow("Rôle", if (BotJson.isLead(bot)) "chef de flotte" else BotJson.fleetRole(bot).ifBlank { "solo" })
        crew?.crew?.let { members ->
            KeyValueRow("Membres", "${members.size} / ${crew.maxCrew}")
            members.forEach { member ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    BotFace(member, avatars[BotJson.id(member)], small = true)
                    Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                        Text(BotJson.name(member), color = colors.textPrimary, style = MaterialTheme.typography.bodyMedium)
                        val memberLive = watch.activity[BotJson.id(member)].orEmpty().ifBlank { BotJson.activity(member) }
                        Text(
                            text = listOf(BotJson.stateLabel(BotJson.state(member)), memberLive.takeIf { it.isNotBlank() })
                                .filterNotNull().joinToString(" · "),
                            color = colors.textSecondary,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }
        if (crew?.error?.isNotBlank() == true) {
            Text(crew.error, color = colors.textSecondary, style = MaterialTheme.typography.labelSmall)
        }
        ButtonRow {
            TextButton(onClick = { viewModel.promote(id) }) { Text("Promouvoir") }
            TextButton(onClick = { viewModel.autoCrew(id) }) { Text("Flotte auto") }
            TextButton(onClick = { showAddCrew = true }) { Text("Ajouter un membre") }
        }
    }

    if (state.tasks.isNotEmpty()) {
        SectionTitle("Tâches déléguées")
        CardBlock {
            state.tasks.forEach { task ->
                val goal = (task["goal"] as? JsonPrimitive)?.contentOrNull.orEmpty()
                val status = (task["status"] as? JsonPrimitive)?.contentOrNull.orEmpty()
                val assignee = (task["assignee"] as? JsonPrimitive)?.contentOrNull.orEmpty()
                ActionRow(
                    title = goal.ifBlank { status },
                    subtitle = listOf(status, assignee.takeIf { it.isNotBlank() }?.let { "→ $it" }).filterNotNull().joinToString(" · "),
                    trailing = "",
                    onClick = {},
                )
            }
        }
    }

    if (state.inbox.isNotEmpty()) {
        SectionTitle("Boîte aux lettres")
        CardBlock {
            state.inbox.forEach { mail ->
                KeyValueRow(
                    (mail["from"] as? JsonPrimitive)?.contentOrNull.orEmpty().ifBlank { "message" },
                    (mail["body"] as? JsonPrimitive)?.contentOrNull.orEmpty().take(160),
                )
            }
        }
    }

    if (state.journal.isNotEmpty()) {
        SectionTitle("Journal")
        CardBlock {
            state.journal.takeLast(8).reversed().forEach { entry ->
                KeyValueRow(
                    (entry["at"] as? JsonPrimitive)?.contentOrNull.orEmpty().take(16).ifBlank { "—" },
                    (entry["message"] as? JsonPrimitive)?.contentOrNull.orEmpty()
                        .ifBlank { (entry["event"] as? JsonPrimitive)?.contentOrNull.orEmpty() }
                        .take(160),
                )
            }
        }
    }

    SectionTitle("Suppression")
    CardBlock {
        Text(
            text = "Supprime l'équipier et son profil. Une flotte emporte ses membres.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
        )
        ButtonRow {
            TextButton(onClick = { viewModel.delete(id) }) { Text("Supprimer", color = colors.danger) }
        }
    }

    if (showAddCrew) {
        AddCrewSheet(
            onDismiss = { showAddCrew = false },
            onConfirm = { crewId, name, description, persona, tier ->
                showAddCrew = false
                viewModel.addCrew(id, crewId, name, description, persona, tier)
            },
        )
    }
}

/**
 * The bot's face: its uploaded photo when there is one, the monogram otherwise.
 * `remember(avatar)` decodes once per byte array — the roster recomposes on every
 * activity line, and re-decoding a bitmap there would be wasteful.
 */
@Composable
private fun BotFace(bot: JsonObject, avatar: ByteArray?, small: Boolean = false) {
    val size = if (small) 26.dp else 38.dp
    if (avatar != null) {
        val bitmap = remember(avatar) {
            BitmapFactory.decodeByteArray(avatar, 0, avatar.size)?.asImageBitmap()
        }
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = "Photo de ${BotJson.name(bot)}",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape),
            )
            return
        }
    }
    Monogram(bot, small)
}

@Composable
private fun Monogram(bot: JsonObject, small: Boolean = false) {
    val hue = BotJson.hue(bot).toFloat()
    val background = Color.hsv(hue, 0.45f, 0.75f)
    val size = if (small) 26.dp else 38.dp
    Box(
        modifier = Modifier
            .size(size)
            .background(background, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = BotJson.monogram(bot),
            color = Color.Black.copy(alpha = 0.75f),
            style = if (small) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun PromptField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onConfirm) { Text(confirmLabel) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddCrewSheet(
    onDismiss: () -> Unit,
    onConfirm: (id: String, name: String, description: String?, persona: String?, tier: String?) -> Unit,
) {
    val colors = LocalMercuryColors.current
    var id by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var persona by remember { mutableStateOf("") }
    var tier by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = { Text("Nouveau membre", color = colors.textPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = id, onValueChange = { id = it }, label = { Text("Identifiant") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nom") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Spécialité") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = persona, onValueChange = { persona = it }, label = { Text("Persona (optionnel)") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("readonly", "builder", "operator", "full").forEach { candidate ->
                        FilterChip(
                            selected = tier == candidate,
                            onClick = { tier = if (tier == candidate) null else candidate },
                            label = { Text(candidate) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(id.trim(), name.trim(), description.trim().ifBlank { null }, persona.trim().ifBlank { null }, tier) },
                enabled = id.isNotBlank() && name.isNotBlank(),
            ) { Text("Ajouter") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}
