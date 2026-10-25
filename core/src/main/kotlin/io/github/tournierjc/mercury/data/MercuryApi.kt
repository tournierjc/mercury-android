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
import io.github.tournierjc.mercury.data.dto.BotRunsPage
import io.github.tournierjc.mercury.data.dto.BotTiersPage
import io.github.tournierjc.mercury.data.dto.BotsResponse
import io.github.tournierjc.mercury.data.dto.CreateBotRequest
import io.github.tournierjc.mercury.data.dto.UpdateBotRequest
import io.github.tournierjc.mercury.data.dto.ConfigDto
import io.github.tournierjc.mercury.data.dto.BrainStatusDto
import io.github.tournierjc.mercury.data.dto.ChatSettings
import io.github.tournierjc.mercury.data.dto.ChatSettingsUpdate
import io.github.tournierjc.mercury.data.dto.CodeStatusDto
import io.github.tournierjc.mercury.data.dto.DeleteThreadRequest
import io.github.tournierjc.mercury.data.dto.HealthDto
import io.github.tournierjc.mercury.data.dto.MemoriesPage
import io.github.tournierjc.mercury.data.dto.MemoriesResponse
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
import io.github.tournierjc.mercury.data.dto.SchedulesResponse
import io.github.tournierjc.mercury.data.dto.SimpleResponse
import io.github.tournierjc.mercury.data.dto.SkillDto
import io.github.tournierjc.mercury.data.dto.SkillsResponse
import io.github.tournierjc.mercury.data.dto.StatusDto
import io.github.tournierjc.mercury.data.dto.SwitchModelRequest
import io.github.tournierjc.mercury.data.dto.UpdateMemoryRequest
import io.github.tournierjc.mercury.data.dto.UsageDto
import io.github.tournierjc.mercury.data.dto.UsernameChangeRequest
import io.github.tournierjc.mercury.data.dto.WorkspaceFileDto
import io.github.tournierjc.mercury.data.dto.WorkspaceInfoDto
import io.github.tournierjc.mercury.data.dto.WorkspaceTreeDto
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class MercuryException(message: String) : IOException(message)

class AuthException(message: String) : IOException(message)

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
                        throw AuthException("Identifiants refusés par Mercury")

                    else -> throw MercuryException("Échec de connexion : HTTP ${response.code}")
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

    /** Sends a user turn. Returns the session id the turn was bound to. */
    suspend fun send(content: String, sessionId: String?): String? {
        val payload = json.encodeToString(SendRequest.serializer(), SendRequest(content, sessionId))
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
    suspend fun dispatchBot(id: String, message: String, from: String? = null): BotDispatch =
        withContext(Dispatchers.IO) {
            val payload = buildJsonObject {
                put("message", kotlinx.serialization.json.JsonPrimitive(message))
                if (!from.isNullOrBlank()) put("from", kotlinx.serialization.json.JsonPrimitive(from))
            }.toString()
            val request = Request.Builder()
                .url(url("/api/bots/${encode(id)}/message"))
                .post(payload.toRequestBody(jsonMedia))
                .build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (response.code == 401 || response.code == 403) throw AuthException("Session expirée")
                runCatching { json.decodeFromString(BotDispatch.serializer(), body) }.getOrNull()
                    ?: BotDispatch(accepted = false, reasonCode = if (body.isBlank()) "HTTP ${response.code}" else body.take(120))
            }
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
                            AuthException("Session expirée")
                        } else {
                            MercuryException("Flux équipiers indisponible : HTTP ${response.code}")
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

    suspend fun plugins(): List<PluginDto> = getDecoded("/api/plugins", PluginsResponse.serializer()).items

    suspend fun pluginHealth(): PluginHealthDto = getDecoded("/api/plugins/health", PluginHealthDto.serializer())

    suspend fun providers(): List<ProviderDto> =
        getDecoded("/api/providers", kotlinx.serialization.builtins.ListSerializer(ProviderDto.serializer()))

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

    suspend fun botOutputs(id: String): BotOutputsPage =
        getDecoded("/api/bots/${encode(id)}/outputs", BotOutputsPage.serializer())

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

    suspend fun deleteSchedule(id: String): SimpleResponse =
        executeRaw("DELETE", "/api/schedules/${encode(id)}").let { body ->
            runCatching { json.decodeFromString(SimpleResponse.serializer(), body) }
                .getOrElse { SimpleResponse(success = true) }
        }

    // ---------------------------------------------------------------------
    // Memory and workspace
    // ---------------------------------------------------------------------

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

    suspend fun config(): ConfigDto = getDecoded("/api/config", ConfigDto.serializer())

    /** Reads one workspace file; Mercury refuses anything outside the workspace root. */
    suspend fun workspaceFile(path: String): WorkspaceFileDto = getDecoded(
        "/api/workspace/file",
        WorkspaceFileDto.serializer(),
        mapOf("path" to path),
    )

    suspend fun workspaceInfo(): WorkspaceInfoDto = getDecoded("/api/workspace/info", WorkspaceInfoDto.serializer())

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
                            AuthException("Session expirée")
                        } else {
                            MercuryException("Flux SSE indisponible : HTTP ${response.code}")
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
                trySend(ChatEvent.Failure(t.message ?: "Flux interrompu"))
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
    private suspend fun <T> deleteJson(path: String, deserializer: DeserializationStrategy<T>): T =
        withContext(Dispatchers.IO) {
            val request = Request.Builder().url(url(path)).delete().build()
            json.decodeFromString(deserializer, execute(request))
        }

    private suspend fun executeRaw(method: String, path: String): String = withContext(Dispatchers.IO) {
        val builder = Request.Builder().url(url(path))
        val request = if (method == "DELETE") builder.delete().build() else builder.get().build()
        execute(request)
    }

    private fun encode(segment: String): String =
        java.net.URLEncoder.encode(segment, "UTF-8").replace("+", "%20")

    private fun execute(request: Request): String = client.newCall(request).execute().use { response ->
        val body = response.body?.string().orEmpty()
        if (!response.isSuccessful) {
            if (response.code == 401 || response.code == 403) throw AuthException("Session expirée")
            val message = runCatching { json.decodeFromString(ApiError.serializer(), body).message }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
            throw MercuryException(message ?: "HTTP ${response.code}")
        }
        body
    }

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
                ?: throw MercuryException("URL invalide : $base")
            return url.newBuilder()
        }
    }
}
