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
import io.github.tournierjc.mercury.data.InstancePatch
import io.github.tournierjc.mercury.data.ServerAddress
import io.github.tournierjc.mercury.device.AppLanguage
import io.github.tournierjc.mercury.device.Languages
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import io.github.tournierjc.mercury.data.dto.InstanceConfig
import io.github.tournierjc.mercury.data.dto.ProviderDto
import io.github.tournierjc.mercury.ui.theme.LocalMercuryColors
import io.github.tournierjc.mercury.util.formatTokens
import io.github.tournierjc.mercury.vm.SettingsViewModel

private enum class AccountDialog { Username, Password }

/**
 * Model picker, tool-approval mode, token budget, provider wiring, the instance the app is attached
 * to and account actions. Each control writes straight to the matching Mercury route.
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
    var showLanguagePicker by remember { mutableStateOf(false) }
    // The provider itself, not its name: the pin dialog opens on the pin already stored.
    var editingProvider by remember { mutableStateOf<ProviderDto?>(null) }
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
            refreshing = state.loading,
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
                // One row that opens the list, not one row per language: the handful of languages
                // the app ships already filled the section, and every added one pushed the model
                // and the tools further down. The labels are each language's own name, never
                // translated — an English speaker looking for French recognises « Français ».
                val current = LocaleStore.language(context)
                Box {
                    ActionRow(
                        title = languageLabel(current),
                        subtitle = stringResource(R.string.settings_language_hint),
                        trailing = stringResource(R.string.settings_change),
                        onClick = { showLanguagePicker = true },
                    )
                    DropdownMenu(
                        expanded = showLanguagePicker,
                        onDismissRequest = { showLanguagePicker = false },
                    ) {
                        Languages.all.forEach { language ->
                            DropdownMenuItem(
                                text = { Text(languageLabel(language)) },
                                trailingIcon = if (language == current) {
                                    { Icon(Icons.Rounded.Check, contentDescription = null, tint = colors.accent) }
                                } else {
                                    null
                                },
                                onClick = {
                                    showLanguagePicker = false
                                    if (language != current) {
                                        LocaleStore.set(context, language)
                                        (context as? android.app.Activity)?.recreate()
                                    }
                                },
                            )
                        }
                    }
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
                        ?: if (state.loading) stringResource(R.string.common_loading) else stringResource(R.string.settings_no_model),
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
                                attention.watchDisabled = true
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
                                    attention.watchDisabled = false
                                    watchOn = true
                                } else {
                                    attention.watchDisabled = true
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
                    // A zero budget is no ceiling: "9.9M / 0 tokens" would read as the opposite, and a
                    // progress bar against no ceiling measures nothing.
                    val unlimited = usage.dailyBudget <= 0L
                    Text(
                        text = if (unlimited) {
                            stringResource(R.string.settings_usage_unlimited, formatTokens(usage.dailyUsed))
                        } else {
                            stringResource(R.string.settings_usage, formatTokens(usage.dailyUsed), formatTokens(usage.dailyBudget))
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.textPrimary,
                    )
                    if (!unlimited) {
                        ProgressLine(usage.ratio)
                        Text(
                            text = stringResource(R.string.settings_usage_remaining, formatTokens(usage.remaining), usage.lastResetDate.ifBlank { "quotidien" }),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textSecondary,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
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
                        text = if (state.loading) stringResource(R.string.common_loading) else stringResource(R.string.settings_providers_none),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                    )
                }
                state.providers.forEach { provider ->
                    val name = provider.name.ifBlank { "—" }
                    ProviderRow(
                        provider = provider,
                        busy = state.busy,
                        onEdit = { editingProvider = provider },
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

            CollapsibleSection(stringResource(R.string.instance_title)) {
                InstanceSection(
                    instance = state.instance,
                    workspace = state.settings?.workspace.orEmpty(),
                    providers = state.providerNames,
                    busy = state.busy,
                    loading = state.loading,
                    onSave = viewModel::saveInstance,
                    onSaveWorkspace = viewModel::saveWorkspace,
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

    editingProvider?.let { provider ->
        val id = provider.name.ifBlank { "—" }
        ProviderEditDialog(
            id = id,
            existing = provider.routing,
            savedBaseUrl = provider.baseUrl,
            savedModel = provider.model,
            savedEnabled = provider.enabled,
            onDismiss = { editingProvider = null },
            onConfirm = { apiKey, baseUrl, model, enabled, routing ->
                editingProvider = null
                viewModel.saveProvider(id, apiKey, baseUrl, model, enabled, routing)
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

/**
 * What the picker shows for a language: its own name, or — for "follow the phone" — the word the
 * current UI language uses for the system, the one label that has to be translated.
 */
@Composable
private fun languageLabel(language: AppLanguage): String =
    if (language.followsSystem) stringResource(R.string.settings_language_system) else language.nativeName

/**
 * Réglages › Instance — ce que le démon dit de lui-même : le nom et le propriétaire qu'il annonce,
 * le fournisseur par défaut, le plafond quotidien de jetons et le dossier où l'agent travaille.
 *
 * Les quatre premiers partent ensemble par `PUT /api/config`; le dossier a sa propre route
 * (`PUT /api/chat/settings`) et donc son propre bouton. Un seul bouton pour les deux ferait dire
 * « enregistré » à l'écran alors que la moitié du formulaire a échoué.
 *
 * L'intervalle de cœur est affiché et *pas* modifiable : la route d'écriture ne l'accepte pas, il se
 * règle dans `config.yaml` ou par l'environnement du démon. Un chiffre en lecture seule vaut mieux
 * qu'un champ qui ne part jamais.
 */
@Composable
private fun InstanceSection(
    instance: InstanceConfig?,
    workspace: String,
    providers: List<String>,
    busy: Boolean,
    loading: Boolean,
    onSave: (name: String, owner: String, defaultProvider: String, tokenBudget: String) -> Unit,
    onSaveWorkspace: (path: String) -> Unit,
) {
    val colors = LocalMercuryColors.current
    if (instance == null) {
        Text(
            text = stringResource(if (loading) R.string.common_loading else R.string.instance_unavailable),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
        )
        return
    }
    // Le formulaire repart de la réponse du démon : après un enregistrement, ce qui s'affiche est ce
    // qui est stocké — jamais la frappe qui a servi à l'écrire.
    var name by remember(instance) { mutableStateOf(instance.identity.name) }
    var owner by remember(instance) { mutableStateOf(instance.identity.owner) }
    var provider by remember(instance) { mutableStateOf(instance.defaultProvider) }
    // `0` est une valeur (pas de plafond), pas une absence : le champ montre le nombre tel quel et
    // seul un champ vidé veut dire « inchangé ».
    // Zero is "no ceiling" and reads better as an empty field than as a number that looks like a
    // limit of none; typing 0 lands on the same empty field (see budgetText below).
    var budget by remember(instance) { mutableStateOf(InstancePatch.budgetField(instance.tokenBudget)) }
    var folder by remember(workspace) { mutableStateOf(workspace) }
    var showProviders by remember { mutableStateOf(false) }

    val invalidBudget = budget.isNotBlank() && (budget.toLongOrNull() ?: -1L) < 0
    val canSave = name.isNotBlank() && !invalidBudget && !busy

    Text(
        text = stringResource(R.string.instance_identity_hint),
        style = MaterialTheme.typography.labelSmall,
        color = colors.textSecondary,
    )
    OutlinedTextField(
        value = name,
        onValueChange = { name = it },
        label = { Text(stringResource(R.string.status_label_agent_name)) },
        singleLine = true,
        isError = name.isBlank(),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    )
    if (name.isBlank()) {
        Text(
            text = stringResource(R.string.instance_name_required),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.danger,
        )
    }
    // Le propriétaire n'est pas obligatoire : une instance peut n'en annoncer aucun, et un champ
    // vidé veut dire « inchangé » (voir `InstancePatch`) — l'exiger forcerait à inventer un nom.
    OutlinedTextField(
        value = owner,
        onValueChange = { owner = it },
        label = { Text(stringResource(R.string.status_label_owner)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    )

    // Même forme que le choix de langue : une ligne qui ouvre la liste, pas une ligne par
    // fournisseur — la section « Fournisseurs » juste au-dessus en dresse déjà la liste complète.
    Box(modifier = Modifier.padding(top = 6.dp)) {
        ActionRow(
            title = stringResource(R.string.instance_provider_title),
            subtitle = provider.ifBlank { stringResource(R.string.instance_provider_none) },
            trailing = if (providers.isEmpty()) null else stringResource(R.string.settings_change),
            enabled = providers.isNotEmpty() && !busy,
            onClick = { showProviders = true },
        )
        DropdownMenu(
            expanded = showProviders,
            onDismissRequest = { showProviders = false },
        ) {
            providers.forEach { key ->
                DropdownMenuItem(
                    text = { Text(key) },
                    trailingIcon = if (key == provider) {
                        { Icon(Icons.Rounded.Check, contentDescription = null, tint = colors.accent) }
                    } else {
                        null
                    },
                    onClick = {
                        provider = key
                        showProviders = false
                    },
                )
            }
        }
    }
    Text(
        text = stringResource(R.string.instance_provider_hint),
        style = MaterialTheme.typography.labelSmall,
        color = colors.textSecondary,
    )

    OutlinedTextField(
        value = budget,
        onValueChange = { budget = InstancePatch.budgetText(it) },
        label = { Text(stringResource(R.string.instance_budget_title)) },
        singleLine = true,
        isError = invalidBudget,
        supportingText = { Text(stringResource(R.string.instance_budget_hint), color = colors.textSecondary) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    )
    if (invalidBudget) {
        Text(
            text = stringResource(R.string.instance_budget_invalid),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.danger,
        )
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    ) {
        OutlinedTextField(
            value = folder,
            onValueChange = { folder = it },
            label = { Text(stringResource(R.string.instance_workspace_title)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
            modifier = Modifier.weight(1f),
        )
        // Le glyphe du composeur : 40 dp de cible et 20 dp de trait. Un second bouton texte aurait
        // ajouté un verbe de plus à une section qui en a déjà un.
        Box(
            modifier = Modifier.size(ComposerControlSize),
            contentAlignment = Alignment.Center,
        ) {
            IconButton(
                onClick = { onSaveWorkspace(folder) },
                enabled = !busy && folder.isNotBlank() && folder.trim() != workspace.trim(),
                modifier = Modifier.size(ComposerControlSize),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = stringResource(R.string.settings_save),
                    tint = colors.accent,
                    modifier = Modifier.size(ComposerControlIcon),
                )
            }
        }
    }
    Text(
        text = stringResource(R.string.instance_workspace_hint),
        style = MaterialTheme.typography.labelSmall,
        color = colors.textSecondary,
        modifier = Modifier.padding(top = 2.dp),
    )

    KeyValueRow(
        label = stringResource(R.string.instance_heartbeat_title),
        value = stringResource(R.string.instance_heartbeat_value, instance.heartbeatInterval.toString()),
    )
    Text(
        text = stringResource(R.string.instance_heartbeat_readonly),
        style = MaterialTheme.typography.labelSmall,
        color = colors.textSecondary,
    )

    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        horizontalArrangement = Arrangement.End,
    ) {
        TextButton(
            onClick = { onSave(name, owner, provider, budget) },
            enabled = canSave,
        ) {
            Text(
                text = stringResource(R.string.settings_save),
                color = if (canSave) colors.accent else colors.textSecondary,
            )
        }
    }
}
