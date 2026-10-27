package io.github.tournierjc.mercury.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.tournierjc.mercury.MercuryApp
import io.github.tournierjc.mercury.bots.BotThread
import io.github.tournierjc.mercury.bots.LiveTurn
import io.github.tournierjc.mercury.data.BotJson
import io.github.tournierjc.mercury.bots.BotMessage
import io.github.tournierjc.mercury.ui.theme.LocalMercuryColors
import io.github.tournierjc.mercury.ui.theme.MercuryColors
import io.github.tournierjc.mercury.vm.BotsViewModel

/**
 * A bot's conversation, as a screen of its own.
 *
 * This is the place you talk to a teammate: the list is what you asked and what it answered,
 * the answer streams while the run is writing (the daemon sends a rolling tail of the visible
 * text), and the header carries the two things you need next — its deliverable folder and its
 * settings. The settings panel is not where you talk; it is where you tune.
 */
@Composable
fun BotChatScreen(
    botId: String,
    onBack: () -> Unit,
    onOpenAdmin: (String) -> Unit,
) {
    val context = LocalContext.current
    val container = remember { (context.applicationContext as MercuryApp).container }
    val viewModel: BotsViewModel = viewModel(factory = BotsViewModel.factory(container))
    val state by viewModel.state.collectAsState()
    val watch by viewModel.watcher.state.collectAsState()
    val avatars by viewModel.avatars.collectAsState()
    val colors = LocalMercuryColors.current

    LaunchedEffect(botId) { viewModel.select(botId) }
    LaunchedEffect(watch.bots) { viewModel.loadAvatars(watch.bots) }

    val bot = watch.bots.firstOrNull { BotJson.id(it) == botId }
    val live: LiveTurn? = watch.live[botId]
    val messages = remember(state.records, state.prompts, state.pending, live) {
        val built = BotThread.build(state.records, state.prompts, live?.jobId, live?.text.orEmpty())
        // A turn sent from this screen appears twice — once as the optimistic bubble, once the
        // journal picks it up. Drop the optimistic copy as soon as its text is in the list.
        val texts = built.filter { !it.fromBot }.map { it.text }.toSet()
        built + state.pending.filter { it.text !in texts }
    }

    // Streaming is a window, not the answer: when the turn stops, the journal has the real one.
    val writing = live?.running == true
    LaunchedEffect(writing) {
        if (!writing) viewModel.reloadConversation(botId)
    }

    var draft by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size, messages.lastOrNull()?.text) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    var showDeliverables by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.canvas)
            .safeDrawingPadding()
            .imePadding(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Retour", tint = colors.textPrimary)
            }
            if (bot != null) BotFace(bot, avatars[botId]) else Box {}
            Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
                Text(
                    text = bot?.let { BotJson.name(it) } ?: botId,
                    color = colors.textPrimary,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                )
                Text(
                    text = listOfNotNull(
                        bot?.let { BotJson.stateLabel(BotJson.state(it)) },
                        live?.takeIf { it.running }?.let { "écrit…" } ?: watch.activity[botId],
                    ).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "au repos" },
                    color = if (live?.running == true) colors.accent else colors.textSecondary,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            // Deliverables are a tap away from the conversation that produced them.
            IconButton(onClick = { showDeliverables = true }) {
                Icon(
                    imageVector = Icons.Rounded.FolderOpen,
                    contentDescription = "Livrables de ${bot?.let { BotJson.name(it) } ?: botId}",
                    tint = if (state.outputs.isEmpty()) colors.textSecondary else colors.accent,
                )
            }
            IconButton(onClick = { onOpenAdmin(botId) }) {
                Icon(Icons.Rounded.MoreHoriz, contentDescription = "Réglages", tint = colors.textSecondary)
            }
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (messages.isEmpty()) {
                Column(modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Parlez à ${bot?.let { BotJson.name(it) } ?: botId}.",
                        color = colors.textPrimary,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "Chaque message part en tâche de fond : sa réponse s'écrit ici pendant qu'il " +
                            "travaille, et ses livrables s'ouvrent par le dossier en haut.",
                        color = colors.textSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    listOf("Que sais-tu de ce Spark ?", "Résume l'état de la flotte", "Écris un fichier de notes").forEach { prompt ->
                        Text(
                            text = prompt,
                            color = colors.accent,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(colors.surfaceMuted, RoundedCornerShape(12.dp))
                                .clickable { draft = prompt }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(messages, key = { it.key }) { message -> MessageBubble(message, colors) }
                    item { Box(modifier = Modifier.padding(bottom = 8.dp)) {} }
                }
            }
        }

        state.error?.let { message ->
            Text(
                text = message,
                color = colors.danger,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
        }
        state.dispatchFeedback?.let { feedback ->
            Text(
                text = feedback,
                color = if (feedback.startsWith("Refusé")) colors.danger else colors.textSecondary,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
        }

        Row(
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                placeholder = { Text("Écrire à ${bot?.let { BotJson.name(it) } ?: botId}") },
                maxLines = 5,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = {
                    val text = draft.trim()
                    if (text.isEmpty()) return@IconButton
                    viewModel.dispatch(text)
                    draft = ""
                },
                enabled = draft.isNotBlank() && !state.busy,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.Send,
                    contentDescription = "Envoyer",
                    tint = if (draft.isNotBlank()) colors.accent else colors.textSecondary,
                )
            }
        }
    }

    if (showDeliverables) {
        // Reloaded on open: a file written while the sheet was closed still shows up.
        LaunchedEffect(showDeliverables) { viewModel.reloadOutputs(botId) }
        BotDeliverablesSheet(
            botId = botId,
            botName = bot?.let { BotJson.name(it) } ?: botId,
            outputs = state.outputs,
            busy = state.busy,
            onPreview = viewModel::previewOutput,
            onShare = viewModel::shareOutput,
            onDelete = viewModel::deleteOutput,
            onReload = { viewModel.reloadOutputs(botId) },
            onDismiss = { showDeliverables = false },
        )
    }
    BotOutputPreviewHost(state, viewModel)
}

@Composable
private fun MessageBubble(message: BotMessage, colors: MercuryColors) {
    val mine = !message.fromBot
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
    ) {
        val text = when {
            message.text.isNotBlank() -> message.text
            message.streaming -> "…"
            message.state == "queued" -> "en file…"
            message.state == "running" -> "travaille…"
            message.state == "failed" -> "échec du run"
            else -> "sans réponse"
        }
        Text(
            text = if (message.streaming && message.text.isNotBlank()) "$text▌" else text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (mine) colors.canvas else colors.textPrimary,
            modifier = Modifier
                .widthIn(max = 300.dp)
                .background(
                    color = if (mine) colors.accent else colors.surfaceMuted,
                    shape = RoundedCornerShape(16.dp),
                )
                .padding(horizontal = 12.dp, vertical = 9.dp),
        )
    }
}
