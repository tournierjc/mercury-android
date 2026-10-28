package io.github.tournierjc.mercury.data

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * A bot's turn limits, and the rule that keeps an edit from erasing its neighbours.
 *
 * `PATCH /api/bots/:id` replaces the whole `autonomy` object: the phone sent one key at a time, so
 * setting a daily budget used to drop the step budget and the turn's wall clock back to the daemon's
 * defaults. That is a silent change of behaviour on a screen about numbers.
 */
class BotAutonomyTest {

    private fun manifest(vararg limits: Pair<String, Int>) = JsonObject(
        mapOf(
            "autonomy" to JsonObject(limits.associate { (key, value) -> key to JsonPrimitive(value) }),
            "model" to JsonPrimitive("x"),
        ),
    )

    @Test
    fun `an edit keeps the limits it did not touch`() {
        val current = manifest(BotAutonomy.STEPS to 54, BotAutonomy.TURN_MINUTES to 30)

        val merged = BotAutonomy.merge(current, mapOf(BotAutonomy.DAILY_TOKENS to 500_000))

        assertEquals(JsonPrimitive(54), merged[BotAutonomy.STEPS])
        assertEquals(JsonPrimitive(30), merged[BotAutonomy.TURN_MINUTES])
        assertEquals(JsonPrimitive(500_000), merged[BotAutonomy.DAILY_TOKENS])
    }

    @Test
    fun `an empty value removes the key, which is how a bot returns to the default`() {
        val current = manifest(BotAutonomy.STEPS to 54, BotAutonomy.TURN_MINUTES to 30)

        val merged = BotAutonomy.merge(current, mapOf(BotAutonomy.STEPS to null))

        assertNull(merged[BotAutonomy.STEPS])
        assertEquals(JsonPrimitive(30), merged[BotAutonomy.TURN_MINUTES])
    }

    @Test
    fun `zero and negative mean the default too`() {
        // A cap of zero would stop every turn before it started; nobody means that.
        val merged = BotAutonomy.merge(
            manifest(BotAutonomy.STEPS to 54),
            mapOf(BotAutonomy.TURN_MINUTES to 0, BotAutonomy.TURN_TOKENS to -1),
        )

        assertNull(merged[BotAutonomy.TURN_MINUTES])
        assertNull(merged[BotAutonomy.TURN_TOKENS])
        assertEquals(JsonPrimitive(54), merged[BotAutonomy.STEPS])
    }

    @Test
    fun `a manifest without autonomy still yields an object`() {
        val merged = BotAutonomy.merge(null, mapOf(BotAutonomy.STEPS to 40))

        assertEquals(JsonPrimitive(40), merged[BotAutonomy.STEPS])
        assertEquals(1, merged.size)
    }

    @Test
    fun `reading a limit that is not set gives the default's absence, not a zero`() {
        assertNull(BotAutonomy.value(manifest(BotAutonomy.STEPS to 54), BotAutonomy.TURN_MINUTES))
        assertEquals(54, BotAutonomy.value(manifest(BotAutonomy.STEPS to 54), BotAutonomy.STEPS))
        assertNull(BotAutonomy.value(null, BotAutonomy.STEPS))
        // A manifest whose autonomy is not an object (an older one, a hand edit) reads as unset.
        assertNull(BotAutonomy.value(JsonObject(mapOf("autonomy" to JsonPrimitive("none"))), BotAutonomy.STEPS))
    }
}
