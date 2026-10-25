package io.github.tournierjc.mercury.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import io.github.tournierjc.mercury.data.ChatMessage
import io.github.tournierjc.mercury.data.ChatState
import io.github.tournierjc.mercury.data.ThreadDto
import io.github.tournierjc.mercury.ui.theme.LocalMercuryColors
import kotlinx.coroutines.launch
import java.util.Locale

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
    onPermission: (String, String) -> Unit,
    onNewChat: () -> Unit,
    onOpenThread: (ThreadDto) -> Unit,
    onSignOut: () -> Unit,
    onDismissError: () -> Unit,
) {
    val colors = LocalMercuryColors.current
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var composerText by rememberSaveable { mutableStateOf("") }
    var voiceError by remember { mutableStateOf<String?>(null) }

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

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ThreadDrawer(
                threads = threads,
                currentSessionId = state.sessionId,
                onNewChat = {
                    onNewChat()
                    scope.launch { drawerState.close() }
                },
                onOpenThread = { thread ->
                    onOpenThread(thread)
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
                    MessageList(state)
                }
            }

            val errorMessage = state.error
            if (errorMessage != null) {
                ErrorBar(message = errorMessage, onDismiss = onDismissError)
            }
            if (voiceError != null) {
                Text(
                    text = voiceError.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.danger,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                )
            }
            state.pendingPermission?.let { prompt ->
                Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
                    PermissionCard(
                        prompt = prompt,
                        onAction = { action -> onPermission(prompt.id, action) },
                    )
                }
            }

            Composer(
                value = composerText,
                onValueChange = { composerText = it },
                onSend = {
                    val text = composerText
                    if (text.isNotBlank()) {
                        composerText = ""
                        onSend(text)
                    }
                },
                onVoice = { startVoice() },
                busy = state.streaming,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }
    }
}

private fun speechIntent(): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
    putExtra(RecognizerIntent.EXTRA_PROMPT, "Parle à Mercury")
}

@Composable
private fun TopBar(
    title: String,
    subtitle: String,
    onMenu: () -> Unit,
    onNewChat: () -> Unit,
) {
    val colors = LocalMercuryColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onMenu) {
            Icon(Icons.Rounded.Menu, contentDescription = "Sessions", tint = colors.textPrimary)
        }
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
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
        IconButton(onClick = onNewChat) {
            Icon(Icons.Rounded.Add, contentDescription = "Nouvelle session", tint = colors.textPrimary)
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
            Row(
                modifier = Modifier
                    .padding(bottom = 8.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(colors.surface)
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

@Composable
private fun MessageList(state: ChatState) {
    val listState = rememberLazyListState()
    val lastLength = state.messages.lastOrNull()?.text?.length ?: 0
    LaunchedEffect(state.messages.size, lastLength) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.lastIndex)
        }
    }
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
        contentPadding = PaddingValues(top = 10.dp, bottom = 16.dp),
    ) {
        items(state.messages, key = { it.id }) { message ->
            MessageItem(message)
        }
    }
}

@Composable
private fun MessageItem(message: ChatMessage) {
    val colors = LocalMercuryColors.current
    if (message.role == ChatMessage.Role.User) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            Text(
                text = message.text,
                style = MaterialTheme.typography.bodyLarge,
                color = colors.textPrimary,
                textAlign = TextAlign.End,
                modifier = Modifier
                    .clip(RoundedCornerShape(22.dp))
                    .background(colors.userBubble)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
        return
    }

    Column(modifier = Modifier.fillMaxWidth()) {
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
        MarkdownText(text = message.text)
        if (message.streaming) {
            Text(
                text = "▍",
                style = MaterialTheme.typography.bodyLarge,
                color = colors.accent,
            )
        }
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
