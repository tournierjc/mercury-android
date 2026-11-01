package io.github.tournierjc.mercury.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.tournierjc.mercury.data.dto.ProviderDto
import io.github.tournierjc.mercury.data.dto.ProviderRoutingDto
import io.github.tournierjc.mercury.data.dto.ProviderTestResult
import io.github.tournierjc.mercury.ui.theme.LocalMercuryColors
import io.github.tournierjc.mercury.R

/**
 * The provider rows, shared by Réglages.
 *
 * They lived in Console, next to a read-only copy in Réglages: the same six providers, described
 * twice, with the actions only in one place. A provider is a setting, so it lives in Réglages,
 * and this file is the single implementation of what a provider row is.
 */

@Composable
internal fun ProviderRow(
    provider: ProviderDto,
    busy: Boolean,
    onEdit: () -> Unit,
    onTest: () -> Unit,
) {
    val colors = LocalMercuryColors.current
    val name = provider.name.ifBlank { "—" }
    val ready = provider.enabled && provider.hasKey
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        // A dot rather than a word: five of the six providers say the same thing, and the
        // exception has to be visible at a glance.
        Column(
            modifier = Modifier
                .size(8.dp)
                .background(
                    when {
                        !provider.enabled -> colors.textSecondary.copy(alpha = 0.35f)
                        provider.hasKey -> colors.accent
                        else -> colors.danger.copy(alpha = 0.7f)
                    },
                    CircleShape,
                ),
        ) {}
        Column(modifier = Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Text(
                text = if (provider.recommended) "$name ★" else name,
                style = MaterialTheme.typography.bodyLarge,
                color = if (ready) colors.textPrimary else colors.textSecondary,
                maxLines = 1,
            )
            Text(
                text = when {
                    !provider.enabled -> stringResource(R.string.prov_disabled)
                    !provider.hasKey -> stringResource(R.string.prov_no_key)
                    provider.model.isNotBlank() -> provider.model
                    else -> stringResource(R.string.prov_ready)
                } + provider.baseUrl.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty() +
                    provider.routing?.let { pin ->
                        // The pin is the answer to "why this host": worth a glance, not a screen.
                        val where = pin.order.joinToString(",").ifBlank { null }
                        val how = pin.quantizations.joinToString(",").ifBlank { null }
                        listOfNotNull(where, how).joinToString("/").let { if (it.isBlank()) "" else " · $it" }
                    }.orEmpty(),
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onEdit, enabled = !busy) {
            Icon(
                Icons.Rounded.Edit,
                contentDescription = stringResource(R.string.prov_edit, name),
                tint = colors.textSecondary,
                modifier = Modifier.size(19.dp),
            )
        }
        IconButton(onClick = onTest, enabled = !busy) {
            Icon(
                Icons.Rounded.Bolt,
                contentDescription = stringResource(R.string.prov_test, name),
                tint = colors.accent,
                modifier = Modifier.size(19.dp),
            )
        }
    }
}

@Composable
internal fun ProviderTestLine(result: ProviderTestResult) {
    val colors = LocalMercuryColors.current
    if (result.success) {
        val catalogue = result.models.take(4).joinToString(", ")
        Text(
            text = stringResource(R.string.prov_connected, result.models.size) +
                (if (catalogue.isNotBlank()) stringResource(R.string.prov_catalogue, catalogue) else "") +
                (if (result.recommendedModel.isNotBlank()) {
                    stringResource(R.string.prov_recommended, result.recommendedModel)
                } else {
                    ""
                }),
            style = MaterialTheme.typography.labelSmall,
            color = colors.accent,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    } else {
        Text(
            text = stringResource(R.string.prov_failed) + result.error.ifBlank { stringResource(R.string.prov_refused) },
            style = MaterialTheme.typography.labelSmall,
            color = colors.danger,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

/**
 * `POST /api/providers/:id` takes `apiKey`, `baseUrl`, `model` and `enabled`, each optional: an
 * empty field is not sent, so the stored value survives.
 */
/**
 * OpenRouter alone reads an endpoint pin: it fronts many hosts for the same weights, and the pin
 * says which one answers. Anywhere else the daemon refuses it, so the fields only appear here.
 */
private const val OPENROUTER_ID = "openrouter"

/** "inference-net, deepinfra" → ["inference-net", "deepinfra"], blanks dropped. */
private fun csvList(value: String): List<String> =
    value.split(',').map { it.trim() }.filter { it.isNotEmpty() }

/**
 * The provider's sheet, opened filled with what the daemon already holds.
 *
 * Every field but the key comes from the row itself (`baseUrl`, `model`, `enabled`). The key is the
 * exception by design: `/api/providers` only ever returns its mask, so the field starts empty and
 * says so — an empty key means "keep the saved one", never "erase it".
 */
@Composable
internal fun ProviderEditDialog(
    id: String,
    existing: ProviderRoutingDto? = null,
    savedBaseUrl: String = "",
    savedModel: String = "",
    savedEnabled: Boolean = true,
    onDismiss: () -> Unit,
    onConfirm: (
        apiKey: String?,
        baseUrl: String?,
        model: String?,
        enabled: Boolean?,
        routing: ProviderRoutingDto?,
    ) -> Unit,
) {
    val colors = LocalMercuryColors.current
    var apiKey by remember { mutableStateOf("") }
    var endpointOrder by remember { mutableStateOf(existing?.order?.joinToString(", ").orEmpty()) }
    var quantizations by remember { mutableStateOf(existing?.quantizations?.joinToString(", ").orEmpty()) }
    var baseUrl by remember(id) { mutableStateOf(savedBaseUrl) }
    var model by remember(id) { mutableStateOf(savedModel) }
    var enabled by remember(id) { mutableStateOf(savedEnabled) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = { Text(id, color = colors.textPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text(stringResource(R.string.prov_key_label)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text(stringResource(R.string.prov_url_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    label = { Text(stringResource(R.string.prov_model_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, keyboardType = KeyboardType.Text),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (id == OPENROUTER_ID) {
                    OutlinedTextField(
                        value = endpointOrder,
                        onValueChange = { endpointOrder = it },
                        label = { Text(stringResource(R.string.prov_routing_order)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = quantizations,
                        onValueChange = { quantizations = it },
                        label = { Text(stringResource(R.string.prov_routing_quant)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = stringResource(R.string.prov_routing_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textSecondary,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.prov_enabled),
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.textPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        apiKey.ifBlank { null },
                        baseUrl.ifBlank { null },
                        model.ifBlank { null },
                        enabled,
                        if (id == OPENROUTER_ID) {
                            ProviderRoutingDto(
                                order = csvList(endpointOrder),
                                quantizations = csvList(quantizations),
                            )
                        } else {
                            null
                        },
                    )
                },
            ) { Text(stringResource(R.string.prov_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.prov_cancel)) } },
    )
}
