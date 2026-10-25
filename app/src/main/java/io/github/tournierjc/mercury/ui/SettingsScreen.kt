package io.github.tournierjc.mercury.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.tournierjc.mercury.MercuryApp
import io.github.tournierjc.mercury.ui.theme.LocalMercuryColors
import io.github.tournierjc.mercury.util.formatTokens
import io.github.tournierjc.mercury.vm.SettingsViewModel

private enum class AccountDialog { Username, Password }

/**
 * Model picker, tool-approval mode, token budget, provider wiring and account
 * actions. Each control writes straight to the matching Mercury route.
 */
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val container = remember { (context.applicationContext as MercuryApp).container }
    val viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.factory(container))
    val state by viewModel.state.collectAsState()
    val colors = LocalMercuryColors.current

    var dialog by remember { mutableStateOf<AccountDialog?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.canvas)
            .safeDrawingPadding(),
    ) {
        ScreenHeader(
            title = "Réglages",
            subtitle = state.models?.current?.displayLabel ?: "Mercury",
            onBack = onBack,
            onRefresh = viewModel::refresh,
        )
        state.message?.let { InlineBanner(it, isError = false, onDismiss = viewModel::clearBanner) }
        state.error?.let { InlineBanner(it, isError = true, onDismiss = viewModel::clearBanner) }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp),
        ) {
            SectionTitle("Modèle")
            CardBlock {
                state.models?.providers?.forEach { model ->
                    ActionRow(
                        title = model.displayLabel,
                        subtitle = "${model.group.ifBlank { model.name }} · ${model.model}",
                        trailing = if (model.isCurrent) "actif" else if (!model.available) "indisponible" else null,
                        selected = model.isCurrent,
                        enabled = model.available,
                        onClick = { viewModel.selectModel(model.name, model.model) },
                    )
                }
                if (state.models?.providers.isNullOrEmpty()) {
                    Text(
                        text = if (state.loading) "Chargement…" else "Aucun modèle exposé par le runtime.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                    )
                }
            }

            SectionTitle("Approbation des outils")
            CardBlock {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Tout autoriser sans confirmation",
                            style = MaterialTheme.typography.bodyLarge,
                            color = colors.textPrimary,
                        )
                        Text(
                            text = "Mode actuel : ${state.settings?.permissionMode ?: "—"}",
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
                            text = "Restreindre l'utilisateur",
                            style = MaterialTheme.typography.bodyLarge,
                            color = colors.textPrimary,
                        )
                        Text(
                            text = "Limite les actions demandées par le chat",
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

            SectionTitle("Budget du jour")
            CardBlock {
                val usage = state.usage
                if (usage == null) {
                    Text(
                        text = "Compteur indisponible.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                    )
                } else {
                    Text(
                        text = "${formatTokens(usage.dailyUsed)} / ${formatTokens(usage.dailyBudget)} tokens",
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.textPrimary,
                    )
                    ProgressLine(usage.ratio)
                    Text(
                        text = "Reste ${formatTokens(usage.remaining)} · reset ${usage.lastResetDate.ifBlank { "quotidien" }}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    usage.byChannel.forEach { (channel, tokens) ->
                        KeyValueRow(label = channel, value = formatTokens(tokens))
                    }
                }
            }

            SectionTitle("Fournisseurs")
            CardBlock {
                state.providers.forEach { provider ->
                    ActionRow(
                        title = provider.name,
                        subtitle = listOf(provider.model, provider.baseUrl.takeIf { it.isNotBlank() })
                            .filterNotNull().joinToString(" · "),
                        trailing = when {
                            provider.hasKey -> if (provider.enabled) "clé ok" else "désactivé"
                            else -> "sans clé"
                        },
                    )
                }
                if (state.providers.isEmpty()) {
                    Text(
                        text = "Aucun fournisseur configuré.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                    )
                }
            }

            SectionTitle("Compte")
            CardBlock {
                ActionRow(
                    title = "Changer l'identifiant",
                    subtitle = container.lastUsername.ifBlank { "—" },
                    onClick = { dialog = AccountDialog.Username },
                )
                ActionRow(
                    title = "Changer le mot de passe",
                    subtitle = "Le mot de passe actuel est demandé",
                    onClick = { dialog = AccountDialog.Password },
                )
                ActionRow(
                    title = "Révoquer la session serveur",
                    subtitle = "Déconnecte aussi les autres onglets",
                    onClick = { viewModel.logoutServer() },
                )
            }

            Text(
                text = "Client Android Mercury · lecture directe de l'API du dashboard",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary,
                modifier = Modifier.padding(vertical = 18.dp, horizontal = 4.dp),
            )
        }
    }

    when (dialog) {
        AccountDialog.Username -> CredentialDialog(
            title = "Nouvel identifiant",
            confirmLabel = "Enregistrer",
            secondLabel = "Identifiant",
            onDismiss = { dialog = null },
            onConfirm = { current, value ->
                dialog = null
                viewModel.changeUsername(current, value)
            },
        )

        AccountDialog.Password -> CredentialDialog(
            title = "Nouveau mot de passe",
            confirmLabel = "Changer",
            secondLabel = "Mot de passe",
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
                    label = { Text("Mot de passe actuel") },
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
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}
