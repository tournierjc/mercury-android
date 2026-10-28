package io.github.tournierjc.mercury.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import io.github.tournierjc.mercury.R
import io.github.tournierjc.mercury.data.ServerAddress
import io.github.tournierjc.mercury.device.LocaleStore
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.tournierjc.mercury.MercuryApp
import io.github.tournierjc.mercury.bots.AttentionPrefs
import io.github.tournierjc.mercury.bots.AttentionSchedule
import io.github.tournierjc.mercury.bots.WatchService
import io.github.tournierjc.mercury.ui.theme.LocalMercuryColors
import io.github.tournierjc.mercury.util.formatTokens
import io.github.tournierjc.mercury.vm.SettingsViewModel

private enum class AccountDialog { Username, Password }

/**
 * Model picker, tool-approval mode, token budget, provider wiring and account
 * actions. Each control writes straight to the matching Mercury route.
 */
@Composable
fun SettingsScreen(onBack: () -> Unit, onChangeServer: (String) -> Unit) {
    val context = LocalContext.current
    val container = remember { (context.applicationContext as MercuryApp).container }
    val viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.factory(container))
    val state by viewModel.state.collectAsState()
    val colors = LocalMercuryColors.current

    var dialog by remember { mutableStateOf<AccountDialog?>(null) }

    var showModelPicker by remember { mutableStateOf(false) }
    var editingProvider by remember { mutableStateOf<String?>(null) }
    // The host address used to be asked once, at first launch, and never again: following the
    // server to another machine meant wiping the app. It now lives with the account rows.
    var showServerDialog by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.canvas)
            .safeDrawingPadding(),
    ) {
        ScreenHeader(
            title = stringResource(R.string.settings_title),
            subtitle = state.models?.current?.displayLabel ?: "Mercury",
            onBack = onBack,
            onRefresh = viewModel::refresh,
        )
        state.message?.let { notice -> NoticeBanner(notice, onDismiss = viewModel::clearBanner) }
        state.error?.let { notice -> NoticeBanner(notice, onDismiss = viewModel::clearBanner) }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp),
        ) {
            CollapsibleSection(stringResource(R.string.settings_language_title)) {
                // The label of each choice is its own name, never translated: an English speaker
                // looking for French has to recognise « Français » in any UI language.
                val current = LocaleStore.tag(context)
                Text(
                    text = stringResource(R.string.settings_language_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
                LocaleStore.choices.forEach { choice ->
                    val label = if (choice.tag.isBlank()) {
                        stringResource(R.string.settings_language_system)
                    } else {
                        choice.label
                    }
                    ActionRow(
                        title = label,
                        trailing = if (choice.tag == current) stringResource(R.string.settings_active) else null,
                        selected = choice.tag == current,
                        onClick = {
                            LocaleStore.set(context, choice.tag)
                            (context as? android.app.Activity)?.recreate()
                        },
                    )
                }
            }

            CollapsibleSection(stringResource(R.string.settings_model_title)) {
                val current = state.models?.current
                // The four rows were the runtime's whole catalogue printed on top of the two places
                // that already choose a model (the conversation, and each bot). One row that opens
                // the list says the same thing without owning the screen.
                ActionRow(
                    title = current?.displayLabel ?: "Mercury",
                    subtitle = current?.let { "${it.group.ifBlank { it.name }} · ${it.model}" }
                        ?: if (state.loading) stringResource(R.string.settings_loading) else stringResource(R.string.settings_no_model),
                    trailing = stringResource(R.string.settings_change),
                    enabled = !state.models?.providers.isNullOrEmpty(),
                    onClick = { showModelPicker = true },
                )
            }

            CollapsibleSection(stringResource(R.string.settings_tools_title)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.settings_approve_all),
                            style = MaterialTheme.typography.bodyLarge,
                            color = colors.textPrimary,
                        )
                        Text(
                            text = stringResource(R.string.settings_mode_current, state.settings?.permissionMode ?: "—"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textSecondary,
                        )
                    }
                    Switch(
                        checked = state.settings?.autoApproves ?: false,
                        onCheckedChange = viewModel::setAutoApprove,
                        enabled = !state.busy,
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 10.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.settings_restrict_user),
                            style = MaterialTheme.typography.bodyLarge,
                            color = colors.textPrimary,
                        )
                        Text(
                            text = stringResource(R.string.settings_restrict_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textSecondary,
                        )
                    }
                    Switch(
                        checked = state.settings?.restrictUser ?: false,
                        onCheckedChange = viewModel::setRestrictUser,
                        enabled = !state.busy,
                    )
                }
            }

            CollapsibleSection(stringResource(R.string.settings_background_title)) {
                // Réglage purement local (aucune route Mercury) : la vérification de fond
                // interroge le roster toutes les N minutes quand l'app est fermée.
                val attention = remember { AttentionPrefs(context) }
                var attentionOn by remember { mutableStateOf(attention.enabled) }
                var interval by remember { mutableIntStateOf(attention.intervalMinutes) }
                var watchOn by remember { mutableStateOf(attention.persistent) }
                val notifications = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission(),
                ) { }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.settings_watch),
                            style = MaterialTheme.typography.bodyLarge,
                            color = colors.textPrimary,
                        )
                        Text(
                            text = if (watchOn) {
                                stringResource(R.string.settings_watch_on)
                            } else {
                                stringResource(R.string.settings_watch_off)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textSecondary,
                        )
                    }
                    Switch(
                        checked = watchOn,
                        onCheckedChange = { on ->
                            if (!on) {
                                attention.persistent = false
                                watchOn = false
                                WatchService.stop(context)
                            } else {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                    context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                                    PackageManager.PERMISSION_GRANTED
                                ) {
                                    notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                                // Le service est le seul moyen d'entendre un équipier sans
                                // ouvrir l'app ; s'il refuse de démarrer, on retombe sur la
                                // vérification périodique au lieu d'afficher un interrupteur menteur.
                                if (WatchService.start(context)) {
                                    attention.persistent = true
                                    watchOn = true
                                } else {
                                    attention.persistent = false
                                    watchOn = false
                                    AttentionSchedule.apply(context)
                                }
                            }
                        },
                    )
                }
                if (watchOn) {
                    Text(
                        text = stringResource(R.string.settings_battery_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textSecondary,
                    )
                    TextButton(onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
                            )
                        }
                    }) {
                        Text(stringResource(R.string.settings_battery_settings), color = colors.accent)
                    }
                }
                // Une ligne pour séparer les deux réglages : collés, ils se lisaient comme un
                // seul bloc de texte et le second interrupteur semblait appartenir au premier.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp)
                        .height(1.dp)
                        .background(colors.hairline),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.settings_alert_closed),
                            style = MaterialTheme.typography.bodyLarge,
                            color = colors.textPrimary,
                        )
                        Text(
                            text = if (attentionOn) {
                                stringResource(R.string.settings_interval_hint, interval)
                            } else {
                                stringResource(R.string.settings_no_notif_hint)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textSecondary,
                        )
                    }
                    Switch(
                        checked = attentionOn,
                        onCheckedChange = { on ->
                            attentionOn = on
                            attention.enabled = on
                            if (on) attention.forget() // une nouvelle activation ré-annonce l'état courant
                            AttentionSchedule.apply(context)
                        },
                    )
                }
                if (attentionOn) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.settings_interval),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textSecondary,
                            modifier = Modifier.weight(1f),
                        )
                        listOf(15, 30, 60).forEach { minutes ->
                            TextButton(onClick = {
                                interval = minutes
                                attention.intervalMinutes = minutes
                                AttentionSchedule.apply(context)
                            }) {
                                Text(
                                    text = stringResource(R.string.settings_minutes, minutes),
                                    color = if (minutes == interval) colors.accent else colors.textSecondary,
                                )
                            }
                        }
                    }
                    Text(
                        text = stringResource(R.string.settings_min_interval),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textSecondary,
                    )
                }
            }

            CollapsibleSection(stringResource(R.string.settings_budget_title)) {
                val usage = state.usage
                if (usage == null) {
                    Text(
                        text = stringResource(R.string.settings_usage_unavailable),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.settings_usage, formatTokens(usage.dailyUsed), formatTokens(usage.dailyBudget)),
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.textPrimary,
                    )
                    ProgressLine(usage.ratio)
                    Text(
                        text = stringResource(R.string.settings_usage_remaining, formatTokens(usage.remaining), usage.lastResetDate.ifBlank { "quotidien" }),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    usage.byChannel.forEach { (channel, tokens) ->
                        KeyValueRow(label = channel, value = formatTokens(tokens))
                    }
                }
            }

            CollapsibleSection(stringResource(R.string.settings_providers_title)) {
                // The only place a provider is described: Console used to carry a second copy with
                // the actions, and Réglages a read-only list of the same six rows.
                if (state.providers.isEmpty()) {
                    Text(
                        text = if (state.loading) stringResource(R.string.settings_loading) else stringResource(R.string.settings_providers_none),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                    )
                }
                state.providers.forEach { provider ->
                    val name = provider.name.ifBlank { "—" }
                    ProviderRow(
                        provider = provider,
                        busy = state.busy,
                        onEdit = { editingProvider = name },
                        onTest = { viewModel.testProvider(name) },
                    )
                    if (state.providerTestFor == name) {
                        state.providerTest?.let { ProviderTestLine(it) }
                    }
                }
                Text(
                    text = stringResource(R.string.settings_providers_restart),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            CollapsibleSection(stringResource(R.string.settings_server_title), defaultOpen = true) {
                ActionRow(
                    title = stringResource(R.string.settings_server_address),
                    subtitle = container.serverUrl.ifBlank { stringResource(R.string.settings_server_unset) },
                    trailing = stringResource(R.string.settings_change),
                    onClick = { showServerDialog = true },
                )
                Text(
                    text = stringResource(R.string.settings_server_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            CollapsibleSection(stringResource(R.string.settings_account_title)) {
                ActionRow(
                    title = stringResource(R.string.settings_account_username),
                    subtitle = container.lastUsername.ifBlank { "—" },
                    onClick = { dialog = AccountDialog.Username },
                )
                ActionRow(
                    title = stringResource(R.string.settings_account_password),
                    subtitle = stringResource(R.string.settings_account_password_hint),
                    onClick = { dialog = AccountDialog.Password },
                )
                ActionRow(
                    title = stringResource(R.string.settings_account_revoke),
                    subtitle = stringResource(R.string.settings_account_revoke_hint),
                    onClick = { viewModel.logoutServer() },
                )
            }

        }
    }

    editingProvider?.let { id ->
        ProviderEditDialog(
            id = id,
            onDismiss = { editingProvider = null },
            onConfirm = { apiKey, baseUrl, model, enabled ->
                editingProvider = null
                viewModel.saveProvider(id, apiKey, baseUrl, model, enabled)
            },
        )
    }

    if (showServerDialog) {
        ServerDialog(
            current = container.serverUrl,
            onDismiss = { showServerDialog = false },
            onConfirm = { url ->
                showServerDialog = false
                onChangeServer(url)
            },
        )
    }

    if (showModelPicker) {
        val models = state.models?.providers.orEmpty()
        AlertDialog(
            onDismissRequest = { showModelPicker = false },
            containerColor = colors.surface,
            title = { Text(stringResource(R.string.settings_chat_model), color = colors.textPrimary) },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 380.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    models.forEach { model ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.extraSmall)
                                .clickable(enabled = model.available) {
                                    showModelPicker = false
                                    viewModel.selectModel(model.name, model.model)
                                }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = model.displayLabel,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (model.available) colors.textPrimary else colors.textSecondary,
                                )
                                Text(
                                    text = "${model.group.ifBlank { model.name }} · ${model.model}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.textSecondary,
                                )
                            }
                            if (model.isCurrent) {
                                Icon(
                                    Icons.Rounded.Check,
                                    contentDescription = stringResource(R.string.settings_current_marker),
                                    tint = colors.accent,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showModelPicker = false }) { Text(stringResource(R.string.settings_close)) } },
        )
    }

    when (dialog) {
        AccountDialog.Username -> CredentialDialog(
            title = stringResource(R.string.settings_new_username),
            confirmLabel = stringResource(R.string.settings_save),
            secondLabel = stringResource(R.string.settings_username),
            onDismiss = { dialog = null },
            onConfirm = { current, value ->
                dialog = null
                viewModel.changeUsername(current, value)
            },
        )

        AccountDialog.Password -> CredentialDialog(
            title = stringResource(R.string.settings_new_password),
            confirmLabel = stringResource(R.string.settings_change_action),
            secondLabel = stringResource(R.string.settings_password),
            onDismiss = { dialog = null },
            onConfirm = { current, value ->
                dialog = null
                viewModel.changePassword(current, value)
            },
        )

        null -> Unit
    }
}

/**
 * Two-field dialog used for both account changes. Values are handed to the
 * ViewModel directly so they never land in observable state.
 */
@Composable
private fun CredentialDialog(
    title: String,
    confirmLabel: String,
    secondLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (current: String, value: String) -> Unit,
) {
    var current by remember { mutableStateOf("") }
    var value by remember { mutableStateOf("") }
    val colors = LocalMercuryColors.current

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = { Text(title, color = colors.textPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = current,
                    onValueChange = { current = it },
                    label = { Text(stringResource(R.string.settings_current_password)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text(secondLabel) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(current, value) },
                enabled = current.isNotBlank() && value.length >= 6,
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_cancel)) } },
    )
}

/**
 * The Mercury host, editable after the fact.
 *
 * It is a connection setting, so it sits with the account rows instead of only on the sign-in
 * screen: the address was asked once at first launch, which left "wipe the app" as the only way to
 * follow the server to another machine. Applying it closes the session — the cookie belongs to the
 * old host — and the sign-in screen comes back with the new address already filled in.
 */
@Composable
private fun ServerDialog(
    current: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember { mutableStateOf(current) }
    val colors = LocalMercuryColors.current
    val problem = ServerAddress.validate(value)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = { Text(stringResource(R.string.settings_server_dialog_title), color = colors.textPrimary) },
        text = {
            Column {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.settings_server_placeholder), color = colors.textSecondary) },
                    isError = problem != null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (problem != null) {
                    Text(
                        text = stringResource(
                            when (problem) {
                                ServerAddress.Problem.Empty -> R.string.settings_server_problem_empty
                                ServerAddress.Problem.UnsupportedScheme -> R.string.settings_server_problem_scheme
                                ServerAddress.Problem.MissingHost -> R.string.settings_server_problem_host
                            }
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.danger,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                Text(
                    text = stringResource(R.string.settings_server_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(ServerAddress.normalize(value)) },
                enabled = problem == null,
            ) { Text(stringResource(R.string.settings_server_apply)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_cancel)) } },
    )
}
