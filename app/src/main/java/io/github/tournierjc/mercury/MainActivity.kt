package io.github.tournierjc.mercury

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.content.Intent
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.tournierjc.mercury.bots.AttentionPrefs
import io.github.tournierjc.mercury.device.LocaleStore
import io.github.tournierjc.mercury.bots.BotNotifier
import io.github.tournierjc.mercury.bots.WatchService
import io.github.tournierjc.mercury.ui.ChatScreen
import io.github.tournierjc.mercury.ui.ConnectScreen
import io.github.tournierjc.mercury.ui.theme.LocalMercuryColors
import io.github.tournierjc.mercury.ui.theme.MercuryTheme
import io.github.tournierjc.mercury.vm.ChatViewModel

class MainActivity : ComponentActivity() {

    /** The chosen language is applied here, before anything draws. */
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LocaleStore.wrap(newBase))
    }

    /**
     * Bot named by the notification being opened, with a nonce — consumed by the UI once shown.
     *
     * The nonce is why it is a pair: two taps on the same notification have to be two events, or
     * the second one lands on unchanged state and the app stays where the owner left it.
     */
    private val pendingBot = mutableStateOf<Pair<String, Int>?>(null)
    private var openNonce = 0

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* refus = pas de notif, l'app continue */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingBot.value = intent?.getStringExtra(BotNotifier.EXTRA_BOT_ID)?.let { it to 0 }
        val container = (application as MercuryApp).container
        askForNotifications()
        // The watch service survives the activity, but a reboot (or a system kill) does not
        // restart it for us on Android 14+: re-arm it every time the owner opens the app,
        // which is a foreground start and therefore always allowed.
        if (AttentionPrefs(this).persistent) WatchService.start(this)
        setContent {
            MercuryTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(LocalMercuryColors.current.canvas),
                ) {
                    MercuryRoot(
                        container = container,
                        openBotId = pendingBot.value?.first,
                        openBotNonce = pendingBot.value?.second ?: 0,
                        onBotOpened = { pendingBot.value = null },
                    )
                }
            }
        }
    }

    /**
     * A second tap on a notification while the app is already open: same destination as the
     * first one, instead of leaving the owner wherever they were.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra(BotNotifier.EXTRA_BOT_ID)?.let { id ->
            openNonce += 1
            pendingBot.value = id to openNonce
        }
    }

    /** Bot notifications need POST_NOTIFICATIONS on Android 13+; a refusal is harmless. */
    private fun askForNotifications() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}

@Composable
private fun MercuryRoot(
    container: AppContainer,
    openBotId: String?,
    openBotNonce: Int,
    onBotOpened: () -> Unit,
) {
    val viewModel: ChatViewModel = viewModel(factory = ChatViewModel.factory(container))
    val state by viewModel.state.collectAsStateWithLifecycle()
    val threads by viewModel.threads.collectAsStateWithLifecycle()
    val signedIn by viewModel.signedIn.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val attachments by viewModel.attachments.collectAsStateWithLifecycle()
    val syncing by viewModel.syncing.collectAsStateWithLifecycle()

    if (!signedIn) {
        ConnectScreen(
            initialUrl = container.serverUrl,
            initialUsername = container.lastUsername,
            busy = busy,
            error = state.error,
            onSubmit = viewModel::signIn,
        )
    } else {
        ChatScreen(
            state = state,
            threads = threads,
            onSend = viewModel::send,
            attachments = attachments,
            onPickAttachments = viewModel::attach,
            onRemoveAttachment = viewModel::removeAttachment,
            onPermission = viewModel::answerPermission,
            onNewChat = {
                viewModel.newChat()
                viewModel.refreshThreads()
            },
            onOpenThread = viewModel::openThread,
            onDeleteThread = viewModel::deleteThread,
            onSignOut = viewModel::signOut,
            onChangeServer = viewModel::changeServer,
            onDismissError = viewModel::dismissError,
            onDismissWarning = viewModel::dismissWarning,
            syncing = syncing,
            openBotId = openBotId,
            openBotNonce = openBotNonce,
            onOpenBotConsumed = onBotOpened,
        )
    }
}
