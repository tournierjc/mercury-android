package io.github.tournierjc.mercury.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import io.github.tournierjc.mercury.MercuryApp
import io.github.tournierjc.mercury.bots.BotThread
import io.github.tournierjc.mercury.data.BotJson
import io.github.tournierjc.mercury.data.ChatMessage
import io.github.tournierjc.mercury.vm.BotsViewModel
import io.github.tournierjc.mercury.data.ChatState
import io.github.tournierjc.mercury.data.PendingFile
import io.github.tournierjc.mercury.data.QuoteReply
import io.github.tournierjc.mercury.data.ThreadDto
import io.github.tournierjc.mercury.util.MediaPicker
import io.github.tournierjc.mercury.ui.theme.LocalMercuryColors
import kotlinx.coroutines.launch
import java.util.Locale

private enum class Screen { Chat, Bridge, Settings, Console, Deliverables, Memory, Boards, Bots }

private val suggestions = listOf(
    "Que sais-tu de ce Spark ?",
    "Résume ma dernière session",
    "Écris un test pour le parseur SSE",
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
    onDismissError: () -> Unit,
    onDismissWarning: () -> Unit,
    /** Bot named by the notification the owner tapped, opened once the screen is up. */
    openBotId: String? = null,
    onOpenBotConsumed: () -> Unit = {},
) {
    val colors = LocalMercuryColors.current
    var screen by rememberSaveable { mutableStateOf(Screen.Chat) }
    var botToOpen by remember { mutableStateOf<String?>(null) }
    var botChat by rememberSaveable { mutableStateOf<String?>(null) }
    // Where the open conversation was entered from, so Back retraces one step.
    var cameFromBots by rememberSaveable { mutableStateOf(false) }

    // A tapped notification is a destination: "Bricoleur a terminé son travail" queues its
    // bot id, and the app lands on that bot instead of the last conversation.
    LaunchedEffect(openBotId) {
        if (openBotId != null) {
            // Straight into the conversation: that is where the work it announced lives.
            botChat = openBotId
            screen = Screen.Chat
            onOpenBotConsumed()
        }
    }

    // Back closes what is on top; it never leaves the app while something is open. The
    // screens each drew their own back arrow, but the system button is the one a thumb finds.
    BackHandler(enabled = screen != Screen.Chat) {
        screen = Screen.Chat
        botToOpen = null
    }

    when (screen) {
        Screen.Bridge -> {
            BridgeScreen(onBack = { screen = Screen.Chat })
            return
        }

        Screen.Settings -> {
            SettingsScreen(onBack = { screen = Screen.Chat })
            return
        }

        Screen.Console -> {
            ConsoleScreen(onBack = { screen = Screen.Chat })
            return
        }

        Screen.Memory -> {
            MemoryScreen(onBack = { screen = Screen.Chat })
            return
        }

        Screen.Boards -> {
            BoardsScreen(onBack = { screen = Screen.Chat })
            return
        }

        Screen.Bots -> {
            BotsScreen(
                onBack = {
                    screen = Screen.Chat
                    botToOpen = null
                },
                initialBotId = botToOpen,
                onOpened = { botToOpen = null },
                // The sheet hands over to the conversation, and back comes home to the sheet:
                // a single step each way, instead of landing in Mercury's screen and having to
                // find the teammate again.
                onOpenChat = { id ->
                    botChat = id
                    cameFromBots = true
                    screen = Screen.Chat
                },
            )
            return
        }

        Screen.Deliverables -> {
            DeliverablesScreen(onBack = { screen = Screen.Chat })
            return
        }

        Screen.Chat -> Unit
    }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // One conversation, two correspondents: Mercury, or a teammate picked in the drawer.
    val container = remember { (context.applicationContext as MercuryApp).container }
    val botsVm: BotsViewModel = viewModel(factory = BotsViewModel.factory(container))
    val botsState by botsVm.state.collectAsState()
    val botWatch by container.botWatcher.state.collectAsState()
    val botAvatars by botsVm.avatars.collectAsState()
    LaunchedEffect(botWatch.bots) { botsVm.loadAvatars(botWatch.bots) }
    LaunchedEffect(botChat) { botChat?.let { botsVm.select(it) } }

    BackHandler(enabled = botChat != null) {
        botChat = null
        if (cameFromBots) {
            // Opened from the roster: Back returns to it, with the same bot still selected.
            cameFromBots = false
            botToOpen = botsState.selectedId
            screen = Screen.Bots
        }
    }

    var composerText by rememberSaveable { mutableStateOf("") }
    // The message being answered, if any: the composer shows it as a chip and the send prepends
    // it as a blockquote, which is how the quote reaches the daemon and survives a reload.
    var replyTo by remember { mutableStateOf<ChatMessage?>(null) }
    LaunchedEffect(botChat) { replyTo = null }
    var voiceError by remember { mutableStateOf<String?>(null) }
    var attachmentError by remember { mutableStateOf<String?>(null) }

    // Photos and PDFs: the system picker needs no storage permission, and a picked
    // file is read once, downscaled, and reduced to bytes — never copied to storage.
    val attachmentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        val files = uris.mapNotNull { uri ->
            MediaPicker.read(context, uri).getOrElse { error ->
                attachmentError = error.message ?: "Pièce jointe illisible"
                null
            }
        }
        if (files.isNotEmpty()) onPickAttachments(files)
    }

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
            runCatching { speechLauncher.launch(speechIntent()) }
                .onFailure { voiceError = "Dictée indisponible sur cet appareil" }
        } else {
            voiceError = "Micro refusé — autorise l'accès dans les réglages"
        }
    }

    fun startVoice() {
        voiceError = null
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            runCatching { speechLauncher.launch(speechIntent()) }
                .onFailure { voiceError = "Dictée indisponible sur cet appareil" }
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // Inside the chat itself, back closes the drawer before it closes the app.
    BackHandler(enabled = drawerState.isOpen) { scope.launch { drawerState.close() } }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ThreadDrawer(
                threads = threads,
                currentSessionId = state.sessionId,
                onNewChat = {
                    botChat = null
                    onNewChat()
                    scope.launch { drawerState.close() }
                },
                onOpenThread = { thread ->
                    botChat = null
                    onOpenThread(thread)
                    scope.launch { drawerState.close() }
                },
                onOpenBridge = {
                    screen = Screen.Bridge
                    scope.launch { drawerState.close() }
                },
                onOpenSettings = {
                    screen = Screen.Settings
                    scope.launch { drawerState.close() }
                },
                onOpenConsole = {
                    screen = Screen.Console
                    scope.launch { drawerState.close() }
                },
                onOpenMemory = {
                    screen = Screen.Memory
                    scope.launch { drawerState.close() }
                },
                onOpenBoards = {
                    screen = Screen.Boards
                    scope.launch { drawerState.close() }
                },
                onOpenBots = {
                    screen = Screen.Bots
                    scope.launch { drawerState.close() }
                },
                onOpenDeliverables = {
                    screen = Screen.Deliverables
                    scope.launch { drawerState.close() }
                },
                bots = botWatch.bots,
                avatars = botAvatars,
                activeBotId = botChat,
                onOpenBot = { id ->
                    botChat = id
                    cameFromBots = false
                    screen = Screen.Chat
                    scope.launch { drawerState.close() }
                },
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
            val bot = botId?.let { id -> botWatch.bots.firstOrNull { BotJson.id(it) == id } }
            val botLive = botId?.let { botWatch.live[it] }
            val botName = bot?.let { BotJson.name(it) } ?: botId.orEmpty()
            val botMessages = remember(botsState.records, botsState.prompts, botsState.answers, botsState.pending, botLive, botId) {
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
                                    message.state == "queued" -> "en file…"
                                    message.state == "failed" -> "échec du run"
                                    else -> "sans réponse"
                                }
                            },
                            streaming = message.streaming,
                        )
                    }
                }
            }
            var showBotDeliverables by remember { mutableStateOf(false) }

            if (botId != null) {
                TopBar(
                    title = botName,
                    subtitle = listOfNotNull(
                        bot?.let { BotJson.stateLabel(BotJson.state(it)) },
                        botLive?.takeIf { it.running }?.let { "écrit…" } ?: botWatch.activity[botId],
                    ).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "au repos" },
                    onMenu = { scope.launch { drawerState.open() } },
                    onNewChat = null,
                    avatar = { if (bot != null) BotFace(bot, botAvatars[botId], small = true) },
                    actions = {
                        IconButton(onClick = { showBotDeliverables = true }) {
                            Icon(
                                imageVector = Icons.Rounded.FolderOpen,
                                contentDescription = "Livrables de $botName",
                                tint = if (botsState.outputs.isEmpty()) colors.textSecondary else colors.accent,
                            )
                        }
                        IconButton(onClick = {
                            botToOpen = botId
                            screen = Screen.Bots
                        }) {
                            Icon(Icons.Rounded.MoreHoriz, contentDescription = "Réglages de $botName", tint = colors.textSecondary)
                        }
                    },
                )
                Box(modifier = Modifier.weight(1f)) {
                    if (botMessages.isEmpty()) {
                        BotWelcome(botName) { prompt -> composerText = ""; botsVm.dispatch(prompt, chat = true) }
                    } else {
                        MessageList(
                            botMessages,
                            onReply = { replyTo = it },
                            // A bot conversation is its own list: switching to it from the
                            // roster must not animate through Mercury's messages.
                            conversationKey = "bot:$botId",
                        )
                    }
                }
                botsState.error?.let { message -> ErrorBar(message = message, onDismiss = { botsVm.clearBanner() }) }
                botsState.dispatchFeedback?.let { feedback ->
                    Text(
                        text = feedback,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (feedback.startsWith("Refusé")) colors.danger else colors.textSecondary,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                    )
                }
                replyTo?.let { quoted -> ReplyChip(quoted) { replyTo = null } }
                Composer(
                    value = composerText,
                    onValueChange = { composerText = it },
                    onSend = {
                        val text = composerText.trim()
                        if (text.isNotEmpty()) {
                            composerText = ""
                            val payload = replyTo?.let { QuoteReply.compose(it.text, text) } ?: text
                            replyTo = null
                            // Chat mode: the bot answers, it does not file a work report.
                            botsVm.dispatch(payload, chat = true)
                        }
                    },
                    onVoice = { startVoice() },
                    onAttach = null,
                    attachmentCount = 0,
                    busy = botsState.busy,
                    hint = "Écrire à $botName…",
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

            val errorMessage = state.error
            if (errorMessage != null) {
                ErrorBar(message = errorMessage, onDismiss = onDismissError)
            }
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
            state.warning?.let { warning ->
                Box(modifier = Modifier.padding(horizontal = 14.dp)) {
                    InlineBanner(
                        message = warning,
                        isError = false,
                        onDismiss = onDismissWarning,
                    )
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

private fun speechIntent(): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
    putExtra(RecognizerIntent.EXTRA_PROMPT, "Parle à Mercury")
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
) {
    val colors = LocalMercuryColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onMenu) {
            Icon(Icons.Rounded.Menu, contentDescription = "Sessions et équipiers", tint = colors.textPrimary)
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
                Icon(Icons.Rounded.Add, contentDescription = "Nouvelle session", tint = colors.textPrimary)
            }
            else -> Box(modifier = Modifier.padding(horizontal = 24.dp))
        }
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
            text = name.ifBlank { "Équipier" },
            style = MaterialTheme.typography.displaySmall,
            color = colors.textPrimary,
        )
        Text(
            text = "Un message part en tâche de fond : sa réponse s'écrit ici pendant qu'il travaille, " +
                "et ses livrables s'ouvrent par le dossier en haut.",
            style = MaterialTheme.typography.bodyLarge,
            color = colors.textSecondary,
            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
        )
        listOf(
            "Que sais-tu de ce Spark ?",
            "Résume l'état de la flotte",
            "Écris un fichier de notes",
        ).forEach { prompt ->
            Text(
                text = prompt,
                style = MaterialTheme.typography.bodyLarge,
                color = colors.textPrimary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(16.dp))
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
            text = "Ton agent tourne sur le Spark. Même mémoire, mêmes outils.",
            style = MaterialTheme.typography.bodyLarge,
            color = colors.textSecondary,
            modifier = Modifier.padding(top = 8.dp, bottom = 28.dp),
        )
        suggestions.forEach { suggestion ->
            // The hint says "appuie sur une suggestion" — it has to be clickable to be true.
            Row(
                modifier = Modifier
                    .padding(bottom = 8.dp)
                    .clip(RoundedCornerShape(18.dp))
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
            text = "Appuie sur une suggestion ou écris le premier message.",
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
) {
    val listState = rememberLazyListState()
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
            listState.animateScrollToItem(messages.lastIndex)
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
                    text = "Sélection du texte — appui long sur un mot pour étendre",
                    style = MaterialTheme.typography.labelSmall,
                    color = LocalMercuryColors.current.textSecondary,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { selecting = null }) { Text("Terminé") }
            }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
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
                    )
                    DropdownMenu(
                        expanded = menuFor == message.id,
                        onDismissRequest = { menuFor = null },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Répondre") },
                            onClick = {
                                menuFor = null
                                onReply(message)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Copier") },
                            onClick = {
                                menuFor = null
                                clipboard.setText(AnnotatedString(message.text))
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Sélectionner du texte") },
                            onClick = {
                                menuFor = null
                                selecting = message.id
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * A message, with the gestures the owner expects on a chat bubble: long press for the menu,
 * and — once chosen there — native text selection, which is why [selecting] swaps the bubble
 * content for a [SelectionContainer] instead of fighting it for the long press.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun MessageItem(
    message: ChatMessage,
    selecting: Boolean = false,
    onLongPress: (() -> Unit)? = null,
) {
    val colors = LocalMercuryColors.current
    val parsed = remember(message.text) { QuoteReply.parse(message.text) }
    val press = if (onLongPress == null) {
        Modifier
    } else {
        Modifier.combinedClickable(onClick = {}, onLongClick = onLongPress)
    }

    if (message.role == ChatMessage.Role.User) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(22.dp))
                    .background(colors.userBubble)
                    .then(press)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                if (parsed.isQuote) QuoteBlock(parsed.quote)
                if (parsed.body.isNotEmpty()) {
                    if (selecting) {
                        SelectionContainer {
                            Text(
                                text = parsed.body,
                                style = MaterialTheme.typography.bodyLarge,
                                color = colors.textPrimary,
                                textAlign = TextAlign.End,
                            )
                        }
                    } else {
                        Text(
                            text = parsed.body,
                            style = MaterialTheme.typography.bodyLarge,
                            color = colors.textPrimary,
                            textAlign = TextAlign.End,
                        )
                    }
                }
            }
        }
        return
    }

    Column(modifier = Modifier.fillMaxWidth().then(press)) {
        if (message.steps.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                message.steps.forEach { step ->
                    Text(
                        text = step.label.ifBlank { step.tool },
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textSecondary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.surface)
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
            }
        }
        if (parsed.isQuote) {
            QuoteBlock(parsed.quote)
        }
        if (selecting) {
            SelectionContainer {
                MarkdownText(text = parsed.body, streaming = message.streaming)
            }
        } else {
            MarkdownText(text = parsed.body, streaming = message.streaming)
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

/** The quote the next message will answer, above the composer, dismissible. */
@Composable
private fun ReplyChip(message: ChatMessage, onClear: () -> Unit) {
    val colors = LocalMercuryColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .clip(RoundedCornerShape(12.dp))
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
                text = if (message.role == ChatMessage.Role.User) "Vous répondez à votre message" else "Vous répondez à Mercury",
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
            Icon(Icons.Rounded.Close, contentDescription = "Annuler la réponse", tint = colors.textSecondary)
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
private fun ErrorBar(message: String, onDismiss: () -> Unit) {
    val colors = LocalMercuryColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surface)
            .padding(start = 14.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.danger,
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
                    .clip(RoundedCornerShape(14.dp))
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
                        contentDescription = "Retirer ${file.name}",
                        tint = colors.textSecondary,
                        modifier = Modifier.size(15.dp),
                    )
                }
            }
        }
    }
}
