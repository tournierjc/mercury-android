package io.github.tournierjc.mercury.data.dto

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The daemon's `available` witness on the two routes whose zeros a screen used to read as facts.
 *
 * Three states matter and must stay distinct: `true` (these numbers are the server's), `false` (the
 * server could not read its own state — the numbers are not facts), and **absent** (a daemon older
 * than the witness, which knows nothing about it: the client must not treat that as a refusal).
 */
class WitnessContractTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `usage carries the witness it was given`() {
        val refused = json.decodeFromString(
            UsageDto.serializer(),
            """{"available":false,"dailyUsed":0,"dailyBudget":0,"lastResetDate":"2026-10-28","remaining":0}""",
        )
        assertEquals(false, refused.available)

        val read = json.decodeFromString(
            UsageDto.serializer(),
            """{"available":true,"dailyUsed":117327062,"dailyBudget":80000000,"lastResetDate":"2026-10-28","remaining":0}""",
        )
        assertEquals(true, read.available)
        assertEquals(80000000L, read.dailyBudget)
    }

    @Test
    fun `a daemon without the witness is neither available nor refused`() {
        val old = json.decodeFromString(
            UsageDto.serializer(),
            """{"dailyUsed":10,"dailyBudget":20,"lastResetDate":"2026-10-28","remaining":10}""",
        )
        assertNull(old.available)
        assertEquals(20L, old.dailyBudget)
    }

    @Test
    fun `the schedules page carries the witness too`() {
        val refused = json.decodeFromString(
            SchedulesResponse.serializer(),
            """{"available":false,"schedules":[],"total":0}""",
        )
        assertEquals(false, refused.available)
        assertEquals(0, refused.total)

        val old = json.decodeFromString(SchedulesResponse.serializer(), """{"schedules":[],"total":0}""")
        assertNull(old.available)
    }
}
