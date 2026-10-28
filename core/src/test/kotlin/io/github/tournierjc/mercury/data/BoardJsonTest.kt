package io.github.tournierjc.mercury.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Boards have no instance on the reference server, so these fixtures follow the
 * handler shapes: `{boards:[…]}`, a detail that may nest its cards, and an
 * execution plan of batches that can hold ids or objects.
 */
class BoardJsonTest {

    private fun obj(text: String) = Json.parseToJsonElement(text).jsonObject

    @Test
    fun `board list reads the boards array`() {
        val root = obj("""{"boards":[{"id":"b1","name":"Refonte","cardCount":4,"pendingCount":2,"active":true}]}""")
        val boards = BoardJson.boards(root)

        assertEquals(1, boards.size)
        assertEquals("b1", BoardJson.id(boards[0]))
        assertEquals("Refonte", BoardJson.title(boards[0]))
        assertEquals(4, BoardJson.intField(boards[0], "cardCount"))
        assertEquals(true, BoardJson.boolField(boards[0], "active"))
    }

    @Test
    fun `cards are read from the root or from a nested board`() {
        val flat = obj("""{"id":"b1","cards":[{"id":"c1","task":"Écrire le parseur","status":"todo"}]}""")
        val nested = obj("""{"board":{"id":"b1","cards":[{"id":"c2","task":"Autre","status":"doing"}]}}""")

        assertEquals("c1", BoardJson.id(BoardJson.cards(flat).single()))
        assertEquals("c2", BoardJson.id(BoardJson.cards(nested).single()))
    }

    @Test
    fun `missing status falls back to pending and missing title to the id`() {
        val card = obj("""{"id":"c3"}""")
        assertEquals("pending", BoardJson.status(card))
        assertEquals("c3", BoardJson.title(card))
    }

    @Test
    fun `cards group by status in the shared order, keeping server order inside a group`() {
        val cards = listOf(
            obj("""{"id":"a","status":"running"}"""),
            obj("""{"id":"b","status":"pending"}"""),
            obj("""{"id":"c","status":"running"}"""),
        )
        val grouped = BoardJson.byStatus(cards)

        assertEquals(listOf("pending", "running"), grouped.map { it.first })
        assertEquals(1, grouped[0].second.size)
        assertEquals(2, grouped[1].second.size)
        assertEquals("a", BoardJson.id(grouped[1].second[0]))
        assertEquals("c", BoardJson.id(grouped[1].second[1]))
    }

    @Test
    fun `execution plan flattens batches of ids or objects`() {
        val ids = obj("""{"ok":true,"batches":[["c1","c2"],["c3"]]}""")
        val objects = obj("""{"ok":true,"batches":[[{"id":"c1","task":"Un"}]]}""")

        assertEquals(listOf(listOf("c1", "c2"), listOf("c3")), BoardJson.batchLabels(ids))
        assertEquals(listOf(listOf("Un")), BoardJson.batchLabels(objects))
        assertTrue(BoardJson.batchLabels(obj("""{"ok":true}""")).isEmpty())
    }

    @Test
    fun `comments and labels read their arrays`() {
        assertEquals(1, BoardJson.comments(obj("""{"comments":[{"id":"k1","content":"ok"}]}""")).size)
        assertEquals("k1", BoardJson.id(BoardJson.comments(obj("""{"comments":[{"id":"k1"}]}""")).single()))
        assertEquals(1, BoardJson.labels(obj("""{"id":"c1","labels":[{"id":"l1","name":"urgent","color":"#f00"}]}""")).size)
    }

    @Test
    fun `dependencies accept both key spellings`() {
        assertEquals(1, BoardJson.dependencies(obj("""{"dependencies":[{"id":"d1"}]}""")).size)
        assertEquals(1, BoardJson.dependencies(obj("""{"dependsOn":["c9"]}""")).size)
    }

    @Test
    fun `feedback and unknown payloads never throw`() {
        assertEquals(1, BoardJson.feedback(obj("""{"ok":true,"feedback":[{"id":"f1","question":"Choix ?"}]}""")).size)
        assertTrue(BoardJson.boards(obj("""{"error":"Not available"}""")).isEmpty())
        assertNull(BoardJson.intField(null, "cardCount"))
    }

    @Test
    fun `an attachment reads its type, its size and whether it is an image`() {
        val image = obj("""{"id":"att_1","name":"maquette.png","path":"/work/maquette.png","type":"image","size":20480,"addedBy":"agent"}""")
        val text = obj("""{"id":"att_2","name":"rapport.md","path":"/work/rapport.md","type":"markdown","size":1312}""")
        val unknown = obj("""{"id":"att_3"}""")

        assertEquals("image", BoardJson.attachmentType(image))
        assertEquals(20480L, BoardJson.attachmentSize(image))
        assertTrue(BoardJson.attachmentIsImage(image))
        assertEquals("image/png", BoardJson.attachmentMimeType(image))

        assertEquals("markdown", BoardJson.attachmentType(text))
        assertEquals(false, BoardJson.attachmentIsImage(text))
        assertEquals("text/markdown", BoardJson.attachmentMimeType(text))

        assertEquals("", BoardJson.attachmentType(unknown))
        assertNull(BoardJson.attachmentSize(unknown))
        assertEquals("application/octet-stream", BoardJson.attachmentMimeType(unknown))
    }

    @Test
    fun `an image is recognised by its name even when the type is generic`() {
        assertTrue(BoardJson.attachmentIsImage(obj("""{"id":"a","name":"photo.JPG","type":"other"}""")))
        assertEquals("image/jpeg", BoardJson.attachmentMimeType(obj("""{"id":"a","name":"photo.JPG","type":"other"}""")))
        assertEquals(
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            BoardJson.attachmentMimeType(obj("""{"id":"a","name":"points.pptx","type":"presentation"}""")),
        )
    }

    @Test
    fun `attachment content reads the payload of the content route`() {
        val root = obj("""{"ok":true,"attachment":{"id":"att_1"},"content":"# Titre\ncorps","size":13}""")
        assertEquals("# Titre\ncorps", BoardJson.attachmentContent(root))
        assertEquals("", BoardJson.attachmentContent(obj("""{"ok":false,"error":"File not found on disk"}""")))
    }

    @Test
    fun `only a text deliverable is read through the content route`() {
        assertTrue(BoardJson.attachmentIsText(obj("""{"id":"a","name":"rapport.md","type":"markdown"}""")))
        assertTrue(BoardJson.attachmentIsText(obj("""{"id":"a","name":"notes.txt"}""")))
        assertEquals(false, BoardJson.attachmentIsText(obj("""{"id":"a","name":"deck.pptx","type":"presentation"}""")))
        assertEquals(false, BoardJson.attachmentIsText(obj("""{"id":"a","name":"scan.pdf","type":"document"}""")))
        // An image is bytes, never text — even when the name looks like one.
        assertEquals(false, BoardJson.attachmentIsText(obj("""{"id":"a","name":"plan.png","type":"image"}""")))
    }

    @Test
    fun `a feedback request hands its question over`() {
        assertEquals(
            "Quel format ?",
            BoardJson.feedbackQuestion(obj("""{"id":"fb_1","cardId":"c1","question":"Quel format ?"}""")),
        )
        assertEquals("", BoardJson.feedbackQuestion(obj("""{"id":"fb_2"}""")))
    }

    @Test
    fun `every known status sorts in the shared order, failed last`() {
        val cards = listOf(
            obj("""{"id":"f","status":"failed"}"""),
            obj("""{"id":"d","status":"done"}"""),
            obj("""{"id":"q","status":"question"}"""),
            obj("""{"id":"r","status":"running"}"""),
            obj("""{"id":"p","status":"paused"}"""),
            obj("""{"id":"t","status":"pending"}"""),
        )

        assertEquals(
            listOf("pending", "paused", "question", "running", "done", "failed"),
            BoardJson.byStatus(cards).map { it.first },
        )
    }

    @Test
    fun `an unknown status is never folded and comes after the six`() {
        val cards = listOf(
            obj("""{"id":"x","status":"archived"}"""),
            obj("""{"id":"t","status":"pending"}"""),
            obj("""{"id":"y","status":"archived"}"""),
        )
        val grouped = BoardJson.byStatus(cards)

        assertEquals(listOf("pending", "archived"), grouped.map { it.first })
        assertEquals(listOf("x", "y"), grouped[1].second.map { BoardJson.id(it) })
    }

    @Test
    fun `an empty status is not rendered`() {
        val cards = listOf(obj("""{"id":"t","status":"pending"}"""))

        assertEquals(listOf("pending"), BoardJson.byStatus(cards).map { it.first })
    }

    @Test
    fun `availability is read, and absent means unknown rather than false`() {
        assertEquals(false, BoardJson.available(obj("""{"boards":[],"available":false}""")))
        assertEquals(true, BoardJson.available(obj("""{"boards":[],"available":true}""")))
        assertNull(BoardJson.available(obj("""{"boards":[]}""")))
    }
}
