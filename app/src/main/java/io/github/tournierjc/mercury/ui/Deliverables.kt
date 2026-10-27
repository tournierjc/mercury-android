package io.github.tournierjc.mercury.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.github.tournierjc.mercury.data.BotOps
import io.github.tournierjc.mercury.data.dto.DeliverableDto
import io.github.tournierjc.mercury.ui.theme.LocalMercuryColors
import io.github.tournierjc.mercury.util.OutputPreviewState

/**
 * Livrables — the files a bot actually produced.
 *
 * One list, two callers: the bot sheet (one bot's folder) and the Console (the whole
 * fleet). Same rows, same three actions — aperçu, partager, supprimer — so a file
 * behaves the same wherever the owner finds it.
 */
@Composable
fun DeliverablesSection(
    title: String,
    outputs: List<DeliverableDto>,
    botNames: Map<String, String> = emptyMap(),
    showOrigin: Boolean = false,
    busy: Boolean = false,
    emptyText: String,
    onPreview: (DeliverableDto) -> Unit,
    onShare: (DeliverableDto) -> Unit,
    onDelete: (DeliverableDto) -> Unit,
) {
    val colors = LocalMercuryColors.current
    val now = remember(outputs) { System.currentTimeMillis() }
    var pendingDelete by remember { mutableStateOf<DeliverableDto?>(null) }

    SectionTitle(title)
    CardBlock {
        if (outputs.isEmpty()) {
            Text(
                text = emptyText,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
            return@CardBlock
        }
        outputs.forEach { output ->
            ActionRow(
                title = output.name,
                subtitle = if (showOrigin) {
                    BotOps.outputOrigin(output, botNames[output.botId].orEmpty(), now)
                } else {
                    BotOps.outputSubtitle(output, now)
                },
                trailing = "aperçu",
                onClick = { onPreview(output) },
            )
            ButtonRow {
                TextButton(onClick = { onPreview(output) }) { Text("Aperçu") }
                TextButton(onClick = { onShare(output) }, enabled = !busy) { Text("Partager") }
                TextButton(onClick = { pendingDelete = output }, enabled = !busy) {
                    Text("Supprimer", color = colors.danger)
                }
            }
        }
    }

    val target = pendingDelete
    if (target != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            containerColor = colors.surface,
            title = { Text("Supprimer ce livrable ?", color = colors.textPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = target.name,
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        color = colors.textPrimary,
                    )
                    Text(
                        text = "Le fichier est retiré du dossier de l'équipier. Cette action est définitive.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    onDelete(target)
                }) { Text("Supprimer", color = colors.danger) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Annuler") } },
        )
    }
}

/**
 * The preview: text inline (the route already caps at 64 kB), a small image decoded with
 * `BitmapFactory`, and a plain sentence for everything else — a phone is not a viewer for
 * every format, and the share sheet is one tap away.
 */
@Composable
fun OutputPreviewDialog(
    state: OutputPreviewState,
    onDismiss: () -> Unit,
    onShare: () -> Unit,
) {
    val colors = LocalMercuryColors.current
    val image = state.image
    val bitmap = remember(image) {
        image?.let { bytes -> BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = { Text(state.output.name, color = colors.textPrimary, style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                KeyValueRow(
                    "Fichier",
                    listOf(
                        if (state.output.final) "final" else "travail",
                        BotOps.sizeLabel(state.output.bytes),
                    ).joinToString(" · "),
                )
                when {
                    state.loading -> Text(
                        text = "Chargement…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                    )

                    bitmap != null -> Image(
                        bitmap = bitmap,
                        contentDescription = "Aperçu de ${state.output.name}",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    state.text != null -> {
                        Text(
                            text = state.text.ifBlank { "(fichier vide)" },
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = colors.textPrimary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(colors.surfaceMuted, RoundedCornerShape(12.dp))
                                .padding(10.dp),
                        )
                        if (state.truncated) {
                            Text(
                                text = "Aperçu tronqué aux 64 premiers ko — partagez le fichier pour le lire en entier.",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textSecondary,
                            )
                        }
                    }

                    else -> Text(
                        text = state.note ?: "Aperçu indisponible.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fermer") } },
        dismissButton = { TextButton(onClick = onShare) { Text("Partager") } },
    )
}
