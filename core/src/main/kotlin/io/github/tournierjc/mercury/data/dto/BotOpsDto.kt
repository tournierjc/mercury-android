package io.github.tournierjc.mercury.data.dto

import io.github.tournierjc.mercury.data.MillisSerializer
import kotlinx.serialization.Serializable

/**
 * Deliverables, run transcripts and the dead-letter queue.
 *
 * Shapes come from the daemon handlers, not from a guess:
 * `GET /api/bots/outputs` and `GET /api/bots/:id/outputs` answer
 * `{outputs:[{botId,name,bytes,mtimeMs,path,final}]}` (`listDeliverables`),
 * `GET /api/bots/:id/outputs/:name/preview` answers `{preview,truncated}`,
 * `GET /api/bots/:id/runs` answers `{runs:[{runId,mtimeMs}]}` and
 * `GET /api/bots/:id/dlq` answers `{dlq:[DurableBotJob]}`.
 *
 * Every field has a default: Mercury grows fields, and a missing one must never
 * blank the screen.
 */

/** One file in a bot's deliverables folder. `final` is false for `work/…` drafts. */
@Serializable
data class DeliverableDto(
    val botId: String = "",
    val name: String = "",
    val bytes: Long = 0,
    @Serializable(with = MillisSerializer::class)
    val mtimeMs: Long = 0,
    val path: String = "",
    val final: Boolean = false,
)

/** `GET /api/bots/:id/outputs/:name/preview` — the first 64 kB as UTF-8. */
@Serializable
data class OutputPreviewDto(
    val preview: String = "",
    val truncated: Boolean = false,
    val error: String = "",
)

/** One row of `GET /api/bots/:id/runs`: the list carries no status, only the file. */
@Serializable
data class RunSummaryDto(
    val runId: String = "",
    @Serializable(with = MillisSerializer::class)
    val mtimeMs: Long = 0,
)

/** A journal line (`BotRunRecord`): the durable outcome of one run. */
@Serializable
data class BotRunDto(
    val runId: String = "",
    val botId: String = "",
    val trigger: String = "",
    val state: String = "",
    @Serializable(with = MillisSerializer::class)
    val startedAt: Long = 0,
    @Serializable(with = MillisSerializer::class)
    val durationMs: Long = 0,
    val tokensIn: Long = 0,
    val tokensOut: Long = 0,
    val summary: String = "",
    val error: String = "",
    val reasonCode: String = "",
    val needsYou: Boolean = false,
    @Serializable(with = MillisSerializer::class)
    val turnStartedAt: Long = 0,
    val steps: Int = 0,
    val toolCalls: Int = 0,
    val peakInputTokens: Long = 0,
    val outcome: String = "",
    val deliverables: List<String> = emptyList(),
    val routineId: String = "",
)

/** `GET /api/bots/:id/journal` — newest last, like the file it reads. */
@Serializable
data class RunRecordPage(val journal: List<BotRunDto> = emptyList(), val error: String = "")

/** One entry of a transcript's tool trace. */
@Serializable
data class TraceStepDto(
    val name: String = "",
    val ok: Boolean = false,
    val arg: String = "",
    val task: Boolean = false,
)

/** `GET /api/bots/:id/runs/:runId` — `{record, prompt, output, trace}`. */
@Serializable
data class RunTranscriptDto(
    val record: BotRunDto = BotRunDto(),
    val prompt: String = "",
    val output: String = "",
    val trace: List<TraceStepDto> = emptyList(),
    val error: String = "",
)

/** A dead-lettered job (`DurableBotJob`): what failed and how many times. */
@Serializable
data class DlqJobDto(
    val id: String = "",
    val botId: String = "",
    val trigger: String = "",
    val prompt: String = "",
    val fromBot: String = "",
    val state: String = "",
    val attempts: Int = 0,
    val createdAt: Long = 0,
    val reasonCode: String = "",
)

/** `GET /api/bots/:id/dlq`. */
@Serializable
data class DlqPage(val dlq: List<DlqJobDto> = emptyList(), val error: String = "")

/**
 * `POST /api/bots/:id/replay/:jobId` — 200 `{accepted:true,jobId,status:"accepted"}`,
 * 404 `{accepted:false,reasonCode:"not_found"}`. Both are decoded: a refusal is an
 * answer the owner can act on, not an outage.
 */
@Serializable
data class DlqReplay(
    val accepted: Boolean = false,
    val jobId: String = "",
    val reasonCode: String = "",
    val status: String = "",
)
