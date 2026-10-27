package io.github.tournierjc.mercury.data.dto

import kotlinx.serialization.Serializable

/**
 * Fleet money and disk: `GET /api/bots-cost`, `GET /api/bots-storage`, plus the
 * bundle import report (`POST /api/bots/import`).
 *
 * Shapes come from `BotManager.costReport` / `getStorage` in
 * `src/bots/bot-manager.ts` and `importBotBundle` in `src/bots/bundle.ts`.
 */

/** Per-bot counters over a window (`BotUsage` in `src/bots/journal.ts`). */
@Serializable
data class BotUsageDto(
    val runs: Int = 0,
    val completed: Int = 0,
    val failed: Int = 0,
    val noOutcome: Int = 0,
    val deliverables: Int = 0,
    val tokensIn: Long = 0,
    val tokensOut: Long = 0,
) {
    /** What the phone ranks and sums: a token is a token whichever side of the turn. */
    val tokens: Long get() = tokensIn + tokensOut
}

@Serializable
data class BotCostRowDto(
    val id: String = "",
    val name: String = "",
    val parent: String? = null,
    val today: BotUsageDto = BotUsageDto(),
    val window: BotUsageDto = BotUsageDto(),
    /** Daily token cap in effect; 0 means unlimited. */
    val cap: Long = 0,
    val paused: Boolean = false,
)

@Serializable
data class FleetCostDto(
    val id: String = "",
    val name: String = "",
    val members: Int = 0,
    val today: BotUsageDto = BotUsageDto(),
    val window: BotUsageDto = BotUsageDto(),
)

/** `GET /api/bots-cost?days=N` — `days` is clamped to 1…90 server-side. */
@Serializable
data class BotsCostReport(
    val days: Int = 7,
    val bots: List<BotCostRowDto> = emptyList(),
    val fleets: List<FleetCostDto> = emptyList(),
    val fleetToday: Long = 0,
    val fleetCap: Long = 0,
    val fleetPaused: Boolean = false,
    val error: String = "",
) {
    /** The bots that actually spend, biggest window first. */
    fun topByTokens(limit: Int = 5): List<BotCostRowDto> =
        bots.filter { it.window.tokens > 0 }.sortedByDescending { it.window.tokens }.take(limit)

    val windowTokens: Long get() = bots.sumOf { it.window.tokens }
}

/** One bot's disk footprint: everything under its folder, journal files included. */
@Serializable
data class BotStorageRow(
    val id: String = "",
    val bytes: Long = 0,
    val journalBytes: Long = 0,
) {
    /** Persona, skills, permissions, sandbox — the journal is counted apart. */
    val profileBytes: Long get() = (bytes - journalBytes).coerceAtLeast(0)
}

/** `GET /api/bots-storage` — `{usage, queue}`. */
@Serializable
data class BotsStorageReport(
    val usage: List<BotStorageRow> = emptyList(),
    val queue: QueueCounts = QueueCounts(),
    val error: String = "",
) {
    val totalBytes: Long get() = usage.sumOf { it.bytes }

    fun topBySize(limit: Int = 5): List<BotStorageRow> =
        usage.filter { it.bytes > 0 }.sortedByDescending { it.bytes }.take(limit)
}

@Serializable
data class BundleSkipDto(val id: String = "", val reason: String = "")

/** `BundleImportReport` — imported bots start disabled, existing ids are skipped. */
@Serializable
data class BundleImportReport(
    val created: List<String> = emptyList(),
    val skipped: List<BundleSkipDto> = emptyList(),
)

@Serializable
data class BundleImportResponse(
    val report: BundleImportReport = BundleImportReport(),
    val error: String = "",
)
