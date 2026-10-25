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
import io.github.tournierjc.mercury.data.dto.BgResponse
import io.github.tournierjc.mercury.data.dto.BotsResponse
import io.github.tournierjc.mercury.data.dto.BrainStatusDto
import io.github.tournierjc.mercury.data.dto.ChatSettings
import io.github.tournierjc.mercury.data.dto.ChatSettingsUpdate
import io.github.tournierjc.mercury.data.dto.CodeStatusDto
import io.github.tournierjc.mercury.data.dto.DeleteThreadRequest
import io.github.tournierjc.mercury.data.dto.HealthDto
import io.github.tournierjc.mercury.data.dto.MemoriesResponse
import io.github.tournierjc.mercury.data.dto.ModelPrefsRequest
import io.github.tournierjc.mercury.data.dto.ModelsResponse
import io.github.tournierjc.mercury.data.dto.PasswordChangeRequest
import io.github.tournierjc.mercury.data.dto.PermissionsDto
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
import io.github.tournierjc.mercury.data.dto.UsageDto
import io.github.tournierjc.mercury.data.dto.UsernameChangeRequest
import io.github.tournierjc.mercury.data.dto.WorkspaceInfoDto
import io.github.tournierjc.mercury.data.dto.WorkspaceTreeDto
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
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
