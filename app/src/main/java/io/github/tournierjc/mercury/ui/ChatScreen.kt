package io.github.tournierjc.mercury.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.os.SystemClock
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.toMutableStateList
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import io.github.tournierjc.mercury.MercuryApp
import io.github.tournierjc.mercury.bots.AppVisibility
import io.github.tournierjc.mercury.bots.BotThread
import io.github.tournierjc.mercury.data.Notice
import io.github.tournierjc.mercury.data.BotJson
import io.github.tournierjc.mercury.data.ChatMessage
import io.github.tournierjc.mercury.vm.BotsViewModel
import io.github.tournierjc.mercury.data.ChatState
import io.github.tournierjc.mercury.data.CodeMode
import io.github.tournierjc.mercury.data.PendingFile
import io.github.tournierjc.mercury.data.QuoteReply
import io.github.tournierjc.mercury.data.RelativeAge
import io.github.tournierjc.mercury.data.isAtBottom
import io.github.tournierjc.mercury.data.relativeAge
import io.github.tournierjc.mercury.data.ThreadDto
import io.github.tournierjc.mercury.util.MediaPicker
import io.github.tournierjc.mercury.ui.theme.LocalMercuryColors
import io.github.tournierjc.mercury.R
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.launch
import java.util.Locale




/**
 * The history survives process death, so a phone that reclaims the app does not silently reset the
 * path — but an entry naming a screen this build no longer has is dropped rather than thrown.
 */
private val navHistorySaver = Saver<SnapshotStateList<NavStep>, List<String>>(
    save = { steps -> steps.map { "${it.screen.name}\u0001${it.conversation.orEmpty()}\u0001${it.drawerOpen}" } },
    restore = { saved ->
        saved.mapNotNull { line ->
            val parts = line.split('\u0001')
            val screen = runCatching { AppScreen.valueOf(parts[0]) }.getOrNull() ?: return@mapNotNull null
            NavStep(screen, parts.getOrNull(1)?.ifBlank { null }, parts.getOrNull(2) == "true")
        }.toMutableStateList()
    },
)


@Composable
fun ChatScreen(
    state: ChatState,
    threads: List<ThreadDto>,
    onSend: (String) -> Unit,
    attachments: List<PendingFile>,
    onPickAttachments: (List<PendingFile>) -> Unit,
    onRemoveAttachment: (Int) -> Unit,
    onPermission: (String, String) -> Unit,
    onNewChat: () -> Unit,
    onOpenThread: (ThreadDto) -> Unit,
    onDeleteThread: (ThreadDto) -> Unit,
    onSignOut: () -> Unit,
    /** Réglages can point the app at another host; the conversation is over once it does. */
    onChangeServer: (String) -> Unit = {},
    onDismissError: () -> Unit,
    onDismissWarning: () -> Unit,
    /** Bot named by the notification the owner tapped, opened once the screen is up. */
    openBotId: String? = null,
    /** Bumped on every tap of a notification, so tapping the same bot twice still arrives. */
    openBotNonce: Int = 0,
    onOpenBotConsumed: () -> Unit = {},
    /** True while the first threads/fleet round-trip is in flight (see ChatViewModel.syncing). */
    syncing: Boolean = false,
    /** The programming mode the daemon is in, drawn above the field. */
    codeMode: CodeMode = CodeMode.Off,
    codeModeAvailable: Boolean = false,
    onCodeMode: (CodeMode) -> Unit = {},
) {
    val colors = LocalMercuryColors.current
    var screen by rememberSaveable { mutableStateOf(AppScreen.Chat) }
    var botToOpen by remember { mutableStateOf<String?>(null) }
    // Seeded from the notification, not set afterwards: the Mercury conversation used to be the
    // first frame of a cold start, so tapping a notification showed it, then jumped.
    var botChat by rememberSaveable { mutableStateOf(openBotId) }
    val context = LocalContext.current
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    // Leaving the app takes two presses; the first says so.
    val exitGate = remember { ExitGate() }
    val exitPrompt = stringResource(R.string.chat_exit_prompt)
    val activity = remember(context) {
        generateSequence(context as Context?) { (it as? ContextWrapper)?.baseContext }
            .filterIsInstance<Activity>()
            .firstOrNull()
    }

    // Where the owner has been. Back used to send every screen home to the conversation, so the
    // second screen deep in a corner of the app was unreachable again: one press and the path was
    // gone. The history remembers the screen, the conversation and the sheet, one entry per move.
    val history = rememberSaveable(saver = navHistorySaver) { mutableStateListOf<NavStep>() }

    /** Leaves the current place for [to], remembering the one being left. */
    fun go(to: AppScreen, conversation: String? = null) {
        history.add(NavStep(screen = screen, conversation = botChat, drawerOpen = drawerState.isOpen))
        screen = to
        botChat = conversation
        botToOpen = null
        if (drawerState.isOpen) scope.launch { drawerState.close() }
    }

    /** Opens [id]'s conversation as a step of its own, from wherever the owner is. */
    fun openConversation(id: String) = go(AppScreen.Chat, conversation = id)

    /** Opens [id]'s settings page, the bot itself already open on it. */
    fun openConfig(id: String) {
        go(AppScreen.Bots)
        botToOpen = id
    }

    /**
     * One step back, in the order a thumb expects: the sheet first, because it is on top, then the
     * place the owner came from — restored exactly as it was left, sheet included. A screen with
     * inner depth (the workspace's folders, a preview) registers its own Back handler deeper in the
     * tree, so it undoes that first.
     */
    fun goBack() {
        when (val action = backAction(drawerState.isOpen, history, botChat)) {
            BackAction.CloseDrawer -> scope.launch { drawerState.close() }
            is BackAction.Restore -> {
                history.removeAt(history.lastIndex)
                val step = action.step
                screen = step.screen
                botChat = step.conversation
                // Coming back to the roster reopens the bot whose page was left: the same place,
                // not the top of the list.
                botToOpen = if (step.screen == AppScreen.Bots) step.conversation else null
                if (step.drawerOpen) scope.launch { drawerState.open() }
            }
            BackAction.CloseConversation -> botChat = null
            // Nothing to close. Leaving takes two presses — the first asks, the second leaves.
            BackAction.LeaveApp -> {
                if (exitGate.press(SystemClock.elapsedRealtime())) {
                    activity?.finish()
                } else {
                    Toast.makeText(context, exitPrompt, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Enabled only while there is something to close or somewhere to go back to: on the
    // conversation with nothing open, Back still leaves the app.
    // Always enabled now: at the door of the app the press is ours to answer, twice.
    BackHandler(enabled = true) { goBack() }

    // A tapped notification is a destination: "Bricoleur a terminé son travail" queues its
    // bot id, and the app lands on that bot instead of the last conversation.
    LaunchedEffect(openBotId, openBotNonce) {
        if (openBotId != null) {
            // Straight into the conversation: that is where the work it announced lives. The
            // nonce makes a second tap on the same bot a new event rather than a no-op.
            if (botChat != openBotId) botChat = openBotId
            // A notification is a new destination, not a step in the path the owner was walking.
            history.clear()
            screen = AppScreen.Chat
            onOpenBotConsumed()
        }
    }


    when (screen) {
        AppScreen.Bridge -> {
            BridgeScreen(onBack = ::goBack)
            return
        }

        AppScreen.Settings -> {
            SettingsScreen(onBack = ::goBack, onChangeServer = onChangeServer)
            return
        }

        AppScreen.Status -> {
            StatusScreen(onBack = ::goBack)
            return
        }

        // The screens the app already had, kept reachable: the second brain's memory, the boards,
        // the roster, the share sheet, and the console — the webapp folded the console's counters
        // into Status and Usage, the app still owns the terminal view.
        AppScreen.Memory -> {
            MemoryScreen(onBack = ::goBack)
            return
        }

        AppScreen.Boards -> {
            BoardsScreen(onBack = ::goBack)
            return
        }

        AppScreen.Bots -> {
            BotsScreen(
                onBack = ::goBack,
                initialBotId = botToOpen,
                onOpened = { botToOpen = null },
                // The sheet hands over to the conversation, and back comes home to the sheet:
                // a single step each way, instead of landing in Mercury's screen and having to
                // find the teammate again.
                onOpenChat = { id -> openConversation(id) },
            )
            return
        }

        AppScreen.Deliverables -> {
            DeliverablesScreen(onBack = ::goBack)
            return
        }

        AppScreen.Console -> {
            ConsoleScreen(onBack = ::goBack)
            return
        }

        AppScreen.Tasks -> {
            TasksScreen(onBack = ::goBack)
            return
        }

        AppScreen.Workspace -> {
            WorkspaceScreen(onBack = ::goBack)
            return
        }

        AppScreen.Persons -> {
            PersonsScreen(onBack = ::goBack)
            return
        }

        AppScreen.Goals -> {
            GoalsScreen(onBack = ::goBack)
            return
        }

        AppScreen.Graph -> {
            GraphScreen(onBack = ::goBack)
            return
        }

        AppScreen.Providers -> {
            ProvidersScreen(onBack = ::goBack)
            return
        }

        AppScreen.Skills -> {
            SkillsScreen(onBack = ::goBack)
            return
        }

        AppScreen.Mcp -> {
            McpScreen(onBack = ::goBack)
            return
        }

        AppScreen.Plugins -> {
            PluginsScreen(onBack = ::goBack)
            return
        }

        AppScreen.Permissions -> {
            PermissionsScreen(onBack = ::goBack)
            return
        }

        AppScreen.Schedules -> {
            SchedulesScreen(onBack = ::goBack)
            return
        }

        AppScreen.Usage -> {
            UsageScreen(onBack = ::goBack)
            return
        }

        AppScreen.Chat -> Unit
    }

    // One conversation, two correspondents: Mercury, or a teammate picked in the drawer.
    val container = remember { (context.applicationContext as MercuryApp).container }
    val botsVm: BotsViewModel = viewModel(factory = BotsViewModel.factory(container))
    val botsState by botsVm.state.collectAsState()
    val botWatch by container.botWatcher.state.collectAsState()
    val botAvatars by botsVm.avatars.collectAsState()
    LaunchedEffect(botWatch.bots) { botsVm.loadAvatars(botWatch.bots) }
    LaunchedEffect(botChat) { botChat?.let { botsVm.select(it) } }

    var composerText by rememberSaveable { mutableStateOf("") }
    // The message being answered, if any: the composer shows it as a chip and the send prepends
    // it as a blockquote, which is how the quote reaches the daemon and survives a reload.
    var replyTo by remember { mutableStateOf<ChatMessage?>(null) }
    /** Files waiting to be sent to the bot on screen. */
    var botFiles by remember { mutableStateOf<List<PendingFile>>(emptyList()) }
    LaunchedEffect(botChat) {
        replyTo = null
        // Files belong to the correspondent they were picked for.
        botFiles = emptyList()
    }
    var voiceError by remember { mutableStateOf<String?>(null) }
    var attachmentError by remember { mutableStateOf<String?>(null) }

    // Photos and PDFs: the system picker needs no storage permission, and a picked
    // file is read once, downscaled, and reduced to bytes — never copied to storage.
    val attachmentUnreadable = stringResource(R.string.chat_attachment_unreadable)
    val attachmentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        val files = uris.mapNotNull { uri ->
            MediaPicker.read(context, uri).getOrElse { error ->
                attachmentError = error.message ?: attachmentUnreadable
                null
            }
        }
        if (files.isNotEmpty()) {
            // A bot's conversation carries its own files: dropping a photo into Mercury's thread
            // because the bot was on screen would send it to the wrong correspondent.
            if (botChat != null) botFiles = (botFiles + files).take(MAX_BOT_FILES) else onPickAttachments(files)
        }
    }

    // Read once, in the composable: the launcher callbacks are not composable scopes.
    val voicePrompt = stringResource(R.string.chat_title)
    val voiceUnavailable = stringResource(R.string.chat_voice_unavailable)
    val micDenied = stringResource(R.string.chat_mic_denied)

    val speechLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                composerText = listOf(composerText, spoken).filter { it.isNotBlank() }.joinToString(" ")
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            runCatching { speechLauncher.launch(speechIntent(voicePrompt)) }
                .onFailure { voiceError = voiceUnavailable }
        } else {
            voiceError = micDenied
        }
    }

    fun startVoice() {
        voiceError = null
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            runCatching { speechLauncher.launch(speechIntent(voicePrompt)) }
                .onFailure { voiceError = voiceUnavailable }
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ThreadDrawer(
                threads = threads,
                currentSessionId = state.sessionId,
                onNewChat = {
                    go(AppScreen.Chat)
                    onNewChat()
                },
                onOpenThread = { thread ->
                    go(AppScreen.Chat)
                    onOpenThread(thread)
                },
                current = if (botChat != null) AppScreen.Chat else screen,
                onOpen = { destination -> go(destination) },
                bots = botWatch.bots,
                avatars = botAvatars,
                activeBotId = botChat,
                onOpenBot = { id -> openConversation(id) },
                // A long press on a name goes to the page that changes it, not to the one that talks
                // to it — and the sheet hands over to Équipiers with that bot already open.
                onOpenBotConfig = { id -> openConfig(id) },
                onDeleteThread = { thread ->
                    onDeleteThread(thread)
                    scope.launch { drawerState.close() }
                },
                onSignOut = {
                    onSignOut()
                    scope.launch { drawerState.close() }
                },
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.canvas)
                .safeDrawingPadding()
                .imePadding(),
        ) {
            val botId = botChat
            // The screen knows which teammate it is showing; the notifier needs the same fact to
            // stay quiet about a message that is already on screen.
            LaunchedEffect(botId) { AppVisibility.openBotId = botId }
            DisposableEffect(Unit) { onDispose { AppVisibility.openBotId = null } }
            val bot = botId?.let { id -> botWatch.bots.firstOrNull { BotJson.id(it) == id } }
            val botLive = botId?.let { botWatch.live[it] }
            val botName = bot?.let { BotJson.name(it) } ?: botId.orEmpty()
            // Read in the composable: the list below is built inside remember { }, where a
            // resource lookup is not allowed.
            val queuedLabel = stringResource(R.string.chat_bot_queued)
            val failedLabel = stringResource(R.string.chat_bot_failed)
            val noAnswerLabel = stringResource(R.string.chat_bot_no_answer)
            val botMessages = remember(botsState.records, botsState.prompts, botsState.answers, botsState.pending, botLive, botId, queuedLabel, failedLabel, noAnswerLabel) {
                if (botId == null) {
                    emptyList()
                } else {
                    val built = BotThread.build(
                        botsState.records,
                        botsState.prompts,
                        botsState.answers,
                        botLive?.jobId,
                        botLive?.text.orEmpty(),
                        botLive?.running == true,
                    )
                    val known = built.filter { !it.fromBot }.map { it.text }.toSet()
                    (built + botsState.pending.filter { it.text !in known }).map { message ->
                        ChatMessage(
                            id = message.key,
                            role = if (message.fromBot) ChatMessage.Role.Assistant else ChatMessage.Role.User,
                            text = message.text.ifBlank {
                                when {
                                    message.streaming -> "…"
                                    message.state == "queued" -> queuedLabel
                                    message.state == "failed" -> failedLabel
                                    else -> noAnswerLabel
                                }
                            },
                            streaming = message.streaming,
                            truncated = message.truncated,
                        )
                    }
                }
            }
            var showBotDeliverables by remember { mutableStateOf(false) }

            if (botId != null) {
                TopBar(
                    title = botName,
                    subtitle = listOfNotNull(
                        bot?.let { noticeText(BotJson.stateNotice(BotJson.state(it))) },
                        botLive?.takeIf { it.running }?.let { stringResource(R.string.chat_bot_writing) } ?: botWatch.activity[botId],
                    ).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { stringResource(R.string.chat_bot_idle) },
                    onMenu = { scope.launch { drawerState.open() } },
                    onNewChat = null,
                    syncing = syncing,
                    avatar = { if (bot != null) BotFace(bot, botAvatars[botId], small = true) },
                    actions = {
                        IconButton(onClick = { showBotDeliverables = true }) {
                            Icon(
                                imageVector = Icons.Rounded.FolderOpen,
                                contentDescription = stringResource(R.string.chat_bot_deliverables, botName),
                                tint = if (botsState.outputs.isEmpty()) colors.textSecondary else colors.accent,
                            )
                        }
                        IconButton(onClick = { openConfig(botId) }) {
                            Icon(Icons.Rounded.MoreHoriz, contentDescription = stringResource(R.string.chat_bot_settings, botName), tint = colors.textSecondary)
                        }
                    },
                )
                Box(modifier = Modifier.weight(1f)) {
                    if (botMessages.isEmpty() && botsState.detailLoading) {
                        // Arriving from a notification: the transcript takes a beat, and the
                        // welcome screen used to flash in that gap as if the conversation were
                        // empty.
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = colors.accent,
                                strokeWidth = 2.dp,
                            )
                        }
                    } else if (botMessages.isEmpty()) {
                        BotWelcome(botName) { prompt -> composerText = ""; botsVm.dispatch(prompt, chat = true) }
                    } else {
                        MessageList(
                            botMessages,
                            onReply = { replyTo = it },
                            // A bot conversation is its own list: switching to it from the
                            // roster must not animate through Mercury's messages.
                            conversationKey = "bot:$botId",
                            onLoadFull = { key -> botsVm.loadAnswer(key.substringBeforeLast("-bot")) },
                        )
                    }
                }
                botsState.error?.let { notice -> ErrorBar(notice = notice, onDismiss = { botsVm.clearBanner() }) }
                botsState.dispatchFeedback?.let { notice ->
                    Text(
                        text = noticeText(notice),
                        style = MaterialTheme.typography.labelSmall,
                        // The severity carries the colour: no more guessing from a leading word.
                        color = noticeTint(notice.severity),
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                    )
                }
                AttachmentStrip(attachments = botFiles, onRemove = { index -> botFiles = botFiles.filterIndexed { i, _ -> i != index } })
                replyTo?.let { quoted -> ReplyChip(quoted) { replyTo = null } }
                Composer(
                    value = composerText,
                    onValueChange = { composerText = it },
                    onSend = {
                        val text = composerText.trim()
                        val files = botFiles
                        if (text.isNotEmpty() || files.isNotEmpty()) {
                            composerText = ""
                            botFiles = emptyList()
                            val payload = replyTo?.let { QuoteReply.compose(it.text, text) } ?: text
                            replyTo = null
                            // Chat mode: the bot answers, it does not file a work report.
                            botsVm.dispatch(payload, files, chat = true)
                        }
                    },
                    onVoice = { startVoice() },
                    onAttach = { attachmentPicker.launch(arrayOf("image/*", "application/pdf")) },
                    attachmentCount = botFiles.size,
                    busy = botsState.busy,
                    hint = stringResource(R.string.chat_bot_write, botName),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                )
                if (showBotDeliverables && botId != null) {
                    LaunchedEffect(showBotDeliverables) { botsVm.reloadOutputs(botId) }
                    BotDeliverablesSheet(
                        botId = botId,
                        botName = botName,
                        outputs = botsState.outputs,
                        busy = botsState.busy,
                        onPreview = botsVm::previewOutput,
                        onShare = botsVm::shareOutput,
                        onDelete = botsVm::deleteOutput,
                        onReload = { botsVm.reloadOutputs(botId) },
                        onDismiss = { showBotDeliverables = false },
                    )
                }
                BotOutputPreviewHost(botsState, botsVm)
            } else {
            TopBar(
                title = state.title.ifBlank { "Mercury" },
                subtitle = listOf(state.provider, state.model).filter { it.isNotBlank() }.joinToString(" · "),
                onMenu = { scope.launch { drawerState.open() } },
                onNewChat = onNewChat,
                syncing = syncing,
            )

            Box(modifier = Modifier.weight(1f)) {
                if (state.isWelcome) {
                    Welcome(
                        onPick = { text ->
                            composerText = ""
                            onSend(text)
                        },
                    )
                } else {
                    MessageList(state.messages, onReply = { replyTo = it }, conversationKey = "mercury")
                }
            }

            state.error?.let { notice -> ErrorBar(notice = notice, onDismiss = onDismissError) }
            attachmentError?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.danger,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                )
            }
            if (voiceError != null) {
                Text(
                    text = voiceError.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.danger,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                )
            }
            state.warning?.let { notice ->
                Box(modifier = Modifier.padding(horizontal = 14.dp)) {
                    // A warning used to be painted as an info line (`isError = false`, always).
                    NoticeBanner(notice, onDismiss = onDismissWarning)
                }
            }
            state.interaction?.let { interaction ->
                Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
                    InteractionCard(
                        interaction = interaction,
                        onAction = { action -> onPermission(interaction.id, action) },
                    )
                }
            }

            AttachmentStrip(attachments = attachments, onRemove = onRemoveAttachment)

            // The programming mode, where the next message is typed: it changes what the agent does
            // with that message, so it belongs above the field and not in the console.
            CodeModeBar(
                mode = codeMode,
                available = codeModeAvailable,
                onPick = onCodeMode,
            )

            replyTo?.let { quoted -> ReplyChip(quoted) { replyTo = null } }
            Composer(
                value = composerText,
                onValueChange = { composerText = it },
                onSend = {
                    val text = composerText
                    if (text.isNotBlank() || attachments.isNotEmpty()) {
                        composerText = ""
                        val payload = if (replyTo != null) {
                            QuoteReply.compose(replyTo!!.text, text)
                        } else {
                            text
                        }
                        replyTo = null
                        onSend(payload)
                    }
                },
                onVoice = { startVoice() },
                onAttach = { attachmentPicker.launch(arrayOf("image/*", "application/pdf")) },
                attachmentCount = attachments.size,
                busy = state.streaming,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            )
            }
        }
    }
}

private fun speechIntent(prompt: String): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
    putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
}

@Composable
internal fun TopBar(
    title: String,
    subtitle: String,
    onMenu: () -> Unit,
    /** The main conversation offers a new session; a bot's conversation does not. */
    onNewChat: (() -> Unit)? = null,
    /** Left of the title, when the conversation is with someone in particular. */
    avatar: (@Composable () -> Unit)? = null,
    /** Right of the title: the actions of the current conversation. */
    actions: (@Composable androidx.compose.foundation.layout.RowScope.() -> Unit)? = null,
    /** Draws the startup sync line under the title while the first round-trip is in flight. */
    syncing: Boolean = false,
) {
    val colors = LocalMercuryColors.current
    Column(modifier = Modifier.fillMaxWidth()) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onMenu) {
            Icon(Icons.Rounded.Menu, contentDescription = stringResource(R.string.chat_subtitle), tint = colors.textPrimary)
        }
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (avatar != null) {
                Box(modifier = Modifier.padding(end = 8.dp)) { avatar() }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        when {
            actions != null -> actions()
            onNewChat != null -> IconButton(onClick = onNewChat) {
                Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.chat_new_session), tint = colors.textPrimary)
            }
            else -> Box(modifier = Modifier.padding(horizontal = 24.dp))
        }
    }
        SyncLine(visible = syncing)
    }
}

/**
 * The startup sync, as a line that fills and empties under the title. It is the only animation in
 * the app on purpose: it marks the one moment where the screen is telling the truth about not
 * knowing yet, and it leaves on its own once the first round-trip lands.
 */
@Composable
private fun SyncLine(visible: Boolean) {
    val colors = LocalMercuryColors.current
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        LinearProgressIndicator(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp),
            color = colors.accent,
            trackColor = colors.hairline,
        )
    }
}

/** The empty state of a teammate's conversation: what to say, and what it will cost. */
@Composable
private fun BotWelcome(name: String, onPick: (String) -> Unit) {
    val colors = LocalMercuryColors.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            text = name.ifBlank { stringResource(R.string.chat_teammate) },
            style = MaterialTheme.typography.displaySmall,
            color = colors.textPrimary,
        )
        Text(
            text = stringResource(R.string.chat_team_intro),
            style = MaterialTheme.typography.bodyLarge,
            color = colors.textSecondary,
            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
        )
        listOf(
            stringResource(R.string.chat_suggestion_spark),
            stringResource(R.string.chat_suggestion_fleet),
            stringResource(R.string.chat_suggestion_notes),
        ).forEach { prompt ->
            Text(
                text = prompt,
                style = MaterialTheme.typography.bodyLarge,
                color = colors.textPrimary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(colors.surface)
                    .clickable { onPick(prompt) }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            )
        }
    }
}

@Composable
private fun Welcome(onPick: (String) -> Unit) {
    val colors = LocalMercuryColors.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            text = "Mercury",
            style = MaterialTheme.typography.displayLarge,
            color = colors.textPrimary,
        )
        Text(
            text = stringResource(R.string.chat_welcome),
            style = MaterialTheme.typography.bodyLarge,
            color = colors.textSecondary,
            modifier = Modifier.padding(top = 8.dp, bottom = 28.dp),
        )
        listOf(
            stringResource(R.string.chat_suggestion_spark),
            stringResource(R.string.chat_suggestion_session),
            stringResource(R.string.chat_suggestion_test),
        ).forEach { suggestion ->
            // The hint says "appuie sur une suggestion" — it has to be clickable to be true.
            Row(
                modifier = Modifier
                    .padding(bottom = 8.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(colors.surface)
                    .clickable { onPick(suggestion) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(
                    text = suggestion,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textPrimary,
                    modifier = Modifier.widthIn(max = 280.dp),
                )
            }
        }
        Text(
            text = stringResource(R.string.chat_tap_suggestion),
            style = MaterialTheme.typography.labelSmall,
            color = colors.textSecondary,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun MessageList(
    messages: List<ChatMessage>,
    onReply: (ChatMessage) -> Unit = {},
    /**
     * Which conversation this list belongs to — `mercury`, or a bot id.
     *
     * Entering a conversation is not "the same list growing": it is a different list, and it must
     * arrive already at the bottom. Animating there from the previous conversation's position is
     * what made a bot's chat look like it went through the Mercury screen and scrolled the whole
     * history on the way.
     */
    conversationKey: String = "mercury",
    /**
     * Called with the message id when the owner asks for the rest of a truncated answer. Null
     * hides the affordance: Mercury's own thread always has its text in full.
     */
    onLoadFull: (String) -> Unit = {},
) {
    val listState = rememberLazyListState()
    // The round « back to the end » button is decided from the list's own layout: shown the moment
    // the last message stops being the one on screen, gone as soon as it is back.
    val atBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()
            isAtBottom(
                lastVisibleIndex = last?.index ?: -1,
                itemsCount = info.totalItemsCount,
                lastItemBottom = last?.let { it.offset + it.size } ?: 0,
                viewportEnd = info.viewportEndOffset,
            )
        }
    }
    val scrollScope = rememberCoroutineScope()
    // A programmatic scroll also leaves the bottom for a moment; the button belongs to the owner's
    // own scrolling, not to our animation, so it stays quiet while we drive the list.
    var autoScrolling by remember { mutableStateOf(false) }
    val lastLength = messages.lastOrNull()?.text?.length ?: 0
    val clipboard = LocalClipboardManager.current
    // One menu at a time, one message in selection mode at a time: both are modal gestures on
    // the same press, so they cannot be independent pieces of state.
    var menuFor by remember { mutableStateOf<String?>(null) }
    var selecting by remember { mutableStateOf<String?>(null) }

    // Empty until this list has been positioned for the current conversation.
    val shownConversation = remember { mutableStateOf("") }
    LaunchedEffect(conversationKey) { shownConversation.value = "" }
    LaunchedEffect(conversationKey, messages.size, lastLength) {
        if (messages.isEmpty()) return@LaunchedEffect
        if (shownConversation.value == conversationKey) {
            // Same conversation, a new message: the smooth scroll is the right gesture.
            autoScrolling = true
            try {
                listState.animateScrollToItem(messages.lastIndex)
            } finally {
                autoScrolling = false
            }
        } else {
            // Entering (or returning to) a conversation: land at the end, instantly — animating
            // there from the previous conversation's position scrolled the whole history.
            listState.scrollToItem(messages.lastIndex)
            shownConversation.value = conversationKey
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (selecting != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(LocalMercuryColors.current.surface)
                    .padding(horizontal = 18.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.chat_selection_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = LocalMercuryColors.current.textSecondary,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { selecting = null }) { Text(stringResource(R.string.chat_done)) }
            }
        }
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    // Narrower gutters than a reading screen: the bubbles need the width they lose.
                    .padding(horizontal = 10.dp),
                // A chat's rhythm, not a document's: 22 dp between two bubbles read as paragraph
                // breaks, and the bubbles' own margins now do the separating.
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(top = 10.dp, bottom = 16.dp),
            ) {
                items(messages, key = { it.id }) { message ->
                    Box(modifier = Modifier.fillMaxWidth()) {
                        MessageItem(
                            message = message,
                            selecting = selecting == message.id,
                            onLongPress = {
                                if (selecting == null) menuFor = message.id
                            },
                            onLoadFull = if (message.truncated) ({ onLoadFull(message.id) }) else null,
                        )
                        DropdownMenu(
                            expanded = menuFor == message.id,
                            onDismissRequest = { menuFor = null },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.chat_reply)) },
                                onClick = {
                                    menuFor = null
                                    onReply(message)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.chat_copy)) },
                                onClick = {
                                    menuFor = null
                                    clipboard.setText(AnnotatedString(message.text))
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.chat_select_text)) },
                                onClick = {
                                    menuFor = null
                                    selecting = message.id
                                },
                            )
                        }
                    }
                }
            }

            // Back to the end of the conversation. It belongs to the list, so it floats over it,
            // bottom right, where the thumb already is — never over the input bar.
            JumpToBottomOverlay(
                visible = !atBottom && !autoScrolling && messages.isNotEmpty(),
                onClick = {
                    scrollScope.launch {
                        autoScrolling = true
                        try {
                            listState.animateScrollToItem(messages.lastIndex)
                        } finally {
                            autoScrolling = false
                        }
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 4.dp, bottom = 10.dp),
            )
        }
    }
}

/**
 * The wrapper exists for one reason: `AnimatedVisibility` has a `ColumnScope` overload, and called
 * from inside a column that also holds a box, the wrong one wins. Away from both receivers the
 * plain function is the only candidate.
 */
@Composable
private fun JumpToBottomOverlay(visible: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + scaleIn(initialScale = 0.85f),
        exit = fadeOut() + scaleOut(targetScale = 0.85f),
        modifier = modifier,
    ) {
        JumpToBottom(onClick = onClick)
    }
}

/**
 * The round arrow that takes the owner back to the last message: one tap, no gesture to learn.
 * A circle because it is a target, not a sentence — and drawn with the app's own surface so it
 * reads as floating over the conversation rather than as a button of the composer.
 */
@Composable
private fun JumpToBottom(onClick: () -> Unit) {
    val colors = LocalMercuryColors.current
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = colors.surface,
        contentColor = colors.textPrimary,
        shadowElevation = 6.dp,
        border = BorderStroke(1.dp, colors.hairlineStrong),
        modifier = Modifier.size(44.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Rounded.ArrowDownward,
                contentDescription = stringResource(R.string.chat_jump_to_bottom),
                tint = colors.textPrimary,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

/**
 * A message, with the gestures the owner expects on a chat bubble: long press for the menu,
 * and — once chosen there — native text selection, which is why [selecting] swaps the bubble
 * content for a [SelectionContainer] instead of fighting it for the long press.
 */
/** How many files one message to a bot may carry: the chat route's own limit, not a phone's. */
private const val MAX_BOT_FILES = 5

/**
 * A message, in the shape every phone chat has taught its owner to read: the answer on the left in
 * a bubble, the message on the right in another, the tail corner pointing at whoever spoke, and
 * nothing stretched to the full width of the screen.
 *
 * The two bubbles used to be different objects — the owner's was a rounded box, the agent's was bare
 * text laid on the canvas with its tool activity as a strip of pills above it. Bare text has no
 * edge, so a long answer read as part of the screen, and the strip scrolled sideways past its own
 * content. Now: one bubble per turn, one framed block for the calls, the same margins on both sides.
 *
 * Long press still opens the menu, and [selecting] still swaps the body for a [SelectionContainer]
 * instead of fighting it for the gesture.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun MessageItem(
    message: ChatMessage,
    selecting: Boolean = false,
    onLongPress: (() -> Unit)? = null,
    /** Offered under a bubble whose text is a cut summary; null when there is nothing to load. */
    onLoadFull: (() -> Unit)? = null,
) {
    val colors = LocalMercuryColors.current
    val parsed = remember(message.text) { QuoteReply.parse(message.text) }
    val user = message.role == ChatMessage.Role.User
    val press = if (onLongPress == null) {
        Modifier
    } else {
        Modifier.combinedClickable(onClick = {}, onLongClick = onLongPress)
    }

    // A stored tool summary IS the message: with no body there is no bubble, only the block.
    val bodyVisible = parsed.body.isNotBlank() || parsed.isQuote || message.streaming

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (user) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            // Not the full width either side: a bubble that touches both margins stops reading as
            // a bubble, and on a phone the wasted column is what keeps the two voices apart.
            modifier = Modifier.fillMaxWidth(if (user) 0.82f else 0.9f),
            horizontalAlignment = if (user) Alignment.End else Alignment.Start,
        ) {
            if (!user && message.steps.isNotEmpty()) {
                // The calls sit above the answer and outside its bubble: they are what produced it,
                // not what it says. Open while streaming, folded once it is history.
                ToolTraceBlock(
                    steps = message.steps,
                    folded = message.toolTrace,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
            if (bodyVisible) {
                Column(
                    modifier = Modifier
                        .clip(if (user) UserBubble else BotBubble)
                        .background(if (user) colors.userBubble else colors.surface)
                        .then(press)
                        .padding(horizontal = 13.dp, vertical = 8.dp),
                ) {
                    if (parsed.isQuote) QuoteBlock(parsed.quote)
                    if (parsed.body.isNotEmpty()) {
                        if (selecting) {
                            SelectionContainer {
                                MessageBody(message = message, body = parsed.body, user = user)
                            }
                        } else {
                            MessageBody(message = message, body = parsed.body, user = user)
                        }
                    }
                    if (message.streaming) {
                        Text(
                            text = "▍",
                            style = MaterialTheme.typography.bodyLarge,
                            color = colors.accent,
                        )
                    }
                }
            }
            // The age, under the bubble, on the bubble's own side — the way the webapp says it.
            // Nothing while the answer is still arriving: "à l'instant" under a cursor is noise,
            // and the timestamp is only true once the turn is over.
            val age = if (!message.streaming) remember(message.sentAt) { relativeAge(System.currentTimeMillis(), message.sentAt) } else null
            if (age != null) {
                Text(
                    text = ageLabel(age),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(top = 3.dp, start = 4.dp, end = 4.dp),
                )
            }
            // The journal cuts an answer at 300 characters, which is inside a fenced block as
            // often as not: the bubble says its text is a summary, and offers the rest.
            if (message.truncated && onLoadFull != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(top = 4.dp, start = 4.dp)
                        .clip(RoundedCornerShape(50))
                        .clickable { onLoadFull() }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Icon(
                        Icons.Rounded.ExpandMore,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = stringResource(R.string.chat_answer_truncated),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.accent,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }
        }
    }
}

/**
 * « à l'instant », « il y a 3 min » — the words, in the app's language. Core hands back the unit
 * and the count; the plurals are resources, like every other sentence on this screen.
 */
@Composable
private fun ageLabel(age: RelativeAge): String = when (age) {
    RelativeAge.JustNow -> stringResource(R.string.chat_age_now)
    is RelativeAge.Minutes -> pluralStringResource(R.plurals.chat_age_minutes, age.count, age.count)
    is RelativeAge.Hours -> pluralStringResource(R.plurals.chat_age_hours, age.count, age.count)
    is RelativeAge.Days -> pluralStringResource(R.plurals.chat_age_days, age.count, age.count)
}

/** The body of a bubble: plain words for the owner, rendered markdown for the agent. */
@Composable
private fun MessageBody(message: ChatMessage, body: String, user: Boolean) {
    if (user) {
        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge,
            color = LocalMercuryColors.current.textPrimary,
        )
    } else {
        MarkdownText(text = body, streaming = message.streaming)
    }
}

/**
 * The bubbles' geometry, WhatsApp's own: an 18 dp radius on three corners and a 4 dp corner where
 * the tail would be — bottom-right for the owner, bottom-left for the agent.
 */
private val BubbleRadius = 18.dp
private val UserBubble = RoundedCornerShape(
    topStart = BubbleRadius,
    topEnd = BubbleRadius,
    bottomStart = BubbleRadius,
    bottomEnd = 4.dp,
)
private val BotBubble = RoundedCornerShape(
    topStart = BubbleRadius,
    topEnd = BubbleRadius,
    bottomStart = 4.dp,
    bottomEnd = BubbleRadius,
)

/** The quote the next message will answer, above the composer, dismissible. */
@Composable
private fun ReplyChip(message: ChatMessage, onClear: () -> Unit) {
    val colors = LocalMercuryColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .clip(MaterialTheme.shapes.extraSmall)
            .background(colors.surface)
            .padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .heightIn(min = 26.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(colors.accent),
        )
        Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
            Text(
                text = if (message.role == ChatMessage.Role.User) stringResource(R.string.chat_replying_to_self) else stringResource(R.string.chat_replying_to_mercury),
                style = MaterialTheme.typography.labelSmall,
                color = colors.accent,
            )
            Text(
                text = QuoteReply.parse(message.text).body.ifBlank { message.text },
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onClear) {
            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.chat_cancel_reply), tint = colors.textSecondary)
        }
    }
}

/** The quoted turn, drawn as a quote rather than as literal `>` characters. */
@Composable
private fun QuoteBlock(quote: String) {
    val colors = LocalMercuryColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .heightIn(min = 28.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(colors.accent),
        )
        Text(
            text = quote,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 10.dp),
        )
    }
}

@Composable
private fun ErrorBar(notice: Notice, onDismiss: () -> Unit) {
    val colors = LocalMercuryColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp)
            .clip(MaterialTheme.shapes.extraSmall)
            .background(colors.surface)
            .padding(start = 14.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = noticeText(notice),
            style = MaterialTheme.typography.bodyMedium,
            color = noticeTint(notice.severity),
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onDismiss) {
            Text(text = "✕", color = colors.textSecondary)
        }
    }
}

/**
 * The files waiting to be sent, above the composer. A chip, not a thumbnail grid:
 * the point is to see what will travel and drop the wrong one, not to admire it.
 */
@Composable
private fun AttachmentStrip(attachments: List<PendingFile>, onRemove: (Int) -> Unit) {
    if (attachments.isEmpty()) return
    val colors = LocalMercuryColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        attachments.forEachIndexed { index, file ->
            Row(
                modifier = Modifier
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(colors.surface)
                    .padding(start = 10.dp, end = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 170.dp),
                )
                IconButton(onClick = { onRemove(index) }, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.chat_remove_attachment, file.name),
                        tint = colors.textSecondary,
                        modifier = Modifier.size(15.dp),
                    )
                }
            }
        }
    }
}
