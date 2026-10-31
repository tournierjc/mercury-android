package io.github.tournierjc.mercury.data

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import io.github.tournierjc.mercury.data.dto.AgentsResponse
import io.github.tournierjc.mercury.data.dto.AddMemoryRequest
import io.github.tournierjc.mercury.data.dto.BeliefGraphDto
import io.github.tournierjc.mercury.data.dto.BeliefsPage
import io.github.tournierjc.mercury.data.dto.BgResponse
import io.github.tournierjc.mercury.data.dto.BotDispatch
import io.github.tournierjc.mercury.data.dto.BotCrewResponse
import io.github.tournierjc.mercury.data.dto.TerminalResult
import io.github.tournierjc.mercury.data.dto.BotDetailDto
import io.github.tournierjc.mercury.data.dto.BotJournalPage
import io.github.tournierjc.mercury.data.dto.BotOutputsPage
import io.github.tournierjc.mercury.data.dto.BotRunDto
import io.github.tournierjc.mercury.data.dto.BotRunsPage
import io.github.tournierjc.mercury.data.dto.BotTiersPage
import io.github.tournierjc.mercury.data.dto.BotsCostReport
import io.github.tournierjc.mercury.data.dto.BotsResponse
import io.github.tournierjc.mercury.data.dto.BotsStorageReport
import io.github.tournierjc.mercury.data.dto.BundleImportResponse
import io.github.tournierjc.mercury.data.dto.DlqPage
import io.github.tournierjc.mercury.data.dto.DlqReplay
import io.github.tournierjc.mercury.data.dto.OutputPreviewDto
import io.github.tournierjc.mercury.data.dto.RunRecordPage
import io.github.tournierjc.mercury.data.dto.RunTranscriptDto
import io.github.tournierjc.mercury.data.dto.CreateBotRequest
import io.github.tournierjc.mercury.data.dto.UpdateBotRequest
import io.github.tournierjc.mercury.data.dto.InstanceConfig
import io.github.tournierjc.mercury.data.dto.InstanceConfigSave
import io.github.tournierjc.mercury.data.dto.BrainStatusDto
import io.github.tournierjc.mercury.data.dto.ChatSettings
import io.github.tournierjc.mercury.data.dto.ChatSettingsUpdate
import io.github.tournierjc.mercury.data.dto.CodeStatusDto
import io.github.tournierjc.mercury.data.dto.DeleteThreadRequest
import io.github.tournierjc.mercury.data.dto.HealthDto
import io.github.tournierjc.mercury.data.dto.ConnectFlowDto
import io.github.tournierjc.mercury.data.dto.InstallRecordDto
import io.github.tournierjc.mercury.data.dto.SandboxToolDto
import io.github.tournierjc.mercury.data.dto.SandboxToolsPage
import io.github.tournierjc.mercury.data.dto.MemoriesPage
import io.github.tournierjc.mercury.data.dto.MemoriesResponse
import io.github.tournierjc.mercury.data.dto.McpServersResponse
import io.github.tournierjc.mercury.data.dto.MemoryDto
import io.github.tournierjc.mercury.data.dto.ModelPrefsRequest
import io.github.tournierjc.mercury.data.dto.ModelsResponse
import io.github.tournierjc.mercury.data.dto.PasswordChangeRequest
import io.github.tournierjc.mercury.data.dto.PermissionsDto
import io.github.tournierjc.mercury.data.dto.PersonsPage
import io.github.tournierjc.mercury.data.dto.PluginDto
import io.github.tournierjc.mercury.data.dto.PluginHealthDto
import io.github.tournierjc.mercury.data.dto.PluginsResponse
import io.github.tournierjc.mercury.data.dto.ProviderDto
import io.github.tournierjc.mercury.data.dto.ProviderSaveResult
import io.github.tournierjc.mercury.data.dto.ProviderTestResult
import io.github.tournierjc.mercury.data.dto.ProviderUpdateRequest
import io.github.tournierjc.mercury.data.dto.RegistrySkillInstallRequest
import io.github.tournierjc.mercury.data.dto.RegistrySkillInstallResult
import io.github.tournierjc.mercury.data.dto.SchedulesResponse
import io.github.tournierjc.mercury.data.dto.ShutdownResult
import io.github.tournierjc.mercury.data.dto.SimpleResponse
import io.github.tournierjc.mercury.data.dto.SkillDto
import io.github.tournierjc.mercury.data.dto.SkillInstallRequest
import io.github.tournierjc.mercury.data.dto.SkillInstallResult
import io.github.tournierjc.mercury.data.dto.SkillsResponse
import io.github.tournierjc.mercury.data.dto.StatusDto
import io.github.tournierjc.mercury.data.dto.SwitchModelRequest
import io.github.tournierjc.mercury.data.dto.UpdateMemoryRequest
import io.github.tournierjc.mercury.data.dto.UsageDto
import io.github.tournierjc.mercury.data.dto.UsernameChangeRequest
import io.github.tournierjc.mercury.data.dto.GitBranchDto
import io.github.tournierjc.mercury.data.dto.GitBranchesDto
import io.github.tournierjc.mercury.data.dto.GitCommitDto
import io.github.tournierjc.mercury.data.dto.GitDiffDto
import io.github.tournierjc.mercury.data.dto.GitLogDto
import io.github.tournierjc.mercury.data.dto.GitStatusDto
import io.github.tournierjc.mercury.data.dto.SpotifyDevicesDto
import io.github.tournierjc.mercury.data.dto.SpotifyNowPlayingDto
import io.github.tournierjc.mercury.data.dto.SpotifyStatusDto
import io.github.tournierjc.mercury.data.dto.CommitMessageDto
import io.github.tournierjc.mercury.data.dto.WorkspaceFileDto
import io.github.tournierjc.mercury.data.dto.WorkspaceInfoDto
import io.github.tournierjc.mercury.data.dto.WorkspaceRootRequest
import io.github.tournierjc.mercury.data.dto.WorkspaceRootResult
import io.github.tournierjc.mercury.data.dto.WorkspaceTreeDto
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class MercuryException(message: String) : IOException(message)

open class AuthException(message: String) : IOException(message)

/**
 * The sign-in was refused (wrong password, unknown user) — not the same thing as a session that
 * expired mid-use, and it must not tell the owner to sign in again when they are signing in.
 */
class CredentialsRefusedException(message: String) : AuthException(message)

/**
 * Thin client over the HTTP API that Mercury's web dashboard already exposes
 * (`/api/auth/login`, the `/api/chat/…` routes). No Mercury-side plugin or patch needed.
 */
class MercuryApi(
    client: OkHttpClient,
    private val sessionJar: SessionCookieJar? = null,
    private val json: Json = MercuryEventParser.defaultJson,
) {

    private val client: OkHttpClient = client
    private val noRedirectClient: OkHttpClient = client.newBuilder().followRedirects(false).build()
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    @Volatile
    var baseUrl: String = ""
        private set

    fun hasSession(): Boolean = sessionJar?.hasSession() == true

    fun restoreBaseUrl(url: String) {
        baseUrl = normalizeBaseUrl(url)
    }

    fun clearSession() {
        sessionJar?.clear()
    }

    suspend fun login(rawBaseUrl: String, username: String, password: String) {
        val normalized = normalizeBaseUrl(rawBaseUrl)
        val form = FormBody.Builder()
            .add("username", username)
            .add("password", password)
            .build()
        val request = Request.Builder()
            .url(urlFor(normalized, "/api/auth/login").build())
            .post(form)
            .build()
        withContext(Dispatchers.IO) {
            noRedirectClient.newCall(request).execute().use { response ->
                when {
                    response.code in 200..399 -> Unit
                    response.code == 401 || response.code == 403 ->
                        throw CredentialsRefusedException("sign-in refused")

                    else -> throw MercuryException("connect: HTTP ${response.code}")
                }
            }
        }
        baseUrl = normalized
    }

    suspend fun health(): HealthResponse = getDecoded("/api/health", HealthResponse.serializer())

    suspend fun threads(): List<ThreadDto> =
        getDecoded("/api/chat/threads", ThreadsResponse.serializer()).threads.filter { it.id.isNotEmpty() }

    suspend fun threadMessages(threadId: String): List<MessageDto> =
        getDecoded("/api/chat/threads/$threadId", ThreadMessagesResponse.serializer()).messages

    suspend fun newThread(): ThreadDto? {
        val body = execute(
            Request.Builder()
                .url(url("/api/chat/threads"))
                .post("{}".toRequestBody(jsonMedia))
                .build(),
        )
        val thread = json.decodeFromString(ThreadDto.serializer(), body)
        return thread.takeIf { it.id.isNotEmpty() }
    }

    /**
     * Sends a user turn, optionally with files. Returns the session id the turn was bound to.
     *
     * A photo may travel without text: the server then names the turn
     * `(Attached: photo.jpg)` instead of sending an empty block a provider rejects.
     */
    suspend fun send(
        content: String,
        sessionId: String?,
        attachments: List<AttachmentUpload> = emptyList(),
    ): String? {
        val payload = json.encodeToString(
            SendRequest.serializer(),
            SendRequest(content, sessionId, attachments.takeIf { it.isNotEmpty() }),
        )
        val body = execute(
            Request.Builder()
                .url(url("/api/chat/send"))
                .post(payload.toRequestBody(jsonMedia))
                .build(),
        )
        return json.decodeFromString(SendResponse.serializer(), body).sessionId
    }

    suspend fun resolvePermission(requestId: String, action: String) {
        val payload = json.encodeToString(PermissionActionRequest.serializer(), PermissionActionRequest(action))
        execute(
            Request.Builder()
                .url(url("/api/chat/permission/$requestId"))
                .post(payload.toRequestBody(jsonMedia))
                .build(),
        )
    }

    // ---------------------------------------------------------------------
    // Bot customization, supervision and background dispatch
    // ---------------------------------------------------------------------

    /**
     * `POST /api/bots` — a new teammate.
     *
     * The daemon requires both an `id` and a `name`; `tier` (when given) writes the whole
     * permissions file, so the bot can act the moment it exists instead of starting read-only
     * with a tool gate that disagrees with its path scopes.
     *
     * Failures come back as a 4xx with `{error}`: the message is thrown so the form can show
     * the daemon's own reason (an id already taken, a tier that does not exist).
     */
    suspend fun createBot(
        id: String,
        name: String,
        tier: String? = null,
        persona: String? = null,
        description: String? = null,
    ): JsonObject = withContext(Dispatchers.IO) {
        val payload = buildJsonObject {
            put("id", JsonPrimitive(id))
            put("name", JsonPrimitive(name))
            if (!tier.isNullOrBlank()) put("tier", JsonPrimitive(tier))
            if (!persona.isNullOrBlank()) put("persona", JsonPrimitive(persona))
            if (!description.isNullOrBlank()) put("description", JsonPrimitive(description))
        }.toString()
        val request = Request.Builder()
            .url(url("/api/bots"))
            .post(payload.toRequestBody(jsonMedia))
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val reason = runCatching {
                    val parsed = json.parseToJsonElement(body) as JsonObject
                    (parsed["error"] as? JsonPrimitive)?.contentOrNull
                }.getOrNull()
                throw MercuryException(reason ?: "create: HTTP ${response.code}")
            }
            json.parseToJsonElement(body) as JsonObject
        }
    }

    /**
     * `POST /api/bots/:leadId/crew/adopt` — attach an existing bot to a fleet.
     *
     * `addBotCrew` only creates; this puts a bot that already exists under a lead, keeping its
     * identity, its persona and its history.
     */
    suspend fun adoptBotCrew(leadId: String, memberId: String): JsonObject =
        postJson(
            "/api/bots/${encode(leadId)}/crew/adopt",
            buildJsonObject { put("memberId", JsonPrimitive(memberId)) },
            JsonObject.serializer(),
        )

    /**
     * `POST /api/bots/:leadId/crew/:memberId/detach` — a member leaves its fleet.
     *
     * The bot keeps its identity, its history and its files; it stops being a crew member and
     * its profile moves back out of the lead's folder.
     */
    suspend fun detachBotCrew(leadId: String, memberId: String): JsonObject =
        postJson(
            "/api/bots/${encode(leadId)}/crew/${encode(memberId)}/detach",
            JsonObject(emptyMap()),
            JsonObject.serializer(),
        )

    /**
     * `PATCH /api/bots/:id` — the only route that rewrites a manifest. Accepted keys:
     * `enabled`, `description`, `model`, `tools`, `memory`, `comms`, `schedules`,
     * `autonomy`, `retention`. There is no name and no avatar field in Mercury 1.4.2.
     */
    suspend fun patchBot(id: String, patch: JsonObject): JsonObject =
        patchJson("/api/bots/${encode(id)}", patch, JsonObject.serializer())

    /**
     * `POST /api/bots/:id/message` — the "do this in the background" route: the job is
     * queued (202 `{accepted:true, jobId, status:"accepted"}`) and the bot's main thread is
     * never blocked. A busy or paused bot answers 409 `{accepted:false, reasonCode}`; that
     * body is decoded instead of thrown so the UI can explain the refusal.
     */
    /**
     * `POST /api/bots/:id/chat` — a message in the owner's conversation with a bot.
     *
     * Same queue and the same `202 {jobId}` answer as [dispatchBot], but the turn runs in
     * chat mode: the bot answers instead of filing a work report. A daemon that predates the
     * route answers 404; the caller keeps working because this throws, and the caller decides
     * — the conversation screen shows the reason rather than a silent nothing.
     */
    suspend fun chatWithBot(
        id: String,
        message: String,
        attachments: List<AttachmentUpload> = emptyList(),
    ): BotDispatch = dispatchBot(id, message, from = null, attachments = attachments, chat = true)

    suspend fun dispatchBot(
        id: String,
        message: String,
        from: String? = null,
        attachments: List<AttachmentUpload> = emptyList(),
        /** True to answer like a conversation, false to run as a delivery task. */
        chat: Boolean = false,
    ): BotDispatch =
        withContext(Dispatchers.IO) {
            val payload = buildJsonObject {
                put("message", kotlinx.serialization.json.JsonPrimitive(message))
                if (!from.isNullOrBlank()) put("from", kotlinx.serialization.json.JsonPrimitive(from))
                // Only when there is something to send: a server without the feature
                // ignores an absent field, but an empty array is still a new shape.
                if (attachments.isNotEmpty()) {
                    put(
                        "attachments",
                        kotlinx.serialization.json.JsonArray(
                            attachments.map { json.encodeToJsonElement(AttachmentUpload.serializer(), it) },
                        ),
                    )
                }
            }.toString()
            // Two registers, one message: `/chat` answers the owner in the conversation, `/message`
            // runs the turn as a delivery task. A daemon older than the `/chat` route answers 404 —
            // and a conversation must not die on a route the server does not have, so the same
            // message goes through `/message`. Only a 404 falls back: a 409 (busy, paused) or a 401
            // is a real answer about this bot, not a route that is missing.
            val registers = if (chat) listOf("chat", "message") else listOf("message")
            var last: BotDispatch? = null
            for ((index, register) in registers.withIndex()) {
                val request = Request.Builder()
                    .url(url("/api/bots/${encode(id)}/$register"))
                    .post(payload.toRequestBody(jsonMedia))
                    .build()
                val parsed = client.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (response.code == 401 || response.code == 403) throw AuthException("session expired")
                    if (response.code == 404 && index < registers.lastIndex) {
                        null // no such route here: try the other register
                    } else {
                        runCatching { json.decodeFromString(BotDispatch.serializer(), body) }.getOrNull()
                            ?: BotDispatch(accepted = false, reasonCode = if (body.isBlank()) "HTTP ${response.code}" else body.take(120))
                    }
                }
                if (parsed != null) return@withContext parsed
                last = BotDispatch(accepted = false, reasonCode = "HTTP 404")
            }
            last ?: BotDispatch(accepted = false, reasonCode = "HTTP 404")
        }
    suspend fun botPersona(id: String): JsonObject =
        getDecoded("/api/bots/${encode(id)}/persona", JsonObject.serializer())

    suspend fun setBotPersona(id: String, persona: String): JsonObject = putJson(
        "/api/bots/${encode(id)}/persona",
        JsonObject(mapOf("persona" to kotlinx.serialization.json.JsonPrimitive(persona))),
        JsonObject.serializer(),
    )

    /** Delegated tasks of a fleet lead: `{tasks:[{id,assignee,status,stage,goal,result}]}`. */
    suspend fun botTasks(id: String): JsonObject =
        getDecoded("/api/bots/${encode(id)}/tasks", JsonObject.serializer())

    /** Pending bot-to-bot or owner mail for a bot. */
    suspend fun botInbox(id: String): JsonObject =
        getDecoded("/api/bots/${encode(id)}/inbox", JsonObject.serializer())

    /**
     * Live roster and activity of the whole bot fleet. Reconnection is the caller's job:
     * the flow completes when the stream ends.
     */
    fun botEvents(): Flow<BotEvent> = callbackFlow {
        val request = Request.Builder()
            .url(url("/api/bots/events"))
            .header("Accept", "text/event-stream")
            .get()
            .build()
        val call = client.newCall(request)
        val job = launch(Dispatchers.IO) {
            try {
                call.execute().use { response ->
                    if (!response.isSuccessful) {
                        throw if (response.code == 401) {
                            AuthException("session expired")
                        } else {
                            MercuryException("crew stream: HTTP ${response.code}")
                        }
                    }
                    val source = response.body?.source() ?: run { close(); return@use }
                    val parser = SseParser()
                    while (!source.exhausted()) {
                        val line = source.readUtf8Line() ?: break
                        for (raw in parser.feed(line + "\n")) {
                            trySend(BotEvent.parse(raw.type, raw.data))
                        }
                    }
                }
                close()
            } catch (e: Exception) {
                close(e)
            }
        }
        awaitClose { job.cancel(); call.cancel() }
    }

    /**
     * Live board: `GET /api/boards/:id/events` pushes the whole normalised board on connect
     * and after every change (the daemon debounces 800 ms), plus a `feedback-request` frame
     * when a running card waits for a decision. Reconnection is the caller's job: the flow
     * completes when the stream ends, and the caller keeps the board fresh another way.
     */
    fun boardEvents(boardId: String): Flow<BoardEvent> = callbackFlow {
        val request = Request.Builder()
            .url(url("/api/boards/${encode(boardId)}/events"))
            .header("Accept", "text/event-stream")
            .get()
            .build()
        val call = client.newCall(request)
        val job = launch(Dispatchers.IO) {
            try {
                call.execute().use { response ->
                    if (!response.isSuccessful) {
                        throw if (response.code == 401 || response.code == 403) {
                            AuthException("session expired")
                        } else {
                            MercuryException("board stream: HTTP ${response.code}")
                        }
                    }
                    val source = response.body?.source() ?: run { close(); return@use }
                    val parser = SseParser()
                    while (!source.exhausted()) {
                        val line = source.readUtf8Line() ?: break
                        for (raw in parser.feed(line + "\n")) {
                            trySend(BoardEvent.parse(raw.type, raw.data))
                        }
                    }
                }
                close()
            } catch (e: Exception) {
                close(e)
            }
        }
        awaitClose { job.cancel(); call.cancel() }
    }

    // ---------------------------------------------------------------------
    // Terminal: one shell command in the workspace
    // ---------------------------------------------------------------------

    /**
     * `POST /api/terminal/exec` — runs [command] in the workspace (or [cwd] inside it)
     * with a 30 s timeout. Arbitrary code execution on the host: the UI gates it.
     */
    suspend fun execTerminal(command: String, cwd: String? = null): TerminalResult = postJson(
        "/api/terminal/exec",
        body("command" to command, "cwd" to cwd),
        TerminalResult.serializer(),
    )

    // ---------------------------------------------------------------------
    // Git: the workspace repository, eleven routes from `workspace-ide.ts`
    // ---------------------------------------------------------------------

    /** `GET /api/git/status` — branch, upstream distance, changed files (both sides). */
    suspend fun gitStatus(): GitStatusDto = getDecoded("/api/git/status", GitStatusDto.serializer())

    suspend fun gitBranches(): List<GitBranchDto> =
        getDecoded("/api/git/branches", GitBranchesDto.serializer()).branches

    /** `GET /api/git/log` — the daemon caps the count at 100. */
    suspend fun gitLog(count: Int = 10): List<GitCommitDto> =
        getDecoded("/api/git/log", GitLogDto.serializer(), mapOf("count" to count.toString())).commits

    /**
     * `GET /api/git/diff` — staged or unstaged, optionally one file. The answer is a raw
     * patch: the console shows it as monospace text, it does not render it.
     */
    suspend fun gitDiff(path: String? = null, staged: Boolean = false): String = getDecoded(
        "/api/git/diff",
        GitDiffDto.serializer(),
        buildMap {
            put("staged", if (staged) "true" else "false")
            if (!path.isNullOrBlank()) put("file", path)
        },
    ).diff

    /** `POST /api/git/checkout` — `create` runs `checkout -b`. */
    suspend fun gitCheckout(branch: String, create: Boolean = false): SimpleResponse = postJson(
        "/api/git/checkout",
        body("branch" to branch, "create" to create.takeIf { it }),
        SimpleResponse.serializer(),
    )

    /** `POST /api/git/stage` — `{files:[…]}`, one `git add` per call. */
    suspend fun gitStage(files: List<String>): SimpleResponse =
        postJson("/api/git/stage", body("files" to jsonStrings(files)), SimpleResponse.serializer())

    /** `POST /api/git/unstage` — `reset HEAD`, the paths stay in the working tree. */
    suspend fun gitUnstage(files: List<String>): SimpleResponse =
        postJson("/api/git/unstage", body("files" to jsonStrings(files)), SimpleResponse.serializer())

    /**
     * `POST /api/git/commit` — the daemon appends its own `Co-authored-by` trailer, so the
     * message sent here is the message the owner typed and nothing more.
     */
    suspend fun gitCommit(message: String): SimpleResponse = postJson(
        "/api/git/commit",
        body("message" to message),
        SimpleResponse.serializer(),
    )

    /** `POST /api/git/push` — defaults to `origin`, current branch, no `-u` unless asked. */
    suspend fun gitPush(branch: String? = null, setUpstream: Boolean = false): SimpleResponse = postJson(
        "/api/git/push",
        body("branch" to branch, "setUpstream" to setUpstream.takeIf { it }),
        SimpleResponse.serializer(),
    )

    suspend fun gitPull(): SimpleResponse =
        postJson("/api/git/pull", JsonObject(emptyMap()), SimpleResponse.serializer())

    /**
     * `POST /api/git/generate-commit-message` — the runtime's model reads the staged diff
     * (or the unstaged one when nothing is staged) and answers one message.
     */
    suspend fun generateCommitMessage(): String = postJson(
        "/api/git/generate-commit-message",
        JsonObject(emptyMap()),
        CommitMessageDto.serializer(),
    ).message

    // ---------------------------------------------------------------------
    // Spotify: a convenience surface, nine playback routes
    // ---------------------------------------------------------------------

    /** `GET /api/spotify/status` — always 200, `{available:false}` without a client. */
    suspend fun spotifyStatus(): SpotifyStatusDto =
        getDecoded("/api/spotify/status", SpotifyStatusDto.serializer())

    /** `GET /api/spotify/now-playing` — one formatted line, or `{error}` when unconnected. */
    suspend fun spotifyNowPlaying(): SpotifyNowPlayingDto =
        getDecoded("/api/spotify/now-playing", SpotifyNowPlayingDto.serializer())

    /** `GET /api/spotify/devices` — Spotify's own body, nested one level down. */
    suspend fun spotifyDevices(): List<SpotifyDevice> =
        SpotifyJson.devices(getDecoded("/api/spotify/devices", SpotifyDevicesDto.serializer()).devices)

    suspend fun spotifyPlay(): SimpleResponse =
        postJson("/api/spotify/play", JsonObject(emptyMap()), SimpleResponse.serializer())

    suspend fun spotifyPause(): SimpleResponse =
        postJson("/api/spotify/pause", JsonObject(emptyMap()), SimpleResponse.serializer())

    suspend fun spotifyNext(): SimpleResponse =
        postJson("/api/spotify/next", JsonObject(emptyMap()), SimpleResponse.serializer())

    suspend fun spotifyPrevious(): SimpleResponse =
        postJson("/api/spotify/previous", JsonObject(emptyMap()), SimpleResponse.serializer())

    /** `POST /api/spotify/volume` — `{volume: 0…100}`. */
    suspend fun spotifyVolume(volume: Int): SimpleResponse = postJson(
        "/api/spotify/volume",
        body("volume" to volume.coerceIn(0, 100)),
        SimpleResponse.serializer(),
    )

    /** `POST /api/spotify/shuffle` — `{state: true|false}`. */
    suspend fun spotifyShuffle(state: Boolean): SimpleResponse =
        postJson("/api/spotify/shuffle", body("state" to state), SimpleResponse.serializer())

    /** A JSON array of strings, as `/api/git/stage` and `/api/git/unstage` read it. */
    private fun jsonStrings(values: List<String>): JsonArray = JsonArray(values.map { JsonPrimitive(it) })

    // ---------------------------------------------------------------------
    // Fleets: a lead bot and its crew
    // ---------------------------------------------------------------------

    suspend fun botCrew(id: String): BotCrewResponse =
        getDecoded("/api/bots/${encode(id)}/crew", BotCrewResponse.serializer())

    /** `POST /api/bots/:id/crew` — adds a crew member under [leadId]. */
    suspend fun addBotCrew(
        leadId: String,
        id: String,
        name: String,
        description: String? = null,
        persona: String? = null,
        tier: String? = null,
    ): JsonObject = postJson(
        "/api/bots/${encode(leadId)}/crew",
        body("id" to id, "name" to name, "description" to description, "persona" to persona, "tier" to tier),
        JsonObject.serializer(),
    )

    suspend fun promoteBot(id: String): JsonObject =
        postJson("/api/bots/${encode(id)}/promote", JsonObject(emptyMap()), JsonObject.serializer())

    /** `POST /api/bots/:id/autocrew` — 202: the runtime builds the crew in the background. */
    suspend fun autoCrewBot(id: String): JsonObject =
        postJson("/api/bots/${encode(id)}/autocrew", JsonObject(emptyMap()), JsonObject.serializer())

    suspend fun botPermissions(id: String): JsonObject =
        getDecoded("/api/bots/${encode(id)}/permissions", JsonObject.serializer())

    /** `PUT /api/bots/:id/permissions` — a named tier replaces the whole file. */
    suspend fun setBotPermissions(id: String, tier: String): JsonObject = putJson(
        "/api/bots/${encode(id)}/permissions",
        JsonObject(mapOf("tier" to kotlinx.serialization.json.JsonPrimitive(tier))),
        JsonObject.serializer(),
    )

    /**
     * `PUT /api/bots/:id/permissions {permissions}` — writes a document instead of a tier.
     *
     * This is how a tier is taken back: the daemon's default document
     * (`{paths:[{scope:'self', read, write}]}`, no tool gate) is what a bot has before anyone
     * picks a tier, and it belongs to no tier — so the sheet has nothing to light up.
     */
    suspend fun setBotPermissionsDoc(id: String, permissions: JsonObject): JsonObject = putJson(
        "/api/bots/${encode(id)}/permissions",
        buildJsonObject { put("permissions", permissions) },
        JsonObject.serializer(),
    )

    // ---------------------------------------------------------------------
    // Boards: Mercury's kanban execution surface (43 routes).
    // Shapes are navigated with BoardJson because no board exists on the
    // reference instance, so nothing here has an observed contract.
    // ---------------------------------------------------------------------

    suspend fun boards(): JsonObject = getDecoded("/api/boards", JsonObject.serializer())

    suspend fun board(id: String): JsonObject = getDecoded("/api/boards/${encode(id)}", JsonObject.serializer())

    suspend fun createBoard(name: String, description: String? = null): JsonObject = postJson(
        "/api/boards",
        body("name" to name, "description" to description),
        JsonObject.serializer(),
    )

    suspend fun updateBoard(id: String, patch: JsonObject): JsonObject =
        patchJson("/api/boards/${encode(id)}", patch, JsonObject.serializer())

    suspend fun deleteBoard(id: String): SimpleResponse =
        deleteJson("/api/boards/${encode(id)}", SimpleResponse.serializer())

    suspend fun activateBoard(id: String): JsonObject =
        postJson("/api/boards/${encode(id)}/activate", JsonObject(emptyMap()), JsonObject.serializer())

    suspend fun deactivateBoard(id: String): JsonObject =
        postJson("/api/boards/${encode(id)}/deactivate", JsonObject(emptyMap()), JsonObject.serializer())

    /** Asks the runtime to break a goal into cards. */
    suspend fun generateCards(id: String, prompt: String? = null): JsonObject = postJson(
        "/api/boards/${encode(id)}/generate",
        body("prompt" to prompt),
        JsonObject.serializer(),
    )

    suspend fun addCard(
        boardId: String,
        task: String,
        priority: String? = null,
        tokenBudget: Int? = null,
    ): JsonObject = postJson(
        "/api/boards/${encode(boardId)}/cards",
        body("task" to task, "priority" to priority, "tokenBudget" to tokenBudget),
        JsonObject.serializer(),
    )

    suspend fun addCardsBulk(boardId: String, tasks: List<String>): JsonObject = postJson(
        "/api/boards/${encode(boardId)}/cards/bulk",
        JsonObject(mapOf("cards" to kotlinx.serialization.json.JsonArray(tasks.map { kotlinx.serialization.json.JsonPrimitive(it) }))),
        JsonObject.serializer(),
    )

    suspend fun updateCard(boardId: String, cardId: String, patch: JsonObject): JsonObject =
        patchJson("/api/boards/${encode(boardId)}/cards/${encode(cardId)}", patch, JsonObject.serializer())

    suspend fun deleteCard(boardId: String, cardId: String): SimpleResponse =
        deleteJson("/api/boards/${encode(boardId)}/cards/${encode(cardId)}", SimpleResponse.serializer())

    suspend fun reorderCards(boardId: String, cardIds: List<String>): JsonObject = postJson(
        "/api/boards/${encode(boardId)}/cards/reorder",
        JsonObject(mapOf("cardIds" to kotlinx.serialization.json.JsonArray(cardIds.map { kotlinx.serialization.json.JsonPrimitive(it) }))),
        JsonObject.serializer(),
    )

    suspend fun clearDoneCards(boardId: String): SimpleResponse =
        postJson("/api/boards/${encode(boardId)}/cards/clear-done", JsonObject(emptyMap()), SimpleResponse.serializer())

    suspend fun runCard(boardId: String, cardId: String): JsonObject =
        postJson("/api/boards/${encode(boardId)}/cards/${encode(cardId)}/run", JsonObject(emptyMap()), JsonObject.serializer())

    suspend fun haltCard(boardId: String, cardId: String): JsonObject =
        postJson("/api/boards/${encode(boardId)}/cards/${encode(cardId)}/halt", JsonObject(emptyMap()), JsonObject.serializer())

    suspend fun cascadeCard(boardId: String, cardId: String): JsonObject =
        postJson("/api/boards/${encode(boardId)}/cards/${encode(cardId)}/cascade", JsonObject(emptyMap()), JsonObject.serializer())

    suspend fun runAllCards(boardId: String): JsonObject =
        postJson("/api/boards/${encode(boardId)}/run-all", JsonObject(emptyMap()), JsonObject.serializer())

    suspend fun haltAllCards(boardId: String): JsonObject =
        postJson("/api/boards/${encode(boardId)}/halt-all", JsonObject(emptyMap()), JsonObject.serializer())

    suspend fun executionPlan(boardId: String): JsonObject =
        getDecoded("/api/boards/${encode(boardId)}/execution-plan", JsonObject.serializer())

    suspend fun smartExecute(boardId: String): JsonObject =
        postJson("/api/boards/${encode(boardId)}/smart-execute", JsonObject(emptyMap()), JsonObject.serializer())

    suspend fun cardComments(boardId: String, cardId: String): JsonObject = getDecoded(
        "/api/boards/${encode(boardId)}/cards/${encode(cardId)}/comments",
        JsonObject.serializer(),
    )

    suspend fun addComment(boardId: String, cardId: String, content: String, authorName: String? = null): JsonObject =
        postJson(
            "/api/boards/${encode(boardId)}/cards/${encode(cardId)}/comments",
            body("content" to content, "authorName" to authorName),
            JsonObject.serializer(),
        )

    suspend fun deleteComment(boardId: String, cardId: String, commentId: String): JsonObject =
        deleteJson(
            "/api/boards/${encode(boardId)}/cards/${encode(cardId)}/comments/${encode(commentId)}",
            JsonObject.serializer(),
        )

    suspend fun cardAttachments(boardId: String, cardId: String): JsonObject = getDecoded(
        "/api/boards/${encode(boardId)}/cards/${encode(cardId)}/attachments",
        JsonObject.serializer(),
    )

    suspend fun addAttachment(
        boardId: String,
        cardId: String,
        name: String,
        path: String,
        type: String? = null,
    ): JsonObject = postJson(
        "/api/boards/${encode(boardId)}/cards/${encode(cardId)}/attachments",
        body("name" to name, "path" to path, "type" to type),
        JsonObject.serializer(),
    )

    suspend fun deleteAttachment(boardId: String, cardId: String, attachmentId: String): JsonObject =
        deleteJson(
            "/api/boards/${encode(boardId)}/cards/${encode(cardId)}/attachments/${encode(attachmentId)}",
            JsonObject.serializer(),
        )

    /**
     * `GET …/attachments/:attachmentId/content` — the daemon reads the file as UTF-8 and
     * wraps it: `{ok,attachment,content,size}`. This is the readable form of a markdown or
     * text deliverable; bytes for a viewer come from [cardAttachmentDownload].
     */
    suspend fun cardAttachmentContent(boardId: String, cardId: String, attachmentId: String): JsonObject =
        getDecoded(
            "/api/boards/${encode(boardId)}/cards/${encode(cardId)}/attachments/${encode(attachmentId)}/content",
            JsonObject.serializer(),
        )

    /**
     * `GET …/attachments/:attachmentId/download` — the raw file, typed and named by the
     * daemon (`Content-Type`, `Content-Disposition: attachment; filename="…"`). Binary, so
     * the bytes never go through the JSON decoder.
     */
    suspend fun cardAttachmentDownload(
        boardId: String,
        cardId: String,
        attachmentId: String,
    ): DownloadedAttachment = withContext(Dispatchers.IO) {
        val path = "/api/boards/${encode(boardId)}/cards/${encode(cardId)}/attachments/" +
            "${encode(attachmentId)}/download"
        val request = Request.Builder().url(url(path)).get().build()
        client.newCall(request).execute().use { response ->
            val body = response.body
            when {
                response.isSuccessful -> DownloadedAttachment(
                    bytes = body?.bytes() ?: ByteArray(0),
                    mimeType = body?.contentType()?.toString()?.substringBefore(';')?.trim()
                        ?.takeIf { it.isNotBlank() }
                        ?: DownloadedAttachment.DEFAULT_MIME,
                    fileName = DownloadedAttachment.fileNameFrom(response.header("Content-Disposition")),
                )

                response.code == 401 || response.code == 403 -> throw AuthException("session expired")
                else -> throw MercuryException(
                    apiError(body?.string().orEmpty()) ?: "HTTP ${response.code}",
                )
            }
        }
    }

    suspend fun addLabel(boardId: String, cardId: String, name: String, color: String): JsonObject = postJson(
        "/api/boards/${encode(boardId)}/cards/${encode(cardId)}/labels",
        body("name" to name, "color" to color),
        JsonObject.serializer(),
    )

    suspend fun removeLabel(boardId: String, cardId: String, labelId: String): JsonObject =
        deleteJson(
            "/api/boards/${encode(boardId)}/cards/${encode(cardId)}/labels/${encode(labelId)}",
            JsonObject.serializer(),
        )

    /** `parentId = null` detaches the card. */
    suspend fun setCardParent(boardId: String, cardId: String, parentId: String?): JsonObject = postJson(
        "/api/boards/${encode(boardId)}/cards/${encode(cardId)}/parent",
        body("parentId" to parentId),
        JsonObject.serializer(),
    )

    suspend fun addDependency(boardId: String, cardId: String, dependsOnCardId: String): JsonObject = postJson(
        "/api/boards/${encode(boardId)}/cards/${encode(cardId)}/dependencies",
        body("dependsOnCardId" to dependsOnCardId),
        JsonObject.serializer(),
    )

    suspend fun removeDependency(boardId: String, cardId: String, dependencyId: String): JsonObject =
        deleteJson(
            "/api/boards/${encode(boardId)}/cards/${encode(cardId)}/dependencies/${encode(dependencyId)}",
            JsonObject.serializer(),
        )

    suspend fun boardContext(boardId: String): JsonObject =
        getDecoded("/api/boards/${encode(boardId)}/context", JsonObject.serializer())

    suspend fun setContextDirectory(boardId: String, directory: String): JsonObject = postJson(
        "/api/boards/${encode(boardId)}/context/directory",
        body("directory" to directory),
        JsonObject.serializer(),
    )

    suspend fun setContextInstructions(boardId: String, instructions: String): JsonObject = postJson(
        "/api/boards/${encode(boardId)}/context/instructions",
        body("instructions" to instructions),
        JsonObject.serializer(),
    )

    suspend fun setContextVariable(boardId: String, key: String, value: String): JsonObject = postJson(
        "/api/boards/${encode(boardId)}/context/variables",
        body("key" to key, "value" to value),
        JsonObject.serializer(),
    )

    suspend fun setContextKnowledge(boardId: String, knowledge: String): JsonObject = postJson(
        "/api/boards/${encode(boardId)}/context/knowledge",
        body("knowledge" to knowledge),
        JsonObject.serializer(),
    )

    suspend fun setContextStructure(boardId: String, structure: JsonObject): JsonObject = postJson(
        "/api/boards/${encode(boardId)}/context/structure",
        JsonObject(mapOf("structure" to structure)),
        JsonObject.serializer(),
    )

    suspend fun boardFeedback(boardId: String): JsonObject =
        getDecoded("/api/boards/${encode(boardId)}/feedback", JsonObject.serializer())

    suspend fun respondFeedback(boardId: String, feedbackId: String, response: String): JsonObject = postJson(
        "/api/boards/${encode(boardId)}/feedback/${encode(feedbackId)}/respond",
        body("response" to response),
        JsonObject.serializer(),
    )

    /**
     * Builds a request body, dropping null fields so Mercury keeps its defaults
     * instead of receiving explicit nulls.
     */
    private fun body(vararg pairs: Pair<String, Any?>): JsonObject = JsonObject(
        pairs.mapNotNull { (key, value) ->
            when (value) {
                null -> null
                is String -> key to kotlinx.serialization.json.JsonPrimitive(value)
                is Int -> key to kotlinx.serialization.json.JsonPrimitive(value)
                is Long -> key to kotlinx.serialization.json.JsonPrimitive(value)
                is Boolean -> key to kotlinx.serialization.json.JsonPrimitive(value)
                is kotlinx.serialization.json.JsonElement -> key to value
                else -> key to kotlinx.serialization.json.JsonPrimitive(value.toString())
            }
        }.toMap(),
    )

    // ---------------------------------------------------------------------
    // Chat surface: models, settings, history, account, budget
    // ---------------------------------------------------------------------

    suspend fun models(): ModelsResponse = getDecoded("/api/chat/models", ModelsResponse.serializer())

    /** Switches the runtime model for subsequent turns. */
    suspend fun switchModel(provider: String, model: String): SimpleResponse =
        postJson("/api/chat/models/switch", SwitchModelRequest(provider, model), SimpleResponse.serializer())

    /** Renames a model in the picker (label) or pins its reasoning level. */
    suspend fun setModelPrefs(
        provider: String,
        model: String,
        label: String? = null,
        reasoning: String? = null,
    ): SimpleResponse =
        postJson(
            "/api/chat/models/prefs",
            ModelPrefsRequest(provider, model, label, reasoning),
            SimpleResponse.serializer(),
        )

    suspend fun settings(): ChatSettings = getDecoded("/api/chat/settings", ChatSettings.serializer())

    /**
     * Partial update: null fields keep their current value. Mercury derives
     * `permissionMode` from `bypassPermissions` — there is no direct setter.
     */
    suspend fun updateSettings(update: ChatSettingsUpdate): ChatSettings =
        putJson("/api/chat/settings", update, io.github.tournierjc.mercury.data.dto.ChatSettingsResponse.serializer())
            .toSettings()

    /** Deletes a thread; Mercury requires the id echoed back as confirmation. */
    suspend fun deleteThread(threadId: String): SimpleResponse =
        postJson(
            "/api/chat/threads/${encode(threadId)}/delete",
            DeleteThreadRequest(threadId),
            SimpleResponse.serializer(),
        )

    suspend fun usage(): UsageDto = getDecoded("/api/usage", UsageDto.serializer())

    /** Server-side logout: invalidates the session cookie instead of dropping it locally. */
    suspend fun logout() {
        runCatching { execute(Request.Builder().url(url("/api/auth/logout")).get().build()) }
        clearSession()
    }

    suspend fun changePassword(currentPassword: String, newPassword: String): SimpleResponse =
        postJson(
            "/api/auth/password",
            PasswordChangeRequest(currentPassword, newPassword),
            SimpleResponse.serializer(),
        )

    suspend fun changeUsername(currentPassword: String, newUsername: String): SimpleResponse =
        postJson(
            "/api/auth/username",
            UsernameChangeRequest(currentPassword, newUsername),
            SimpleResponse.serializer(),
        )

    // ---------------------------------------------------------------------
    // Runtime state
    // ---------------------------------------------------------------------

    suspend fun status(): StatusDto = getDecoded("/api/status", StatusDto.serializer())

    suspend fun healthDetail(): HealthDto = getDecoded("/api/health", HealthDto.serializer())

    /** Restarts the Mercury daemon. The HTTP call is accepted before the restart. */
    suspend fun restart(): SimpleResponse = postJson("/api/restart", JsonObject(emptyMap()), SimpleResponse.serializer())

    /**
     * `POST /api/shutdown` — graceful stop of the daemon: channels are notified, memory is
     * consolidated and the pid file is released. Accepted from the host only (loopback plus
     * the attach token), so a refusal is a 403 whose message belongs in the UI — only a 401
     * means the session is gone.
     */
    suspend fun shutdown(): ShutdownResult = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url("/api/shutdown"))
            .post("{}".toRequestBody(jsonMedia))
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (response.code == 401) throw AuthException("session expired")
            runCatching { json.decodeFromString(ShutdownResult.serializer(), body) }.getOrNull()
                ?: ShutdownResult(ok = false, error = body.take(160).ifBlank { "HTTP ${response.code}" })
        }
    }

    // ---------------------------------------------------------------------
    // Capabilities
    // ---------------------------------------------------------------------

    suspend fun permissions(): PermissionsDto = getDecoded("/api/permissions", PermissionsDto.serializer())

    suspend fun skills(): List<SkillDto> =
        getDecoded("/api/skills", SkillsResponse.serializer()).skills.filter { it.name.isNotBlank() }

    suspend fun setSkillActive(name: String, active: Boolean): SimpleResponse = postJson(
        "/api/skills/${encode(name)}/${if (active) "activate" else "deactivate"}",
        JsonObject(emptyMap()),
        SimpleResponse.serializer(),
    )

    /**
     * `POST /api/skills/install` — installs from a URL. The daemon refuses anything that is
     * not `http(s)://`, so a local folder path cannot travel through this route.
     */
    suspend fun installSkill(url: String): SkillInstallResult =
        postJson("/api/skills/install", SkillInstallRequest(url.trim()), SkillInstallResult.serializer())

    /** `POST /api/skills/install-from-registry` — `id` is `<category>/<slug>`. */
    suspend fun installSkillFromRegistry(id: String, force: Boolean = false): RegistrySkillInstallResult =
        postJson(
            "/api/skills/install-from-registry",
            RegistrySkillInstallRequest(id.trim(), force.takeIf { it }),
            RegistrySkillInstallResult.serializer(),
        )

    /** `DELETE /api/skills/:name` — removes the folder; the daemon answers `{success:true}`. */
    suspend fun deleteSkill(name: String): SimpleResponse =
        deleteJson("/api/skills/${encode(name)}", SimpleResponse.serializer())

    suspend fun plugins(): List<PluginDto> = getDecoded("/api/plugins", PluginsResponse.serializer()).items

    suspend fun pluginHealth(): PluginHealthDto = getDecoded("/api/plugins/health", PluginHealthDto.serializer())

    suspend fun providers(): List<ProviderDto> =
        getDecoded("/api/providers", kotlinx.serialization.builtins.ListSerializer(ProviderDto.serializer()))

    /**
     * `POST /api/providers/:id` — writes a key, a base URL, a model or the enabled flag.
     * Absent fields keep their stored value; the change only takes effect at the next boot,
     * which is why the answer carries `restartRequired`.
     */
    suspend fun saveProvider(id: String, update: ProviderUpdateRequest): ProviderSaveResult =
        postJson("/api/providers/${encode(id)}", update, ProviderSaveResult.serializer())

    /**
     * `POST /api/providers/:id/test` — probes the provider. A missing key or an unreachable
     * endpoint answers 400 `{success:false, error}`, which is an answer to show in place,
     * not an exception to swallow.
     */
    suspend fun testProvider(id: String): ProviderTestResult = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url("/api/providers/${encode(id)}/test"))
            .post("{}".toRequestBody(jsonMedia))
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (response.code == 401 || response.code == 403) throw AuthException("session expired")
            runCatching { json.decodeFromString(ProviderTestResult.serializer(), body) }.getOrNull()
                ?: ProviderTestResult(
                    success = false,
                    error = body.take(160).ifBlank { "HTTP ${response.code}" },
                )
        }
    }

    /** `GET /api/mcp` — configured MCP servers and their live state, read-only. */
    suspend fun mcpServers(): McpServersResponse =
        getDecoded("/api/mcp", McpServersResponse.serializer())

    suspend fun codeStatus(): CodeStatusDto = getDecoded("/api/code/status", CodeStatusDto.serializer())

    /** Programming mode: `off`, `auto`, `plan` or `execute`. */
    suspend fun setCodeMode(state: String): CodeStatusDto = postJson(
        "/api/code/set",
        JsonObject(mapOf("state" to kotlinx.serialization.json.JsonPrimitive(state))),
        CodeStatusDto.serializer(),
    )

    suspend fun toggleCodeMode(): CodeStatusDto =
        postJson("/api/code/toggle", JsonObject(emptyMap()), CodeStatusDto.serializer())

    // ---------------------------------------------------------------------
    // Work: bots, tasks, agents, schedules
    // ---------------------------------------------------------------------

    suspend fun bots(): BotsResponse = getDecoded("/api/bots", BotsResponse.serializer())

    suspend fun startBot(id: String): SimpleResponse =
        postJson("/api/bots/${encode(id)}/start", JsonObject(emptyMap()), SimpleResponse.serializer())

    suspend fun stopBot(id: String): SimpleResponse =
        postJson("/api/bots/${encode(id)}/stop", JsonObject(emptyMap()), SimpleResponse.serializer())

    suspend fun messageBot(id: String, message: String): SimpleResponse = postJson(
        "/api/bots/${encode(id)}/message",
        JsonObject(mapOf("message" to kotlinx.serialization.json.JsonPrimitive(message))),
        SimpleResponse.serializer(),
    )

    suspend fun runBot(id: String, routine: String? = null): SimpleResponse = postJson(
        "/api/bots/${encode(id)}/run",
        if (routine.isNullOrBlank()) JsonObject(emptyMap()) else
            JsonObject(mapOf("routine" to kotlinx.serialization.json.JsonPrimitive(routine))),
        SimpleResponse.serializer(),
    )

    suspend fun botDetail(id: String): BotDetailDto =
        getDecoded("/api/bots/${encode(id)}", BotDetailDto.serializer())

    suspend fun botRuns(id: String): BotRunsPage =
        getDecoded("/api/bots/${encode(id)}/runs", BotRunsPage.serializer())

    /**
     * `GET /api/bots/:id/runs/:runId` — one run's transcript `{record, prompt, output,
     * trace}`. `runId` may be `latest`, which the daemon resolves to the newest file.
     */
    suspend fun botRun(id: String, runId: String): RunTranscriptDto =
        getDecoded("/api/bots/${encode(id)}/runs/${encode(runId)}", RunTranscriptDto.serializer())

    /** `GET /api/sandbox/tools` — what the bots can run, and whether it is signed in. */
    suspend fun sandboxTools(): List<SandboxToolDto> =
        getDecoded("/api/sandbox/tools", SandboxToolsPage.serializer()).tools

    /**
     * `POST /api/sandbox/tools/:id/install` — starts the job and answers with its record.
     *
     * An install is minutes long (a release tarball, a Python venv), so the daemon answers with the
     * job and the screen polls; treating it as one request would time out and lie.
     */
    suspend fun installSandboxTool(id: String): InstallRecordDto = postJson(
        "/api/sandbox/tools/${encode(id)}/install",
        JsonObject(emptyMap()),
        InstallRecordDto.serializer(),
    )

    suspend fun sandboxToolInstall(id: String): InstallRecordDto =
        getDecoded("/api/sandbox/tools/${encode(id)}/install", InstallRecordDto.serializer())

    /** `POST /api/sandbox/tools/:id/connect` — starts the device flow, returns the code to type. */
    suspend fun connectSandboxTool(id: String): ConnectFlowDto = postJson(
        "/api/sandbox/tools/${encode(id)}/connect",
        JsonObject(emptyMap()),
        ConnectFlowDto.serializer(),
    )

    /**
     * `POST /api/sandbox/tools/:id/disconnect` — ends the session.
     *
     * The sandbox is shared, so this revokes the identity for every bot at once; the answer is the
     * tool's status re-read by the daemon, which is what the row then draws.
     */
    suspend fun disconnectSandboxTool(id: String): SandboxToolDto = postJson(
        "/api/sandbox/tools/${encode(id)}/disconnect",
        JsonObject(emptyMap()),
        SandboxToolDto.serializer(),
    )

    suspend fun sandboxToolConnect(id: String): ConnectFlowDto =
        getDecoded("/api/sandbox/tools/${encode(id)}/connect", ConnectFlowDto.serializer())

    /** `GET /api/bots/:id/journal` — the run records, newest last. */
    suspend fun botRunJournal(id: String, limit: Int = 50): List<BotRunDto> = getDecoded(
        "/api/bots/${encode(id)}/journal",
        RunRecordPage.serializer(),
        mapOf("limit" to limit.toString()),
    ).journal

    suspend fun botOutputs(id: String): BotOutputsPage =
        getDecoded("/api/bots/${encode(id)}/outputs", BotOutputsPage.serializer())

    /** `GET /api/bots/outputs` — the fleet inbox: every bot's deliverables, newest first. */
    suspend fun fleetOutputs(): BotOutputsPage =
        getDecoded("/api/bots/outputs", BotOutputsPage.serializer())

    /**
     * `GET /api/bots/:id/outputs/:name/preview` — the first 64 kB of a deliverable as
     * text. Only meaningful for text files: the daemon decodes bytes as UTF-8.
     */
    suspend fun outputPreview(id: String, name: String): OutputPreviewDto = getDecoded(
        "/api/bots/${encode(id)}/outputs/${encode(name)}/preview",
        OutputPreviewDto.serializer(),
    )

    /**
     * `GET /api/bots/:id/outputs/:name/download` — byte-exact file, content type guessed
     * from the extension by the daemon. The phone saves or shares it.
     */
    suspend fun downloadOutput(id: String, name: String): ByteArray = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url("/api/bots/${encode(id)}/outputs/${encode(name)}/download"))
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            when {
                response.code == 401 || response.code == 403 -> throw AuthException("session expired")
                response.code == 404 -> throw MercuryException("404: output not found")
                !response.isSuccessful -> throw MercuryException("HTTP ${response.code}")
                else -> response.body?.bytes() ?: ByteArray(0)
            }
        }
    }

    /** `DELETE /api/bots/:id/outputs/:name` — owner curation: the file leaves the disk. */
    suspend fun deleteOutput(id: String, name: String): SimpleResponse =
        deleteJson("/api/bots/${encode(id)}/outputs/${encode(name)}", SimpleResponse.serializer())

    /** `GET /api/bots/:id/dlq` — dead-lettered jobs with their attempt count. */
    suspend fun botDlq(id: String): DlqPage =
        getDecoded("/api/bots/${encode(id)}/dlq", DlqPage.serializer())

    /**
     * `POST /api/bots/:id/replay/:jobId` — re-queues a dead-lettered job. A 404 body
     * `{accepted:false,reasonCode}` is decoded rather than thrown: the refusal is the
     * answer ("ce job n'existe plus"), and the UI says so.
     */
    suspend fun replayDlq(id: String, jobId: String): DlqReplay = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url("/api/bots/${encode(id)}/replay/${encode(jobId)}"))
            .post("{}".toRequestBody(jsonMedia))
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (response.code == 401 || response.code == 403) throw AuthException("session expired")
            runCatching { json.decodeFromString(DlqReplay.serializer(), body) }.getOrNull()
                ?: DlqReplay(accepted = false, reasonCode = "HTTP ${response.code}")
        }
    }

    /** `DELETE /api/bots-dlq[?bot=id]` — drops dead letters; no `bot` clears the fleet. */
    suspend fun clearDlq(botId: String? = null): SimpleResponse = deleteJson(
        "/api/bots-dlq",
        SimpleResponse.serializer(),
        if (botId.isNullOrBlank()) emptyMap() else mapOf("bot" to botId),
    )

    suspend fun botJournal(id: String, limit: Int = 20): BotJournalPage = getDecoded(
        "/api/bots/${encode(id)}/journal",
        BotJournalPage.serializer(),
        mapOf("limit" to limit.toString()),
    )

    suspend fun botTiers(): List<io.github.tournierjc.mercury.data.dto.BotTierDto> =
        getDecoded("/api/bots/tiers", BotTiersPage.serializer()).tiers

    suspend fun createBot(id: String, name: String, description: String? = null, tier: String? = null): SimpleResponse =
        postJson("/api/bots", CreateBotRequest(id, name, description, tier), SimpleResponse.serializer())

    suspend fun updateBot(id: String, update: UpdateBotRequest): SimpleResponse =
        patchJson("/api/bots/${encode(id)}", update, SimpleResponse.serializer())

    suspend fun deleteBot(id: String): SimpleResponse =
        deleteJson("/api/bots/${encode(id)}", SimpleResponse.serializer())

    suspend fun setBotEnabled(id: String, enabled: Boolean): SimpleResponse = postJson(
        "/api/bots/${encode(id)}/${if (enabled) "enable" else "disable"}",
        JsonObject(emptyMap()),
        SimpleResponse.serializer(),
    )

    suspend fun startAllBots(): SimpleResponse =
        postJson("/api/bots-start-all", JsonObject(emptyMap()), SimpleResponse.serializer())

    suspend fun stopAllBots(): SimpleResponse =
        postJson("/api/bots-stop-all", JsonObject(emptyMap()), SimpleResponse.serializer())

    // ---------------------------------------------------------------------
    // Fleet money, disk and bundles
    // ---------------------------------------------------------------------

    /** `GET /api/bots-cost?days=N` — per-bot and per-fleet token spend (ADR-022). */
    suspend fun botsCost(days: Int = 7): BotsCostReport = getDecoded(
        "/api/bots-cost",
        BotsCostReport.serializer(),
        mapOf("days" to days.coerceIn(1, 90).toString()),
    )

    /** `GET /api/bots-storage` — per-bot folder size and the queue counters. */
    suspend fun botsStorage(): BotsStorageReport =
        getDecoded("/api/bots-storage", BotsStorageReport.serializer())

    /**
     * `GET /api/bots/:id/bundle` — the shareable bundle, verbatim. The daemon serves it as an
     * attachment (`Content-Disposition: <id>-bundle.json`), not as an envelope, so it is
     * returned as text and shared as-is.
     */
    suspend fun botBundle(id: String): String = withContext(Dispatchers.IO) {
        execute(Request.Builder().url(url("/api/bots/${encode(id)}/bundle")).get().build())
    }

    /**
     * `POST /api/bots/import` — the bundle document is the request body itself. Imported bots
     * start disabled and an existing id is skipped unless [overwrite] refreshes it.
     */
    suspend fun importBotBundle(bundleJson: String, overwrite: Boolean = false): BundleImportResponse =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(
                    urlWith("/api/bots/import") {
                        if (overwrite) addQueryParameter("overwrite", "1")
                    },
                )
                .post(bundleJson.toRequestBody(jsonMedia))
                .build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (response.code == 401 || response.code == 403) throw AuthException("session expired")
                runCatching { json.decodeFromString(BundleImportResponse.serializer(), body) }.getOrNull()
                    ?: BundleImportResponse(error = body.take(160).ifBlank { "HTTP ${response.code}" })
            }
        }

    suspend fun backgroundTasks(): BgResponse = getDecoded("/api/bg", BgResponse.serializer())

    suspend fun cancelTask(id: String): SimpleResponse =
        postJson("/api/bg/${encode(id)}/cancel", JsonObject(emptyMap()), SimpleResponse.serializer())

    suspend fun clearFinishedTasks(): SimpleResponse =
        postJson("/api/bg/clear", JsonObject(emptyMap()), SimpleResponse.serializer())

    suspend fun agents(): AgentsResponse = getDecoded("/api/agents", AgentsResponse.serializer())

    suspend fun haltAgents(): SimpleResponse =
        postJson("/api/agents/halt", JsonObject(emptyMap()), SimpleResponse.serializer())

    suspend fun stopAgents(): SimpleResponse =
        postJson("/api/agents/stop", JsonObject(emptyMap()), SimpleResponse.serializer())

    suspend fun schedules(): List<JsonObject> =
        getDecoded("/api/schedules", SchedulesResponse.serializer()).schedules

    /** The whole page, so a caller can read the daemon's `available` witness beside the rows. */
    suspend fun schedulesPage(): SchedulesResponse =
        getDecoded("/api/schedules", SchedulesResponse.serializer())

    suspend fun deleteSchedule(id: String): SimpleResponse =
        executeRaw("DELETE", "/api/schedules/${encode(id)}").let { body ->
            runCatching { json.decodeFromString(SimpleResponse.serializer(), body) }
                .getOrElse { SimpleResponse(success = true) }
        }

    // ---------------------------------------------------------------------
    // Memory and workspace
    // ---------------------------------------------------------------------

    /**
     * A raw GET for the screens that only display what a route says — status, usage, schedules,
     * skills, MCP, plugins, permissions, workspace. No DTO to keep in sync with the server for a
     * value the app shows and never acts on; the screens read the fields they need and degrade to an
     * empty state on anything unexpected.
     */
    suspend fun getJson(path: String): JsonObject = getDecoded(path, JsonObject.serializer())

    suspend fun brainStatus(): BrainStatusDto = getDecoded("/api/brain/status", BrainStatusDto.serializer())

    suspend fun searchMemories(query: String): MemoriesResponse = getDecoded(
        "/api/brain/memory/search",
        MemoriesResponse.serializer(),
        mapOf("q" to query),
    )

    // Second brain: memories, beliefs, persons, graph — browsable and editable.

    suspend fun memories(
        limit: Int = 50,
        offset: Int = 0,
        query: String? = null,
        type: String? = null,
        scope: String = "conscious",
    ): MemoriesPage = getDecoded(
        "/api/brain/memory",
        MemoriesPage.serializer(),
        buildMap {
            put("limit", limit.toString())
            put("offset", offset.toString())
            put("scope", scope)
            if (!query.isNullOrBlank()) put("q", query)
            if (!type.isNullOrBlank()) put("type", type)
        },
    )

    suspend fun memory(id: String): MemoryDto =
        getDecoded("/api/brain/memory/${encode(id)}", MemoryDto.serializer())

    suspend fun addMemory(type: String, summary: String, detail: String? = null): MemoryDto = postJson(
        "/api/brain/memory",
        AddMemoryRequest(type, summary, detail),
        MemoryDto.serializer(),
    )

    suspend fun updateMemory(id: String, update: UpdateMemoryRequest): SimpleResponse = putJson(
        "/api/brain/memory/${encode(id)}",
        update,
        SimpleResponse.serializer(),
    )

    /** Soft delete: Mercury keeps the row but drops it from recall. */
    suspend fun forgetMemory(id: String): SimpleResponse =
        deleteJson("/api/brain/memory/${encode(id)}", SimpleResponse.serializer())

    suspend fun beliefs(status: String? = null, kind: String? = null, limit: Int = 200): BeliefsPage =
        getDecoded(
            "/api/brain/beliefs",
            BeliefsPage.serializer(),
            buildMap {
                put("limit", limit.toString())
                if (!status.isNullOrBlank()) put("status", status)
                if (!kind.isNullOrBlank()) put("kind", kind)
            },
        )

    suspend fun confirmBelief(id: String): SimpleResponse =
        postJson("/api/brain/beliefs/${encode(id)}/confirm", JsonObject(emptyMap()), SimpleResponse.serializer())

    suspend fun correctBelief(id: String, statement: String): SimpleResponse = postJson(
        "/api/brain/beliefs/${encode(id)}/correct",
        JsonObject(mapOf("statement" to kotlinx.serialization.json.JsonPrimitive(statement))),
        SimpleResponse.serializer(),
    )

    suspend fun forgetBelief(id: String): SimpleResponse =
        postJson("/api/brain/beliefs/${encode(id)}/forget", JsonObject(emptyMap()), SimpleResponse.serializer())

    suspend fun persons(query: String = "", limit: Int = 100): PersonsPage = getDecoded(
        "/api/brain/persons",
        PersonsPage.serializer(),
        buildMap {
            put("limit", limit.toString())
            if (query.isNotBlank()) put("q", query)
        },
    )

    suspend fun beliefGraph(): BeliefGraphDto = getDecoded("/api/brain/graph", BeliefGraphDto.serializer())

    /** Memories the brain set aside because they contradicted something stronger. */
    suspend fun quarantine(): MemoriesPage = getDecoded("/api/brain/quarantine", MemoriesPage.serializer())

    suspend fun restoreQuarantined(id: String): SimpleResponse = postJson(
        "/api/brain/quarantine/${encode(id)}/restore",
        JsonObject(emptyMap()),
        SimpleResponse.serializer(),
    )

    /**
     * `GET /api/config` — the instance's identity and the numbers the daemon keeps for itself.
     *
     * Read by Réglages › Instance, which writes four of these keys back through
     * [saveInstanceConfig]; the heartbeat in the same payload is not writable from the app.
     */
    suspend fun instanceConfig(): InstanceConfig = getDecoded("/api/config", InstanceConfig.serializer())

    /**
     * `PUT /api/config` — the route accepts `identity.name`, `identity.owner`, `defaultProvider` and
     * `tokenBudget`, and nothing else. [patch] is built by `InstancePatch`, which sends only the keys
     * the owner actually changed: the daemon rewrites the whole object it holds, so echoing an
     * unchanged field back makes "who changed what" unreadable in the config file's history.
     */
    suspend fun saveInstanceConfig(patch: JsonObject): InstanceConfigSave =
        putJson("/api/config", patch, InstanceConfigSave.serializer())

    /**
     * `PUT /api/chat/settings` with `workspace` alone — the folder the agent reads and writes in.
     *
     * A separate call from [saveInstanceConfig] because the daemon keeps this one in the chat
     * settings, not in the instance config: same screen, two routes.
     */
    suspend fun saveWorkspace(path: String): ChatSettings =
        updateSettings(ChatSettingsUpdate(workspace = path.trim()))

    /** Reads one workspace file; Mercury refuses anything outside the workspace root. */
    suspend fun workspaceFile(path: String): WorkspaceFileDto = getDecoded(
        "/api/workspace/file",
        WorkspaceFileDto.serializer(),
        mapOf("path" to path),
    )

    suspend fun workspaceInfo(): WorkspaceInfoDto = getDecoded("/api/workspace/info", WorkspaceInfoDto.serializer())

    /**
     * `PUT /api/workspace/root` — moves the workspace root. The daemon resolves the path and
     * refuses one that does not exist (404) or is not a directory (400).
     */
    suspend fun setWorkspaceRoot(path: String): WorkspaceRootResult =
        putJson("/api/workspace/root", WorkspaceRootRequest(path.trim()), WorkspaceRootResult.serializer())

    suspend fun workspaceTree(path: String? = null): WorkspaceTreeDto = getDecoded(
        "/api/workspace/tree",
        WorkspaceTreeDto.serializer(),
        if (path.isNullOrBlank()) emptyMap() else mapOf("path" to path),
    )

    /**
     * Live feed of the runtime for one session. Reconnection is the caller's
     * job: the flow completes when the stream ends.
     */
    fun events(sessionId: String?): Flow<ChatEvent> = callbackFlow {
        val url = urlWith("/api/chat/events") {
            if (!sessionId.isNullOrBlank()) addQueryParameter("sessionId", sessionId)
        }
        val request = Request.Builder()
            .url(url)
            .header("Accept", "text/event-stream")
            .get()
            .build()
        val call = client.newCall(request)
        val job = launch(Dispatchers.IO) {
            try {
                call.execute().use { response ->
                    if (!response.isSuccessful) {
                        throw if (response.code == 401) {
                            AuthException("session expired")
                        } else {
                            MercuryException("sse: HTTP ${response.code}")
                        }
                    }
                    val source = response.body?.source()
                    if (source == null) {
                        close()
                        return@use
                    }
                    val parser = SseParser()
                    while (!source.exhausted()) {
                        val line = source.readUtf8Line() ?: break
                        parser.feed(line + "\n").forEach { raw ->
                            trySend(MercuryEventParser.parse(raw, json))
                        }
                    }
                }
                close()
            } catch (t: Throwable) {
                trySend(ChatEvent.Failure(t.message.orEmpty()))
                close()
            }
        }
        awaitClose {
            call.cancel()
            job.cancel()
        }
    }

    private suspend fun <T> getDecoded(
        path: String,
        deserializer: DeserializationStrategy<T>,
        query: Map<String, String> = emptyMap(),
    ): T = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(urlWith(path) { query.forEach { (k, v) -> addQueryParameter(k, v) } })
            .get()
            .build()
        json.decodeFromString(deserializer, execute(request))
    }

    /** POST a JSON body and decode the answer. */
    private suspend inline fun <reified Req, reified Res> postJson(
        path: String,
        body: Req,
        responseSerializer: kotlinx.serialization.KSerializer<Res>,
    ): Res = withContext(Dispatchers.IO) {
        val payload = json.encodeToString(
            kotlinx.serialization.json.JsonElement.serializer(),
            json.encodeToJsonElement(kotlinx.serialization.serializer<Req>(), body),
        )
        val request = Request.Builder()
            .url(url(path))
            .post(payload.toRequestBody(jsonMedia))
            .build()
        json.decodeFromString(responseSerializer, execute(request))
    }

    /** PUT a JSON body and decode the answer. */
    private suspend inline fun <reified Req, reified Res> putJson(
        path: String,
        body: Req,
        responseSerializer: kotlinx.serialization.KSerializer<Res>,
    ): Res = withContext(Dispatchers.IO) {
        val payload = json.encodeToString(
            kotlinx.serialization.json.JsonElement.serializer(),
            json.encodeToJsonElement(kotlinx.serialization.serializer<Req>(), body),
        )
        val request = Request.Builder()
            .url(url(path))
            .put(payload.toRequestBody(jsonMedia))
            .build()
        json.decodeFromString(responseSerializer, execute(request))
    }

    /** PATCH a JSON body and decode the answer. */
    private suspend inline fun <reified Req, reified Res> patchJson(
        path: String,
        body: Req,
        responseSerializer: kotlinx.serialization.KSerializer<Res>,
    ): Res = withContext(Dispatchers.IO) {
        val payload = json.encodeToString(
            kotlinx.serialization.json.JsonElement.serializer(),
            json.encodeToJsonElement(kotlinx.serialization.serializer<Req>(), body),
        )
        val request = Request.Builder().url(url(path)).patch(payload.toRequestBody(jsonMedia)).build()
        json.decodeFromString(responseSerializer, execute(request))
    }

    /** DELETE that decodes its answer (Mercury answers `{success:true}`). */
    private suspend fun <T> deleteJson(
        path: String,
        deserializer: DeserializationStrategy<T>,
        query: Map<String, String> = emptyMap(),
    ): T = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(urlWith(path) { query.forEach { (k, v) -> addQueryParameter(k, v) } })
            .delete()
            .build()
        json.decodeFromString(deserializer, execute(request))
    }

    /**
     * The bot's face, or null when it has none — a 404 here is an answer, not a failure.
     * Callers draw a monogram instead, which is why this never throws for "no avatar".
     */
    suspend fun botAvatar(botId: String): ByteArray? = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url("/api/bots/${encode(botId)}/avatar")).get().build()
        client.newCall(request).execute().use { response ->
            when {
                response.code == 404 -> null
                response.code == 401 || response.code == 403 -> throw AuthException("session expired")
                !response.isSuccessful -> throw MercuryException("HTTP ${response.code}")
                else -> response.body?.bytes()
            }
        }
    }

    /**
     * Replaces the bot's face. `data` is base64: bytes on the wire, never a path, and the
     * server picks the format from the signature — an image it cannot read is refused, not
     * stored under a lying name.
     */
    suspend fun uploadBotAvatar(botId: String, data: String) {
        val payload = json.encodeToString(AvatarRequest.serializer(), AvatarRequest(data))
        execute(
            Request.Builder()
                .url(url("/api/bots/${encode(botId)}/avatar"))
                .put(payload.toRequestBody(jsonMedia))
                .build(),
        )
    }

    /** Removes the face; the monogram comes back on the next roster frame. */
    suspend fun deleteBotAvatar(botId: String) {
        execute(
            Request.Builder()
                .url(url("/api/bots/${encode(botId)}/avatar"))
                .delete()
                .build(),
        )
    }

    private suspend fun executeRaw(method: String, path: String): String = withContext(Dispatchers.IO) {
        val builder = Request.Builder().url(url(path))
        val request = if (method == "DELETE") builder.delete().build() else builder.get().build()
        execute(request)
    }

    private fun encode(segment: String): String =
        java.net.URLEncoder.encode(segment, "UTF-8").replace("+", "%20")

    /**
     * Any blocking call goes here first.
     *
     * `NetworkOnMainThreadException` is what a phone throws when a socket call runs on the UI
     * thread: `viewModelScope` launches on `Dispatchers.Main.immediate`, and a `suspend fun`
     * that never switches dispatcher runs right there. That is exactly how `send` failed —
     * "Envoi impossible — NetworkOnMainThreadException" — while the reads, already wrapped,
     * were fine. One helper, used by every request, makes the mistake unrepresentable.
     */
    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }

    private suspend fun execute(request: Request): String = io {
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                if (response.code == 401 || response.code == 403) throw AuthException("session expired")
                throw MercuryException(apiError(body) ?: "HTTP ${response.code}")
            }
            body
        }
    }

    /** Mercury answers `{error:"…"}`; anything else keeps the status code as the message. */
    private fun apiError(body: String): String? = runCatching {
        json.decodeFromString(ApiError.serializer(), body).message
    }.getOrNull()?.takeIf { it.isNotBlank() }

    private fun url(path: String): HttpUrl =
        urlWith(path) {}

    private fun urlWith(path: String, block: HttpUrl.Builder.() -> Unit): HttpUrl {
        val base = normalizeBaseUrl(baseUrl)
        val builder = urlFor(base, path)
        builder.block()
        return builder.build()
    }

    companion object {
        fun normalizeBaseUrl(raw: String): String {
            val trimmed = raw.trim().trimEnd('/')
            if (trimmed.isEmpty()) return ""
            val withScheme = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                trimmed
            } else {
                "https://$trimmed"
            }
            return withScheme
        }

        private fun urlFor(base: String, path: String): HttpUrl.Builder {
            val url = (base.trimEnd('/') + path).toHttpUrlOrNull()
                ?: throw MercuryException("invalid base url: $base")
            return url.newBuilder()
        }
    }
}
