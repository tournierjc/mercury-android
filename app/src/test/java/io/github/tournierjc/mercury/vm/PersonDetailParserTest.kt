package io.github.tournierjc.mercury.vm

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The person detail the row unfolds into.
 *
 * The brain routes are loose JSON: `traits` may be strings or objects, the summary may arrive as
 * `summary` or `detail`, and a memory's sentence as `summary`, `content` or `statement`. Each of
 * those is what the daemon has actually sent at some point, so each is pinned here.
 */
class PersonDetailParserTest {

    private fun obj(text: String): JsonObject = Json.parseToJsonElement(text) as JsonObject

    @Test
    fun `the page shape is read`() {
        val card = obj(
            """{"person":{"id":"p1","name":"Camille","relationship":"sister","summary":"Lives in Lyon.","traits":["patient","cooks"]}}""",
        )
        val memories = obj(
            """{"memories":[{"id":"m1","type":"preference","summary":"Prefers terse replies.","createdAt":"2026-10-27T09:12:00.000Z"},{"id":"m2","type":"episode","summary":"Met at the market.","createdAt":"2026-10-26T18:00:00.000Z"}],"total":2}""",
        )

        val detail = PersonDetailParser.parse(card, memories)

        assertEquals("Lives in Lyon.", detail.summary)
        assertEquals(listOf("patient", "cooks"), detail.traits)
        assertEquals(2, detail.total)
        assertEquals("preference", detail.memories.first().type)
        assertEquals("Prefers terse replies.", detail.memories.first().summary)
        assertEquals("2026-10-27T09:12:00.000Z", detail.memories.first().createdAt)
    }

    @Test
    fun `traits as objects and a summary under another name are read`() {
        val card = obj("""{"person":{"detail":"Travels a lot.","traits":[{"label":"warm"},{"name":"blunt"},{"text":" "}]}}""")
        val detail = PersonDetailParser.parse(card, obj("""{"memories":[]}"""))

        assertEquals("Travels a lot.", detail.summary)
        assertEquals(listOf("warm", "blunt"), detail.traits)
        assertEquals(0, detail.total)
        assertTrue(detail.memories.isEmpty())
    }

    @Test
    fun `a memory carrying content instead of summary is not dropped`() {
        val memories = obj("""{"memories":[{"type":"episode","content":"Walked the dog together."}]}""")
        val detail = PersonDetailParser.parse(obj("""{"person":{}}"""), memories)

        assertEquals("Walked the dog together.", detail.memories.single().summary)
        // No total in the payload: the count is what came back, never a 0 next to a list of one.
        assertEquals(1, detail.total)
    }

    @Test
    fun `a person route answering without a wrapper still parses`() {
        val detail = PersonDetailParser.parse(
            obj("""{"summary":"Met once.","traits":[]}"""),
            obj("""{"memories":[{"summary":"a"},{"summary":"b"}],"total":9}"""),
        )

        assertEquals("Met once.", detail.summary)
        assertEquals(9, detail.total)
        assertEquals(2, detail.memories.size)
    }
}
