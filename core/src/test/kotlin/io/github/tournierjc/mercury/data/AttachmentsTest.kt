package io.github.tournierjc.mercury.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The attachment contract, as the merged route reads it
 * (`src/web/api/uploads.ts` on Mercury's main): base64 in `data`, the format
 * decided by bytes, 4 files, 8 MB per image, 15 MB per PDF.
 */
class AttachmentsTest {

    private val json = Json { encodeDefaults = false }

    private fun png(size: Int = 8) = ByteArray(size) { 0x89.toByte() }.also {
        // 8-byte PNG signature, then filler
        byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a).copyInto(it)
    }

    private fun pdf(size: Int = 16) = "%PDF-1.4\n...".toByteArray().let { head ->
        ByteArray(size).also { head.copyInto(it) }
    }

    @Test
    fun `base64 round-trips the bytes`() {
        val bytes = byteArrayOf(0, 1, 2, 127, -1, -128)
        val decoded = java.util.Base64.getDecoder().decode(Attachments.encode(bytes))
        assertTrue(bytes.contentEquals(decoded))
    }

    @Test
    fun `a PDF is recognised by its signature, not its name`() {
        assertTrue(PendingFile("scan", pdf()).isPdf())
        assertFalse(PendingFile("vacances.pdf", png()).isPdf())
    }

    @Test
    fun `a batch of photos passes`() {
        assertNull(Attachments.problemWith(listOf(PendingFile("a.jpg", png()), PendingFile("b.jpg", png()))))
        assertNull(Attachments.problemWith(emptyList()))
    }

    @Test
    fun `a fifth file is refused as too many`() {
        val files = (1..5).map { PendingFile("p$it.jpg", png()) }
        assertEquals(AttachmentProblem.TooMany, Attachments.problemWith(files))
    }

    @Test
    fun `an oversized image and an oversized PDF are refused separately`() {
        val heavyImage = PendingFile("gros.jpg", png(Attachments.MAX_IMAGE_BYTES + 1))
        assertEquals(AttachmentProblem.TooLarge("gros.jpg", 8), Attachments.problemWith(listOf(heavyImage)))

        val heavyPdf = PendingFile("gros.pdf", pdf(Attachments.MAX_DOCUMENT_BYTES + 1))
        assertEquals(AttachmentProblem.TooLarge("gros.pdf", 15), Attachments.problemWith(listOf(heavyPdf)))

        // A PDF at 9 MB is fine — the image cap must not apply to it.
        assertNull(Attachments.problemWith(listOf(PendingFile("these.pdf", pdf(9 * 1024 * 1024)))))
    }

    @Test
    fun `a file becomes the wire shape the route expects`() {
        val upload = PendingFile("photo.jpg", png()).toUpload()
        assertEquals("photo.jpg", upload.name)
        assertNull(upload.mime) // the server sniffed it; claiming one would be noise
        assertTrue(upload.data.isNotEmpty())
        assertFalse(upload.data.startsWith("data:"))
    }

    @Test
    fun `a text-only send keeps the request byte-identical to before`() {
        val payload = json.encodeToString(
            SendRequest.serializer(),
            SendRequest("bonjour", "sess-1", null),
        )
        assertEquals("""{"content":"bonjour","sessionId":"sess-1"}""", payload)
    }

    @Test
    fun `attachments ride as an array of name and data`() {
        val payload = json.encodeToString(
            SendRequest.serializer(),
            SendRequest(
                content = "",
                sessionId = null,
                attachments = listOf(PendingFile("photo.jpg", png()).toUpload()),
            ),
        )
        val node = Json.parseToJsonElement(payload).jsonObject
        assertFalse(node.containsKey("sessionId"))
        val first = node["attachments"]!!.jsonArray[0].jsonObject
        assertEquals("photo.jpg", first["name"]!!.jsonPrimitive.content)
        assertTrue(first["data"]!!.jsonPrimitive.content.isNotEmpty())
        assertFalse(first.containsKey("mime"))
    }

    @Test
    fun `a caption-less photo is legal`() {
        val payload = json.encodeToString(
            SendRequest.serializer(),
            SendRequest("", null, listOf(PendingFile("photo.jpg", png()).toUpload())),
        )
        assertTrue(payload.contains("\"content\":\"\""))
    }
}
