package io.github.tournierjc.mercury.data

import java.util.Base64

/**
 * Files that travel with a turn.
 *
 * Mercury decides the format from the bytes, so the client's only job is to send
 * something the server can read and to refuse locally what the server would
 * refuse anyway — with a message the user can act on.
 *
 * Limits mirror the server (`src/web/api/uploads.ts`): 4 files, 8 MB per image,
 * 15 MB per PDF. A photo is compressed before it gets here, so the caps are a
 * guard against a surprise, not a routine rejection.
 */
object Attachments {

    const val MAX_FILES = 4
    const val MAX_IMAGE_BYTES = 8 * 1024 * 1024
    const val MAX_DOCUMENT_BYTES = 15 * 1024 * 1024

    /** Base64 without a `data:` prefix — what the route expects in `attachments[].data`. */
    fun encode(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    /**
     * Null when the batch can be sent, otherwise the reason, in French, ready for the UI.
     * Documents are told apart by a PDF header rather than by name, like the server does.
     */
    fun problemWith(files: List<PendingFile>): String? {
        if (files.isEmpty()) return null
        if (files.size > MAX_FILES) return "4 pièces jointes au maximum"
        for (file in files) {
            val limit = if (file.isPdf()) MAX_DOCUMENT_BYTES else MAX_IMAGE_BYTES
            if (file.bytes.size > limit) {
                return "${file.name} dépasse ${limit / (1024 * 1024)} Mo"
            }
        }
        return null
    }
}

/** A file chosen on the device, before it becomes an [AttachmentUpload]. */
data class PendingFile(val name: String, val bytes: ByteArray) {

    /** PDF by signature: the extension a picker reports is not evidence. */
    fun isPdf(): Boolean =
        bytes.size >= 5 && bytes.copyOfRange(0, 5).toString(Charsets.ISO_8859_1) == "%PDF-"

    fun toUpload(): AttachmentUpload = AttachmentUpload(name = name, data = Attachments.encode(bytes))

    override fun equals(other: Any?): Boolean =
        this === other || (other is PendingFile && name == other.name && bytes.contentEquals(other.bytes))

    override fun hashCode(): Int = 31 * name.hashCode() + bytes.contentHashCode()
}
