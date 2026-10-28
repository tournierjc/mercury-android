package io.github.tournierjc.mercury.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Memory, comms and retention: three objects `PATCH /api/bots/:id` replaces wholesale.
 *
 * The phone edits one control at a time, so every write has to carry the object back whole. The
 * tests below are that rule — an edit keeps its neighbours, `false` is a value and not an
 * absence, and an emptied list removes the key instead of sending `[]`.
 */
class BotConfigPatchTest {

    private fun manifest(): JsonObject = JsonObject(
        mapOf(
            BotConfigPatch.MEMORY to JsonObject(
                mapOf(
                    BotConfigPatch.SCOPE to JsonPrimitive(BotConfigPatch.SCOPE_OWN),
                    BotConfigPatch.LEARN to JsonPrimitive(true),
                    BotConfigPatch.ALLOW_CROSS_BOT_RECALL to JsonArray(listOf(JsonPrimitive("scribe"))),
                ),
            ),
            BotConfigPatch.COMMS to JsonObject(
                mapOf(BotConfigPatch.CAN_MESSAGE to JsonArray(listOf(JsonPrimitive("scribe"), JsonPrimitive("veille")))),
            ),
            BotConfigPatch.RETENTION to JsonObject(
                mapOf(
                    BotConfigPatch.TRANSCRIPT_RUNS to JsonPrimitive(200),
                    BotConfigPatch.MAILBOX_TTL_HOURS to JsonPrimitive(72),
                    BotConfigPatch.SANDBOX_JANITOR to JsonObject(
                        mapOf(
                            BotConfigPatch.JANITOR_ENABLED to JsonPrimitive(true),
                            BotConfigPatch.HOT_DAYS to JsonPrimitive(7),
                        ),
                    ),
                ),
            ),
            "autonomy" to JsonObject(mapOf(BotAutonomy.STEPS to JsonPrimitive(54))),
        ),
    )

    /** What the daemon does with a `PATCH`: the objects it is sent replace the ones it holds. */
    private fun apply(manifest: JsonObject, body: JsonObject): JsonObject = JsonObject(manifest + body)

    @Test
    fun `editing the memory keeps comms and the autonomy limits intact`() {
        val current = manifest()

        val written = BotConfigPatch.memory(current, scope = BotConfigPatch.SCOPE_SHARED_READ, learn = true, recall = listOf("scribe"))
        val patched = apply(current, JsonObject(mapOf(BotConfigPatch.MEMORY to written)))

        assertEquals(JsonPrimitive(BotConfigPatch.SCOPE_SHARED_READ), patched.getValue(BotConfigPatch.MEMORY).jsonObject[BotConfigPatch.SCOPE])
        // The two objects the write did not mention are still the manifest's, key for key.
        assertEquals(current[BotConfigPatch.COMMS], patched[BotConfigPatch.COMMS])
        assertEquals(current["autonomy"], patched["autonomy"])
        // And the body carries one object only: that is what keeps the others alive.
        assertEquals(setOf(BotConfigPatch.MEMORY), (JsonObject(mapOf(BotConfigPatch.MEMORY to written))).keys)
    }

    @Test
    fun `learning off is written, not dropped as a falsy value`() {
        val written = BotConfigPatch.memory(manifest(), scope = BotConfigPatch.SCOPE_OWN, learn = false, recall = null)

        assertEquals(JsonPrimitive(false), written[BotConfigPatch.LEARN])
        assertEquals("false", written.getValue(BotConfigPatch.LEARN).toString())
    }

    @Test
    fun `an emptied recall list removes the key`() {
        val written = BotConfigPatch.memory(manifest(), scope = BotConfigPatch.SCOPE_OWN, learn = true, recall = emptyList())

        assertNull(written[BotConfigPatch.ALLOW_CROSS_BOT_RECALL])
        // An empty array would tell the daemon "this list is empty", which is not the same claim.
        assertFalse(written.containsKey(BotConfigPatch.ALLOW_CROSS_BOT_RECALL))
    }

    @Test
    fun `an emptied canMessage list removes the key too`() {
        assertNull(BotConfigPatch.comms(manifest(), emptyList())[BotConfigPatch.CAN_MESSAGE])
        assertEquals(JsonArray(listOf(JsonPrimitive("veille"))), BotConfigPatch.comms(manifest(), listOf("veille"))[BotConfigPatch.CAN_MESSAGE])
    }

    @Test
    fun `a manifest without the object still yields one`() {
        val written = BotConfigPatch.memory(null, scope = BotConfigPatch.SCOPE_OWN, learn = false, recall = listOf("scribe"))

        assertEquals(BotConfigPatch.SCOPE_OWN, (written[BotConfigPatch.SCOPE] as? JsonPrimitive)?.contentOrNull)
        assertEquals(JsonPrimitive(false), written[BotConfigPatch.LEARN])
        assertEquals(3, written.size)
        assertEquals(1, BotConfigPatch.retention(null, mapOf(BotConfigPatch.DLQ_CAP to 20)).size)
        assertEquals(1, BotConfigPatch.comms(null, listOf("scribe")).size)
    }

    @Test
    fun `a retention number the caller did not name keeps its value`() {
        val written = BotConfigPatch.retention(manifest(), mapOf(BotConfigPatch.MAILBOX_TTL_HOURS to 24L))

        assertEquals(JsonPrimitive(200), written[BotConfigPatch.TRANSCRIPT_RUNS])
        assertEquals(JsonPrimitive(24), written[BotConfigPatch.MAILBOX_TTL_HOURS])
    }

    @Test
    fun `clearing a retention number removes it, which is the daemon's default`() {
        val written = BotConfigPatch.retention(manifest(), mapOf(BotConfigPatch.MAILBOX_TTL_HOURS to null))

        assertNull(written[BotConfigPatch.MAILBOX_TTL_HOURS])
        // Zero would read as "keep nothing", so it means the default as well.
        assertNull(BotConfigPatch.retention(manifest(), mapOf(BotConfigPatch.TRANSCRIPT_RUNS to 0L))[BotConfigPatch.TRANSCRIPT_RUNS])
    }

    @Test
    fun `the janitor merges into its own sub-object instead of replacing it`() {
        val written = BotConfigPatch.retention(
            manifest(),
            janitor = BotConfigPatch.Janitor(enabled = true, hotDays = 7, archiveDays = 30),
        )

        val janitor = written.getValue(BotConfigPatch.SANDBOX_JANITOR).jsonObject
        assertEquals(JsonPrimitive(true), janitor[BotConfigPatch.JANITOR_ENABLED])
        assertEquals(JsonPrimitive(7), janitor[BotConfigPatch.HOT_DAYS])
        assertEquals(JsonPrimitive(30), janitor[BotConfigPatch.ARCHIVE_DAYS])
        // The numbers outside the sub-object are untouched.
        assertEquals(JsonPrimitive(200), written[BotConfigPatch.TRANSCRIPT_RUNS])
    }

    @Test
    fun `an empty janitor removes the sub-object rather than sending a hollow one`() {
        val written = BotConfigPatch.retention(manifest(), janitor = BotConfigPatch.Janitor())

        assertNull(written[BotConfigPatch.SANDBOX_JANITOR])
    }

    @Test
    fun `switching the janitor off keeps the durations it still holds`() {
        val written = BotConfigPatch.retention(
            manifest(),
            janitor = BotConfigPatch.Janitor(
                enabled = false,
                hotDays = BotConfigPatch.janitorDays(manifest(), BotConfigPatch.HOT_DAYS),
                archiveDays = BotConfigPatch.janitorDays(manifest(), BotConfigPatch.ARCHIVE_DAYS),
            ),
        )

        val janitor = written.getValue(BotConfigPatch.SANDBOX_JANITOR).jsonObject
        assertEquals(JsonPrimitive(false), janitor[BotConfigPatch.JANITOR_ENABLED])
        assertEquals(JsonPrimitive(7), janitor[BotConfigPatch.HOT_DAYS])
    }

    @Test
    fun `the readers answer a manifest that is missing, malformed or a scalar`() {
        assertEquals(BotConfigPatch.SCOPE_OWN, BotConfigPatch.text(manifest(), BotConfigPatch.MEMORY, BotConfigPatch.SCOPE))
        assertNull(BotConfigPatch.text(null, BotConfigPatch.MEMORY, BotConfigPatch.SCOPE))
        assertNull(BotConfigPatch.text(JsonObject(mapOf(BotConfigPatch.MEMORY to JsonPrimitive("own"))), BotConfigPatch.MEMORY, BotConfigPatch.SCOPE))
        assertEquals(200L, BotConfigPatch.number(manifest(), BotConfigPatch.RETENTION, BotConfigPatch.TRANSCRIPT_RUNS))
        assertEquals(listOf("scribe"), BotConfigPatch.strings(manifest(), BotConfigPatch.MEMORY, BotConfigPatch.ALLOW_CROSS_BOT_RECALL))
        assertEquals(true, BotConfigPatch.janitorEnabled(manifest()))
        assertEquals(7L, BotConfigPatch.janitorDays(manifest(), BotConfigPatch.HOT_DAYS))
        assertNull(BotConfigPatch.janitorDays(JsonObject(mapOf(BotConfigPatch.RETENTION to JsonPrimitive("x"))), BotConfigPatch.HOT_DAYS))
    }

    @Test
    fun `a null delta value removes the key and an unnamed one keeps it`() {
        val written = BotConfigPatch.merge(
            manifest(),
            BotConfigPatch.COMMS,
            mapOf(BotConfigPatch.CAN_MESSAGE to JsonNull),
        )

        assertTrue(written.isEmpty())
        // A key left out of the delta is not a request to delete it.
        assertEquals(1, BotConfigPatch.merge(manifest(), BotConfigPatch.COMMS, emptyMap()).size)
    }
}
