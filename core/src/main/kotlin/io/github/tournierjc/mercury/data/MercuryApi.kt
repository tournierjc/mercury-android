package io.github.tournierjc.mercury.data

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.Json
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

    private suspend fun <T> getDecoded(path: String, deserializer: DeserializationStrategy<T>): T =
        withContext(Dispatchers.IO) {
            val body = execute(Request.Builder().url(url(path)).get().build())
            json.decodeFromString(deserializer, body)
        }

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
