package io.github.tournierjc.mercury.data

import io.github.tournierjc.mercury.data.dto.BotRunDto
import io.github.tournierjc.mercury.data.dto.DeliverableDto
import io.github.tournierjc.mercury.data.dto.DlqJobDto
import io.github.tournierjc.mercury.data.dto.ReasoningBlockDto
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

    /**
     * `512 o` / `512 B`, `12.4 ko` / `12.4 kB`, `3.1 Mo` / `3.1 MB` — what a phone header can
     * afford. The unit is a word (octet, kilo-octet, méga-octet), so it is a key, not a suffix.
     */
    /** `12.4` — the number alone, for a sentence that supplies its own unit. */
    fun sizeDigits(bytes: Long): String = when {
        bytes >= 1024L * 1024 -> oneDecimal(bytes / (1024.0 * 1024.0))
        bytes >= 1024L -> oneDecimal(bytes / 1024.0)
        else -> bytes.toString()
    }

    fun sizeNotice(bytes: Long): Notice? = when {
        bytes < 0 -> null
        bytes >= 1024L * 1024 -> Notice(NoticeKey.SIZE_MB, args = listOf(oneDecimal(bytes / (1024.0 * 1024.0))))
        bytes >= 1024L -> Notice(NoticeKey.SIZE_KB, args = listOf(oneDecimal(bytes / 1024.0)))
        else -> Notice(NoticeKey.SIZE_BYTES, args = listOf(bytes.toString()))
    }

    private fun oneDecimal(value: Double): String {
        // Half-up: 3,1999 Mo reads "3,2 Mo", not "3,1" — a size is an approximation,
        // unlike the token counter which truncates on purpose.
        val rounded = (value * 10).roundToLong()
        val whole = rounded / 10
        val tenth = rounded % 10
        // A dot, not the French comma: `:core` has no locale, and a compact counter is not a
        // sentence. English read "4,2k" as a typo, which is worse than the other way round.
        return if (tenth == 0L) "$whole" else "$whole.$tenth"
    }

    private val stampFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("dd/MM HH:mm").withZone(ZoneId.systemDefault())

    /** `27/10 09:31`; epoch 0 means "unknown", never 01/01 1970. */
    fun dateLabel(epochMs: Long): String =
        if (epochMs <= 0) "—" else stampFormatter.format(Instant.ofEpochMilli(epochMs))

    /** A relative age as a key: the inbox needs the age, the screen owns the words. */
    fun ageNotice(epochMs: Long, nowMs: Long): Notice? {
        if (epochMs <= 0) return null
        val seconds = (nowMs - epochMs) / 1000
        return when {
            seconds < 60 -> Notice(NoticeKey.AGE_NOW).asInfo()
            seconds < 3_600 -> Notice(NoticeKey.AGE_MINUTES, args = listOf((seconds / 60).toString())).asInfo()
            seconds < 86_400 -> Notice(NoticeKey.AGE_HOURS, args = listOf((seconds / 3_600).toString())).asInfo()
            else -> Notice(NoticeKey.AGE_DAYS, args = listOf((seconds / 86_400).toString())).asInfo()
        }
    }

    /**
     * The two words of an inbox subtitle that need a language: `final`/`travail`, and the age.
     * The size stays out of it — `12,4 ko` is a number and a unit, the same in both languages — so
     * the screen joins `noticeText(scope) · sizeLabel(...) · noticeText(age)` itself.
     */
    fun scopeNotice(output: DeliverableDto): Notice =
        Notice(if (output.final) NoticeKey.OUTPUT_FINAL else NoticeKey.OUTPUT_DRAFT).asInfo()

    /** `Veille` or the bot id when the name is unknown. Data, not words. */
    fun originName(output: DeliverableDto, botName: String): String = botName.ifBlank { output.botId }

    /** `réussi`, `échec`, `en cours`… The journal's `state` is the outcome of one run. */
    fun runStateNotice(state: String): Notice? = when (state.lowercase()) {
        "completed", "done", "success", "ok" -> Notice(NoticeKey.RUN_SUCCEEDED).asInfo()
        "failed", "error" -> Notice(NoticeKey.RUN_FAILED_STATE)
        "cancelled", "canceled", "timeout" -> Notice(NoticeKey.RUN_CANCELLED_STATE)
        "running" -> Notice(NoticeKey.RUN_RUNNING_STATE).asInfo()
        "queued", "pending" -> Notice(NoticeKey.RUN_QUEUED_STATE).asInfo()
        else -> state.takeIf { it.isNotBlank() }?.let { Notice(NoticeKey.UNKNOWN_CODE, args = listOf(it)).asInfo() }
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
    fun tokensNotices(tokensIn: Long, tokensOut: Long): List<Notice> {
        if (tokensIn <= 0 && tokensOut <= 0) return emptyList()
        return listOf(
            Notice(NoticeKey.TOKENS_IN, args = listOf(tokens(tokensIn))).asInfo(),
            Notice(NoticeKey.TOKENS_OUT, args = listOf(tokens(tokensOut))).asInfo(),
        )
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

    /**
     * `27/10 09:31 · réussi · 12,3 s` — the one line a run row shows. The state is the only word
     * in it, so the caller passes it already resolved (see `runStateNotice`).
     */
    fun runSubtitle(row: RunRow, stateText: String): String = listOf(
        dateLabel(if (row.startedAt > 0) row.startedAt else row.mtimeMs),
        stateText,
        durationLabel(row.durationMs),
    ).filter { it.isNotBlank() && it != "—" }.joinToString(" · ")

    /**
     * Tokens and tool calls on a second line; empty when the run recorded none. The three words in
     * it (token counts, tool calls, the error prefix) arrive already resolved.
     */
    fun runDetail(tokensText: String, toolCallsText: String, errorText: String?): String = listOfNotNull(
        tokensText.takeIf { it.isNotBlank() },
        toolCallsText.takeIf { it.isNotBlank() },
        errorText?.takeIf { it.isNotBlank() },
    ).joinToString(" · ")

    /** The tool-call count of a run, `3 outils`, as a key. */
    fun toolCallsNotice(row: RunRow): Notice? = when {
        row.toolCalls <= 0 -> null
        row.toolCalls == 1 -> Notice(NoticeKey.RUN_TOOL_CALLS_ONE).asInfo()
        else -> Notice(NoticeKey.RUN_TOOL_CALLS_MANY, args = listOf(row.toolCalls.toString())).asInfo()
    }

    /** The error line of a run, with the daemon's own text kept as the argument. */
    fun errorNotice(row: RunRow): Notice? =
        row.error.takeIf { it.isNotBlank() }?.let {
            Notice(NoticeKey.RUN_ERROR_LINE, args = listOf(it.take(120))).withDetail(null)
        }

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

    /**
     * `#3f2a9b1c · 2 tentatives · 27/10 09:31`, plus the reason when there is one. The two words
     * (the attempt count and the reason) arrive already resolved by the caller.
     */
    fun incidentSubtitle(job: DlqJobDto, attemptsText: String, reasonText: String?): String = listOfNotNull(
        job.id.take(8).takeIf { it.isNotBlank() }?.let { "#$it" },
        attemptsText.takeIf { it.isNotBlank() },
        dateLabel(job.createdAt).takeIf { it != "—" },
        reasonText?.takeIf { it.isNotBlank() },
    ).joinToString(" · ")

    /** The attempt count as a key: `1 tentative`, `2 tentatives`. */
    fun attemptsNotice(job: DlqJobDto): Notice =
        if (job.attempts <= 1) Notice(NoticeKey.ATTEMPTS_ONE).asInfo()
        else Notice(NoticeKey.ATTEMPTS_MANY, args = listOf(job.attempts.toString())).asInfo()

    /** The route's `reasonCode` in words the owner can act on. */
    fun reasonNotice(code: String): Notice? = when (code) {
        "" -> null
        "not_found" -> Notice(NoticeKey.REASON_NOT_FOUND)
        "provider_rate_limit" -> Notice(NoticeKey.REASON_PROVIDER_RATE_LIMIT)
        "provider_error" -> Notice(NoticeKey.REASON_PROVIDER_ERROR)
        "timeout" -> Notice(NoticeKey.REASON_TIMEOUT)
        "cancelled" -> Notice(NoticeKey.REASON_CANCELLED)
        "budget_exhausted" -> Notice(NoticeKey.REASON_BUDGET_EXHAUSTED)
        else -> Notice(NoticeKey.UNKNOWN_CODE, args = listOf(code.replace('_', ' '))).withDetail(null)
    }

    /** `✓ read_file /work/report.md` for one trace step. */
    fun traceLabel(step: TraceStepDto, toolFallback: String): String {
        val mark = if (step.ok) "✓" else "✗"
        val arg = step.arg.takeIf { it.isNotBlank() }?.let { " $it" }.orEmpty()
        return "$mark ${step.name.ifBlank { toolFallback }}$arg"
    }

    /**
     * The thinking of a run, one block per step, each capped.
     *
     * A step can think for thousands of characters; a screen that dumps 24k of it is unreadable, and
     * one that quietly cuts is a lie, so every cut is marked with an ellipsis. There is no "collapsed
     * preview" here: a folded panel shows nothing, and this answers what to draw once it is opened.
     */
    fun reasoningBlocks(
        blocks: List<ReasoningBlockDto>,
        cap: Int = 4_000,
    ): List<ReasoningBlockDto> {
        if (blocks.isEmpty()) return emptyList()
        return mergeSteps(blocks).map { block ->
            val text = block.text.trim()
            block.copy(text = if (text.length > cap) text.take(cap).trimEnd() + "…" else text)
        }
    }

    /**
     * How many steps the thinking covers — the number a folded panel shows instead of the text.
     *
     * One step can think twice: the daemon appends a block per reasoning spurt, so a single step
     * often yields two. Drawn apart they read as two identical "étape 2" labels — the screen is not
     * wrong about the count, the grouping is.
     */
    fun reasoningStepCount(blocks: List<ReasoningBlockDto>): Int = mergeSteps(blocks).size

    private fun mergeSteps(blocks: List<ReasoningBlockDto>): List<ReasoningBlockDto> {
        if (blocks.isEmpty()) return emptyList()
        return blocks.fold(mutableListOf<ReasoningBlockDto>()) { acc, block ->
            val previous = acc.lastOrNull()
            if (previous != null && previous.step == block.step) {
                acc[acc.lastIndex] = previous.copy(
                    text = previous.text.trimEnd() + "\n\n" + block.text.trimStart(),
                )
            } else {
                acc.add(block)
            }
            acc
        }
    }

    /** One line for a run's transcript header. */
    fun transcriptLine(stateText: String, startedAt: Long, durationMs: Long): String =
        listOf(dateLabel(startedAt), stateText, durationLabel(durationMs))
            .filter { it.isNotBlank() && it != "—" }
            .joinToString(" · ")
}
