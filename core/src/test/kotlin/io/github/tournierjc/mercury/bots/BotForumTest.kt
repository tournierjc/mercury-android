package io.github.tournierjc.mercury.bots

import io.github.tournierjc.mercury.data.BotJson
import io.github.tournierjc.mercury.data.dto.BotRunDto
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The sessions forum: every teammate's conversation, newest activity first.
 *
 * The forum reads the same source the conversation screen does — the journal — and the one
 * decision worth pinning is the order: the journal decides what is new, the roster decides who
 * is listed and settles the ties. Re-sorting by name would move a teammate under the owner's
 * thumb, and a bot the journal no longer knows must still be reachable.
 */
class BotForumTest {

    private fun bot(id: String) = buildJsonObject { put("id", id); put("name", id) }

    private fun run(id: String, at: Long) = BotRunDto(runId = id, startedAt = at)

    @Test
    fun `the newest journal line of each bot rides its row`() {
        val rows = BotForum.rows(
            listOf(bot("a"), bot("b")),
            mapOf(
                "a" to listOf(run("a1", 1_000), run("a2", 2_000)),
                "b" to listOf(run("b1", 3_000)),
            ),
        )

        assertEquals("a2", rows.first { it.id == "a" }.newest?.runId)
        assertEquals("b1", rows.first { it.id == "b" }.newest?.runId)
    }

    @Test
    fun `the last talker is drawn first`() {
        val rows = BotForum.rows(
            listOf(bot("quiet"), bot("busy")),
            mapOf(
                "quiet" to listOf(run("q1", 1_000)),
                "busy" to listOf(run("b1", 9_000)),
            ),
        )

        assertEquals(listOf("busy", "quiet"), rows.map { it.id })
    }

    @Test
    fun `the roster's order settles the ties`() {
        // Two bots whose newest runs started at the same millisecond: neither rises above the
        // other, and the roster's order — the owner's own mental list — decides.
        val rows = BotForum.rows(
            listOf(bot("first"), bot("second")),
            mapOf(
                "first" to listOf(run("f1", 5_000)),
                "second" to listOf(run("s1", 5_000)),
            ),
        )

        assertEquals(listOf("first", "second"), rows.map { it.id })
    }

    @Test
    fun `a bot that has never run keeps its place and carries no record`() {
        val rows = BotForum.rows(
            listOf(bot("fresh"), bot("talker")),
            mapOf("talker" to listOf(run("t1", 7_000))),
        )

        val fresh = rows.first { it.id == "fresh" }
        assertNull(fresh.newest)
        assertEquals("talker", rows.first().id)
        // Still listed: opening the conversation is allowed whatever the journal says.
        assertEquals(listOf("talker", "fresh"), rows.map { it.id })
    }

    @Test
    fun `a bot the journal does not know at all stays in the list`() {
        val rows = BotForum.rows(
            listOf(bot("a"), bot("unknown")),
            mapOf("a" to listOf(run("a1", 1_000))),
        )

        assertEquals(setOf("a", "unknown"), rows.map { it.id }.toSet())
        assertNull(rows.first { it.id == "unknown" }.newest)
    }

    @Test
    fun `the roster entry rides the row, for the face and the name`() {
        val bot = buildJsonObject { put("id", "scribe"); put("name", "Scribe") }
        val rows = BotForum.rows(listOf(bot), emptyMap())

        assertEquals(bot, rows.single().bot)
        assertEquals("Scribe", BotJson.name(rows.single().bot))
    }

    @Test
    fun `the ids the forum reads cover are exactly the bots it lists`() {
        assertEquals(
            listOf("a", "b"),
            BotForum.ids(listOf(bot("a"), bot("b"))),
        )
    }
}
