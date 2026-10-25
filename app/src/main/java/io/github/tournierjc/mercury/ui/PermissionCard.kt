package io.github.tournierjc.mercury.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import io.github.tournierjc.mercury.data.PermissionPrompt
import io.github.tournierjc.mercury.ui.theme.LocalMercuryColors

private fun label(action: String): String = when (action) {
    "yes" -> "Autoriser"
    "always" -> "Toujours"
    "no" -> "Refuser"
    else -> action
}

@Composable
fun PermissionCard(
    prompt: PermissionPrompt,
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
            text = "AUTORISATION DEMANDÉE",
            style = MaterialTheme.typography.labelSmall,
            color = colors.accent,
        )
        Text(
            text = prompt.prompt,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textPrimary,
            modifier = Modifier.padding(top = 8.dp, bottom = 14.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            prompt.options.forEach { action ->
                if (action == "no") {
                    OutlinedButton(
                        onClick = { onAction(action) },
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.textSecondary),
                    ) { Text(label(action)) }
                } else {
                    Button(
                        onClick = { onAction(action) },
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (action == "yes") colors.textPrimary else colors.surfaceMuted,
                            contentColor = if (action == "yes") colors.canvas else colors.textPrimary,
                        ),
                    ) { Text(label(action)) }
                }
            }
        }
        Text(
            text = "Le runtime attend ta réponse pour continuer.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}
