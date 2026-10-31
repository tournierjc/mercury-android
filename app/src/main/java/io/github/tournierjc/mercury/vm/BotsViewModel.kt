package io.github.tournierjc.mercury.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.tournierjc.mercury.AppContainer
import io.github.tournierjc.mercury.data.Attachments
import io.github.tournierjc.mercury.data.AuthException
import io.github.tournierjc.mercury.data.BotJson
import io.github.tournierjc.mercury.data.BotOps
import io.github.tournierjc.mercury.data.MercuryApi
import io.github.tournierjc.mercury.data.Notice
import io.github.tournierjc.mercury.data.NoticeKey
import io.github.tournierjc.mercury.data.toNotice
import io.github.tournierjc.mercury.ui.attachmentNotice
import io.github.tournierjc.mercury.data.PendingFile
import io.github.tournierjc.mercury.data.RunRow
import io.github.tournierjc.mercury.data.WireTime
import io.github.tournierjc.mercury.data.BotSlug
import io.github.tournierjc.mercury.data.dto.BotCrewResponse
import io.github.tournierjc.mercury.bots.BotMessage
import io.github.tournierjc.mercury.bots.BotThread
import io.github.tournierjc.mercury.data.dto.BotRunDto
import io.github.tournierjc.mercury.data.dto.DeliverableDto
import io.github.tournierjc.mercury.data.dto.DlqJobDto
import io.github.tournierjc.mercury.data.dto.RunTranscriptDto
import io.github.tournierjc.mercury.data.dto.TraceStepDto
import io.github.tournierjc.mercury.util.OutputActions
import io.github.tournierjc.mercury.util.OutputPreviewState
import io.github.tournierjc.mercury.util.SharedFile
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull

data class BotsUiState(
    val selectedId: String? = null,
    val manifest: JsonObject? = null,
    val liveState: JsonObject? = null,
    val persona: String = "",
    val personaLoaded: Boolean = false,
    val permissions: JsonObject? = null,
    val crew: BotCrewResponse? = null,
    val tasks: List<JsonObject> = emptyList(),
    val journal: List<JsonObject> = emptyList(),
    val inbox: List<JsonObject> = emptyList(),
    /** Deliverables of the selected bot, newest first. */
    val outputs: List<DeliverableDto> = emptyList(),
    /** Runs, journal lines joined with the transcripts the daemon still keeps. */
    val runs: List<RunRow> = emptyList(),
    /** Journal lines of the selected bot, kept to rebuild the conversation while it streams. */
    val records: List<BotRunDto> = emptyList(),
    /** `runId` → the task the owner dispatched, from the run transcripts. */
    val prompts: Map<String, String> = emptyMap(),
    /** `runId` → the full answer: the journal truncates its summary at 300 characters. */
    val answers: Map<String, String> = emptyMap(),
    /** `runId` → the transcript's `timestamp`, epoch milliseconds (SPECS/115 §5 bis). */
    val dates: Map<String, Long?> = emptyMap(),
    /** `runId` → the tool calls the turn made: the bubbles' step pills. */
    val traces: Map<String, List<TraceStepDto>> = emptyMap(),
    /** The conversation as the chat screen draws it, oldest first. */
    val messages: List<BotMessage> = emptyList(),
    /** Turns sent from this screen that the journal does not know about yet. */
    val pending: List<BotMessage> = emptyList(),
    val dlq: List<DlqJobDto> = emptyList(),
    val runDetail: RunTranscriptDto? = null,
    val runDetailLoading: Boolean = false,
    /** The deliverable being previewed, with its text or its bytes. */
    val outputPreview: OutputPreviewState? = null,
    /** A downloaded deliverable the screen hands to the share sheet, then clears. */
    val pendingShare: SharedFile? = null,
    val busy: Boolean = false,
    /** What the last finished action says (a key, resolved by the screen). */
    val message: Notice? = null,
    val error: Notice? = null,
    /** What the last dispatch answered: queued, or the reason it was refused. */
    val dispatchFeedback: Notice? = null,
    val detailLoading: Boolean = false,
    /** Models the daemon offers: the provider dropdown of the bot's model section. */
    val models: List<io.github.tournierjc.mercury.data.dto.ModelRef> = emptyList(),
    /** Tier catalogue — `readonly`, `builder`, `operator`, `full` with their deny lists. */
    val tiers: List<io.github.tournierjc.mercury.data.dto.BotTierDto> = emptyList(),
    /** Filled by the bundle download, consumed by the share sheet, then cleared. */
    val bundle: String? = null,
    val bundleFor: String = "",
)

/**
 * Teammates: customization (persona, permissions, model, autonomy), supervision (activity,
 * needs-you, journal), the fleet (crew, delegated tasks) and background dispatch.
 */
class BotsViewModel(
    private val api: MercuryApi,
    private val container: AppContainer,
) : ViewModel() {

    val watcher = container.botWatcher

    /** Deliverable actions, shared with the Console's fleet inbox. */
    private val outputActions = OutputActions(api)

    private val _state = MutableStateFlow(BotsUiState())
    val state: StateFlow<BotsUiState> = _state.asStateFlow()

    /** Faces by bot id. A bot without one keeps its monogram — nothing is invented. */
    private val _avatars = MutableStateFlow<Map<String, ByteArray>>(emptyMap())
    val avatars: StateFlow<Map<String, ByteArray>> = _avatars.asStateFlow()
    private val avatarInFlight = mutableSetOf<String>()

    init {
        watcher.start()
    }

    /**
     * Fetches the faces the roster says exist and we do not hold yet. Driven by
     * `hasAvatar`, so a Mercury without avatars costs zero requests.
     */
    fun loadAvatars(bots: List<JsonObject>) {
        bots.filter { BotJson.hasAvatar(it) }
            .map { BotJson.id(it) }
            .filter { id -> id.isNotBlank() && _avatars.value[id] == null && avatarInFlight.add(id) }
            .forEach { id ->
                viewModelScope.launch {
                    runCatching { api.botAvatar(id) }
                        .onSuccess { bytes -> if (bytes != null) _avatars.update { it + (id to bytes) } }
                        .onFailure { /* a missing face is not an error: the monogram stays */ }
                    avatarInFlight.remove(id)
                }
            }
    }

    /** Puts a picked photo on a bot. The bytes leave the phone already downscaled. */
    fun uploadAvatar(id: String, bytes: ByteArray) {
        viewModelScope.launch {
            runCatching { api.uploadBotAvatar(id, Attachments.encode(bytes)) }
                .onSuccess {
                    _avatars.update { it + (id to bytes) }
                    _state.update { s -> s.copy(message = Notice(NoticeKey.CREW_PHOTO_SAVED)) }
                }
                .onFailure { e ->
                    // A 4xx here is the server refusing the bytes: say why, do not retry.
                    _state.update { s -> s.copy(error = e.readable()) }
                }
        }
    }

    fun removeAvatar(id: String) {
        viewModelScope.launch {
            runCatching { api.deleteBotAvatar(id) }
                .onSuccess {
                    _avatars.update { it - id }
                    _state.update { s -> s.copy(message = Notice(NoticeKey.CREW_PHOTO_REMOVED)) }
                }
                .onFailure { e -> _state.update { s -> s.copy(error = e.readable()) } }
        }
    }

    fun refreshRoster() {
        viewModelScope.launch {
            runCatching { api.bots() }
                .onSuccess { _state.update { s -> s.copy(error = null) } }
                .onFailure { e -> _state.update { s -> s.copy(error = e.readable()) } }
        }
    }

    fun select(id: String?) {
        if (id == null) {
            _state.update { BotsUiState() }
            return
        }
        _state.update {
            it.copy(
                selectedId = id,
                manifest = null,
                persona = "",
                personaLoaded = false,
                permissions = null,
                crew = null,
                tasks = emptyList(),
                journal = emptyList(),
                inbox = emptyList(),
                outputs = emptyList(),
                runs = emptyList(),
                dlq = emptyList(),
                runDetail = null,
                outputPreview = null,
                records = emptyList(),
                prompts = emptyMap(),
                answers = emptyMap(),
                dates = emptyMap(),
                messages = emptyList(),
                pending = emptyList(),
                detailLoading = true,
                dispatchFeedback = null,
            )
        }
        viewModelScope.launch {
            val detail = runCatching { api.botDetail(id) }.getOrNull()
            val persona = runCatching { api.botPersona(id) }.getOrNull()
            val permissions = runCatching { api.botPermissions(id) }.getOrNull()
            val crew = runCatching { api.botCrew(id) }.getOrNull()
            val tasks = runCatching { api.botTasks(id) }.getOrNull()
            val inbox = runCatching { api.botInbox(id) }.getOrNull()
            val outputs = runCatching { api.botOutputs(id) }.getOrNull()
            val transcripts = runCatching { api.botRuns(id) }.getOrNull()
            val records = runCatching { api.botRunJournal(id) }.getOrNull()
            val dlq = runCatching { api.botDlq(id) }.getOrNull()
            // The journal carries the answer, not the question: the prompt lives in the run
            // transcript. Twelve of them, fetched side by side, are enough to read a
            // conversation and cheap enough to load with the sheet.
            val last = records.orEmpty().takeLast(12)
            val turns = last.map { record ->
                async { record.runId to transcript(id, record.runId) }
            }.awaitAll().toMap()
            val prompts = turns.mapValues { (_, transcript) -> transcript?.prompt.orEmpty() }
            val answers = turns.mapValues { (_, transcript) -> transcript?.output.orEmpty() }
            // The transcript's `timestamp`, presence included (SPECS/115 §3.4): absent keeps the
            // journal's clock, a written-but-unreadable one draws no hour, a date dates the turn.
            val dates = mutableMapOf<String, Long?>()
            turns.forEach { (runId, transcript) ->
                when (val date = transcript?.timestamp ?: WireTime.Absent) {
                    is WireTime.At -> dates[runId] = date.millis
                    WireTime.Unreadable -> dates[runId] = null
                    WireTime.Absent -> Unit
                }
            }
            val traces = turns.mapValues { (_, transcript) -> transcript?.trace.orEmpty() }
            _state.update {
                it.copy(
                    manifest = detail?.bot,
                    liveState = detail?.state,
                    persona = (persona?.get("persona") as? JsonPrimitive)?.contentOrNull.orEmpty(),
                    personaLoaded = true,
                    permissions = permissions,
                    crew = crew,
                    tasks = array(tasks, "tasks"),
                    inbox = array(inbox, "inbox"),
                    journal = detail?.journal ?: emptyList(),
                    outputs = outputs?.outputs.orEmpty(),
                    runs = BotOps.mergeRuns(transcripts?.runs.orEmpty(), records.orEmpty()),
                    records = last,
                    prompts = prompts,
                    answers = answers,
                    dates = dates,
                    traces = traces,
                    messages = BotThread.build(last, prompts, answers, dates, traces = traces),
                    pending = emptyList(),
                    dlq = dlq?.dlq.orEmpty(),
                    detailLoading = false,
                )
            }
        }
    }

    private fun array(node: JsonObject?, key: String): List<JsonObject> =
        (node?.get(key) as? kotlinx.serialization.json.JsonArray)
            ?.mapNotNull { entry -> runCatching { entry as JsonObject }.getOrNull() }
            ?: emptyList()

    /**
     * Re-reads the journal and the last prompts of one bot.
     *
     * Called when a turn stops streaming: the live tail is a 600-character window, so the
     * final answer has to come back from the journal once the run is over.
     */
    /**
     * One run's transcript, with a retry.
     *
     * At turn end the journal and the transcript are written a moment apart: a single fetch
     * can miss the answer and leave the conversation showing the truncated summary — the
     * answer the owner just watched being written then looks like it vanished.
     */
    private suspend fun transcript(id: String, runId: String): RunTranscriptDto? {
        repeat(3) { attempt ->
            runCatching { api.botRun(id, runId) }.getOrNull()?.let { transcript ->
                if (transcript.output.isNotBlank()) return transcript
            }
            if (attempt < 2) delay(300L * (attempt + 1))
        }
        return runCatching { api.botRun(id, runId) }.getOrNull()
    }

    fun reloadConversation(id: String) {
        if (id.isBlank()) return
        viewModelScope.launch {
            runCatching {
                val records = api.botRunJournal(id).takeLast(12)
                val turns = records.map { record ->
                    async { record.runId to transcript(id, record.runId) }
                }.awaitAll().toMap()
                records to turns
            }.onSuccess { (records, turns) ->
                if (_state.value.selectedId != id) return@onSuccess
                val prompts = turns.mapValues { (_, t) -> t?.prompt.orEmpty() }
                val answers = turns.mapValues { (_, t) -> t?.output.orEmpty() }
                val dates = mutableMapOf<String, Long?>()
                turns.forEach { (runId, t) ->
                    when (val date = t?.timestamp ?: WireTime.Absent) {
                        is WireTime.At -> dates[runId] = date.millis
                        WireTime.Unreadable -> dates[runId] = null
                        WireTime.Absent -> Unit
                    }
                }
                val traces = turns.mapValues { (_, t) -> t?.trace.orEmpty() }
                _state.update {
                    it.copy(
                        records = records,
                        prompts = prompts,
                        answers = answers,
                        dates = dates,
                        traces = traces,
                        messages = BotThread.build(records, prompts, answers, dates, traces = traces),
                        pending = emptyList(),
                    )
                }
            }
        }
    }

    /** Re-reads one bot's deliverable folder — what the conversation's sheet shows. */
    fun reloadOutputs(id: String) {
        if (id.isBlank()) return
        viewModelScope.launch {
            runCatching { api.botOutputs(id) }
                .onSuccess { page -> if (_state.value.selectedId == id) _state.update { it.copy(outputs = page.outputs) } }
                .onFailure { e -> _state.update { it.copy(error = e.readable()) } }
        }
    }

    /**
     * Queues a message for the bot: the call returns in milliseconds, the work runs after.
     *
     * Files can go alone — the route accepts an empty prompt when something is attached,
     * and the server captions the turn itself.
     */
    fun dispatch(message: String, files: List<PendingFile> = emptyList(), chat: Boolean = false) {
        val id = _state.value.selectedId ?: return
        if (message.isBlank() && files.isEmpty()) return
        val notice = attachmentNotice(Attachments.problemWith(files))
        if (notice != null) {
            _state.update { it.copy(error = notice) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, dispatchFeedback = null, error = null) }
            runCatching {
                if (chat) {
                    api.chatWithBot(id, message.trim(), attachments = files.map { it.toUpload() })
                } else {
                    api.dispatchBot(id, message.trim(), attachments = files.map { it.toUpload() })
                }
            }
                .onSuccess { result ->
                    _state.update {
                        it.copy(
                            busy = false,
                            // The turn appears at once, as the owner wrote it; the answer
                            // replaces the empty bubble when the run lands in the journal.
                            pending = if (result.accepted) {
                                it.pending + BotMessage(
                                    key = "${result.jobId}-me",
                                    fromBot = false,
                                    text = message.trim().ifBlank { files.joinToString(" · ") { it.name } },
                                    // Not dated: the phone's clock is not the server's, and this
                                    // turn has no journal line yet (SPECS/115 §3.4).
                                    at = null,
                                    state = "queued",
                                )
                            } else {
                                it.pending
                            },
                            dispatchFeedback = if (result.accepted) {
                                Notice(NoticeKey.DISPATCH_QUEUED, args = listOf(result.jobId.take(12))).asInfo()
                            } else {
                                // `reasonCode` is the server's own code (queue_full, paused…) and
                                // travels as the detail: it says more than a house sentence would.
                                Notice(NoticeKey.DISPATCH_REFUSED).withDetail(result.reasonCode)
                            },
                        )
                    }
                }
                .onFailure { e -> _state.update { it.copy(busy = false, error = e.readable()) } }
        }
    }

    fun setEnabled(id: String, enabled: Boolean) =
        patch(
            id,
            """{"enabled":$enabled}""",
            // The same fact the console reports, same key, teammate named.
            Notice(if (enabled) NoticeKey.TEAMMATE_ENABLED else NoticeKey.TEAMMATE_DISABLED, args = listOf(id)),
        )

    /**
     * `model` is an object in the manifest — `{ provider, model }` — so a bot inherits the main
     * default only when the key is absent. Sending a bare string wrote the wrong shape.
     */
    fun setModel(id: String, provider: String, model: String) = patch(
        id,
        """{"model":{"provider":"${escape(provider)}","model":"${escape(model)}"}}""",
        Notice(NoticeKey.TEAMMATE_MODEL_SAVED),
    )

    /** Back to the main default: the key is removed rather than emptied. */
    fun clearModel(id: String) = patch(id, """{"model":null}""", Notice(NoticeKey.TEAMMATE_MODEL_CLEARED))

    /** The provider/model list, once per screen: it changes when a key is added, not per bot. */
    fun loadModels() {
        if (_state.value.models.isNotEmpty()) return
        viewModelScope.launch {
            runCatching { api.models().providers }
                .onSuccess { list -> _state.update { it.copy(models = list) } }
                .onFailure { /* the dropdown stays empty; the free-text path is gone, no fake list */ }
        }
    }

    fun setDescription(id: String, description: String) =
        patch(id, """{"description":"${escape(description)}"}""", Notice(NoticeKey.TEAMMATE_DESCRIPTION_SAVED))

    /** Daily token cap lives in `autonomy`; 0 means "no cap". */
    fun setDailyBudget(id: String, tokens: Int) =
        patch(
            id,
            """{"autonomy":{"dailyTokenBudget":${if (tokens > 0) tokens else 0}}}""",
            Notice(NoticeKey.TEAMMATE_BUDGET_SAVED),
        )

    fun savePersona(text: String) {
        val id = _state.value.selectedId ?: return
        if (text.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            runCatching { api.setBotPersona(id, text) }
                .onSuccess { _state.update { s -> s.copy(busy = false, message = Notice(NoticeKey.PERSONA_SAVED)) } }
                .onFailure { e -> _state.update { s -> s.copy(busy = false, error = e.readable()) } }
        }
    }

    /**
     * Applies a tier and keeps the answer.
     *
     * The route returns the document it just wrote, so the cells can light up from that instead
     * of a second round trip — and a failure used to leave the sheet looking untouched, which
     * read as "the buttons do nothing".
     */
    fun setTier(id: String, tier: String) {
        viewModelScope.launch {
            // No banner: the cells are the feedback, and a message appearing above them shifted
            // the whole sheet on every tap.
            _state.update { it.copy(busy = true, message = null, error = null) }
            runCatching { api.setBotPermissions(id, tier) }
                .onSuccess { result ->
                    val stored = result["permissions"] as? JsonObject
                    _state.update { s -> s.copy(busy = false, permissions = stored ?: s.permissions) }
                }
                .onFailure { e -> _state.update { s -> s.copy(busy = false, error = e.readable()) } }
        }
    }

    /**
     * Takes the tier back: the bot returns to the daemon's default document, and no cell is lit.
     *
     * Tapping the active tier used to leave it active, which made the row look read-only to
     * anyone whose bot had never had a tier — the state was simply "no tier", not "nothing yet".
     */
    fun clearTier(id: String) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, message = null, error = null) }
            val defaults = buildJsonObject {
                put(
                    "paths",
                    JsonArray(
                        listOf(
                            buildJsonObject {
                                put("scope", JsonPrimitive("self"))
                                put("read", JsonPrimitive(true))
                                put("write", JsonPrimitive(true))
                            },
                        ),
                    ),
                )
            }
            runCatching { api.setBotPermissionsDoc(id, defaults) }
                .onSuccess { result ->
                    val stored = result["permissions"] as? JsonObject
                    _state.update { s -> s.copy(busy = false, permissions = stored ?: s.permissions) }
                }
                .onFailure { e -> _state.update { s -> s.copy(busy = false, error = e.readable()) } }
        }
    }

    /** The tier catalogue, for the sheets whose permissions file predates the `tier` field. */
    fun loadTiers() {
        if (_state.value.tiers.isNotEmpty()) return
        viewModelScope.launch {
            runCatching { api.botTiers() }
                .onSuccess { list -> _state.update { it.copy(tiers = list) } }
                .onFailure { /* no catalogue: the cells simply show no selection */ }
        }
    }

    fun promote(id: String) = act(Notice(NoticeKey.PROMOTED_LEAD)) { api.promoteBot(id) }

    fun autoCrew(id: String) = act(Notice(NoticeKey.CREW_AUTOBUILD_REQUESTED)) { api.autoCrewBot(id) }

    fun addCrew(leadId: String, id: String, name: String, description: String?, persona: String?, tier: String?) =
        act(Notice(NoticeKey.CREW_MEMBER_ADDED)) { api.addBotCrew(leadId, id, name, description, persona, tier) }

    /** An existing solo bot joins the fleet instead of a new one being created. */
    /**
     * `POST /api/bots/import` — a shared bundle, verbatim, as the body.
     *
     * It lives here rather than in the Console: the fleet is the Équipiers screen, and importing
     * one is the first thing that screen offers to an instance with no bots.
     */
    fun importBundle(bundleJson: String, overwrite: Boolean) = act(Notice(NoticeKey.BUNDLE_IMPORTED)) {
        api.importBotBundle(bundleJson, overwrite)
    }

    fun adoptCrew(leadId: String, memberId: String) =
        act(Notice(NoticeKey.CREW_MEMBER_ADOPTED)) { api.adoptBotCrew(leadId, memberId) }

    /** A member leaves the fleet and becomes solo again — reversible, so no confirmation. */
    fun detachCrew(leadId: String, memberId: String) =
        act(Notice(NoticeKey.CREW_MEMBER_DETACHED)) { api.detachBotCrew(leadId, memberId) }

    fun start(id: String) = act(Notice(NoticeKey.BOT_STARTED)) { api.startBot(id) }

    fun stop(id: String) = act(Notice(NoticeKey.BOT_STOPPED)) { api.stopBot(id) }

    fun run(id: String) = act(Notice(NoticeKey.RUN_REQUESTED)) { api.runBot(id) }

    /**
     * Deleting the open bot must also close its sheet: the manifest is gone, and a detail
     * screen left pointing at it would fetch a 404 on every reload.
     */
    fun delete(id: String) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, message = null, error = null) }
            runCatching { api.deleteBot(id) }
                .onSuccess {
                    if (_state.value.selectedId == id) {
                        _state.value = BotsUiState() // the sheet closes; the roster is the screen
                    }
                    _state.update { s -> s.copy(busy = false, message = Notice(NoticeKey.TEAMMATE_DELETED_NAMED, args = listOf(id))) }
                    refreshRoster()
                }
                .onFailure { e -> _state.update { s -> s.copy(busy = false, error = e.readable()) } }
        }
    }

    /**
     * `POST /api/bots` — a new teammate, opened straight away.
     *
     * The id is the slug the form proposed; the daemon refuses a duplicate or a malformed one
     * and its message is what the owner sees, because it names the actual problem.
     */
    fun createBot(name: String, id: String, tier: String, persona: String) {
        val cleanName = name.trim()
        val cleanId = id.trim().ifBlank { BotSlug.from(cleanName) }
        if (cleanName.isEmpty()) {
            _state.update { it.copy(error = Notice(NoticeKey.NAME_REQUIRED)) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, message = null, error = null) }
            runCatching {
                api.createBot(
                    id = cleanId,
                    name = cleanName,
                    tier = tier.ifBlank { null },
                    persona = persona.trim().ifBlank { null },
                )
            }
                .onSuccess {
                    _state.update { s -> s.copy(busy = false, message = Notice(NoticeKey.TEAMMATE_CREATED, args = listOf(cleanName))) }
                    refreshRoster()
                    select(cleanId)
                }
                .onFailure { e -> _state.update { s -> s.copy(busy = false, error = e.readable()) } }
        }
    }

    /**
     * `GET /api/bots/:id/bundle` — the shareable document. The screen hands it to the
     * share sheet as soon as it arrives, then clears it.
     */
    fun exportBundle(id: String) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            runCatching { api.botBundle(id) }
                .onSuccess { text -> _state.update { it.copy(busy = false, bundle = text, bundleFor = id) } }
                .onFailure { e -> _state.update { it.copy(busy = false, error = e.readable()) } }
        }
    }

    fun clearBundle() = _state.update { it.copy(bundle = null, bundleFor = "") }

    fun clearBanner() = _state.update { it.copy(message = null, error = null, dispatchFeedback = null) }

    /** Surfaces a screen-side failure (share sheet, missing file) in the same banner. */
    fun showError(message: String) =
        _state.update { it.copy(error = Notice(NoticeKey.REQUEST_REFUSED).withDetail(message)) }

    // -------------------------------------------------------------------
    // Deliverables: list, preview, share, remove (the fleet inbox reuses
    // the same OutputActions from the Console)
    // -------------------------------------------------------------------

    fun reloadOutputs() {
        val id = _state.value.selectedId ?: return
        viewModelScope.launch {
            runCatching { api.botOutputs(id) }
                .onSuccess { page -> _state.update { it.copy(outputs = page.outputs) } }
                .onFailure { e -> _state.update { it.copy(error = e.readable()) } }
        }
    }

    fun previewOutput(output: DeliverableDto) {
        val id = output.botId.ifBlank { _state.value.selectedId.orEmpty() }
        if (id.isBlank() || output.name.isBlank()) return
        _state.update { it.copy(outputPreview = OutputPreviewState(output.copy(botId = id), loading = true)) }
        viewModelScope.launch {
            val preview = outputActions.preview(output.copy(botId = id))
            _state.update { it.copy(outputPreview = preview) }
        }
    }

    fun dismissPreview() = _state.update { it.copy(outputPreview = null) }

    /** Downloads the bytes; the screen hands them to the share sheet and clears the slot. */
    fun shareOutput(output: DeliverableDto) {
        val id = output.botId.ifBlank { _state.value.selectedId.orEmpty() }
        if (id.isBlank() || output.name.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            runCatching { outputActions.download(output.copy(botId = id)) }
                .onSuccess { file -> _state.update { it.copy(busy = false, pendingShare = file) } }
                .onFailure { e -> _state.update { it.copy(busy = false, error = e.readable()) } }
        }
    }

    fun consumeShare() = _state.update { it.copy(pendingShare = null) }

    fun deleteOutput(output: DeliverableDto) {
        val id = output.botId.ifBlank { _state.value.selectedId.orEmpty() }
        if (id.isBlank() || output.name.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            runCatching { api.deleteOutput(id, output.name) }
                .onSuccess {
                    _state.update { s -> s.copy(busy = false, message = Notice(NoticeKey.OUTPUT_DELETED)) }
                    reloadOutputs()
                }
                .onFailure { e -> _state.update { s -> s.copy(busy = false, error = e.readable()) } }
        }
    }

    // -------------------------------------------------------------------
    // Run health: the transcript of one run, replaying a dead letter
    // -------------------------------------------------------------------

    fun openRun(row: RunRow) {
        val id = _state.value.selectedId ?: return
        if (!row.hasTranscript || row.runId.isBlank()) return
        _state.update { it.copy(runDetailLoading = true, runDetail = null) }
        viewModelScope.launch {
            runCatching { api.botRun(id, row.runId) }
                .onSuccess { transcript -> _state.update { it.copy(runDetail = transcript, runDetailLoading = false) } }
                .onFailure { e -> _state.update { it.copy(runDetailLoading = false, error = e.readable()) } }
        }
    }

    fun closeRun() = _state.update { it.copy(runDetail = null, runDetailLoading = false) }

    /**
     * Re-queues one dead-lettered job. A refusal (`not_found`, the job was consumed by a
     * previous replay) is an answer: it lands in the error banner, not in a retry loop.
     */
    fun replayIncident(job: DlqJobDto) {
        val id = _state.value.selectedId ?: return
        if (job.id.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, message = null, error = null) }
            runCatching { api.replayDlq(id, job.id) }
                .onSuccess { result ->
                    _state.update {
                        if (result.accepted) {
                            it.copy(busy = false, message = Notice(NoticeKey.JOB_RESTARTED, args = listOf(result.jobId.take(12))))
                        } else {
                            // The code is language-neutral; the label table still lives in
                            // BotOps and travels as the detail until it moves to resources.
                            it.copy(busy = false, error = Notice(NoticeKey.RESTART_REFUSED).withDetail(result.reasonCode))
                        }
                    }
                    reloadIncidents()
                }
                .onFailure { e -> _state.update { s -> s.copy(busy = false, error = e.readable()) } }
        }
    }

    /** Drops every dead letter of the fleet — the confirmation lives in the UI. */
    fun clearDlq() {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, message = null, error = null) }
            runCatching { api.clearDlq() }
                .onSuccess { response ->
                    _state.update { s ->
                        s.copy(busy = false, message = Notice(NoticeKey.INCIDENTS_CLEARED, args = listOf(response.cleared.toString())))
                    }
                    reloadIncidents()
                }
                .onFailure { e -> _state.update { s -> s.copy(busy = false, error = e.readable()) } }
        }
    }

    private fun reloadIncidents() {
        val id = _state.value.selectedId ?: return
        viewModelScope.launch {
            runCatching { api.botDlq(id) }
                .onSuccess { page -> _state.update { it.copy(dlq = page.dlq) } }
                .onFailure { /* the banner already carries the failure that led here */ }
        }
    }

    private fun patch(id: String, patchJson: String, success: Notice) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            runCatching {
                api.patchBot(id, kotlinx.serialization.json.Json.parseToJsonElement(patchJson) as JsonObject)
            }
                .onSuccess {
                    _state.update { s -> s.copy(busy = false, message = success) }
                    // The sheet's rows read the model from the manifest: without this the row
                    // kept the old label until the bot was reopened, which looked like the
                    // change had not been applied.
                    refreshManifest(id)
                }
                .onFailure { e -> _state.update { s -> s.copy(busy = false, error = e.readable()) } }
        }
    }

    /** The manifest alone, after a write. Deliberately not `select()`: no transcripts, no crew. */
    private suspend fun refreshManifest(id: String) {
        runCatching { api.botDetail(id) }.getOrNull()?.let { detail ->
            _state.update { it.copy(manifest = detail.bot, liveState = detail.state) }
        }
    }

    private fun act(success: Notice, block: suspend () -> Any?) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, message = null, error = null) }
            runCatching { block() }
                .onSuccess { _state.update { s -> s.copy(busy = false, message = success) } }
                .onFailure { e -> _state.update { s -> s.copy(busy = false, error = e.readable()) } }
        }
    }

    private fun escape(text: String): String =
        text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")

    /** One mapping for the whole file (and the same one the other screens use): see `toNotice`. */
    private fun Throwable.readable(): Notice = toNotice()

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { BotsViewModel(container.api, container) }
        }
    }
}
