package io.github.tournierjc.mercury.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.tournierjc.mercury.AppContainer
import io.github.tournierjc.mercury.data.Attachments
import io.github.tournierjc.mercury.data.AuthException
import io.github.tournierjc.mercury.data.BotAutonomy
import kotlinx.serialization.json.put
import io.github.tournierjc.mercury.data.BotConfigPatch
import io.github.tournierjc.mercury.data.BotJson
import io.github.tournierjc.mercury.data.BotOps
import io.github.tournierjc.mercury.data.NoticeSeverity
import io.github.tournierjc.mercury.data.BotPermissionsDoc
import io.github.tournierjc.mercury.data.BotRoutines
import io.github.tournierjc.mercury.data.MercuryApi
import io.github.tournierjc.mercury.data.Notice
import io.github.tournierjc.mercury.data.NoticeKey
import io.github.tournierjc.mercury.data.toNotice
import io.github.tournierjc.mercury.ui.attachmentNotice
import io.github.tournierjc.mercury.data.PendingFile
import io.github.tournierjc.mercury.data.RunRow
import io.github.tournierjc.mercury.data.BotSlug
import io.github.tournierjc.mercury.data.dto.BotCrewResponse
import io.github.tournierjc.mercury.bots.BotMessage
import io.github.tournierjc.mercury.bots.BotThread
import io.github.tournierjc.mercury.data.dto.BotRunDto
import io.github.tournierjc.mercury.data.dto.DeliverableDto
import io.github.tournierjc.mercury.data.dto.DlqJobDto
import io.github.tournierjc.mercury.data.dto.ReasoningBlockDto
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
    /**
     * The permissions document being edited. Null means "nothing pending", so every screen keeps
     * reading [permissions]; a draft equal to it is not an edit either
     * (`BotPermissionsDoc.isDirty`). Saving is an explicit tap on the sheet's header button, which
     * is what makes the "your changes will be lost" prompt possible.
     */
    val permissionsDraft: JsonObject? = null,
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
    /** `runId` → the tool calls the turn made: the bubbles' step pills. */
    val traces: Map<String, List<TraceStepDto>> = emptyMap(),
    /** `runId` → the full answer: the journal truncates its summary at 300 characters. */
    val answers: Map<String, String> = emptyMap(),
    /**
     * `runId` → the thinking the transcript recorded, step by step.
     *
     * Kept in the state, not only folded into [messages]: the chat screen rebuilds the conversation
     * on every live tick, and a screen that rebuilds from some of the state drops the rest — which
     * is exactly how the tool calls and the thinking were fetched, stored, and never drawn.
     */
    val reasoning: Map<String, List<ReasoningBlockDto>> = emptyMap(),
    /** `runIds` whose thinking the daemon bounded. */
    val reasoningTruncated: Set<String> = emptySet(),
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
    /** A roster read on demand: the header button spins on this, not on the first load. */
    val refreshing: Boolean = false,
    /** What the last dispatch answered: queued, or the reason it was refused. */
    val dispatchFeedback: Notice? = null,
    val detailLoading: Boolean = false,
    /** Models the daemon offers: the provider dropdown of the bot's model section. */
    val models: List<io.github.tournierjc.mercury.data.dto.ModelRef> = emptyList(),
    /** Tier catalogue — `readonly`, `builder`, `operator`, `full` with their deny lists. */
    val tiers: List<io.github.tournierjc.mercury.data.dto.BotTierDto> = emptyList(),
    /**
     * The roster as the selection last saw it: the memory section picks the bots a teammate may
     * read from this list, and it only receives the state.
     */
    val roster: List<JsonObject> = emptyList(),
    /** Filled by the bundle download, consumed by the share sheet, then cleared. */
    val bundle: String? = null,
    val bundleFor: String = "",
)

/**
 * Teammates: customization (persona, permissions, model, autonomy), supervision (activity,
 * needs-you, journal), the fleet (crew, delegated tasks) and background dispatch.
 */
/**
 * The name a rename sends: whitespace collapsed, trimmed, at most 60 characters — the rules the
 * creation route applies — or null when there is nothing left to send.
 */
fun cleanBotName(name: String): String? = name.trim().replace(Regex("\\s+"), " ").take(60).takeIf { it.isNotBlank() }

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

    /**
     * Re-reads the fleet and **applies** it. It used to fetch `/api/bots` and discard the answer —
     * clearing the error banner and nothing else, which is why the refresh button looked dead.
     */
    fun refreshRoster() {
        viewModelScope.launch {
            _state.update { s -> s.copy(refreshing = true) }
            runCatching { api.bots() }
                .onSuccess { roster ->
                    watcher.ingest(roster)
                    _state.update { s -> s.copy(refreshing = false, error = null, roster = roster.bots) }
                }
                .onFailure { e -> _state.update { s -> s.copy(refreshing = false, error = e.readable()) } }
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
                permissionsDraft = null,
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
                messages = emptyList(),
                // Not cleared: coming back to the screen mid-turn must show the message again.
                pending = BotThread.keepPending(it.pending, emptyList()),
                detailLoading = true,
                dispatchFeedback = null,
            )
        }
        viewModelScope.launch {
            val detail = runCatching { api.botDetail(id) }.getOrNull()
            // The live feed usually carries the roster already; the sheet's cross-bot list must
            // not be empty on a cold start, and one read of `/api/bots` is cheaper than a
            // section that silently offers nothing.
            val roster = watcher.state.value.bots.ifEmpty { runCatching { api.bots() }.getOrNull()?.bots.orEmpty() }
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
            val traces = turns.mapValues { (_, transcript) -> transcript?.trace.orEmpty() }
            // The transcript already had it: reading the thinking costs no extra request.
            val thoughts = turns.mapValues { (_, transcript) -> transcript?.reasoning.orEmpty() }
            val thoughtsCut = turns.filterValues { it?.reasoningTruncated == true }.keys
            // A slow fetch must not repaint the sheet of the bot the owner has since selected:
            // twelve transcripts can lag well behind a quick second selection.
            if (_state.value.selectedId != id) return@launch
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
                    traces = traces,
                    reasoning = thoughts,
                    reasoningTruncated = thoughtsCut,
                    messages = BotThread.build(last, prompts, answers, traces = traces, reasoning = thoughts, reasoningTruncated = thoughtsCut),
                    // Kept until the journal holds its record: a refresh must not erase a message
                    // the owner already sent.
                    pending = BotThread.keepPending(it.pending, last),
                    dlq = dlq?.dlq.orEmpty(),
                    roster = roster,
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

    /**
     * Loads one run's full answer, for a bubble that only had the journal's summary.
     *
     * The transcript is the only place the whole answer lives: the summary is cut at 300
     * characters, which is where a fenced `openui` block stops being a layout. The answer is
     * merged into the map the thread is built from, so the bubble redraws instead of the screen
     * reloading twelve runs to gain one.
     */
    fun loadAnswer(runId: String) {
        val id = _state.value.selectedId ?: return
        if (runId.isBlank() || _state.value.answers[runId]?.isNotBlank() == true) return
        viewModelScope.launch {
            val transcript = runCatching { api.botRun(id, runId) }.getOrNull() ?: return@launch
            if (_state.value.selectedId != id) return@launch
            _state.update { state ->
                val answers = state.answers + (runId to transcript.output)
                val traces = state.traces + (runId to transcript.trace)
                val reasoning = if (transcript.reasoning.isNotEmpty()) {
                    state.reasoning + (runId to transcript.reasoning)
                } else {
                    state.reasoning
                }
                val prompts = if (transcript.prompt.isNotBlank()) state.prompts + (runId to transcript.prompt) else state.prompts
                state.copy(
                    answers = answers,
                    prompts = prompts,
                    traces = traces,
                    reasoning = reasoning,
                    messages = BotThread.build(state.records, prompts, answers, traces = traces, reasoning = reasoning),
                )
            }
        }
    }

    /**
     * One cheap look at the journal; the conversation is reloaded only if it says something new.
     *
     * The thread used to reload on the live feed's writing → over transition alone. When that event
     * never arrives — the stream dropped, the turn ended while the app was in the background, the
     * process was killed — the answer sat in the journal and the owner had to relaunch the app to
     * see it. The journal is the durable record, so it is what the open screen watches.
     */
    fun pollConversation(id: String = _state.value.selectedId.orEmpty()) {
        if (id.isBlank()) return
        viewModelScope.launch {
            val newest = runCatching { api.botRunJournal(id, limit = 1) }.getOrNull()
                ?.lastOrNull()?.runId
            if (!BotThread.isStale(_state.value.records.lastOrNull()?.runId, newest)) return@launch
            if (_state.value.selectedId != id) return@launch
            reloadConversation(id)
        }
    }

    fun reloadConversation(id: String) {
        if (id.isBlank()) return
        viewModelScope.launch {
            runCatching {
                val records = api.botRunJournal(id).takeLast(12)
                val turns = records.map { record ->
                    async { record.runId to transcript(id, record.runId) }
                }.awaitAll().toMap()
                Triple(
                    records,
                    turns.mapValues { (_, t) -> t?.prompt.orEmpty() },
                    turns.mapValues { (_, t) -> t?.output.orEmpty() },
                )
            }.onSuccess { (records, prompts, answers) ->
                if (_state.value.selectedId != id) return@onSuccess
                _state.update {
                    it.copy(
                        records = records,
                        prompts = prompts,
                        answers = answers,
                        messages = BotThread.build(records, prompts, answers),
                        pending = BotThread.keepPending(it.pending, records),
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
                    // Écrire à un bot, c'est s'abonner à ses nouvelles : un équipier qui n'est
                    // pas leader sonne désormais sur le téléphone.
                    if (result.accepted) container.botNotifyMemory.mark(id)
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
                                    at = System.currentTimeMillis(),
                                    state = "queued",
                                    botId = id,
                                )
                            } else {
                                it.pending
                            },
                            dispatchFeedback = if (result.accepted) {
                                // The job id is a log detail, not something an owner reads over his keyboard: the
                                // echo in the thread and the phase line already say it is queued.
                                Notice(NoticeKey.DISPATCH_QUEUED).asInfo()
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

    fun setEnabled(id: String, enabled: Boolean) {
        val body = buildJsonObject { put("enabled", enabled) }
        patch(
            id,
            body,
            // The same fact the console reports, same key, teammate named.
            Notice(if (enabled) NoticeKey.TEAMMATE_ENABLED else NoticeKey.TEAMMATE_DISABLED, args = listOf(id)),
        )
    }

    /**
     * `model` is an object in the manifest — `{ provider, model }` — so a bot inherits the main
     * default only when the key is absent. Sending a bare string wrote the wrong shape.
     */
    fun setModel(id: String, provider: String, model: String) {
        val body = buildJsonObject {
            put(
                "model",
                buildJsonObject {
                    put("provider", provider)
                    put("model", model)
                },
            )
        }
        patch(id, body, Notice(NoticeKey.TEAMMATE_MODEL_SAVED))
    }

    /** Back to the main default: the key is removed rather than emptied. */
    fun clearModel(id: String) {
        val body = buildJsonObject { put("model", null as String?) }
        patch(id, body, Notice(NoticeKey.TEAMMATE_MODEL_CLEARED))
    }

    /** The provider/model list, once per screen: it changes when a key is added, not per bot. */
    fun loadModels() {
        if (_state.value.models.isNotEmpty()) return
        viewModelScope.launch {
            runCatching { api.models().providers }
                .onSuccess { list -> _state.update { it.copy(models = list) } }
                .onFailure { /* the dropdown stays empty; the free-text path is gone, no fake list */ }
        }
    }

    /**
     * The name is a label the owner reads; the id is the identity and does not move. Same rules as
     * creation — whitespace collapsed, trimmed, 60 characters — and a blank name is refused here
     * too, so the sheet cannot send one.
     */
    fun rename(id: String, name: String) {
        val cleaned = cleanBotName(name)
        if (cleaned == null) {
            _state.update { it.copy(error = Notice(NoticeKey.TEAMMATE_NAME_REFUSED)) }
            return
        }
        patch(id, buildJsonObject { put("name", cleaned) }, Notice(NoticeKey.TEAMMATE_RENAMED))
    }

    fun setDescription(id: String, description: String) {
        // Free text pasted from anywhere: trimming is what keeps a stray CRLF or a trailing
        // space from being stored, and the structured build keeps every control character
        // safe on the way out — the hand-built JSON never did.
        patch(id, buildJsonObject { put("description", description.trim()) }, Notice(NoticeKey.TEAMMATE_DESCRIPTION_SAVED))
    }

    /** Daily token cap lives in `autonomy`; 0 means "no cap". */
    /**
     * Writes one autonomy limit, merged into the manifest's own object.
     *
     * `PATCH /api/bots/:id` replaces `autonomy` wholesale, so the old one-key payload erased the
     * other limits — the daily budget, then the step budget and the turn's wall clock silently
     * back to the defaults. [BotAutonomy.merge] sends the whole object every time.
     */
    fun setAutonomyLimit(id: String, key: String, value: Int?) {
        val merged = BotAutonomy.merge(_state.value.manifest, mapOf(key to value))
        val body = buildJsonObject {
            put("autonomy", merged)
        }
        patch(id, body, Notice(NoticeKey.TEAMMATE_BUDGET_SAVED))
    }

    fun setDailyBudget(id: String, tokens: Int) =
        setAutonomyLimit(id, BotAutonomy.DAILY_TOKENS, tokens)

    /**
     * Writes one routine, with the list rebuilt from the manifest the sheet already holds.
     *
     * `PATCH /api/bots/:id` replaces `schedules` wholesale, exactly like `autonomy`: sending a
     * single entry would drop every other routine without a word of complaint from the daemon.
     */
    fun saveRoutine(id: String, routine: BotRoutines.Routine) {
        // The form refuses to send an invalid one, so this is the belt to its braces: a malformed
        // cron reaches the daemon, which stores it and never fires it, and nothing on screen says so.
        if (!BotRoutines.isValid(routine)) return
        writeSchedules(id, BotRoutines.upsert(BotRoutines.list(_state.value.manifest), routine))
    }

    /** Drops one routine by name; the rest of the array goes back whole, for the same reason. */
    fun deleteRoutine(id: String, name: String) {
        writeSchedules(id, BotRoutines.remove(BotRoutines.list(_state.value.manifest), name))
    }

    private fun writeSchedules(id: String, routines: List<JsonObject>) {
        val body = buildJsonObject {
            put(BotRoutines.KEY, BotRoutines.serialize(routines))
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            runCatching { api.patchBot(id, body) }
                .onSuccess {
                    // No banner: the list redrawing itself from the refreshed manifest is the
                    // answer, and a notice above it shifted the whole sheet on every save.
                    _state.update { s -> s.copy(busy = false) }
                    refreshManifest(id)
                }
                .onFailure { e -> _state.update { s -> s.copy(busy = false, error = e.readable()) } }
        }
    }

    // -------------------------------------------------------------------
    // Memory, comms and retention: three manifest objects the daemon
    // replaces whole, so each write carries the object back complete
    // -------------------------------------------------------------------

    /**
     * Writes one top-level object of a bot's manifest.
     *
     * `PATCH /api/bots/:id` replaces every object it is sent, so [value] is never a partial one:
     * it comes from [BotConfigPatch], merged from the manifest the sheet already holds. No
     * banner — the section redraws from the manifest the write re-reads, and a failure still
     * lands in the error slot above it.
     */
    fun setBotConfig(id: String, key: String, value: JsonObject) {
        val body = buildJsonObject { put(key, value) }
        patch(id, body, success = null)
    }

    /**
     * The `memory` object, written whole.
     *
     * The fields this edit does not name are read back from the manifest and travel with it: a
     * null here means "the manifest does not hold it either", which removes nothing that existed.
     */
    private fun writeMemory(id: String, scope: String? = null, learn: Boolean? = null, recall: List<String>? = null) {
        val manifest = _state.value.manifest
        val value = BotConfigPatch.memory(
            manifest,
            scope = scope ?: BotConfigPatch.text(manifest, BotConfigPatch.MEMORY, BotConfigPatch.SCOPE),
            learn = learn ?: BotConfigPatch.flag(manifest, BotConfigPatch.MEMORY, BotConfigPatch.LEARN),
            recall = recall ?: BotConfigPatch.strings(manifest, BotConfigPatch.MEMORY, BotConfigPatch.ALLOW_CROSS_BOT_RECALL),
        )
        setBotConfig(id, BotConfigPatch.MEMORY, value)
    }

    fun setMemoryScope(id: String, scope: String) = writeMemory(id, scope = scope)

    fun setMemoryLearn(id: String, learn: Boolean) = writeMemory(id, learn = learn)

    /** The bots whose learned context this one may re-read; an empty list clears the grant. */
    fun setMemoryRecall(id: String, ids: List<String>) = writeMemory(id, recall = ids)

    /** The roster the bot is told it may write to, as the prompt shows it. */
    fun setComms(id: String, canMessage: List<String>) =
        setBotConfig(id, BotConfigPatch.COMMS, BotConfigPatch.comms(_state.value.manifest, canMessage))

    /** One retention number; a blank field (null) or 0 hands the key back to the daemon's default. */
    fun setRetentionLimit(id: String, key: String, value: Long?) = setBotConfig(
        id,
        BotConfigPatch.RETENTION,
        BotConfigPatch.retention(_state.value.manifest, mapOf(key to value)),
    )

    /** The sandbox janitor: its switch and its two durations, merged as one sub-object. */
    fun setSandboxJanitor(id: String, enabled: Boolean, hotDays: Long?, archiveDays: Long?) = setBotConfig(
        id,
        BotConfigPatch.RETENTION,
        BotConfigPatch.retention(
            _state.value.manifest,
            janitor = BotConfigPatch.Janitor(enabled = enabled, hotDays = hotDays, archiveDays = archiveDays),
        ),
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

    /**
     * Records an edit to the permissions document without sending it.
     *
     * The sheet used to write on every tap, which left nothing to save and nothing to warn about
     * when the owner backed out mid-edit. Edits now accumulate here, and the header button is the
     * only thing that talks to the daemon.
     */
    fun draftPermissions(doc: JsonObject) {
        _state.update { it.copy(permissionsDraft = doc) }
    }

    /** Throws the pending edit away — after the prompt, or when the sheet is left. */
    fun discardPermissionsDraft() {
        _state.update { it.copy(permissionsDraft = null) }
    }

    /**
     * Writes the pending document and keeps the answer.
     *
     * The draft is cleared only on success: a failed write leaves the owner's edits on screen, where
     * they can be retried, instead of dropping them into an error banner.
     */
    fun savePermissionsDraft(id: String) {
        val draft = _state.value.permissionsDraft ?: return
        if (!BotPermissionsDoc.isDirty(_state.value.permissions, draft)) {
            // Nothing changed, so there is nothing to write: a round trip for an identical document
            // only risks a version conflict on the daemon's side.
            _state.update { it.copy(permissionsDraft = null) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, message = null, error = null) }
            runCatching { api.setBotPermissionsDoc(id, draft) }
                .onSuccess { result ->
                    val stored = result["permissions"] as? JsonObject
                    _state.update { s -> s.copy(busy = false, permissions = stored ?: draft, permissionsDraft = null) }
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
        // The banner names the teammate, not the id: the manifest is gone once the delete lands,
        // so the label is read up front (BotJson.name falls back to the id when the roster
        // carries no better one).
        val name = BotJson.name(
            _state.value.roster.firstOrNull { BotJson.id(it) == id }
                ?: watcher.state.value.bots.firstOrNull { BotJson.id(it) == id },
        )
        viewModelScope.launch {
            _state.update { it.copy(busy = true, message = null, error = null) }
            runCatching { api.deleteBot(id) }
                .onSuccess {
                    if (_state.value.selectedId == id) {
                        _state.value = BotsUiState() // the sheet closes; the roster is the screen
                    }
                    _state.update { s -> s.copy(busy = false, message = Notice(NoticeKey.TEAMMATE_DELETED_NAMED, args = listOf(name))) }
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

    /**
     * Dismisses the "queued" acknowledgement alone.
     *
     * It fades on its own after a few seconds; clearing the banner wholesale would also take away
     * a failure the owner has not read yet.
     */
    fun clearDispatchFeedback() =
        _state.update { it.copy(dispatchFeedback = it.dispatchFeedback?.takeIf { n -> n.severity != NoticeSeverity.Info }) }

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

    private fun patch(id: String, body: JsonObject, success: Notice?) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            runCatching {
                api.patchBot(id, body)
            }
                .onSuccess {
                    _state.update { s -> s.copy(busy = false, message = success ?: s.message) }
                    // The sheet's rows read the model from the manifest: without this the row
                    // kept the old label until the bot was reopened, which looked like the
                    // change had not been applied.
                    refreshManifest(id)
                    // And the roster feeds the drawer, the header and the fleet screen: the
                    // daemon announces the edit, this is the same answer taken directly, so the
                    // name changes even if the live feed is a beat behind.
                    refreshRoster()
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

    /** One mapping for the whole file (and the same one the other screens use): see `toNotice`. */
    private fun Throwable.readable(): Notice = toNotice()

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { BotsViewModel(container.api, container) }
        }
    }
}
