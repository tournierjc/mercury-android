package io.github.tournierjc.mercury

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
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
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.tournierjc.mercury.ui.ChatScreen
import io.github.tournierjc.mercury.ui.ConnectScreen
import io.github.tournierjc.mercury.ui.theme.LocalMercuryColors
import io.github.tournierjc.mercury.ui.theme.MercuryTheme
import io.github.tournierjc.mercury.vm.ChatViewModel

class MainActivity : ComponentActivity() {

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* refus = pas de notif, l'app continue */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as MercuryApp).container
        askForNotifications()
        setContent {
            MercuryTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(LocalMercuryColors.current.canvas),
                ) {
                    MercuryRoot(container)
                }
            }
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
private fun MercuryRoot(container: AppContainer) {
    val viewModel: ChatViewModel = viewModel(factory = ChatViewModel.factory(container))
    val state by viewModel.state.collectAsStateWithLifecycle()
    val threads by viewModel.threads.collectAsStateWithLifecycle()
    val signedIn by viewModel.signedIn.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()

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
            onPermission = viewModel::answerPermission,
            onNewChat = {
                viewModel.newChat()
                viewModel.refreshThreads()
            },
            onOpenThread = viewModel::openThread,
            onDeleteThread = viewModel::deleteThread,
            onSignOut = viewModel::signOut,
            onDismissError = viewModel::dismissError,
            onDismissWarning = viewModel::dismissWarning,
        )
    }
}
