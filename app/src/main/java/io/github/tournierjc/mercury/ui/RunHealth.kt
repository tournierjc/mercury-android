package io.github.tournierjc.mercury.ui

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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.github.tournierjc.mercury.data.BotOps
import io.github.tournierjc.mercury.data.RunRow
import io.github.tournierjc.mercury.data.dto.DlqJobDto
import io.github.tournierjc.mercury.data.dto.RunTranscriptDto
import io.github.tournierjc.mercury.ui.theme.LocalMercuryColors

/**
 * Run health: what the bot did, how it ended, and what the queue gave up on.
 *
 * A row says when, how it ended and how long it took; tapping it opens the transcript
 * (prompt, reply, tool trace). A run whose transcript was evicted still lists — the
 * journal line is the durable record — but has nothing to open.
 */
@Composable
fun RunsSection(
    runs: List<RunRow>,
    emptyText: String,
    onOpen: (RunRow) -> Unit,
) {
    val colors = LocalMercuryColors.current
    // Collapsible like every other block of the sheet: a run list is a log, not the headline.
    CollapsibleSection("Runs") {
        if (runs.isEmpty()) {
            Text(text = emptyText, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
            return@CollapsibleSection
        }
        runs.take(12).forEach { run ->
            ActionRow(
                title = BotOps.runSubtitle(run),
                subtitle = BotOps.runDetail(run).ifBlank { run.summary.take(140) },
                trailing = if (run.hasTranscript) "détail" else "",
                enabled = run.hasTranscript,
                onClick = if (run.hasTranscript) ({ onOpen(run) }) else null,
            )
        }
        if (runs.size > 12) {
            Text(
                text = "… ${runs.size - 12} run(s) plus ancien(s)",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/** One run's transcript: header, error, prompt, reply and the tool trace. */
@Composable
fun RunDetailDialog(
    transcript: RunTranscriptDto?,
    loading: Boolean,
    onDismiss: () -> Unit,
) {
    val colors = LocalMercuryColors.current

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = { Text("Détail du run", color = colors.textPrimary) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val loaded = transcript
                if (loading || loaded == null) {
                    Text("Chargement…", style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
                    return@Column
                }
                val record = loaded.record
                KeyValueRow("État", BotOps.runStateLabel(record.state), highlight = !BotOps.runStateFailed(record.state))
                KeyValueRow("Démarré", BotOps.dateLabel(record.startedAt))
                KeyValueRow("Durée", BotOps.durationLabel(record.durationMs))
                KeyValueRow("Tokens", BotOps.tokensLabel(record.tokensIn, record.tokensOut))
                KeyValueRow("Déclencheur", record.trigger.ifBlank { "—" })
                KeyValueRow("Outils", "${record.toolCalls} appel(s) · ${record.steps} étape(s)")
                record.outcome.takeIf { it.isNotBlank() }?.let { KeyValueRow("Résultat", it) }
                record.summary.takeIf { it.isNotBlank() }?.let { KeyValueRow("Résumé", it) }
                record.error.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = "Erreur : $it",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = colors.danger,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colors.surfaceMuted, RoundedCornerShape(12.dp))
                            .padding(10.dp),
                    )
                }
                loaded.prompt.takeIf { it.isNotBlank() }?.let { Block("Consigne", it) }
                loaded.output.takeIf { it.isNotBlank() }?.let { Block("Réponse", it) }
                if (loaded.trace.isNotEmpty()) {
                    Text(
                        text = "TRACE",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    loaded.trace.take(40).forEach { step ->
                        Text(
                            text = BotOps.traceLabel(step),
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = if (step.ok) colors.textPrimary else colors.danger,
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fermer") } },
    )
}

@Composable
private fun Block(label: String, text: String) {
    val colors = LocalMercuryColors.current
    Text(
        text = label.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = colors.textSecondary,
        modifier = Modifier.padding(top = 8.dp),
    )
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        color = colors.textPrimary,
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surfaceMuted, RoundedCornerShape(12.dp))
            .padding(10.dp),
    )
}

/**
 * Incidents (DLQ): the jobs the queue abandoned, with a replay per incident and the
 * fleet-wide purge — which is confirmed, because it drops every bot's dead letters.
 */
@Composable
fun DlqSection(
    incidents: List<DlqJobDto>,
    busy: Boolean,
    onReplay: (DlqJobDto) -> Unit,
    onClear: () -> Unit,
) {
    val colors = LocalMercuryColors.current
    var confirmClear by remember { mutableStateOf(false) }

    CollapsibleSection("Incidents") {
        if (incidents.isEmpty()) {
            Text(
                text = "Aucun incident : la file n'a abandonné aucun job.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
            return@CollapsibleSection
        }
        incidents.forEach { job ->
            ActionRow(
                title = job.prompt.take(120).ifBlank { "job ${job.id.take(8)}" },
                subtitle = BotOps.incidentSubtitle(job),
                trailing = "relancer",
                enabled = !busy,
                onClick = { onReplay(job) },
            )
        }
        ButtonRow {
            TextButton(onClick = { confirmClear = true }, enabled = !busy) {
                Text("Vider la DLQ", color = colors.danger)
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            containerColor = colors.surface,
            title = { Text("Vider la DLQ ?", color = colors.textPrimary) },
            text = {
                Text(
                    text = "Tous les jobs abandonnés de toute la flotte sont supprimés. " +
                        "Rien n'est relancé : utilisez « Relancer » sur un incident pour le rejouer.",
                    color = colors.textSecondary,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    onClear()
                }) { Text("Vider", color = colors.danger) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Annuler") } },
        )
    }
}
