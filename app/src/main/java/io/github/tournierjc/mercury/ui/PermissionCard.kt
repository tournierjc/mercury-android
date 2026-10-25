package io.github.tournierjc.mercury.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.tournierjc.mercury.data.Interaction
import io.github.tournierjc.mercury.data.InteractionKind
import io.github.tournierjc.mercury.ui.theme.LocalMercuryColors

/**
 * One card for every question the runtime can block on: tool approval, loop
 * "keep going?", free-form choice and auto-approval mode. Mercury answers all of
 * them through `POST /api/chat/permission/:id`, so only the copy varies.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InteractionCard(
    interaction: Interaction,
    onAction: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalMercuryColors.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(22.dp))
            .padding(18.dp),
    ) {
        Text(
            text = title(interaction.kind),
            style = MaterialTheme.typography.labelSmall,
            color = colors.accent,
        )
        Text(
            text = interaction.text,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textPrimary,
            modifier = Modifier.padding(top = 8.dp, bottom = 14.dp),
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            interaction.options.forEach { option ->
                if (isSecondary(option)) {
                    OutlinedButton(
                        onClick = { onAction(option) },
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.textSecondary),
                    ) { Text(label(option)) }
                } else {
                    Button(
                        onClick = { onAction(option) },
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isPrimary(option)) colors.textPrimary else colors.surfaceMuted,
                            contentColor = if (isPrimary(option)) colors.canvas else colors.textPrimary,
                        ),
                    ) { Text(label(option)) }
                }
            }
        }
        Text(
            text = footnote(interaction.kind),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

private fun title(kind: InteractionKind): String = when (kind) {
    InteractionKind.Permission -> "AUTORISATION DEMANDÉE"
    InteractionKind.Continue -> "LE RUNTIME DEMANDE À CONTINUER"
    InteractionKind.Choice -> "CHOIX DEMANDÉ"
    InteractionKind.Mode -> "MODE D'APPROBATION"
}

private fun footnote(kind: InteractionKind): String = when (kind) {
    InteractionKind.Permission -> "Le runtime attend ta réponse pour continuer."
    InteractionKind.Continue -> "Une boucle a été détectée : continue ou arrête ici."
    InteractionKind.Choice -> "Réponds pour débloquer l'agent."
    InteractionKind.Mode -> "Tu peux changer d'avis à tout moment dans les réglages."
}

private fun label(action: String): String = when (action) {
    "yes", "confirm", "ok" -> "Autoriser"
    "always" -> "Toujours"
    "no", "cancel", "stop" -> "Refuser"
    "ask-me" -> "Rester en demande"
    "allow-all" -> "Tout autoriser"
    else -> action.replaceFirstChar { it.uppercase() }
}

private fun isPrimary(action: String): Boolean = action in setOf("yes", "confirm", "ok", "always", "allow-all")

private fun isSecondary(action: String): Boolean = action in setOf("no", "cancel", "stop", "ask-me")
