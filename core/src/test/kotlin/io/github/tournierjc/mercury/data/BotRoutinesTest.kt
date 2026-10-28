package io.github.tournierjc.mercury.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The routine list, and the rule that keeps one edit from deleting its neighbours.
 *
 * `PATCH /api/bots/:id` replaces `schedules` wholesale: the phone either sends the whole rebuilt
 * array or it silently removes every routine it did not name. These cases pin that rule down where
 * no daemon is needed to see it.
 */
class BotRoutinesTest {

    private fun stored(name: String, cron: String = "0 3 * * *") = JsonObject(
        mapOf(
            "name" to JsonPrimitive(name),
            "cron" to JsonPrimitive(cron),
            "prompt" to JsonPrimitive("resume la nuit"),
            "deliver" to JsonPrimitive("bot-chat"),
            "expects" to JsonPrimitive("work"),
        ),
    )

    private fun routine(name: String, cron: String = "0 7 * * *", prompt: String = "résume mes courriels") =
        BotRoutines.Routine(name = name, cron = cron, prompt = prompt)

    @Test
    fun `an added routine keeps the ones it did not name`() {
        val current = listOf(stored("veille"), stored("rapport"))

        val updated = BotRoutines.upsert(current, routine("matin"))

        assertEquals(listOf("veille", "rapport", "matin"), updated.map { BotRoutines.name(it) })
        assertEquals(3, BotRoutines.serialize(updated).size)
    }

    @Test
    fun `a name already taken replaces in place instead of duplicating`() {
        val current = listOf(stored("veille"), stored("rapport"), stored("matin"))

        // Case is not identity: « Rapport » is the same routine as « rapport ».
        val updated = BotRoutines.upsert(current, routine("Rapport", cron = "30 8 * * 1-5"))

        assertEquals(3, updated.size)
        assertEquals("Rapport", BotRoutines.name(updated[1]))
        assertEquals("30 8 * * 1-5", BotRoutines.read(updated[1]).cron)
        // The neighbours are untouched, and the replaced one kept its position.
        assertEquals("veille", BotRoutines.name(updated[0]))
        assertEquals("matin", BotRoutines.name(updated[2]))
    }

    @Test
    fun `an invalid cron is refused and leaves the list alone`() {
        val current = listOf(stored("veille"))

        val updated = BotRoutines.upsert(current, routine("matin", cron = "0 7 * *"))

        assertEquals(current, updated)
        assertFalse(BotRoutines.isValid(routine("matin", cron = "tous les matins")))
        assertTrue(BotRoutines.isValid(routine("matin", cron = "*/15 8-18 * * 1,2,3-5")))
    }

    @Test
    fun `a cron reads five fields of digits and schedule characters only`() {
        assertTrue(BotRoutines.isValidCron("0 3 * * *"))
        assertTrue(BotRoutines.isValidCron("  */15 8-18 1,15 *  1-5 "))
        assertTrue(BotRoutines.isValidCron("59 23 31 12 0"))

        assertFalse(BotRoutines.isValidCron(""))
        assertFalse(BotRoutines.isValidCron("0 3 * *"))
        assertFalse(BotRoutines.isValidCron("0 3 * * * *"))
        assertFalse(BotRoutines.isValidCron("0 3 * * MON"))
        assertFalse(BotRoutines.isValidCron("every 3 hours"))
    }

    @Test
    fun `a manifest without schedules gives an empty list`() {
        assertTrue(BotRoutines.list(null).isEmpty())
        assertTrue(BotRoutines.list(JsonObject(mapOf("name" to JsonPrimitive("veille")))).isEmpty())
        // A hand-edited manifest whose schedules is not an array reads as empty, not as a crash.
        assertTrue(BotRoutines.list(JsonObject(mapOf("schedules" to JsonPrimitive("none")))).isEmpty())
    }

    @Test
    fun `reading a manifest gives its routines back as typed entries`() {
        val manifest = JsonObject(
            mapOf(
                "schedules" to JsonArray(
                    listOf(
                        JsonObject(
                            mapOf(
                                "name" to JsonPrimitive("matin"),
                                "cron" to JsonPrimitive("0 7 * * *"),
                                "prompt" to JsonPrimitive("résume mes courriels"),
                                "deliver" to JsonPrimitive("telegram"),
                                "gateScript" to JsonPrimitive("./gate.sh"),
                                "expects" to JsonPrimitive("check"),
                                "minIntervalMinutes" to JsonPrimitive(90),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val read = BotRoutines.read(BotRoutines.list(manifest).single())

        assertEquals("matin", read.name)
        assertEquals("0 7 * * *", read.cron)
        assertEquals("telegram", read.deliver)
        assertEquals("check", read.expects)
        assertEquals(90, read.minIntervalMinutes)
        assertEquals("./gate.sh", read.gateScript)
    }

    @Test
    fun `a written routine carries the daemon's keys and leaves the optional ones out`() {
        val plain = BotRoutines.upsert(emptyList(), routine("matin")).single()

        assertEquals(setOf("name", "cron", "prompt", "deliver", "expects"), plain.keys)
        assertEquals("bot-chat", plain["deliver"]!!.jsonPrimitive.content)
        assertEquals("work", plain["expects"]!!.jsonPrimitive.content)
        assertNull(plain["gateScript"])
        assertNull(plain["minIntervalMinutes"])

        val full = BotRoutines.upsert(
            emptyList(),
            BotRoutines.Routine(
                name = "matin",
                cron = "0 7 * * *",
                prompt = "résume mes courriels",
                deliver = "none",
                expects = "check",
                minIntervalMinutes = 30,
                gateScript = "./gate.sh",
            ),
        ).single()

        assertEquals("./gate.sh", full["gateScript"]!!.jsonPrimitive.content)
        assertEquals(30, full["minIntervalMinutes"]!!.jsonPrimitive.intOrNull)
        assertEquals("none", full["deliver"]!!.jsonPrimitive.content)
        // The daemon's own `expects` is a word, never a boolean: the app must not shorten it.
        assertNull(full["expects"]!!.jsonPrimitive.booleanOrNull)
    }

    @Test
    fun `deleting by name drops only that routine`() {
        val current = listOf(stored("veille"), stored("rapport"), stored("matin"))

        val updated = BotRoutines.remove(current, "RAPPORT")

        assertEquals(listOf("veille", "matin"), updated.map { BotRoutines.name(it) })
        // Deleting a name that is not there is a no-op, not an error.
        assertEquals(updated, BotRoutines.remove(updated, "inconnue"))
    }
}
