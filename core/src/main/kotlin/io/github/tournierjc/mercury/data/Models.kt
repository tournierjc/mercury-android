package io.github.tournierjc.mercury.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ThreadDto(
    val id: String = "",
    val shortId: String = "",
    val alias: String = "",
    val title: String = "",
    val updatedAt: String = "",
)

@Serializable
data class ThreadsResponse(val threads: List<ThreadDto> = emptyList())

/**
 * One stored message of a thread, as `GET /api/chat/threads/:id` serves it.
 *
 * `timestamp` is the message's own date, epoch milliseconds per SPECS/115 §5 bis — read through
 * [TimestampSerializer], which also accepts the ISO string `chat.ts` `toThread` still sends. It
 * is optional: a message the daemon did not date gets no hour under its bubble.
 */
@Serializable
data class MessageDto(
    val role: String = "assistant",
    val kind: String? = null,
    val content: String = "",
    @Serializable(with = TimestampSerializer::class)
    val timestamp: Long? = null,
)

@Serializable
data class ThreadMessagesResponse(val messages: List<MessageDto> = emptyList())

/**
 * One file on the wire: base64 in `data`, no `data:` prefix. `mime` is a hint —
 * the server reads the signature and refuses anything that is not png, jpeg,
 * webp, gif or PDF.
 */
@Serializable
data class AttachmentUpload(
    val name: String,
    val mime: String? = null,
    val data: String,
)

/**
 * A user turn. `attachments` stays absent for a text-only message, so the request
 * is byte-identical to what earlier versions sent.
 */
@Serializable
data class SendRequest(
    val content: String,
    val sessionId: String? = null,
    val attachments: List<AttachmentUpload>? = null,
)

@Serializable
data class SendResponse(val sessionId: String? = null, val error: String? = null)

@Serializable
data class HealthResponse(val pid: Int? = null, val version: String = "", val booting: Boolean = false)

@Serializable
data class PermissionActionRequest(val action: String)

/** Avatar bytes on the wire: base64 in `data`, format decided by the server from the signature. */
@Serializable
data class AvatarRequest(val data: String)

@Serializable
data class ApiError(@SerialName("error") val message: String = "")

/**
 * Bytes read from `GET /api/boards/:boardId/cards/:cardId/attachments/:id/download`.
 *
 * The daemon owns the type and the name of the file it serves (`Content-Type` and
 * `Content-Disposition: attachment; filename="…"`), so the client repeats them instead of
 * guessing from the extension — a deliverable is often a `.md` or a `.pptx`, and a viewer
 * opened with the wrong type shows nothing. [fileName] is blank when the server sent no
 * name: the caller falls back to the attachment's own name.
 */
data class DownloadedAttachment(
    val bytes: ByteArray,
    val mimeType: String,
    val fileName: String,
) {
    companion object {
        const val DEFAULT_MIME = "application/octet-stream"

        private val EXTENDED_NAME = Regex("""filename\*\s*=\s*[^']*'[^']*'\s*([^;\s]+)""", RegexOption.IGNORE_CASE)
        private val PLAIN_NAME = Regex("""filename\s*=\s*(?:"([^"]*)"|([^;]*))""", RegexOption.IGNORE_CASE)

        /** `attachment; filename="rapport.md"` → `rapport.md`, RFC 5987 `filename*` first. */
        fun fileNameFrom(contentDisposition: String?, fallback: String = ""): String {
            if (contentDisposition.isNullOrBlank()) return fallback
            EXTENDED_NAME.find(contentDisposition)?.groupValues?.get(1)?.let { encoded ->
                val decoded = runCatching { java.net.URLDecoder.decode(encoded, "UTF-8") }.getOrNull()
                if (!decoded.isNullOrBlank()) return safeName(decoded)
            }
            PLAIN_NAME.find(contentDisposition)?.let { match ->
                val name = match.groupValues[1].ifBlank { match.groupValues[2] }
                if (name.isNotBlank()) return safeName(name)
            }
            return fallback
        }

        /** A header is server data: never let it name a path. */
        private fun safeName(raw: String): String =
            raw.trim().substringAfterLast('/').substringAfterLast('\\')
    }
}
