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
    fun `missing status falls back to todo and missing title to the id`() {
        val card = obj("""{"id":"c3"}""")
        assertEquals("todo", BoardJson.status(card))
        assertEquals("c3", BoardJson.title(card))
    }

    @Test
    fun `cards group by status keeping server order`() {
        val cards = listOf(
            obj("""{"id":"a","status":"doing"}"""),
            obj("""{"id":"b","status":"todo"}"""),
            obj("""{"id":"c","status":"doing"}"""),
        )
        val grouped = BoardJson.byStatus(cards)

        assertEquals(listOf("doing", "todo"), grouped.map { it.first })
        assertEquals(2, grouped[0].second.size)
        assertEquals("a", BoardJson.id(grouped[0].second[0]))
        assertEquals("c", BoardJson.id(grouped[0].second[1]))
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
}
