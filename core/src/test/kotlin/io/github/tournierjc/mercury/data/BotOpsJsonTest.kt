package io.github.tournierjc.mercury.data

import io.github.tournierjc.mercury.data.dto.BotOutputsPage
import io.github.tournierjc.mercury.data.dto.BotRunDto
import io.github.tournierjc.mercury.data.dto.BotRunsPage
import io.github.tournierjc.mercury.data.dto.DlqPage
import io.github.tournierjc.mercury.data.dto.DlqReplay
import io.github.tournierjc.mercury.data.dto.OutputPreviewDto
import io.github.tournierjc.mercury.data.dto.RunRecordPage
import io.github.tournierjc.mercury.data.dto.RunTranscriptDto
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Deliverables, runs and DLQ payloads, captured from the daemon handlers:
 * `listDeliverables`, `readDeliverable`, `listTranscripts`, `BotRunRecord` and
 * `DurableBotJob` in the bots module, and the routes in `src/web/api/bots.ts`.
 */
class BotOpsJsonTest {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    // GET /api/bots/outputs — listDeliverables(): finals at the top, work/ drafts below.
    private val outputsPayload = """
        {"outputs":[
          {"botId":"veille","name":"brief.md","bytes":11300,"mtimeMs":1793000600000,"path":"/home/m/deliverables/veille/brief.md","final":true},
          {"botId":"publisher","name":"work/2026-10-27 final report.md","bytes":2048,"mtimeMs":1793000000000,"path":"/home/m/deliverables/publisher/work/2026-10-27 final report.md","final":false},
          {"botId":"publisher","name":"cover.png","bytes":5242880,"mtimeMs":1792999000000,"path":"/home/m/deliverables/publisher/cover.png","final":true}
        ]}
    """.trimIndent()

    // GET /api/bots/:id/runs — listTranscripts(): the id and the file time, nothing else.
    private val runsPayload = """
        {"runs":[{"runId":"3f2a9b1c","mtimeMs":1793000600000},{"runId":"9a8b7c6d","mtimeMs":1793000000000}]}
    """.trimIndent()

    // BotRunRecord as appended by the journal.
    private val journalPayload = """
        {"journal":[
          {"runId":"9a8b7c6d","botId":"veille","trigger":"cron","state":"failed","startedAt":1792999900000,
           "durationMs":18500,"tokensIn":4200,"tokensOut":800,"summary":"Résumé impossible","error":"provider timeout",
           "reasonCode":"timeout","needsYou":true,"steps":6,"toolCalls":3,"outcome":"none","deliverables":[]},
          {"runId":"3f2a9b1c","botId":"veille","trigger":"api","state":"completed","startedAt":1793000500000,
           "durationMs":12300,"tokensIn":51000,"tokensOut":2400,"summary":"Brief publié","steps":9,"toolCalls":4,
           "outcome":"deliverable","deliverables":["brief.md"]}
        ]}
    """.trimIndent()

    // GET /api/bots/:id/runs/:runId — readTranscript(): {record, prompt, output, trace}.
    private val transcriptPayload = """
        {"record":{"runId":"3f2a9b1c","botId":"veille","trigger":"api","state":"completed","startedAt":1793000500000,
          "durationMs":12300,"tokensIn":51000,"tokensOut":2400,"summary":"Brief publié","steps":9,"toolCalls":4},
         "prompt":"Rédige le brief du jour",
         "output":"Brief écrit dans brief.md",
         "trace":[{"name":"write_file","ok":true,"arg":"brief.md"},{"name":"read_file","ok":false,"arg":"/work/old.md"}]}
    """.trimIndent()

    // GET /api/bots/:id/dlq — DurableBotJob, dead after two attempts.
    private val dlqPayload = """
        {"dlq":[{"id":"7f3a2b10-4c5d","botId":"veille","trigger":"cron","prompt":"Résume les flux",
          "state":"dead","attempts":2,"createdAt":1792999000000,"idempotencyKey":"cron:2026-10-27","reasonCode":"provider_rate_limit"}]}
    """.trimIndent()

    @Test
    fun `fleet inbox decodes finals and work drafts`() {
        val page = json.decodeFromString(BotOutputsPage.serializer(), outputsPayload)

        assertEquals(3, page.outputs.size)
        assertEquals("veille", page.outputs[0].botId)
        assertEquals("brief.md", page.outputs[0].name)
        assertTrue(page.outputs[0].final)
        assertFalse(page.outputs[1].final)
        assertEquals("work/2026-10-27 final report.md", page.outputs[1].name)
        assertEquals(2048L, page.outputs[1].bytes)
        assertEquals(1793000600000L, page.outputs[0].mtimeMs)
    }

    @Test
    fun `empty pages stay empty instead of failing`() {
        assertEquals(0, json.decodeFromString(BotOutputsPage.serializer(), """{"outputs":[]}""").outputs.size)
        assertEquals(0, json.decodeFromString(BotRunsPage.serializer(), """{"runs":[]}""").runs.size)
        assertEquals(0, json.decodeFromString(DlqPage.serializer(), """{"dlq":[]}""").dlq.size)
        assertEquals(0, json.decodeFromString(RunRecordPage.serializer(), """{"journal":[]}""").journal.size)
    }

    @Test
    fun `kinds follow the extension, not the size`() {
        val page = json.decodeFromString(BotOutputsPage.serializer(), outputsPayload)
        val brief = page.outputs[0]
        val report = page.outputs[1]
        val cover = page.outputs[2]

        assertEquals(OutputKind.Text, BotOps.kind(brief.name))
        assertEquals(OutputKind.Text, BotOps.kind(report.name))
        assertEquals(OutputKind.Image, BotOps.kind(cover.name))
        assertEquals(OutputKind.Binary, BotOps.kind("archive.tar.gz"))
        assertEquals(OutputKind.Binary, BotOps.kind("README"))

        assertTrue(BotOps.previewable(brief))
        // 5 Mo: an image the dialog shares instead of decoding.
        assertFalse(BotOps.inlineImage(cover))
        assertEquals("png", BotOps.extension(cover.name))

        // The share sheet asks for a type: it mirrors the daemon's MIME_BY_EXT.
        assertEquals("text/markdown", BotOps.mimeType(brief.name))
        assertEquals("text/markdown", BotOps.mimeType("work/brief.md"))
        assertEquals("image/png", BotOps.mimeType(cover.name))
        assertEquals("application/json", BotOps.mimeType("data.json"))
        assertEquals("application/octet-stream", BotOps.mimeType("archive.unknown"))
        assertEquals("application/octet-stream", BotOps.mimeType("README"))
    }

    @Test
    fun `sizes and durations read like a phone header`() {
        assertEquals("512 o", BotOps.sizeLabel(512))
        assertEquals("2 ko", BotOps.sizeLabel(2048))
        assertEquals("11,7 ko", BotOps.sizeLabel(12000))
        assertEquals("3,2 Mo", BotOps.sizeLabel(3_355_443))
        assertEquals("—", BotOps.sizeLabel(-1))

        assertEquals("—", BotOps.durationLabel(0))
        assertEquals("820 ms", BotOps.durationLabel(820))
        assertEquals("12,3 s", BotOps.durationLabel(12_300))
        assertEquals("3 min 05 s", BotOps.durationLabel(185_000))
    }

    @Test
    fun `run labels translate the journal states`() {
        assertEquals(NoticeKey.RUN_SUCCEEDED, BotOps.runStateNotice("completed")?.key)
        assertEquals(NoticeKey.RUN_FAILED_STATE, BotOps.runStateNotice("failed")?.key)
        assertEquals(NoticeKey.RUN_CANCELLED_STATE, BotOps.runStateNotice("timeout")?.key)
        assertEquals(NoticeKey.RUN_RUNNING_STATE, BotOps.runStateNotice("running")?.key)
        assertNull(BotOps.runStateNotice(""))
        assertTrue(BotOps.runStateFailed("failed"))
        assertFalse(BotOps.runStateFailed("completed"))

        val short = BotOps.tokensNotices(4200, 800)
        assertEquals(listOf(NoticeKey.TOKENS_IN, NoticeKey.TOKENS_OUT), short.map { it.key })
        assertEquals(listOf("4,2k", "800"), short.flatMap { it.args })
        assertEquals("1,2M", BotOps.tokensNotices(1_200_000, 0).first().args.single())
        // A run that reported nothing draws no line at all, rather than a dash.
        assertTrue(BotOps.tokensNotices(0, 0).isEmpty())
    }

    @Test
    fun `preview carries the text and the truncation flag`() {
        val preview = json.decodeFromString(
            OutputPreviewDto.serializer(),
            """{"preview":"# The final report","truncated":true}""",
        )
        assertEquals("# The final report", preview.preview)
        assertTrue(preview.truncated)

        val empty = json.decodeFromString(OutputPreviewDto.serializer(), """{"preview":"","truncated":false}""")
        assertFalse(empty.truncated)
        assertEquals("", empty.preview)
    }

    @Test
    fun `run rows join the transcript list with the journal`() {
        val runs = json.decodeFromString(BotRunsPage.serializer(), runsPayload).runs
        val records = json.decodeFromString(RunRecordPage.serializer(), journalPayload).journal

        val rows = BotOps.mergeRuns(runs, records)

        assertEquals(2, rows.size)
        // Newest first: 3f2a9b1c started last and matches its transcript.
        assertEquals("3f2a9b1c", rows[0].runId)
        assertTrue(rows[0].hasTranscript)
        assertEquals("completed", rows[0].state)
        assertEquals(12300L, rows[0].durationMs)
        assertEquals(51000L, rows[0].tokensIn)
        assertEquals("9a8b7c6d", rows[1].runId)
        assertEquals("provider timeout", rows[1].error)
        assertTrue(rows[1].hasTranscript)
    }

    @Test
    fun `a run whose transcript was evicted keeps its journal line`() {
        val records = json.decodeFromString(RunRecordPage.serializer(), journalPayload).journal
        val rows = BotOps.mergeRuns(emptyList(), records)

        assertEquals(2, rows.size)
        assertFalse(rows[0].hasTranscript)
        assertEquals("completed", rows[0].state)
    }

    @Test
    fun `a transcript the journal no longer knows still lists`() {
        val runs = json.decodeFromString(BotRunsPage.serializer(), runsPayload).runs
        val rows = BotOps.mergeRuns(runs, emptyList())

        assertEquals(2, rows.size)
        assertTrue(rows[0].hasTranscript)
        assertEquals("3f2a9b1c", rows[0].runId)
        assertNull(BotOps.runStateNotice(rows[0].state))
        // No journal line means no start time: the row falls back on the file time.
        assertEquals(1793000600000L, rows[0].mtimeMs)
    }

    @Test
    fun `run ids truncated by the transcript file name still match`() {
        val runs = json.decodeFromString(BotRunsPage.serializer(), """{"runs":[{"runId":"4c5d6e7f","mtimeMs":5}]}""").runs
        val records = listOf(BotRunDto(runId = "job-4c5d6e7f", state = "failed", startedAt = 1000))

        val rows = BotOps.mergeRuns(runs, records)

        assertEquals(1, rows.size)
        assertTrue(rows[0].hasTranscript)
        // The row must carry the id the /runs/:runId route answers for.
        assertEquals("4c5d6e7f", rows[0].runId)
    }

    @Test
    fun `run lines compose date, state, duration and error`() {
        val runs = json.decodeFromString(BotRunsPage.serializer(), runsPayload).runs
        val records = json.decodeFromString(RunRecordPage.serializer(), journalPayload).journal
        val rows = BotOps.mergeRuns(runs, records)

        val failed = rows[1]
        assertEquals(NoticeKey.RUN_FAILED_STATE, BotOps.runStateNotice(failed.state)?.key)
        assertTrue(BotOps.runSubtitle(failed, "échoué").contains("échoué"))
        assertTrue(BotOps.runSubtitle(failed, "échoué").contains("18,5 s"))
        assertEquals(NoticeKey.RUN_ERROR_LINE, BotOps.errorNotice(failed)?.key)
        assertEquals("provider timeout", BotOps.errorNotice(failed)?.args?.single())
        assertEquals(NoticeKey.RUN_TOOL_CALLS_MANY, BotOps.toolCallsNotice(failed)?.key)
        assertEquals(listOf("3"), BotOps.toolCallsNotice(failed)?.args)
        assertEquals(NoticeKey.RUN_TOOL_CALLS_ONE, BotOps.toolCallsNotice(rows[0].copy(toolCalls = 1))?.key)

        val detail = BotOps.runDetail(
            tokensText = "51k entrée · 2 sortie",
            toolCallsText = "3 outils",
            errorText = "erreur : provider timeout",
        )
        assertTrue(detail.contains("erreur : provider timeout"))
        assertTrue(detail.contains("3 outils"))
        assertTrue(detail.contains("51k entrée"))
    }

    @Test
    fun `transcript decodes the record, the reply and the trace`() {
        val transcript = json.decodeFromString(RunTranscriptDto.serializer(), transcriptPayload)

        assertEquals("3f2a9b1c", transcript.record.runId)
        assertEquals("completed", transcript.record.state)
        assertEquals("Rédige le brief du jour", transcript.prompt)
        assertEquals("Brief écrit dans brief.md", transcript.output)
        assertEquals(2, transcript.trace.size)
        assertEquals("✓ write_file brief.md", BotOps.traceLabel(transcript.trace[0], "outil"))
        assertEquals("✗ read_file /work/old.md", BotOps.traceLabel(transcript.trace[1], "outil"))
        val line = BotOps.transcriptLine("réussi", transcript.record.startedAt, transcript.record.durationMs)
        assertTrue(line.contains("réussi"))
    }

    @Test
    fun `dlq incidents keep the attempt count and the reason`() {
        val page = json.decodeFromString(DlqPage.serializer(), dlqPayload)

        assertEquals(1, page.dlq.size)
        val job = page.dlq[0]
        assertEquals("7f3a2b10-4c5d", job.id)
        assertEquals(2, job.attempts)
        assertEquals("dead", job.state)
        assertEquals("provider_rate_limit", job.reasonCode)

        assertEquals(NoticeKey.ATTEMPTS_MANY, BotOps.attemptsNotice(job).key)
        assertEquals(listOf("2"), BotOps.attemptsNotice(job).args)
        val subtitle = BotOps.incidentSubtitle(job, attemptsText = "2 tentatives", reasonText = "quota fournisseur")
        assertTrue(subtitle.startsWith("#7f3a2b10"))
        assertTrue(subtitle.contains("2 tentatives"))
        assertTrue(subtitle.contains("quota fournisseur"))
        assertEquals(NoticeKey.REASON_NOT_FOUND, BotOps.reasonNotice("not_found")?.key)
        // A code this app does not know travels as its argument, spaces instead of underscores.
        assertEquals(NoticeKey.REASON_OTHER, BotOps.reasonNotice("disk_full")?.key)
        assertEquals("disk full", BotOps.reasonNotice("disk_full")?.args?.single())
    }

    @Test
    fun `replay answers the acceptance and the refusal`() {
        val accepted = json.decodeFromString(
            DlqReplay.serializer(),
            """{"accepted":true,"jobId":"7f3a2b10-4c5d","status":"accepted"}""",
        )
        assertTrue(accepted.accepted)
        assertEquals("7f3a2b10-4c5d", accepted.jobId)
        assertEquals("accepted", accepted.status)

        val refused = json.decodeFromString(DlqReplay.serializer(), """{"accepted":false,"reasonCode":"not_found"}""")
        assertFalse(refused.accepted)
        assertEquals("not_found", refused.reasonCode)
    }

    @Test
    fun `ages and stamps never invent a date for epoch zero`() {
        val now = 1793000600000L
        assertEquals(NoticeKey.AGE_NOW, BotOps.ageNotice(now - 30_000, now)?.key)
        assertEquals(listOf("2"), BotOps.ageNotice(now - 120_000, now)?.args)
        assertEquals(NoticeKey.AGE_HOURS, BotOps.ageNotice(now - 7_200_000, now)?.key)
        assertEquals(listOf("3"), BotOps.ageNotice(now - 3 * 86_400_000L, now)?.args)
        assertNull(BotOps.ageNotice(0, now))
        assertEquals("—", BotOps.dateLabel(0))
    }

    @Test
    fun `an output subtitle says whether it is a final or a draft`() {
        val page = json.decodeFromString(BotOutputsPage.serializer(), outputsPayload)
        val now = 1793000600000L

        assertEquals(NoticeKey.OUTPUT_FINAL, BotOps.scopeNotice(page.outputs[0]).key)
        assertEquals(NoticeKey.OUTPUT_DRAFT, BotOps.scopeNotice(page.outputs[1]).key)
        assertEquals("Veille", BotOps.originName(page.outputs[0], "Veille"))
        // A missing name falls back on the id rather than an empty prefix.
        // The bot id stands in when the name is unknown.
        assertEquals("veille", BotOps.originName(page.outputs[0], ""))
    }
}
