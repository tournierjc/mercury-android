package io.github.tournierjc.mercury.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.tournierjc.mercury.AppContainer
import io.github.tournierjc.mercury.data.MercuryApi
import io.github.tournierjc.mercury.data.Notice
import io.github.tournierjc.mercury.data.dto.McpServerDto
import io.github.tournierjc.mercury.data.dto.PermissionsDto
import io.github.tournierjc.mercury.data.dto.ProviderDto
import io.github.tournierjc.mercury.data.dto.ProviderRoutingDto
import io.github.tournierjc.mercury.data.dto.ProviderTestResult
import io.github.tournierjc.mercury.data.dto.SkillDto
import io.github.tournierjc.mercury.data.dto.UsageDto
import io.github.tournierjc.mercury.data.dto.UsageEntry
import io.github.tournierjc.mercury.data.toNotice
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

/**
 * A plugin as `/api/plugins` reports it.
 *
 * [PluginDto] is the manifest the daemon types; the live fields — `enabled`, `running`, `tools`,
 * `renderers` — are only in the payload, and they are exactly what this screen exists to show.
 * So the route is read as raw JSON and folded into this one object instead of widening a DTO that
 * belongs to another screen.
 */
data class PluginSummary(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val kind: String = "",
    val tier: String = "",
    val status: String = "",
    val version: String = "",
    val enabled: Boolean = false,
    val running: Boolean = false,
    val tools: Int = 0,
    val renderers: List<String> = emptyList(),
)

/** One row of `GET /api/schedules`. `nextRun` is absent on this daemon, hence the default. */
data class ScheduleSummary(
    val id: String = "",
    val cron: String = "",
    val description: String = "",
    val prompt: String = "",
    val botId: String = "",
    val createdAt: String = "",
    val nextRun: String = "",
)

/** One filesystem scope of the runtime's permission manifest. */
data class ScopeSummary(val path: String = "", val read: Boolean = false, val write: Boolean = false)

/**
 * The two capabilities a phone can usefully describe: which paths the runtime may touch, and
 * whether a shell is open (and how many patterns it refuses).
 */
data class PermissionSummary(
    val filesystemEnabled: Boolean = false,
    val scopes: List<ScopeSummary> = emptyList(),
    val shellEnabled: Boolean = false,
    val blockedCount: Int = 0,
)

/** Tokens spent under one heading — a model, a channel — with its share of the logged total. */
data class UsageSlice(val label: String = "", val tokens: Long = 0, val share: Int = 0)

/** One logged request, with its wall clock already formatted for the row. */
data class UsageRequest(
    val model: String = "",
    val provider: String = "",
    val channel: String = "",
    val input: Long = 0,
    val output: Long = 0,
    val total: Long = 0,
    val time: String = "",
)

/**
 * The Configure group, in one state: seven read-only surfaces that answer "what is this instance
 * made of". Every list defaults to empty so each screen renders its empty state instead of a
 * spinner over a blank page.
 */
data class ConfigureUiState(
    val loading: Boolean = true,
    val providers: List<ProviderDto> = emptyList(),
    val skills: List<SkillDto> = emptyList(),
    val mcpAvailable: Boolean = true,
    val mcpServers: List<McpServerDto> = emptyList(),
    val plugins: List<PluginSummary> = emptyList(),
    val permissions: PermissionSummary? = null,
    val schedules: List<ScheduleSummary> = emptyList(),
    val usage: UsageDto? = null,
    /** False when the daemon says it could not read the usage tracker — its zeros are not facts. */
    val usageAvailable: Boolean = true,
    /** False when the daemon has no scheduler attached: an empty list is not "no routines". */
    val schedulesAvailable: Boolean = true,
    val byModel: List<UsageSlice> = emptyList(),
    val byChannel: List<UsageSlice> = emptyList(),
    val recent: List<UsageRequest> = emptyList(),
    /** A write to a provider is in flight: the row's two glyphs wait rather than queue a second. */
    val busy: Boolean = false,
    /** The last probe, and whose it was — a result shown under another row would be a lie. */
    val providerTest: ProviderTestResult? = null,
    val providerTestFor: String = "",
    val error: Notice? = null,
    /**
     * The routes that did not answer. A failed route leaves its list empty, and an empty list
     * rendered as "aucun fournisseur / 0 planification" is a claim about the server made out of our
     * own failure — the 401 after every restart is not an edge case here, it is the default state.
     */
    val failed: Set<ConfigureSlice> = emptySet(),
)

/** One per route, named so a screen can ask whether its own slice is trustworthy. */
enum class ConfigureSlice { Providers, Skills, Mcp, Plugins, Permissions, Schedules, Usage }

/**
 * Reads the seven Configure routes once and hands each screen its slice.
 *
 * One instance serves the whole group (Compose keys it to the activity), so walking from
 * Fournisseurs to Usage does not re-fetch what was just loaded; every screen still carries a
 * refresh button. A failing route leaves its list empty and raises one banner — it never blanks
 * the screen or hides the other six answers.
 */
class ConfigureViewModel(private val api: MercuryApi) : ViewModel() {

    private val _state = MutableStateFlow(ConfigureUiState())
    val state: StateFlow<ConfigureUiState> = _state.asStateFlow()
    private val providerEditor = ProviderEditor(api)

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            var failure: Notice? = null
            fun note(e: Throwable) {
                if (failure == null) failure = e.toNotice()
            }

            val failed = mutableSetOf<ConfigureSlice>()
            suspend fun <T> slice(which: ConfigureSlice, block: suspend () -> T, fallback: T): T =
                runCatching { block() }.getOrElse { note(it); failed += which; fallback }

            val providers = slice(ConfigureSlice.Providers, { api.providers() }, emptyList())
            val skills = slice(ConfigureSlice.Skills, { api.skills() }, emptyList())
            val mcp = slice(ConfigureSlice.Mcp, { api.mcpServers() }, null)
            // Plugins need fields PluginDto does not carry (enabled, running, tools, renderers),
            // so this one route is read raw.
            val pluginList = slice(ConfigureSlice.Plugins, { plugins(api.getJson("/api/plugins")) }, emptyList())
            val permissionSummary = slice(ConfigureSlice.Permissions, { permissions(api.permissions()) }, null)
            var schedulesAvailable = true
            val scheduleList = slice<List<ScheduleSummary>>(
                ConfigureSlice.Schedules,
                {
                    val page = api.schedulesPage()
                    schedulesAvailable = page.available != false
                    page.schedules.map { row -> schedule(row) }
                },
                emptyList(),
            )
            var usageAvailable = true
            val usage = slice<UsageDto?>(
                ConfigureSlice.Usage,
                {
                    val dto = api.usage()
                    usageAvailable = dto.available != false
                    dto
                },
                null,
            )

            _state.update {
                it.copy(
                    loading = false,
                    providers = providers,
                    skills = skills,
                    mcpAvailable = mcp?.available ?: true,
                    mcpServers = mcp?.servers.orEmpty(),
                    plugins = pluginList,
                    permissions = permissionSummary,
                    schedules = scheduleList,
                    usage = usage,
                    usageAvailable = usageAvailable,
                    schedulesAvailable = schedulesAvailable,
                    byModel = usage?.requestLog.orEmpty().let { log -> slices(log) { entry -> entry.model } },
                    byChannel = usage?.requestLog.orEmpty().let { log -> slices(log) { entry -> entry.channelType } },
                    recent = usage?.requestLog.orEmpty().let(::recent),
                    error = failure,
                    failed = failed,
                )
            }
        }
    }

    /**
     * Writes what the edit sheet collected: a key, an endpoint, a model, the enabled flag, routing.
     *
     * The Fournisseurs screen used to answer these two glyphs with empty lambdas, so the pencil and
     * the bolt did nothing at all — the only writable copy of the row lived in Réglages. Same API,
     * same dialog, one screen that does what it draws.
     */
    fun saveProvider(
        id: String,
        apiKey: String? = null,
        baseUrl: String? = null,
        model: String? = null,
        enabled: Boolean? = null,
        routing: ProviderRoutingDto? = null,
    ) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            providerEditor.save(id, apiKey, baseUrl, model, enabled, routing)
                .onSuccess { _state.update { it.copy(busy = false) } }
                .onFailure { e -> _state.update { it.copy(busy = false, error = e.toNotice()) } }
            // Only a save reloads the list — and so it must not follow a failure, whose banner
            // refresh() would wipe before it was ever read.
            if (_state.value.error == null) refresh()
        }
    }

    /** Probes one provider; the answer lands under its own row, server words untouched. */
    fun testProvider(id: String) {
        viewModelScope.launch {
            _state.update { it.copy(providerTest = null, providerTestFor = id, error = null) }
            val result = providerEditor.test(id)
            _state.update { it.copy(providerTest = result, providerTestFor = id) }
        }
    }

    fun clearBanner() {
        _state.update { it.copy(error = null) }
    }

    // -----------------------------------------------------------------
    // Payload folding: raw JSON in, one small object out
    // -----------------------------------------------------------------

    private fun plugins(raw: JsonObject): List<PluginSummary> =
        (raw["items"] as? JsonArray).orEmpty()
            .mapNotNull { it as? JsonObject }
            .map { item ->
                PluginSummary(
                    id = item.text("id"),
                    name = item.text("name"),
                    description = item.text("description"),
                    kind = item.text("kind"),
                    tier = item.text("tier"),
                    status = item.text("status"),
                    version = item.text("installedVersion").ifBlank { item.text("latestVersion") },
                    enabled = item.flag("enabled"),
                    running = item.flag("running"),
                    tools = (item["tools"] as? JsonArray)?.size ?: 0,
                    renderers = item.strings("renderers"),
                )
            }

    private fun permissions(dto: PermissionsDto): PermissionSummary {
        val capabilities = (dto.manifest["capabilities"] as? JsonObject).orEmpty()
        val filesystem = capabilities["filesystem"] as? JsonObject
        val shell = capabilities["shell"] as? JsonObject
        val scopes = (filesystem?.get("scopes") as? JsonArray).orEmpty()
            .mapNotNull { it as? JsonObject }
            .map { scope ->
                ScopeSummary(
                    path = scope.text("path"),
                    read = scope.flag("read"),
                    write = scope.flag("write"),
                )
            }
        return PermissionSummary(
            filesystemEnabled = filesystem?.flag("enabled") == true,
            scopes = scopes,
            shellEnabled = shell?.flag("enabled") == true,
            blockedCount = (shell?.get("blocked") as? JsonArray)?.size ?: 0,
        )
    }

    private fun schedule(row: JsonObject): ScheduleSummary = ScheduleSummary(
        id = row.text("id"),
        cron = row.text("cron"),
        description = row.text("description"),
        prompt = row.text("prompt"),
        botId = row.text("botId"),
        createdAt = row.text("createdAt"),
        nextRun = row.text("nextRun")
            .ifBlank { row.text("next_run") }
            .ifBlank { row.text("nextRunAt") },
    )

    /** Grouped, summed, biggest first. Entries that spent nothing are noise on this screen. */
    private fun slices(log: List<UsageEntry>, key: (UsageEntry) -> String): List<UsageSlice> {
        val total = log.sumOf { it.totalTokens }
        return log.groupBy { key(it).ifBlank { UNNAMED } }
            .map { (label, entries) ->
                val tokens = entries.sumOf { it.totalTokens }
                UsageSlice(
                    label = label,
                    tokens = tokens,
                    share = if (total > 0) ((tokens * 100) / total).toInt() else 0,
                )
            }
            .filter { it.tokens > 0 }
            .sortedByDescending { it.tokens }
    }

    /** The log is a window, not the day: the newest twenty, without pretending otherwise. */
    private fun recent(log: List<UsageEntry>): List<UsageRequest> = log
        .sortedByDescending { it.timestamp }
        .take(20)
        .map { entry ->
            UsageRequest(
                model = entry.model,
                provider = entry.provider,
                channel = entry.channelType,
                input = entry.inputTokens,
                output = entry.outputTokens,
                total = entry.totalTokens,
                time = clock(entry.timestamp),
            )
        }

    private fun clock(timestamp: Long): String {
        if (timestamp <= 0) return ""
        return runCatching {
            Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).format(CLOCK)
        }.getOrDefault("")
    }

    companion object {
        /** What an empty model or channel reads as: punctuation, not a word to translate. */
        const val UNNAMED: String = "—"

        private val CLOCK: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { ConfigureViewModel(container.api) }
        }
    }
}

private fun JsonObject.text(key: String): String = (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()

private fun JsonObject.flag(key: String): Boolean = (this[key] as? JsonPrimitive)?.booleanOrNull ?: false

private fun JsonObject.strings(key: String): List<String> =
    (this[key] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
