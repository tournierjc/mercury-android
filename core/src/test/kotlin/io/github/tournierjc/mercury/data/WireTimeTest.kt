package io.github.tournierjc.mercury.data

import io.github.tournierjc.mercury.data.dto.RunTranscriptDto
import java.time.OffsetDateTime
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The three shapes of a transcript's `timestamp` are three rules (SPECS/115 §3.4), so the decoder
 * must not collapse them: these pin the presence bit, not just the millis. The JSON configuration
 * is the production one — `coerceInputValues` and `explicitNulls = false` are what used to make a
 * written `null` look like an absent key.
 */
class WireTimeTest {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }

    private fun decode(field: String): RunTranscriptDto = json.decodeFromString(
        RunTranscriptDto.serializer(),
        """{"record":{"runId":"3f2a9b1c"},"prompt":"q","output":"r"$field}""",
    )

    @Test
    fun `a written date is the exchange's hour`() {
        val dto = decode(""","timestamp":1761855120000""")

        assertEquals(WireTime.At(1_761_855_120_000L), dto.timestamp)
    }

    @Test
    fun `an absent key keeps the turn's clock`() {
        assertEquals(WireTime.Absent, decode("").timestamp)
    }

    @Test
    fun `a written null draws no hour`() {
        assertEquals(WireTime.Unreadable, decode(""","timestamp":null""").timestamp)
    }

    @Test
    fun `an unreadable value is neither a date nor an absence`() {
        assertEquals(WireTime.Unreadable, decode(""","timestamp":"hier""").timestamp)
        assertEquals(WireTime.Unreadable, decode(""","timestamp":0""").timestamp)
    }

    @Test
    fun `the ISO shape the old endpoint sent is still read`() {
        val expected = OffsetDateTime.parse("2026-10-30T21:07:20Z").toInstant().toEpochMilli()

        assertEquals(WireTime.At(expected), decode(""","timestamp":"2026-10-30T21:07:20Z""").timestamp)
    }
}
