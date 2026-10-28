package io.github.tournierjc.mercury.data.dto

import io.github.tournierjc.mercury.data.BotEvent
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * System / admin payloads.
 *
 * Envelopes are typed because the dashboard relies on them; collection items
 * that Mercury does not document (bots, schedules, tasks, memories, tree
 * entries) stay [JsonObject] on purpose: inventing a schema would be a lie the
 * first time Mercury adds or renames a field.
 */

@Serializable
data class LoopLag(val p99Ms: Long = 0, val maxMs: Long = 0)

@Serializable
data class HealthPlugins(val safeMode: Boolean = false, val running: Int = 0, val tools: Int = 0)

@Serializable
data class HealthDto(
    val ok: Boolean = false,
    val pid: Int = 0,
    val version: String = "",
    val state: String = "",
    val booting: Boolean = false,
    val loopLag: LoopLag = LoopLag(),
    val plugins: HealthPlugins = HealthPlugins(),
)

@Serializable
data class ProviderState(val enabled: Boolean = false, val hasKey: Boolean = false)

@Serializable
data class TokenState(val dailyUsed: Long = 0, val dailyBudget: Long = 0)

@Serializable
data class MemoryState(val total: Int = 0, val byType: Map<String, Int> = emptyMap())

@Serializable
data class StatusDto(
    val running: Boolean = false,
    val state: String = "",
    val uptime: Long = 0,
    val providers: Map<String, ProviderState> = emptyMap(),
    val tokens: TokenState = TokenState(),
    val memory: MemoryState = MemoryState(),
)

@Serializable
data class PermissionsDto(val manifest: JsonObject = JsonObject(emptyMap()))

@Serializable
data class SkillDto(
    val name: String = "",
    val description: String = "",
    val active: Boolean = false,
    val version: String? = null,
    val allowedTools: List<String> = emptyList(),
    val hasScripts: Boolean = false,
    val hasReferences: Boolean = false,
)

@Serializable
data class SkillsResponse(val skills: List<SkillDto> = emptyList(), val total: Int = 0)

@Serializable
data class PluginDto(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val kind: String = "",
    val tier: String = "",
    val status: String = "",
    val installedVersion: String = "",
    val latestVersion: String = "",
    val sizeBytes: Long = 0,
)

@Serializable
data class PluginsResponse(val items: List<PluginDto> = emptyList())

@Serializable
data class PluginHealthDto(
    val safeMode: Boolean = false,
    val running: Int = 0,
    val tools: Int = 0,
    val commands: Int = 0,
    val plugins: List<JsonObject> = emptyList(),
)

@Serializable
data class QueueCounts(val pending: Int = 0, val claimed: Int = 0, val dlq: Int = 0)

@Serializable
data class BotsResponse(
    val bots: List<JsonObject> = emptyList(),
    val queue: QueueCounts = QueueCounts(),
    val available: Boolean = true,
)

/**
 * The queue counters as the live feed reports them.
 *
 * Same three numbers under two names (`QueueCounts` for HTTP, `QueueSnapshot` for the feed): mapped
 * here, once, so a manual refresh cannot quietly swap `pending` and `claimed`.
 */
fun QueueCounts.asSnapshot(): BotEvent.QueueSnapshot =
    BotEvent.QueueSnapshot(pending = pending, claimed = claimed, dlq = dlq)

/** `GET /api/bots/:id` — the manifest and its live state, both untyped. */
@Serializable
data class BotDetailDto(
    val bot: JsonObject? = null,
    val state: JsonObject? = null,
    val journal: List<JsonObject> = emptyList(),
    val error: String = "",
)

@Serializable
data class BotRunsPage(val runs: List<RunSummaryDto> = emptyList(), val error: String = "")

@Serializable
data class BotOutputsPage(val outputs: List<DeliverableDto> = emptyList(), val error: String = "")

@Serializable
data class BotJournalPage(val journal: List<JsonObject> = emptyList(), val error: String = "")

@Serializable
data class BotTierDto(
    val id: String = "",
    val label: String = "",
    val description: String = "",
    val deny: List<String> = emptyList(),
)

@Serializable
data class BotTiersPage(val tiers: List<BotTierDto> = emptyList())

/** `POST /api/bots` — `id` and `name` are the only required fields. */
@Serializable
data class CreateBotRequest(
    val id: String,
    val name: String,
    val description: String? = null,
    val tier: String? = null,
)

/** `PATCH /api/bots/:id` — absent fields keep their value. */
@Serializable
data class UpdateBotRequest(
    val enabled: Boolean? = null,
    val description: String? = null,
    val model: String? = null,
)

@Serializable
data class BgResponse(val tasks: List<JsonObject> = emptyList(), val available: Boolean = true)

@Serializable
data class AgentsResponse(val agents: List<JsonObject> = emptyList(), val available: Boolean = true)

@Serializable
data class SchedulesResponse(
    /** Null on a daemon that predates the witness; see [io.github.tournierjc.mercury.data.dto.UsageDto]. */
    val available: Boolean? = null,
    val schedules: List<JsonObject> = emptyList(),
    val total: Int = 0,
)

@Serializable
data class ProviderDto(
    val name: String = "",
    val hasKey: Boolean = false,
    val baseUrl: String = "",
    val model: String = "",
    val enabled: Boolean = false,
    val recommended: Boolean = false,
)

@Serializable
data class CodeStatusDto(
    val available: Boolean = false,
    val state: String = "off",
    val active: Boolean = false,
    val statusText: String = "",
)

@Serializable
data class WorkspaceInfoDto(
    val cwd: String = "",
    val projectName: String = "",
    val projectType: String = "",
    val isGit: Boolean = false,
    val branch: String = "",
    val remoteUrl: String = "",
)

@Serializable
data class WorkspaceTreeDto(
    val root: String = "",
    val currentPath: String = ".",
    val items: List<JsonObject> = emptyList(),
)

@Serializable
data class BrainStatusDto(
    val total: Int = 0,
    val subconsciousTotal: Int = 0,
    val byType: Map<String, Int> = emptyMap(),
    val learningPaused: Boolean = false,
    val profileSummary: String = "",
    val activeSummary: String = "",
    val available: Boolean = true,
    val shareLearning: Boolean = false,
    val shareableCount: Int = 0,
)

@Serializable
data class MemoriesResponse(
    val memories: List<JsonObject> = emptyList(),
    val total: Int = 0,
    val available: Boolean = true,
)

/** `GET /api/bots/:id/crew` — a fleet lead, its crew and the configured cap. */
@Serializable
data class BotCrewResponse(
    val lead: JsonObject? = null,
    val crew: List<JsonObject> = emptyList(),
    val maxCrew: Int = 0,
    val error: String = "",
)

/**
 * `POST /api/bots/:id/message` — 202 `{accepted:true, jobId, status:"accepted"}`,
 * 409 `{accepted:false, reasonCode}` (busy, paused, cap reached).
 */
@Serializable
data class BotDispatch(
    val accepted: Boolean = false,
    val jobId: String = "",
    val status: String = "",
    val reasonCode: String = "",
)
