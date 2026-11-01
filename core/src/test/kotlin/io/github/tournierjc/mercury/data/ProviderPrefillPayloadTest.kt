package io.github.tournierjc.mercury.data

import io.github.tournierjc.mercury.data.dto.ProviderDto
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The edit sheet opens on what the daemon already holds.
 *
 * The sheet drew its fields from this DTO: while `/api/providers` answered, the endpoint and the
 * model were there and the dialog ignored them, so a configured provider opened blank. The payload
 * below is the daemon's own answer (trimmed to what matters) — if one of these fields ever stops
 * coming back, the sheet goes blank again and only this test says so.
 */
class ProviderPrefillPayloadTest {

    private val payload = """
        [
          {"name":"openaiCompat","kind":"endpoint","apiKey":"••••••","hasKey":true,
           "baseUrl":"http://127.0.0.1:8083/v1","model":"qwen3.8-flash-next","enabled":true,
           "configured":true,"isDefault":false},
          {"name":"openrouter","kind":"gateway","apiKey":"","hasKey":false,
           "baseUrl":"https://openrouter.ai/api/v1","model":"","enabled":false,
           "configured":false,"isDefault":false,
           "routing":{"order":["inference-net"],"quantizations":["fp8"]}}
        ]
    """.trimIndent()

    private val providers = Json { ignoreUnknownKeys = true }.decodeFromString<List<ProviderDto>>(payload)

    @Test
    fun `a configured endpoint keeps its saved url and model`() {
        val compat = providers.first { it.name == "openaiCompat" }

        assertEquals("http://127.0.0.1:8083/v1", compat.baseUrl)
        assertEquals("qwen3.8-flash-next", compat.model)
        assertTrue(compat.enabled)
        assertTrue(compat.hasKey)
    }

    @Test
    fun `the key never comes back — only its mask`() {
        // The DTO carries no key field at all: the sheet leaves it empty on purpose, and the daemon
        // reads an empty key as "keep the saved one". A field here would mean the mask, decoded into
        // something a careless caller could send back as a real key.
        assertTrue(ProviderDto::class.java.declaredFields.none { it.name == "apiKey" })
    }

    @Test
    fun `an untouched provider keeps its defaults and its routing`() {
        val gateway = providers.first { it.name == "openrouter" }

        assertEquals("https://openrouter.ai/api/v1", gateway.baseUrl)
        assertEquals("", gateway.model)
        assertEquals(false, gateway.enabled)
        assertEquals(listOf("inference-net"), gateway.routing?.order)
        assertEquals(listOf("fp8"), gateway.routing?.quantizations)
    }
}
