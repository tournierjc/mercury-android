package io.github.tournierjc.mercury.ui

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Create
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.HideImage
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.PersonAddAlt
import androidx.compose.material.icons.rounded.PersonRemove
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material3.IconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.ui.res.pluralStringResource
import io.github.tournierjc.mercury.data.FleetRow
import io.github.tournierjc.mercury.data.fleetRows
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.tournierjc.mercury.MercuryApp
import io.github.tournierjc.mercury.R
import io.github.tournierjc.mercury.bots.BotWatchState
import io.github.tournierjc.mercury.data.NoticeSeverity
import io.github.tournierjc.mercury.data.BotAutonomy
import io.github.tournierjc.mercury.data.BotJson
import io.github.tournierjc.mercury.data.BotOps
import io.github.tournierjc.mercury.data.BotSlug
import io.github.tournierjc.mercury.ui.theme.LocalMercuryColors
import io.github.tournierjc.mercury.data.Attachments
import io.github.tournierjc.mercury.data.PendingFile
import io.github.tournierjc.mercury.util.ModelRefs
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

    // Inner depth goes first: a bot's page closes before Équipiers hands Back to the screen it came
    // from, which is what « back one step » means when the step is inside the screen.
    BackHandler(enabled = state.selectedId != null) { viewModel.select(null) }
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
            title = selected?.let { BotJson.name(it) } ?: stringResource(R.string.bots_title_roster),
            subtitle = if (selected == null) {
                stringResource(R.string.bots_header_subtitle, watch.bots.size, watch.queue.pending) +
                    if (!watch.connected) stringResource(R.string.bots_header_offline) else ""
            } else {
                noticeText(BotJson.stateNotice(BotJson.state(selected)))
            },
            onBack = { if (state.selectedId == null) onBack() else viewModel.select(null) },
            onRefresh = viewModel::refreshRoster,
            refreshing = state.refreshing,
            leading = selected?.let { bot -> { BotFace(bot, state.selectedId?.let { avatars[it] }, small = true) } },
        ) {
            // A teammate can be made from the app: until now the roster was read-only and a
            // bot had to be created with a POST or by hand.
            if (state.selectedId == null) {
                IconButton(onClick = { showNewBot = true }) {
                    Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.bots_cd_new_bot), tint = colors.accent)
                }
            } else {
                // The conversation is one tap away from the sheet, like in the chat's top bar.
                IconButton(onClick = { onOpenChat(state.selectedId!!) }) {
                    Icon(
                        Icons.AutoMirrored.Rounded.Chat,
                        contentDescription = stringResource(R.string.bots_cd_conversation),
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
        state.message?.let { notice -> NoticeBanner(notice, onDismiss = viewModel::clearBanner) }
        state.error?.let { notice -> NoticeBanner(notice, onDismiss = viewModel::clearBanner) }

        // A downloaded deliverable is handed to the share sheet from the screen: the
        // view model has no Context, and the slot is cleared so it fires exactly once.
        val pendingShare = state.pendingShare
        // Resolved here: the effect block below is not a composable scope.
        val shareFailed = stringResource(R.string.bots_error_share_failed)
        LaunchedEffect(pendingShare) {
            if (pendingShare != null) {
                runCatching { OutputSharing.share(context, pendingShare) }
                    .onFailure { viewModel.showError(it.message ?: shareFailed) }
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
                FleetBlock(viewModel, watch, avatars, onSelect = viewModel::select)
                RosterList(watch, onSelect = viewModel::select, onOpenChat = onOpenChat, avatars = avatars)
                if (watch.recent.isNotEmpty()) {
                    CollapsibleSection(stringResource(R.string.bots_section_recent_activity)) {
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

/**
 * The fleet itself: import a shared bundle, and see who leads a crew.
 *
 * It used to be a block of the Console, next to everything else — with the leaders as a row of
 * text chips. The fleet is this screen's business, and a leader reads as a bot: face, name, state.
 */
@Composable
private fun FleetBlock(
    viewModel: BotsViewModel,
    watch: BotWatchState,
    avatars: Map<String, ByteArray>,
    onSelect: (String) -> Unit,
) {
    val colors = LocalMercuryColors.current
    val context = LocalContext.current
    val unreadableFile = stringResource(R.string.bots_error_unreadable_file)
    var overwrite by remember { mutableStateOf(false) }
    var importError by remember { mutableStateOf<String?>(null) }

    // `import_bot_bundle` is a .json file; some pickers report it as text or octet-stream.
    val bundlePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
        }.getOrNull()
        if (text.isNullOrBlank()) {
            importError = unreadableFile
        } else {
            importError = null
            viewModel.importBundle(text, overwrite)
        }
    }

    val leads = watch.bots.filter { BotJson.isLead(it) }

    CollapsibleSection(stringResource(R.string.bots_section_fleet)) {
        IconActionBar(
            listOf(
                QuickAction(
                    icon = Icons.Rounded.UploadFile,
                    label = stringResource(R.string.bots_action_import),
                    tint = colors.accent,
                    onClick = {
                        bundlePicker.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                    },
                ),
                QuickAction(
                    icon = Icons.Rounded.Layers,
                    label = if (overwrite) stringResource(R.string.bots_action_overwrite_on) else stringResource(R.string.bots_action_overwrite),
                    tint = if (overwrite) colors.accent else null,
                    onClick = { overwrite = !overwrite },
                ),
            ),
        )
        Text(
            text = stringResource(R.string.bots_fleet_import_hint),
            style = MaterialTheme.typography.labelSmall,
            color = colors.textSecondary,
        )
        importError?.let { message ->
            Text(message, style = MaterialTheme.typography.labelSmall, color = colors.danger)
        }
        if (leads.isEmpty()) {
            Text(
                text = stringResource(R.string.bots_fleet_no_lead),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        leads.forEach { lead ->
            val id = BotJson.id(lead)
            val crew = BotJson.crewWorking(lead) ?: 0
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.extraSmall)
                    .clickable { onSelect(id) }
                    .padding(vertical = 6.dp),
            ) {
                BotFace(lead, avatars[id], small = true)
                Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
                    Text(
                        text = BotJson.name(lead),
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.textPrimary,
                    )
                    Text(
                        text = listOf(
                            stringResource(R.string.bots_role_fleet_lead),
                            if (crew > 0) stringResource(R.string.bots_crew_running, crew) else null,
                            noticeText(BotJson.stateNotice(BotJson.state(lead))),
                        ).filterNotNull().joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textSecondary,
                    )
                }
                Icon(
                    Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = colors.textSecondary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
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
        CollapsibleSection(
            stringResource(R.string.bots_section_waiting, needsYou.size),
            defaultOpen = true,
            stateKey = "bots-waiting",
        ) {
            needsYou.forEach { bot ->
                ActionRow(
                    title = BotJson.name(bot),
                    subtitle = BotJson.activity(bot).ifBlank { stringResource(R.string.bots_waiting_default_activity) },
                    trailing = stringResource(R.string.bots_action_open_lower),
                    onClick = { onOpenChat(BotJson.id(bot)) },
                )
            }
        }
    }

    CollapsibleSection(stringResource(R.string.bots_section_teammates), defaultOpen = true) {
        if (watch.bots.isEmpty()) {
            Text(
                text = stringResource(R.string.bots_empty_roster),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
            return@CollapsibleSection
        }
        // The roster is a tree, and the lead *is* the node: it used to be a section heading with a
        // copy of the lead as its first row — the same bot twice, one level deeper than it needed to
        // be. Now the disclosure sits on the lead's own row (arrow on the left) and its crew folds
        // underneath, indented under the face.
        var openLeads by rememberSaveable { mutableStateOf(listOf<String>()) }
        val byId = remember(watch.bots) { watch.bots.associateBy { BotJson.id(it) } }
        remember(watch.bots) { fleetRows(watch.bots) }.forEach { row ->
            when (row) {
                is FleetRow.Solo -> byId[row.id]?.let { bot ->
                    BotRosterRow(bot, avatars[row.id], watch, onSelect, onOpenChat)
                }

                is FleetRow.Lead -> byId[row.id]?.let { lead ->
                    val open = row.id in openLeads
                    BotRosterRow(
                        bot = lead,
                        avatar = avatars[row.id],
                        watch = watch,
                        onSelect = onSelect,
                        onOpenChat = onOpenChat,
                        crew = row.crewIds.size.takeIf { it > 0 },
                        expanded = open,
                        onToggle = row.crewIds.takeIf { it.isNotEmpty() }?.let {
                            {
                                openLeads = if (open) openLeads - listOf(row.id) else openLeads + listOf(row.id)
                            }
                        },
                    )
                    if (open) {
                        row.crewIds.forEach { crewId ->
                            byId[crewId]?.let { crew ->
                                BotRosterRow(crew, avatars[crewId], watch, onSelect, onOpenChat, indent = true)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * One bot of the roster. The same row serves a solo bot, a lead and a crew member — only the
 * indent and the disclosure change, so a fleet cannot grow a second row design beside the flat one
 * it came from.
 *
 * A lead carries [crew] (how many answer to it) and, when that crew is non-empty, [onToggle]: the
 * chevron sits at the left edge of the row and unfolds the crew in place. The row *is* the section —
 * it used to be a heading with the same bot repeated as its first row underneath.
 */
@Composable
private fun BotRosterRow(
    bot: JsonObject,
    avatar: ByteArray?,
    watch: BotWatchState,
    onSelect: (String) -> Unit,
    onOpenChat: (String) -> Unit,
    indent: Boolean = false,
    /** The size of the crew behind this lead, or null on any other row. */
    crew: Int? = null,
    expanded: Boolean = false,
    onToggle: (() -> Unit)? = null,
) {
    val colors = LocalMercuryColors.current
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
            .padding(start = if (indent) 40.dp else 0.dp, top = 6.dp, bottom = 6.dp),
    ) {
        // The arrow is left of the face: it reads as the tree's own edge, and it is the one thing on
        // the row that folds instead of navigating. A lead with nobody keeps the slot empty, so the
        // faces of the leads line up whether or not they have a crew.
        when {
            onToggle != null -> IconButton(onClick = onToggle, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = if (expanded) {
                        stringResource(R.string.drawer_fold_crew, BotJson.name(bot))
                    } else {
                        stringResource(R.string.drawer_unfold_crew, BotJson.name(bot))
                    },
                    tint = colors.textSecondary,
                    modifier = Modifier.size(20.dp),
                )
            }
            crew != null -> Spacer(Modifier.width(40.dp))
        }
        BotFace(bot, avatar)
        Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = BotJson.name(bot),
                    color = colors.textPrimary,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                )
                if (BotJson.isLead(bot)) {
                    Text(
                        text = stringResource(R.string.bots_lead_badge) +
                            (BotJson.crewWorking(bot)?.takeIf { it > 0 }
                                ?.let { stringResource(R.string.bots_lead_crew_count, it) } ?: ""),
                        color = colors.accent,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                if (BotJson.needsYou(bot)) {
                    Text("  ⚠", color = colors.danger, style = MaterialTheme.typography.labelSmall)
                }
            }
            Text(
                // The parent is the tree above now; repeating it here would say it twice.
                // Folded, the row says what is behind it instead of the lead's own state: the crew
                // is the thing the arrow is about.
                text = if (crew != null && !expanded) {
                    pluralStringResource(R.plurals.bots_crew_folded, crew, crew)
                } else {
                    listOf(
                        noticeText(BotJson.stateNotice(BotJson.state(bot))),
                        live.takeIf { it.isNotBlank() },
                    ).filterNotNull().joinToString(" · ")
                },
                color = colors.textSecondary,
                style = MaterialTheme.typography.labelSmall,
            )
        }
        IconButton(onClick = { onOpenChat(id) }) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.Chat,
                contentDescription = stringResource(R.string.bots_cd_conversation_with, BotJson.name(bot)),
                tint = colors.textSecondary,
            )
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
    // Picker callbacks below are not composable scopes: resolve the messages here.
    val unreadableFile = stringResource(R.string.bots_error_unreadable_file)
    val unreadablePhoto = stringResource(R.string.bots_error_unreadable_photo)

    // 1600 px / JPEG q80 like the chat composer: a photo from a 50 MP sensor must not
    // travel at full size, and the server's 8 MB image cap is a guard, not a routine stop.
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        MediaPicker.read(context, uri, maxDimension = 1600)
            .onSuccess { file ->
                val batch = dispatchFiles + file
                val problem = attachmentNotice(Attachments.problemWith(batch))
                if (problem != null) {
                    dispatchFileError = noticeText(context, problem)
                } else {
                    dispatchFiles = batch
                    dispatchFileError = null
                }
            }
            .onFailure { error -> dispatchFileError = error.message ?: unreadableFile }
    }
    var personaText by remember(state.personaLoaded, id) { mutableStateOf(state.persona) }
    var showAddCrew by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
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
            .onFailure { error -> avatarError = error.message ?: unreadablePhoto }
    }

    CollapsibleSection(stringResource(R.string.bots_section_preview), defaultOpen = true) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BotFace(bot, avatars[BotJson.id(bot)])
            Column(modifier = Modifier.padding(start = 10.dp)) {
                Text(BotJson.name(bot), color = colors.textPrimary, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = listOf(
                        noticeText(BotJson.stateNotice(BotJson.state(bot))),
                        BotJson.fleetRole(bot).ifBlank { stringResource(R.string.bots_role_solo) },
                        BotJson.enabled(bot).let { if (it) stringResource(R.string.bots_state_enabled) else stringResource(R.string.bots_state_disabled) },
                    ).joinToString(" · "),
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        if (live.isNotBlank()) KeyValueRow(stringResource(R.string.bots_label_activity), live, highlight = true)
        KeyValueRow(stringResource(R.string.bots_label_last_run), BotJson.lastRunState(bot).ifBlank { "—" })
        BotJson.description(bot).takeIf { it.isNotBlank() }?.let { KeyValueRow(stringResource(R.string.bots_label_role), it) }
        KeyValueRow(stringResource(R.string.bots_label_model), BotJson.model(bot).ifBlank { stringResource(R.string.bots_value_default) })
        // Glyphs, not five identical grey words: the same vocabulary as the conversation
        // screen's top bar. Every label is also the icon's content description.
        IconActionBar(
            listOf(
                QuickAction(
                    icon = if (avatars[id] != null) Icons.Rounded.PhotoCamera else Icons.Rounded.AddAPhoto,
                    label = if (avatars[id] != null) stringResource(R.string.bots_action_photo) else stringResource(R.string.bots_action_photo),
                    onClick = {
                        avatarPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                ),
                QuickAction(
                    icon = Icons.Rounded.PlayArrow,
                    label = stringResource(R.string.bots_action_start),
                    enabled = !state.busy,
                    onClick = { viewModel.start(id) },
                ),
                QuickAction(
                    icon = Icons.Rounded.Stop,
                    label = stringResource(R.string.bots_action_stop),
                    enabled = !state.busy,
                    onClick = { viewModel.stop(id) },
                ),
                QuickAction(
                    icon = Icons.Rounded.Bolt,
                    label = stringResource(R.string.bots_action_run),
                    enabled = !state.busy,
                    onClick = { viewModel.run(id) },
                ),
                QuickAction(
                    icon = Icons.Rounded.PowerSettingsNew,
                    label = if (BotJson.enabled(bot)) stringResource(R.string.bots_action_disable) else stringResource(R.string.bots_action_enable),
                    tint = if (BotJson.enabled(bot)) colors.textPrimary else colors.accent,
                    enabled = !state.busy,
                    onClick = { viewModel.setEnabled(id, !BotJson.enabled(bot)) },
                ),
                QuickAction(
                    icon = Icons.Rounded.Create,
                    label = stringResource(R.string.bots_action_rename),
                    enabled = !state.busy,
                    onClick = { showRename = true },
                ),
                QuickAction(
                    icon = Icons.Rounded.IosShare,
                    label = stringResource(R.string.bots_action_share),
                    enabled = !state.busy,
                    onClick = { viewModel.exportBundle(id) },
                ),
                QuickAction(
                    icon = Icons.Rounded.DeleteOutline,
                    label = stringResource(R.string.bots_action_delete),
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
                        label = stringResource(R.string.bots_action_remove_photo),
                        onClick = { viewModel.removeAvatar(id) },
                    ),
                ),
            )
        }
        avatarError?.let { message ->
            Text(message, color = colors.danger, style = MaterialTheme.typography.labelSmall)
        }
        Text(
            text = stringResource(R.string.bots_bundle_hint),
            style = MaterialTheme.typography.labelSmall,
            color = colors.textSecondary,
            modifier = Modifier.padding(top = 6.dp),
        )
    }

    CollapsibleSection(stringResource(R.string.bots_section_write), defaultOpen = true) {
        Text(
            text = stringResource(R.string.bots_write_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
        )

        // The buttons live inside the field: a task is typed and sent in one gesture. Laid out by
        // hand rather than with OutlinedTextField, because its fixed internal padding left the
        // glyphs touching the left edge and the send chip sitting on the border.
        val canSend = !state.busy && (dispatchText.isNotBlank() || dispatchFiles.isNotEmpty())
        Row(
            // Centred, like the chat composer: the field used to sit on a 44 dp minimum with a 6 dp
            // nudge of its own, so the words rode below glyphs that were bottom-aligned in a shorter
            // row — two baselines in one capsule.
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
                .clip(MaterialTheme.shapes.small)
                .border(1.dp, colors.hairline, MaterialTheme.shapes.small)
                .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 6.dp),
            ) {
                if (dispatchText.isEmpty()) {
                    Text(
                        text = stringResource(R.string.bots_dispatch_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary.copy(alpha = 0.7f),
                    )
                }
                BasicTextField(
                    value = dispatchText,
                    onValueChange = { dispatchText = it },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.textPrimary),
                    cursorBrush = SolidColor(colors.accent),
                    maxLines = 5,
                    // One line's own height: the row's height comes from the glyphs beside it.
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            IconButton(
                onClick = { filePicker.launch(arrayOf("image/*", "application/pdf")) },
                modifier = Modifier.size(ComposerControlSize),
            ) {
                Icon(
                    Icons.Rounded.AttachFile,
                    contentDescription = stringResource(R.string.bots_cd_attach_file),
                    tint = colors.textSecondary,
                    modifier = Modifier.size(ComposerControlIcon),
                )
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(ComposerControlSize)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(if (canSend) colors.accent else colors.surfaceMuted)
                    .clickable(enabled = canSend) {
                        viewModel.dispatch(dispatchText, dispatchFiles)
                        dispatchText = ""
                        dispatchFiles = emptyList()
                        dispatchFileError = null
                    },
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.Send,
                    contentDescription = stringResource(R.string.bots_cd_send_task),
                    tint = if (canSend) colors.canvas else colors.textSecondary,
                    modifier = Modifier.size(ComposerControlIcon),
                )
            }
        }
        if (dispatchFiles.isNotEmpty()) {
            Column {
                dispatchFiles.forEach { file ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "📎 ${file.name}",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textSecondary,
                        )
                        IconButton(onClick = { dispatchFiles = dispatchFiles - file }) {
                            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.bots_cd_remove_attachment, file.name), tint = colors.danger)
                        }
                    }
                }
            }
        }
        dispatchFileError?.let { message ->
            Text(message, color = colors.danger, style = MaterialTheme.typography.labelSmall)
        }
        state.dispatchFeedback?.let { notice ->
            Text(
                text = noticeText(notice),
                style = MaterialTheme.typography.labelSmall,
                // Queued or refused: the severity says which, the wording no longer has to.
                color = noticeTint(notice.severity),
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }

    CollapsibleSection(stringResource(R.string.bots_section_persona)) {
        Text(
            text = stringResource(R.string.bots_persona_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
            // Le champ collait à la phrase : la ligne respire avant le cadre.
            modifier = Modifier.padding(bottom = 12.dp),
        )
        OutlinedTextField(
            value = personaText,
            onValueChange = { personaText = it },
            label = { Text(stringResource(R.string.bots_label_persona)) },
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
                    label = stringResource(R.string.bots_action_save),
                    tint = colors.accent,
                    enabled = !state.busy && personaText.isNotBlank() && personaText != state.persona,
                    onClick = { viewModel.savePersona(personaText) },
                ),
                QuickAction(
                    icon = Icons.Rounded.Undo,
                    label = stringResource(R.string.bots_action_revert),
                    enabled = personaText != state.persona,
                    onClick = { personaText = state.persona },
                ),
            ),
        )
    }

    CollapsibleSection(stringResource(R.string.bots_section_permissions)) {
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
            "readonly" to (stringResource(R.string.bots_tier_read_only) to stringResource(R.string.bots_tier_read_only_verb)),
            "builder" to (stringResource(R.string.bots_tier_write) to stringResource(R.string.bots_tier_write_verb)),
            "operator" to (stringResource(R.string.bots_tier_operator) to stringResource(R.string.bots_tier_operator_verb)),
            "full" to (stringResource(R.string.bots_tier_full) to stringResource(R.string.bots_tier_full_verb)),
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
                        .clip(MaterialTheme.shapes.extraSmall)
                        .background(if (selected) colors.accent.copy(alpha = 0.16f) else Color.Transparent)
                        .then(
                            if (selected) Modifier
                            else Modifier.border(1.dp, colors.hairlineStrong, MaterialTheme.shapes.extraSmall),
                        )
                        // Tapping the active tier takes it back: a choice you cannot undo is not
                        // a choice. It returns the bot to the daemon's default document, which
                        // belongs to no tier — hence no cell lit, which is a real state.
                        .clickable {
                            if (tier == tierId) viewModel.clearTier(id) else viewModel.setTier(id, tierId)
                        }
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

    CollapsibleSection(stringResource(R.string.bots_section_model_budget)) {
        // A list of what the instance actually offers, not a free field: the daemon stores
        // { provider, model } and a typo used to fall back to the default in silence.
        val pickedProvider = BotJson.modelProvider(bot)
        val pickedModel = BotJson.modelName(bot)
        // Exact match first: several models can share a provider, and the first one listed was
        // not necessarily the one in use.
        val currentLabel = state.models
            .firstOrNull { ModelRefs.same(pickedProvider, pickedModel, it.name, it.model) }
            ?.displayLabel
            ?: pickedModel
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.extraSmall)
                .background(colors.surfaceMuted)
                .clickable { viewModel.loadModels(); showModelPicker = true }
                .padding(horizontal = 14.dp, vertical = 13.dp),
        ) {
            Icon(Icons.Rounded.Memory, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(18.dp))
            Text(
                text = stringResource(R.string.bots_label_model),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f).padding(start = 12.dp),
            )
            Text(
                text = currentLabel.ifBlank { stringResource(R.string.bots_value_default) },
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
                label = { Text(stringResource(R.string.bots_label_daily_budget)) },
                placeholder = { Text(stringResource(R.string.bots_budget_unlimited)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = { budgetText.trim().toIntOrNull()?.let { viewModel.setDailyBudget(id, it) } },
                ),
                shape = MaterialTheme.shapes.extraSmall,
                trailingIcon = {
                    if (budgetText.isNotBlank()) {
                        IconButton(onClick = { budgetText.trim().toIntOrNull()?.let { viewModel.setDailyBudget(id, it) } }) {
                            Icon(Icons.Rounded.Check, contentDescription = stringResource(R.string.bots_cd_apply_budget), tint = colors.accent)
                        }
                    }
                },
                modifier = Modifier.weight(1f),
            )
        }

        // The three limits a turn dies of. The defaults are placeholders, not values: an empty
        // field means the daemon's own, and the sheet says so rather than inventing a number.
        LimitField(
            label = stringResource(R.string.bots_label_max_steps),
            hint = stringResource(R.string.bots_limit_steps_hint),
            value = BotAutonomy.value(bot, BotAutonomy.STEPS),
            onApply = { viewModel.setAutonomyLimit(id, BotAutonomy.STEPS, it) },
        )
        LimitField(
            label = stringResource(R.string.bots_label_turn_minutes),
            hint = stringResource(R.string.bots_limit_minutes_hint),
            value = BotAutonomy.value(bot, BotAutonomy.TURN_MINUTES),
            onApply = { viewModel.setAutonomyLimit(id, BotAutonomy.TURN_MINUTES, it) },
        )
        LimitField(
            label = stringResource(R.string.bots_label_turn_tokens),
            hint = stringResource(R.string.bots_limit_tokens_hint),
            value = BotAutonomy.value(bot, BotAutonomy.TURN_TOKENS),
            onApply = { viewModel.setAutonomyLimit(id, BotAutonomy.TURN_TOKENS, it) },
        )
    }

    CollapsibleSection(stringResource(R.string.bots_section_fleet)) {
        val crew = state.crew
        KeyValueRow(stringResource(R.string.bots_label_role), if (BotJson.isLead(bot)) stringResource(R.string.bots_role_fleet_lead) else BotJson.fleetRole(bot).ifBlank { stringResource(R.string.bots_role_solo) })
        crew?.crew?.let { members ->
            KeyValueRow(stringResource(R.string.bots_label_members), stringResource(R.string.bots_members_count, members.size, crew.maxCrew))
            members.forEach { member ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    BotFace(member, avatars[BotJson.id(member)], small = true)
                    Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                        Text(BotJson.name(member), color = colors.textPrimary, style = MaterialTheme.typography.bodyMedium)
                        val memberLive = watch.activity[BotJson.id(member)].orEmpty().ifBlank { BotJson.activity(member) }
                        Text(
                            text = listOf(noticeText(BotJson.stateNotice(BotJson.state(member))), memberLive.takeIf { it.isNotBlank() })
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
                            contentDescription = stringResource(R.string.bots_cd_remove_from_fleet, BotJson.name(member)),
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
                text = stringResource(R.string.bots_member_of_fleet, BotJson.parent(bot)),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
            IconActionBar(
                listOf(
                    QuickAction(
                        icon = Icons.Rounded.PersonRemove,
                        label = stringResource(R.string.bots_action_leave),
                        tint = colors.danger,
                        onClick = { viewModel.detachCrew(BotJson.parent(bot), id) },
                    ),
                ),
            )
        }
        IconActionBar(
            listOf(
                QuickAction(icon = Icons.Rounded.Star, label = stringResource(R.string.bots_action_promote), onClick = { viewModel.promote(id) }),
                QuickAction(icon = Icons.Rounded.AutoAwesome, label = stringResource(R.string.bots_action_auto), onClick = { viewModel.autoCrew(id) }),
                QuickAction(icon = Icons.Rounded.PersonAddAlt, label = stringResource(R.string.bots_action_member), onClick = { showAddCrew = true }),
            ),
        )
    }

    if (state.tasks.isNotEmpty()) {
        CollapsibleSection(stringResource(R.string.bots_section_delegated_tasks)) {
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
        CollapsibleSection(stringResource(R.string.bots_section_mailbox)) {
            state.inbox.forEach { mail ->
                KeyValueRow(
                    (mail["from"] as? JsonPrimitive)?.contentOrNull.orEmpty().ifBlank { stringResource(R.string.bots_label_message) },
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
        title = stringResource(R.string.bots_section_deliverables),
        outputs = state.outputs,
        busy = state.busy,
        emptyText = stringResource(R.string.bots_empty_deliverables),
        onPreview = viewModel::previewOutput,
        onShare = viewModel::shareOutput,
        onDelete = viewModel::deleteOutput,
    )

    RunsSection(
        runs = state.runs,
        emptyText = stringResource(R.string.bots_empty_runs),
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
    if (showRename) {
        val current = BotJson.name(bot)
        var draft by remember(id, showRename) { mutableStateOf(current) }
        AlertDialog(
            onDismissRequest = { showRename = false },
            containerColor = colors.surface,
            title = { Text(stringResource(R.string.bots_rename_title), color = colors.textPrimary) },
            text = {
                Column {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        label = { Text(stringResource(R.string.bots_rename_field)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    // The one thing a rename must not be mistaken for: the id, the folders and the
                    // journal entries keep pointing at the bot whoever it is called.
                    Text(
                        text = stringResource(R.string.bots_rename_id_notice, BotJson.id(bot)),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = draft.isNotBlank() && !state.busy,
                    onClick = { showRename = false; viewModel.rename(id, draft) },
                ) {
                    Text(stringResource(R.string.bots_rename_confirm), color = colors.accent)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRename = false }) {
                    Text(stringResource(R.string.bots_rename_cancel), color = colors.textSecondary)
                }
            },
        )
    }
    if (showModelPicker) {
        // The provider alone is not a key: two Qwen models share `openaiCompat`, and matching on
        // the provider ticked both of them at once.
        val picked = BotJson.modelProvider(bot)
        val pickedM = BotJson.modelName(bot)
        AlertDialog(
            onDismissRequest = { showModelPicker = false },
            containerColor = colors.surface,
            title = { Text(stringResource(R.string.bots_model_dialog_title), color = colors.textPrimary) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    // "Default" is a real choice, not the absence of one: it removes the key so
                    // the bot follows whatever the instance is set to.
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.extraSmall)
                            .clickable { showModelPicker = false; viewModel.clearModel(id) }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.bots_model_instance_default),
                            style = MaterialTheme.typography.bodyLarge,
                            color = colors.textPrimary,
                            modifier = Modifier.weight(1f),
                        )
                        if (picked.isBlank() && pickedM.isBlank()) {
                            Icon(Icons.Rounded.Check, contentDescription = stringResource(R.string.bots_cd_selected), tint = colors.accent, modifier = Modifier.size(18.dp))
                        }
                    }
                    HorizontalDivider(color = colors.hairline)
                    if (state.models.isEmpty()) {
                        Text(
                            text = stringResource(R.string.bots_empty_models),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textSecondary,
                            modifier = Modifier.padding(vertical = 10.dp),
                        )
                    }
                    state.models.forEach { ref ->
                        val selected = ModelRefs.same(picked, pickedM, ref.name, ref.model)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.extraSmall)
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
                                Icon(Icons.Rounded.Check, contentDescription = stringResource(R.string.bots_cd_selected), tint = colors.accent, modifier = Modifier.size(18.dp))
                            } else if (!ref.available) {
                                Text(stringResource(R.string.bots_model_no_key), style = MaterialTheme.typography.labelSmall, color = colors.danger)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showModelPicker = false }) { Text(stringResource(R.string.bots_action_close)) } },
        )
    }
    if (showNewCrew) {
        NewBotDialog(
            busy = state.busy,
            title = stringResource(R.string.bots_new_member_title),
            submitLabel = stringResource(R.string.bots_action_add),
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
    val colors = LocalMercuryColors.current
    Box {
        BotFaceCore(bot, avatar, small)
        // A lead carries a crown on its own avatar: the roster tells a chief from a bot standing
        // alone at a glance, without reading a badge next to the name (§roster).
        if (BotJson.isLead(bot)) {
            val badge = if (small) 12.dp else 16.dp
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(badge)
                    .background(colors.surface, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.WorkspacePremium,
                    contentDescription = stringResource(R.string.bots_cd_lead),
                    tint = colors.accent,
                    modifier = Modifier.size(badge - 3.dp),
                )
            }
        }
    }
}

@Composable
private fun BotFaceCore(bot: JsonObject, avatar: ByteArray?, small: Boolean = false) {
    val size = if (small) 26.dp else 38.dp
    if (avatar != null) {
        val bitmap = remember(avatar) {
            BitmapFactory.decodeByteArray(avatar, 0, avatar.size)?.asImageBitmap()
        }
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = stringResource(R.string.bots_cd_photo_of, BotJson.name(bot)),
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
        title = { Text(stringResource(R.string.bots_add_member_title), color = colors.textPrimary) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = stringResource(R.string.bots_add_member_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
                if (adoptable.isEmpty()) {
                    Text(
                        text = stringResource(R.string.bots_add_member_empty),
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
                            .clip(MaterialTheme.shapes.extraSmall)
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
                                text = noticeText(BotJson.stateNotice(BotJson.state(candidate))),
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textSecondary,
                            )
                        }
                        Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.bots_cd_add_bot, BotJson.name(candidate)), tint = colors.accent)
                    }
                }
                HorizontalDivider(color = colors.hairline, modifier = Modifier.padding(vertical = 10.dp))
                // One form for both paths: the roster's "+" and this sheet open the same dialog,
                // so a field added later is added once.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.extraSmall)
                        .clickable { onCreateNew() }
                        .padding(vertical = 8.dp, horizontal = 4.dp),
                ) {
                    Text(
                        text = stringResource(R.string.bots_action_create_bot),
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.accent,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.bots_action_create_bot), tint = colors.accent)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.bots_action_close)) } },
    )
}

/**
 * One numeric limit of a bot.
 *
 * The field shows what the manifest holds; an empty field means "the daemon's default", which is
 * what the placeholder names. Applying an empty field **removes** the key — that is how a bot goes
 * back to the default without anyone having to remember what the default was.
 */
@Composable
private fun LimitField(
    label: String,
    hint: String,
    value: Int?,
    onApply: (Int?) -> Unit,
) {
    val colors = LocalMercuryColors.current
    var text by remember(value) { mutableStateOf(value?.toString().orEmpty()) }
    val apply = { onApply(text.trim().toIntOrNull()) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { typed -> text = typed.filter { it.isDigit() } },
            label = { Text(label) },
            placeholder = { Text(hint) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { apply() }),
            shape = MaterialTheme.shapes.extraSmall,
            trailingIcon = {
                if (text != value?.toString().orEmpty()) {
                    IconButton(onClick = { apply() }) {
                        Icon(Icons.Rounded.Check, contentDescription = label, tint = colors.accent)
                    }
                }
            },
            modifier = Modifier.weight(1f),
        )
    }
}
