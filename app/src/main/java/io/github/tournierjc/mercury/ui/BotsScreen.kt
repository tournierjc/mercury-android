package io.github.tournierjc.mercury.ui

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddAPhoto
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.HideImage
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.PersonAddAlt
import androidx.compose.material.icons.rounded.PersonRemove
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material3.IconButton
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.tournierjc.mercury.MercuryApp
import io.github.tournierjc.mercury.bots.BotWatchState
import io.github.tournierjc.mercury.data.BotJson
import io.github.tournierjc.mercury.data.BotOps
import io.github.tournierjc.mercury.data.BotSlug
import io.github.tournierjc.mercury.ui.theme.LocalMercuryColors
import io.github.tournierjc.mercury.data.Attachments
import io.github.tournierjc.mercury.data.PendingFile
import io.github.tournierjc.mercury.util.MediaPicker
import io.github.tournierjc.mercury.util.OutputSharing
import io.github.tournierjc.mercury.util.ShareText
import io.github.tournierjc.mercury.vm.BotsUiState
import io.github.tournierjc.mercury.vm.BotsViewModel
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Teammates: the roster with live activity and passive state, plus a detail view for
 * customization (persona, tier, model, budget), fleet control and background dispatch.
 *
 * A bot with a custom avatar shows it; the monogram (colour derived from the id) is the
 * fallback, not the default. `hasAvatar` on the roster tells us whether to fetch at all.
 */
@Composable
fun BotsScreen(
    onBack: () -> Unit,
    /** Open the conversation screen for a bot. */
    onOpenChat: (String) -> Unit = {},
    /** Opened straight onto this bot — used when the notification names one. */
    initialBotId: String? = null,
    onOpened: () -> Unit = {},
) {
    val context = LocalContext.current
    val container = remember { (context.applicationContext as MercuryApp).container }
    val viewModel: BotsViewModel = viewModel(factory = BotsViewModel.factory(container))
    val watch by viewModel.watcher.state.collectAsState()
    val state by viewModel.state.collectAsState()
    val avatars by viewModel.avatars.collectAsState()
    // Faces are fetched only for bots whose roster entry says they have one.
    LaunchedEffect(watch.bots) { viewModel.loadAvatars(watch.bots) }
    LaunchedEffect(initialBotId) {
        if (initialBotId != null) {
            viewModel.select(initialBotId)
            onOpened()
        }
    }
    val colors = LocalMercuryColors.current
    var showNewBot by remember { mutableStateOf(false) }

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
            leading = selected?.let { bot -> { BotFace(bot, state.selectedId?.let { avatars[it] }, small = true) } },
        ) {
            // A teammate can be made from the app: until now the roster was read-only and a
            // bot had to be created with a POST or by hand.
            if (state.selectedId == null) {
                IconButton(onClick = { showNewBot = true }) {
                    Icon(Icons.Rounded.Add, contentDescription = "Nouvel équipier", tint = colors.accent)
                }
            } else {
                // The conversation is one tap away from the sheet, like in the chat's top bar.
                IconButton(onClick = { onOpenChat(state.selectedId!!) }) {
                    Icon(
                        Icons.AutoMirrored.Rounded.Chat,
                        contentDescription = "Conversation",
                        tint = colors.textSecondary,
                    )
                }
            }
        }
        if (showNewBot) {
            NewBotDialog(
                busy = state.busy,
                onDismiss = { showNewBot = false },
                onCreate = { name, newId, tier, persona ->
                    showNewBot = false
                    viewModel.createBot(name, newId, tier, persona)
                },
            )
        }
        state.message?.let { InlineBanner(it, isError = false, onDismiss = viewModel::clearBanner) }
        state.error?.let { InlineBanner(it, isError = true, onDismiss = viewModel::clearBanner) }

        // A downloaded deliverable is handed to the share sheet from the screen: the
        // view model has no Context, and the slot is cleared so it fires exactly once.
        val pendingShare = state.pendingShare
        LaunchedEffect(pendingShare) {
            if (pendingShare != null) {
                runCatching { OutputSharing.share(context, pendingShare) }
                    .onFailure { viewModel.showError(it.message ?: "Partage impossible") }
                viewModel.consumeShare()
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp),
        ) {
            if (state.selectedId == null) {
                RosterList(watch, onSelect = viewModel::select, onOpenChat = onOpenChat, avatars = avatars)
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
                BotDetail(state, watch, viewModel, avatars, onOpenChat = onOpenChat)
            }
            Text(" ", modifier = Modifier.padding(bottom = 28.dp))
        }
    }

    // Preview and run detail are dialogs over the sheet: the sheet stays scrolled where
    // the owner left it, and closing the dialog never reloads the bot.
    state.outputPreview?.let { preview ->
        OutputPreviewDialog(
            state = preview,
            onDismiss = viewModel::dismissPreview,
            onShare = {
                viewModel.dismissPreview()
                viewModel.shareOutput(preview.output)
            },
        )
    }

    if (state.runDetail != null || state.runDetailLoading) {
        RunDetailDialog(
            transcript = state.runDetail,
            loading = state.runDetailLoading,
            onDismiss = viewModel::closeRun,
        )
    }
}

@Composable
private fun RosterList(
    watch: BotWatchState,
    onSelect: (String) -> Unit,
    onOpenChat: (String) -> Unit,
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
                    onClick = { onOpenChat(BotJson.id(bot)) },
                )
            }
        }
    }

    SectionTitle("Équipiers")
    CardBlock {
        if (watch.bots.isEmpty()) {
            Text(
                text = "Aucun équipier. Le + en haut à droite en crée un.",
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
                    // Tap = settings, icon = conversation. The roster is a management screen:
                    // the default action there is to configure, and chatting is one deliberate
                    // tap away (the drawer stays the quick way to switch conversations).
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
                IconButton(onClick = { onOpenChat(id) }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Chat,
                        contentDescription = "Conversation avec ${BotJson.name(bot)}",
                        tint = colors.textSecondary,
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
    onOpenChat: (String) -> Unit,
) {
    val colors = LocalMercuryColors.current
    // Captured at composition: `LocalContext.current` is a composable getter and the
    // picker callback below is not one.
    val context = LocalContext.current
    val id = state.selectedId.orEmpty()
    // Deleting is irreversible: the button asks, this remembers the question.
    var confirmDelete by remember { mutableStateOf(false) }
    // Two steps, one form: the picker lists what is free, "Créer un bot" opens NewBotDialog.
    var showNewCrew by remember { mutableStateOf(false) }
    var showModelPicker by remember { mutableStateOf(false) }
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
    var budgetText by remember(id) { mutableStateOf("") }
    var avatarError by remember(id) { mutableStateOf<String?>(null) }

    // The bundle is a document, not a screen: it goes to the share sheet as soon as it lands.
    val bundle = state.bundle
    LaunchedEffect(bundle) {
        if (bundle != null) {
            ShareText.bundle(context, "${state.bundleFor}-bundle.json", bundle)
            viewModel.clearBundle()
        }
    }

    // 512 px is plenty for a face in a roster, and the bytes leave the phone encoded.
    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        MediaPicker.read(context, uri, maxDimension = 512)
            .onSuccess { file -> avatarError = null; viewModel.uploadAvatar(id, file.bytes) }
            .onFailure { error -> avatarError = error.message ?: "Photo illisible" }
    }

    CollapsibleSection("Aperçu", defaultOpen = true) {
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
        // Glyphs, not five identical grey words: the same vocabulary as the conversation
        // screen's top bar. Every label is also the icon's content description.
        IconActionBar(
            listOf(
                QuickAction(
                    icon = if (avatars[id] != null) Icons.Rounded.PhotoCamera else Icons.Rounded.AddAPhoto,
                    label = if (avatars[id] != null) "Photo" else "Photo",
                    onClick = {
                        avatarPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                ),
                QuickAction(
                    icon = Icons.Rounded.PlayArrow,
                    label = "Démarrer",
                    enabled = !state.busy,
                    onClick = { viewModel.start(id) },
                ),
                QuickAction(
                    icon = Icons.Rounded.Stop,
                    label = "Arrêter",
                    enabled = !state.busy,
                    onClick = { viewModel.stop(id) },
                ),
                QuickAction(
                    icon = Icons.Rounded.Bolt,
                    label = "Exécuter",
                    enabled = !state.busy,
                    onClick = { viewModel.run(id) },
                ),
                QuickAction(
                    icon = Icons.Rounded.PowerSettingsNew,
                    label = if (BotJson.enabled(bot)) "Désactiver" else "Activer",
                    tint = if (BotJson.enabled(bot)) colors.textPrimary else colors.accent,
                    enabled = !state.busy,
                    onClick = { viewModel.setEnabled(id, !BotJson.enabled(bot)) },
                ),
                QuickAction(
                    icon = Icons.Rounded.IosShare,
                    label = "Partager",
                    enabled = !state.busy,
                    onClick = { viewModel.exportBundle(id) },
                ),
                QuickAction(
                    icon = Icons.Rounded.DeleteOutline,
                    label = "Supprimer",
                    tint = colors.danger,
                    onClick = { confirmDelete = true },
                ),
            ),
        )
        if (avatars[id] != null) {
            IconActionBar(
                listOf(
                    QuickAction(
                        icon = Icons.Rounded.HideImage,
                        label = "Sans photo",
                        onClick = { viewModel.removeAvatar(id) },
                    ),
                ),
            )
        }
        avatarError?.let { message ->
            Text(message, color = colors.danger, style = MaterialTheme.typography.labelSmall)
        }
        Text(
            text = "Le bundle contient l'identité, le persona, les permissions et les skills — jamais les secrets " +
                "ni le journal. Un chef de flotte exporte toute son équipe.",
            style = MaterialTheme.typography.labelSmall,
            color = colors.textSecondary,
            modifier = Modifier.padding(top = 6.dp),
        )
    }

    CollapsibleSection("Lui écrire", defaultOpen = true) {
        Text(
            text = "La conversation est un écran à part : ce panneau ne sert qu'à le régler. " +
                "Un message part en tâche de fond, la réponse s'écrit dans la conversation.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
        )

        // The buttons live inside the field: a task is typed and sent in one gesture, and the
        // two rows of text buttons beneath it were the least modern thing on the screen.
        OutlinedTextField(
            value = dispatchText,
            onValueChange = { dispatchText = it },
            placeholder = { Text("Tâche rapide à confier") },
            minLines = 2,
            maxLines = 5,
            shape = RoundedCornerShape(16.dp),
            trailingIcon = {
                Row(verticalAlignment = Alignment.Bottom) {
                    IconButton(
                        onClick = { filePicker.launch(arrayOf("image/*", "application/pdf")) },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            Icons.Rounded.AttachFile,
                            contentDescription = "Joindre un fichier",
                            tint = colors.textSecondary,
                            modifier = Modifier.size(19.dp),
                        )
                    }
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(
                                if (!state.busy && (dispatchText.isNotBlank() || dispatchFiles.isNotEmpty())) {
                                    colors.accent
                                } else {
                                    colors.surfaceMuted
                                },
                            )
                            .clickable(enabled = !state.busy && (dispatchText.isNotBlank() || dispatchFiles.isNotEmpty())) {
                                viewModel.dispatch(dispatchText, dispatchFiles)
                                dispatchText = ""
                                dispatchFiles = emptyList()
                                dispatchFileError = null
                            },
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.Send,
                            contentDescription = "Envoyer la tâche",
                            tint = if (!state.busy && (dispatchText.isNotBlank() || dispatchFiles.isNotEmpty())) {
                                colors.canvas
                            } else {
                                colors.textSecondary
                            },
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
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
                        IconButton(onClick = { dispatchFiles = dispatchFiles - file }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Retirer ${file.name}", tint = colors.danger)
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
    }

    CollapsibleSection("Persona") {
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
        // Two controls, centred: save what was typed, or drop the edit and go back to what the
        // daemon holds. Without « Rétablir » the only way out of a bad edit was to retype it.
        IconActionBar(
            centered = true,
            actions = listOf(
                QuickAction(
                    icon = Icons.Rounded.Check,
                    label = "Enregistrer",
                    tint = colors.accent,
                    enabled = !state.busy && personaText.isNotBlank() && personaText != state.persona,
                    onClick = { viewModel.savePersona(personaText) },
                ),
                QuickAction(
                    icon = Icons.Rounded.Undo,
                    label = "Rétablir",
                    enabled = personaText != state.persona,
                    onClick = { personaText = state.persona },
                ),
            ),
        )
    }

    CollapsibleSection("Permissions") {
        LaunchedEffect(state.selectedId) { viewModel.loadTiers() }
        // The file names its tier since 1.5.0-local.9. A file written before that carries only
        // the grants, so the tier is deduced by matching its deny list against the catalogue —
        // and when nothing matches, no cell is selected rather than the wrong one.
        val storedTier = state.permissions?.let { permissions ->
            (permissions["tier"] as? JsonPrimitive)?.contentOrNull.orEmpty()
        }.orEmpty().ifBlank { BotJson.tier(bot) }
        val grantedDeny = ((state.permissions?.get("tools") as? JsonObject)?.get("deny") as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            ?.toSet()
        val inferredTier = if (storedTier.isNotBlank() || grantedDeny == null) {
            storedTier
        } else {
            state.tiers.firstOrNull { it.deny.toSet() == grantedDeny }?.id.orEmpty()
        }
        val tier = inferredTier
        val tiers = listOf(
            "readonly" to ("Lecture" to "lire"),
            "builder" to ("Écriture" to "écrire"),
            "operator" to ("Opérateur" to "exécuter"),
            "full" to ("Complet" to "tout"),
        )
        // One row, four cells: the active tier is filled and ticked, the others are outlines.
        // A chip row that only tints its label is not readable at a glance on a phone.
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
        ) {
            tiers.forEach { (tierId, labels) ->
                val (label, verb) = labels
                val selected = tier == tierId
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selected) colors.accent.copy(alpha = 0.16f) else Color.Transparent)
                        .then(
                            if (selected) Modifier
                            else Modifier.border(1.dp, colors.hairlineStrong, RoundedCornerShape(12.dp)),
                        )
                        .clickable { viewModel.setTier(id, tierId) }
                        .padding(vertical = 9.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (selected) {
                            Icon(
                                Icons.Rounded.Check,
                                contentDescription = null,
                                tint = colors.accent,
                                modifier = Modifier.size(13.dp),
                            )
                        }
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selected) colors.accent else colors.textSecondary,
                            modifier = Modifier.padding(start = if (selected) 4.dp else 0.dp),
                        )
                    }
                    Text(
                        text = verb,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) colors.accent.copy(alpha = 0.7f) else colors.textSecondary.copy(alpha = 0.55f),
                    )
                }
            }
        }
    }

    CollapsibleSection("Modèle et budget") {
        // A list of what the instance actually offers, not a free field: the daemon stores
        // { provider, model } and a typo used to fall back to the default in silence.
        val pickedProvider = BotJson.modelProvider(bot)
        val pickedModel = BotJson.modelName(bot)
        val currentLabel = state.models.firstOrNull { it.name == pickedProvider }
            ?.let { ref -> listOf(ref.displayLabel, pickedModel).filter { it.isNotBlank() }.joinToString(" · ") }
            .orEmpty()
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(colors.surfaceMuted)
                .clickable { viewModel.loadModels(); showModelPicker = true }
                .padding(horizontal = 14.dp, vertical = 13.dp),
        ) {
            Icon(Icons.Rounded.Memory, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(18.dp))
            Text(
                text = "Modèle",
                style = MaterialTheme.typography.bodyLarge,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f).padding(start = 12.dp),
            )
            Text(
                text = currentLabel.ifBlank { "défaut" },
                style = MaterialTheme.typography.bodyMedium,
                color = if (currentLabel.isBlank()) colors.textSecondary else colors.accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1.2f),
            )
            Icon(Icons.Rounded.ExpandMore, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(18.dp))
        }
        // One line, digits only, the check inside the field: a budget is a number, not prose.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        ) {
            OutlinedTextField(
                value = budgetText,
                onValueChange = { budgetText = it.filter { ch -> ch.isDigit() } },
                label = { Text("Budget quotidien (tokens)") },
                placeholder = { Text("0 = illimité") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = { budgetText.trim().toIntOrNull()?.let { viewModel.setDailyBudget(id, it) } },
                ),
                shape = RoundedCornerShape(14.dp),
                trailingIcon = {
                    if (budgetText.isNotBlank()) {
                        IconButton(onClick = { budgetText.trim().toIntOrNull()?.let { viewModel.setDailyBudget(id, it) } }) {
                            Icon(Icons.Rounded.Check, contentDescription = "Appliquer le budget", tint = colors.accent)
                        }
                    }
                },
                modifier = Modifier.weight(1f),
            )
        }
    }

    CollapsibleSection("Flotte") {
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
                    // Leaving a fleet is reversible (the member can be added back), so a tap,
                    // not a confirmation: the banner says what happened.
                    IconButton(onClick = { viewModel.detachCrew(id, BotJson.id(member)) }) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = "Retirer ${BotJson.name(member)} de la flotte",
                            tint = colors.textSecondary,
                        )
                    }
                }
            }
        }
        if (crew?.error?.isNotBlank() == true) {
            Text(crew.error, color = colors.textSecondary, style = MaterialTheme.typography.labelSmall)
        }
        if (BotJson.parent(bot).isNotBlank()) {
            Text(
                text = "Membre de la flotte de ${BotJson.parent(bot)}.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
            IconActionBar(
                listOf(
                    QuickAction(
                        icon = Icons.Rounded.PersonRemove,
                        label = "Quitter",
                        tint = colors.danger,
                        onClick = { viewModel.detachCrew(BotJson.parent(bot), id) },
                    ),
                ),
            )
        }
        IconActionBar(
            listOf(
                QuickAction(icon = Icons.Rounded.Star, label = "Promouvoir", onClick = { viewModel.promote(id) }),
                QuickAction(icon = Icons.Rounded.AutoAwesome, label = "Auto", onClick = { viewModel.autoCrew(id) }),
                QuickAction(icon = Icons.Rounded.PersonAddAlt, label = "Membre", onClick = { showAddCrew = true }),
            ),
        )
    }

    if (state.tasks.isNotEmpty()) {
        CollapsibleSection("Tâches déléguées") {
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
        CollapsibleSection("Boîte aux lettres") {
            state.inbox.forEach { mail ->
                KeyValueRow(
                    (mail["from"] as? JsonPrimitive)?.contentOrNull.orEmpty().ifBlank { "message" },
                    (mail["body"] as? JsonPrimitive)?.contentOrNull.orEmpty().take(160),
                )
            }
        }
    }


    // The journal used to be a section here; it is the conversation, and the conversation screen
    // draws it from the same lines (`state.records`). A second, worse copy on the sheet was one
    // more wall of text — the sheet keeps only what the chat cannot show: files, runs, incidents.
    //
    // What the bot produced, what it did, and what the queue gave up on. The three
    // sections read the deliverables, runs and DLQ routes of this bot.
    DeliverablesSection(
        title = "Livrables",
        outputs = state.outputs,
        busy = state.busy,
        emptyText = "Aucun livrable : l'équipier n'a rien déposé dans son dossier.",
        onPreview = viewModel::previewOutput,
        onShare = viewModel::shareOutput,
        onDelete = viewModel::deleteOutput,
    )

    RunsSection(
        runs = state.runs,
        emptyText = "Aucun run enregistré pour cet équipier.",
        onOpen = viewModel::openRun,
    )

    DlqSection(
        incidents = state.dlq,
        busy = state.busy,
        onReplay = viewModel::replayIncident,
        onClear = viewModel::clearDlq,
    )

    if (showAddCrew) {
        // Existing bots this lead can take: a solo bot has no parent and leads nothing. The
        // daemon refuses the rest, but a list of refusals is not a picker.
        // A bot promoted to lead but leading nobody is still adoptable (the daemon demotes
        // it); one with a crew is not, and the daemon says so if it is offered anyway.
        val adoptable = watch.bots.filter { candidate ->
            BotJson.id(candidate) != id && BotJson.parent(candidate).isBlank()
        }
        AddCrewSheet(
            adoptable = adoptable,
            avatars = avatars,
            onAdopt = { memberId ->
                showAddCrew = false
                viewModel.adoptCrew(id, memberId)
            },
            onCreateNew = {
                showAddCrew = false
                showNewCrew = true
            },
            onDismiss = { showAddCrew = false },
        )
    }
    if (showModelPicker) {
        val picked = BotJson.modelProvider(bot)
        AlertDialog(
            onDismissRequest = { showModelPicker = false },
            containerColor = colors.surface,
            title = { Text("Modèle de l'équipier", color = colors.textPrimary) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    // "Default" is a real choice, not the absence of one: it removes the key so
                    // the bot follows whatever the instance is set to.
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { showModelPicker = false; viewModel.clearModel(id) }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                    ) {
                        Text(
                            text = "Défaut de l'instance",
                            style = MaterialTheme.typography.bodyLarge,
                            color = colors.textPrimary,
                            modifier = Modifier.weight(1f),
                        )
                        if (picked.isBlank()) {
                            Icon(Icons.Rounded.Check, contentDescription = "Sélectionné", tint = colors.accent, modifier = Modifier.size(18.dp))
                        }
                    }
                    HorizontalDivider(color = colors.hairline)
                    if (state.models.isEmpty()) {
                        Text(
                            text = "Aucun modèle : ajoute une clé dans Réglages → Fournisseurs.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textSecondary,
                            modifier = Modifier.padding(vertical = 10.dp),
                        )
                    }
                    state.models.forEach { ref ->
                        val selected = picked == ref.name
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    showModelPicker = false
                                    viewModel.setModel(id, ref.name, ref.model)
                                }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = ref.displayLabel,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (ref.available) colors.textPrimary else colors.textSecondary,
                                )
                                Text(
                                    text = "${ref.name} · ${ref.model}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.textSecondary,
                                )
                            }
                            if (selected) {
                                Icon(Icons.Rounded.Check, contentDescription = "Sélectionné", tint = colors.accent, modifier = Modifier.size(18.dp))
                            } else if (!ref.available) {
                                Text("clé absente", style = MaterialTheme.typography.labelSmall, color = colors.danger)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showModelPicker = false }) { Text("Fermer") } },
        )
    }
    if (showNewCrew) {
        NewBotDialog(
            busy = state.busy,
            title = "Nouveau membre",
            submitLabel = "Ajouter",
            onDismiss = { showNewCrew = false },
            onCreate = { name, crewId, tier, persona ->
                showNewCrew = false
                viewModel.addCrew(id, crewId.ifBlank { BotSlug.from(name) }, name, null, persona.ifBlank { null }, tier)
            },
        )
    }
    if (confirmDelete) {
        ConfirmDeleteBotDialog(
            botName = BotJson.name(bot),
            onDismiss = { confirmDelete = false },
            onConfirm = {
                confirmDelete = false
                viewModel.delete(id)
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
internal fun BotFace(bot: JsonObject, avatar: ByteArray?, small: Boolean = false) {
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
    adoptable: List<JsonObject> = emptyList(),
    avatars: Map<String, ByteArray> = emptyMap(),
    onAdopt: (String) -> Unit = {},
    onCreateNew: () -> Unit = {},
    onDismiss: () -> Unit,
) {
    val colors = LocalMercuryColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = { Text("Ajouter un membre", color = colors.textPrimary) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = "Un équipier libre rejoint la flotte en un tap.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
                if (adoptable.isEmpty()) {
                    Text(
                        text = "Aucun équipier libre : tous sont déjà en flotte, ou il n'y en a pas encore.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
                adoptable.forEach { candidate ->
                    val candidateId = BotJson.id(candidate)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onAdopt(candidateId) }
                            .padding(vertical = 6.dp, horizontal = 4.dp),
                    ) {
                        BotFace(candidate, avatars[candidateId], small = true)
                        Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
                            Text(
                                text = BotJson.name(candidate),
                                style = MaterialTheme.typography.bodyLarge,
                                color = colors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = BotJson.stateLabel(BotJson.state(candidate)),
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textSecondary,
                            )
                        }
                        Icon(Icons.Rounded.Add, contentDescription = "Ajouter ${BotJson.name(candidate)}", tint = colors.accent)
                    }
                }
                HorizontalDivider(color = colors.hairline, modifier = Modifier.padding(vertical = 10.dp))
                // One form for both paths: the roster's "+" and this sheet open the same dialog,
                // so a field added later is added once.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onCreateNew() }
                        .padding(vertical = 8.dp, horizontal = 4.dp),
                ) {
                    Text(
                        text = "Créer un bot",
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.accent,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(Icons.Rounded.Add, contentDescription = "Créer un bot", tint = colors.accent)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fermer") } },
    )
}
