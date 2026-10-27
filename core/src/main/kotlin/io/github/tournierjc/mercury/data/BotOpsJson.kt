package io.github.tournierjc.mercury.data

import io.github.tournierjc.mercury.data.dto.BotRunDto
import io.github.tournierjc.mercury.data.dto.DeliverableDto
import io.github.tournierjc.mercury.data.dto.DlqJobDto
import io.github.tournierjc.mercury.data.dto.RunSummaryDto
import io.github.tournierjc.mercury.data.dto.TraceStepDto
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToLong

/** What a deliverable is, as far as a phone can show it without a decoder. */
enum class OutputKind { Text, Image, Binary }

/**
 * One line of the run list.
 *
 * `GET /api/bots/:id/runs` only knows transcript file names, so the status, the
 * duration and the tokens come from the journal line with the same run id. A run
 * whose transcript was evicted still shows its journal line (not tappable); a run
 * whose journal line rotated away shows "—" (still tappable).
 */
data class RunRow(
    val runId: String,
    val state: String,
    val startedAt: Long,
    val mtimeMs: Long,
    val durationMs: Long,
    val tokensIn: Long,
    val tokensOut: Long,
    val summary: String,
    val error: String,
    val steps: Int,
    val toolCalls: Int,
    val hasTranscript: Boolean,
)

/**
 * Reading rules for deliverables, runs and DLQ incidents.
 *
 * The labels are here, not in the screens, so the same file name reads the same
 * way in the bot sheet and in the fleet inbox — and so they can be unit tested
 * without a device.
 */
object BotOps {

    private val TEXT_EXTENSIONS = setOf(
        "md", "markdown", "txt", "json", "jsonl", "csv", "tsv", "log", "yaml", "yml",
        "ts", "tsx", "js", "kt", "java", "py", "sh", "html", "xml", "toml", "ini",
        "sql", "r", "css", "env",
    )
    private val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "webp", "gif", "bmp")

    /** Images larger than this are shared, not decoded into a dialog. */
    const val MAX_INLINE_IMAGE_BYTES = 4L * 1024 * 1024

    /** The extension of a deliverable, lowercased. Names may carry a `work/` prefix. */
    fun extension(name: String): String {
        val base = name.substringAfterLast('/')
        if (!base.contains('.')) return ""
        return base.substringAfterLast('.').lowercase()
    }

    fun kind(name: String): OutputKind = when (extension(name)) {
        in TEXT_EXTENSIONS -> OutputKind.Text
        in IMAGE_EXTENSIONS -> OutputKind.Image
        else -> OutputKind.Binary
    }

    /** Only text files go through the preview route: it decodes the bytes as UTF-8. */
    fun previewable(output: DeliverableDto): Boolean =
        kind(output.name) == OutputKind.Text && output.bytes > 0

    fun inlineImage(output: DeliverableDto): Boolean =
        kind(output.name) == OutputKind.Image && output.bytes in 1..MAX_INLINE_IMAGE_BYTES

    fun isFinal(output: DeliverableDto): Boolean = output.final

    /**
     * The type a share sheet needs, mirroring the daemon's `MIME_BY_EXT` so a file
     * shared from the phone opens the same way it would from the dashboard.
     */
    fun mimeType(name: String): String = when (extension(name)) {
        "md" -> "text/markdown"
        "txt", "log" -> "text/plain"
        "json", "jsonl" -> "application/json"
        "csv" -> "text/csv"
        "yaml", "yml" -> "application/yaml"
        "html" -> "text/html"
        "svg" -> "image/svg+xml"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "pdf" -> "application/pdf"
        "zip" -> "application/zip"
        "tar" -> "application/x-tar"
        "gz" -> "application/gzip"
        else -> "application/octet-stream"
    }

    /** `512 o`, `12,4 ko`, `3,1 Mo` — what a phone header can afford. */
    fun sizeLabel(bytes: Long): String {
        if (bytes < 0) return "—"
        return when {
            bytes >= 1024L * 1024 -> "${oneDecimal(bytes / (1024.0 * 1024.0))} Mo"
            bytes >= 1024L -> "${oneDecimal(bytes / 1024.0)} ko"
            else -> "$bytes o"
        }
    }

    private fun oneDecimal(value: Double): String {
        // Half-up: 3,1999 Mo reads "3,2 Mo", not "3,1" — a size is an approximation,
        // unlike the token counter which truncates on purpose.
        val rounded = (value * 10).roundToLong()
        val whole = rounded / 10
        val tenth = rounded % 10
        return if (tenth == 0L) "$whole" else "$whole,$tenth"
    }

    private val stampFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("dd/MM HH:mm").withZone(ZoneId.systemDefault())

    /** `27/10 09:31`; epoch 0 means "unknown", never 01/01 1970. */
    fun dateLabel(epochMs: Long): String =
        if (epochMs <= 0) "—" else stampFormatter.format(Instant.ofEpochMilli(epochMs))

    /** `il y a 2 h` — a relative age is what an inbox needs. */
    fun ageLabel(epochMs: Long, nowMs: Long): String {
        if (epochMs <= 0) return "—"
        val seconds = (nowMs - epochMs) / 1000
        return when {
            seconds < 60 -> "à l'instant"
            seconds < 3_600 -> "il y a ${seconds / 60} min"
            seconds < 86_400 -> "il y a ${seconds / 3_600} h"
            else -> "il y a ${seconds / 86_400} j"
        }
    }

    /** `final · 12,4 ko · il y a 2 h` or `travail · …` for drafts under `work/`. */
    fun outputSubtitle(output: DeliverableDto, nowMs: Long): String = listOf(
        if (output.final) "final" else "travail",
        sizeLabel(output.bytes),
        ageLabel(output.mtimeMs, nowMs),
    ).joinToString(" · ")

    /** The line under the file name in the fleet inbox: which bot produced it. */
    fun outputOrigin(output: DeliverableDto, botName: String, nowMs: Long): String =
        "${botName.ifBlank { output.botId }} · ${outputSubtitle(output, nowMs)}"

    /** `réussi`, `échec`, `en cours`… The journal's `state` is the outcome of one run. */
    fun runStateLabel(state: String): String = when (state.lowercase()) {
        "completed", "done", "success", "ok" -> "réussi"
        "failed", "error" -> "échec"
        "cancelled", "canceled", "timeout" -> "interrompu"
        "running" -> "en cours"
        "queued", "pending" -> "en file"
        else -> state.ifBlank { "—" }
    }

    fun runStateFailed(state: String): Boolean = state.lowercase() in setOf("failed", "error", "cancelled", "canceled", "timeout")

    /** `820 ms`, `12,3 s`, `3 min 05 s`. */
    fun durationLabel(ms: Long): String {
        if (ms <= 0) return "—"
        return when {
            ms < 1_000 -> "$ms ms"
            ms < 60_000 -> "${oneDecimal(ms / 1_000.0)} s"
            else -> {
                val minutes = ms / 60_000
                val seconds = (ms % 60_000) / 1_000
                "$minutes min ${seconds.toString().padStart(2, '0')} s"
            }
        }
    }

    /** `4,2k entrée · 1,1k sortie`, or nothing when the run reported no tokens. */
    fun tokensLabel(tokensIn: Long, tokensOut: Long): String {
        if (tokensIn <= 0 && tokensOut <= 0) return "—"
        return "${tokens(tokensIn)} entrée · ${tokens(tokensOut)} sortie"
    }

    private fun tokens(value: Long): String {
        val abs = if (value < 0) -value else value
        val sign = if (value < 0) "-" else ""
        return when {
            abs >= 1_000_000 -> "$sign${oneDecimal(abs / 1_000_000.0)}M"
            abs >= 1_000 -> "$sign${oneDecimal(abs / 1_000.0)}k"
            else -> "$sign$abs"
        }
    }

    /** `27/10 09:31 · réussi · 12,3 s` — the one line a run row shows. */
    fun runSubtitle(row: RunRow): String = listOf(
        dateLabel(if (row.startedAt > 0) row.startedAt else row.mtimeMs),
        runStateLabel(row.state),
        durationLabel(row.durationMs),
    ).filter { it.isNotBlank() && it != "—" }.joinToString(" · ")

    /** Tokens and tool calls on a second line; empty when the run recorded none. */
    fun runDetail(row: RunRow): String = listOfNotNull(
        tokensLabel(row.tokensIn, row.tokensOut).takeIf { it != "—" },
        row.toolCalls.takeIf { it > 0 }?.let { "$it outil${if (it > 1) "s" else ""}" },
        row.error.takeIf { it.isNotBlank() }?.let { "erreur : ${it.take(120)}" },
    ).joinToString(" · ")

    /**
     * Joins the transcript list with the journal lines.
     *
     * The transcript's run id is the last dash-separated segment of
     * `<date>-<runId>.json`, so a job id that itself contains dashes arrives
     * truncated — hence the suffix match. Records first (they carry the outcome),
     * then transcripts the journal no longer knows about.
     */
    fun mergeRuns(runs: List<RunSummaryDto>, records: List<BotRunDto>): List<RunRow> {
        val transcripts = runs.filter { it.runId.isNotBlank() }
        val used = HashSet<String>()
        val rows = ArrayList<RunRow>(records.size + transcripts.size)
        for (record in records) {
            if (record.runId.isBlank() && record.startedAt <= 0) continue
            val match = transcripts.firstOrNull { candidate ->
                candidate.runId !in used && runIdsMatch(record.runId, candidate.runId)
            }
            if (match != null) used += match.runId
            rows += RunRow(
                runId = match?.runId ?: record.runId,
                state = record.state,
                startedAt = record.startedAt,
                mtimeMs = match?.mtimeMs ?: 0,
                durationMs = record.durationMs,
                tokensIn = record.tokensIn,
                tokensOut = record.tokensOut,
                summary = record.summary,
                error = record.error,
                steps = record.steps,
                toolCalls = record.toolCalls,
                hasTranscript = match != null,
            )
        }
        for (orphan in transcripts) {
            if (orphan.runId in used) continue
            rows += RunRow(
                runId = orphan.runId,
                state = "",
                startedAt = 0,
                mtimeMs = orphan.mtimeMs,
                durationMs = 0,
                tokensIn = 0,
                tokensOut = 0,
                summary = "",
                error = "",
                steps = 0,
                toolCalls = 0,
                hasTranscript = true,
            )
        }
        return rows.sortedByDescending { if (it.startedAt > 0) it.startedAt else it.mtimeMs }
    }

    private fun runIdsMatch(recordId: String, transcriptId: String): Boolean {
        if (recordId.isBlank() || transcriptId.isBlank()) return false
        return recordId == transcriptId ||
            recordId.endsWith("-$transcriptId") ||
            transcriptId == recordId.takeLast(8)
    }

    /** `#3f2a9b1c · 2 tentatives · 27/10 09:31`, with the reason when there is one. */
    fun incidentSubtitle(job: DlqJobDto): String = listOf(
        "#${job.id.take(8).ifBlank { "—" }}",
        "${job.attempts} tentative${if (job.attempts > 1) "s" else ""}",
        dateLabel(job.createdAt),
        reasonLabel(job.reasonCode).takeIf { it.isNotBlank() },
    ).filterNotNull().joinToString(" · ")

    /** The route's `reasonCode` in words the owner can act on. */
    fun reasonLabel(code: String): String = when (code) {
        "" -> ""
        "not_found" -> "introuvable"
        "provider_rate_limit" -> "quota fournisseur"
        "provider_error" -> "panne fournisseur"
        "timeout" -> "délai dépassé"
        "cancelled" -> "annulé"
        "budget_exhausted" -> "budget épuisé"
        else -> code.replace('_', ' ')
    }

    /** `✓ read_file /work/report.md` for one trace step. */
    fun traceLabel(step: TraceStepDto): String {
        val mark = if (step.ok) "✓" else "✗"
        val arg = step.arg.takeIf { it.isNotBlank() }?.let { " $it" }.orEmpty()
        return "$mark ${step.name.ifBlank { "outil" }}$arg"
    }

    /** One line for a run's transcript header. */
    fun transcriptLine(state: String, startedAt: Long, durationMs: Long): String =
        listOf(dateLabel(startedAt), runStateLabel(state), durationLabel(durationMs))
            .filter { it.isNotBlank() && it != "—" }
            .joinToString(" · ")
}
