package io.github.tournierjc.mercury.data

import io.github.tournierjc.mercury.data.dto.BotOutputsPage
import io.github.tournierjc.mercury.data.dto.DeliverableDto
import io.github.tournierjc.mercury.data.dto.RunSummaryDto
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The daemon sends `mtimeMs` as a float (`stat().mtimeMs` is `1793106466172.9038`), the client
 * used to declare a `Long`, and the decode threw — behind a `runCatching`, so the screen simply
 * showed "no deliverables" over a bot that had one. These cases pin both shapes down.
 */
class MillisTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    @Test
    fun `a fractional mtime is read, not rejected`() {
        // Verbatim answer of `GET /api/bots/outputs` on a bot that had just written a file.
        val payload = """
            {"outputs":[{"botId":"bricoleur","name":"2026-10-27 Rapport.md","bytes":202,
             "mtimeMs":1793106466172.9038,
             "path":"/home/node/Mercury/Bricoleur/2026-10-27 Rapport.md","final":true}]}
        """.trimIndent()

        val page = json.decodeFromString(BotOutputsPage.serializer(), payload)

        assertEquals(1, page.outputs.size)
        assertEquals(1793106466172L, page.outputs[0].mtimeMs)
        assertEquals("2026-10-27 Rapport.md", page.outputs[0].name)
    }

    @Test
    fun `an integral mtime is read too`() {
        val page = json.decodeFromString(
            BotOutputsPage.serializer(),
            """{"outputs":[{"botId":"a","name":"x.md","mtimeMs":1793106466172}]}""",
        )
        assertEquals(1793106466172L, page.outputs[0].mtimeMs)
    }

    @Test
    fun `runs read a fractional mtime as well`() {
        val run = json.decodeFromString(
            RunSummaryDto.serializer(),
            """{"runId":"6be1a480","mtimeMs":1793106525382.5}""",
        )
        assertEquals("6be1a480", run.runId)
        assertEquals(1793106525382L, run.mtimeMs)
    }

    @Test
    fun `a missing or unreadable timestamp is zero, never an exception`() {
        val page = json.decodeFromString(
            BotOutputsPage.serializer(),
            """{"outputs":[{"botId":"a","name":"x.md"},{"botId":"b","name":"y.md","mtimeMs":"n/a"}]}""",
        )
        assertEquals(0L, page.outputs[0].mtimeMs)
        assertEquals(0L, page.outputs[1].mtimeMs)
        assertTrue(page.outputs.all { it.name.isNotBlank() })
    }

    @Test
    fun `the whole page decodes when one row is odd`() {
        val payload = """
            {"outputs":[
              {"botId":"a","name":"ok.md","bytes":12,"mtimeMs":1793106466172.9,"final":true},
              {"botId":"b","name":"draft.md","bytes":3,"mtimeMs":1793106466173,"final":false}
            ]}
        """.trimIndent()
        val page = json.decodeFromString(BotOutputsPage.serializer(), payload)
        assertEquals(2, page.outputs.size)
        assertEquals(listOf(true, false), page.outputs.map(DeliverableDto::final))
    }
}
