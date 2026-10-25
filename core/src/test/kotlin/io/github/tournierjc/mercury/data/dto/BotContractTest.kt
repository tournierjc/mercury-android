package io.github.tournierjc.mercury.data.dto

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Bots are empty on the reference instance, so these payloads follow the handler
 * source rather than a capture: the envelopes are what matters, and they must stay
 * tolerant because the manifest itself is untyped.
 */
class BotContractTest {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    @Test
    fun `tiers decode the permission presets`() {
        val payload = """
            {"tiers":[{"id":"standard","label":"Standard","description":"Read + write inside the workspace","deny":["terminal","git-push"]},{"id":"locked","label":"Verrouillé","description":"Lecture seule","deny":[]}]}
        """.trimIndent()

        val page = json.decodeFromString(BotTiersPage.serializer(), payload)

        assertEquals(2, page.tiers.size)
        assertEquals("standard", page.tiers[0].id)
        assertEquals(listOf("terminal", "git-push"), page.tiers[0].deny)
        assertTrue(page.tiers[1].deny.isEmpty())
    }

    @Test
    fun `bot detail keeps the manifest and the journal head`() {
        val payload = """
            {"bot":{"id":"veille","name":"Veille","enabled":true,"tier":"standard","model":"deepseek-flash","schedule":"0 8 * * *"},"state":{"status":"idle","lastRunAt":1792946256716},"journal":[{"at":1792946256716,"kind":"run","text":"Résumé envoyé"}]}
        """.trimIndent()

        val detail = json.decodeFromString(BotDetailDto.serializer(), payload)

        assertNotNull(detail.bot)
        assertEquals("Veille", detail.bot!!["name"]!!.jsonPrimitive.content)
        assertEquals("idle", detail.state!!["status"]!!.jsonPrimitive.content)
        assertEquals(1, detail.journal.size)
    }

    @Test
    fun `missing bot surfaces the error without a manifest`() {
        val detail = json.decodeFromString(BotDetailDto.serializer(), """{"error":"Bot not found"}""")
        assertEquals(null, detail.bot)
        assertEquals("Bot not found", detail.error)
    }

    @Test
    fun `runs and outputs pages tolerate an empty list`() {
        assertEquals(0, json.decodeFromString(BotRunsPage.serializer(), """{"runs":[]}""").runs.size)
        assertEquals(0, json.decodeFromString(BotOutputsPage.serializer(), """{"outputs":[]}""").outputs.size)
        assertEquals(0, json.decodeFromString(BotJournalPage.serializer(), """{"journal":[]}""").journal.size)
    }

    @Test
    fun `create request serialises only the required fields`() {
        val payload = json.encodeToString(
            CreateBotRequest.serializer(),
            CreateBotRequest(id = "veille", name = "Veille"),
        )
        assertEquals("""{"id":"veille","name":"Veille"}""", payload)
    }

    @Test
    fun `patch request omits fields it does not set`() {
        val payload = json.encodeToString(
            UpdateBotRequest.serializer(),
            UpdateBotRequest(enabled = false),
        )
        assertEquals("""{"enabled":false}""", payload)
    }
}
