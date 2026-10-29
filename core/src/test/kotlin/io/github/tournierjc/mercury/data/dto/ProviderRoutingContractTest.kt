package io.github.tournierjc.mercury.data.dto

import io.github.tournierjc.mercury.data.MercuryEventParser
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * OpenRouter's endpoint pin, on the wire.
 *
 * The daemon reads `routing` only when the key is present: `{order:[]}` clears a pin, and an
 * absent key leaves it alone. That distinction only survives if the client omits the field
 * rather than sending null, so it is pinned here rather than left to the encoder's defaults.
 */
class ProviderRoutingContractTest {

    private val json: Json = MercuryEventParser.defaultJson

    private fun bodyOf(request: ProviderUpdateRequest): JsonObject =
        json.encodeToJsonElement(ProviderUpdateRequest.serializer(), request).jsonObject

    @Test
    fun `a pin is sent as order and quantizations`() {
        val body = bodyOf(
            ProviderUpdateRequest(
                routing = ProviderRoutingDto(
                    order = listOf("inference-net"),
                    quantizations = listOf("fp8"),
                ),
            ),
        )

        val routing = body["routing"]!!.jsonObject
        assertEquals("""["inference-net"]""", routing["order"].toString())
        assertEquals("""["fp8"]""", routing["quantizations"].toString())
        // Null, not false: the daemon chooses the default, and sending false would pin
        // allow_fallbacks off for a user who never asked.
        assertFalse(routing.containsKey("allowFallbacks"))
    }

    @Test
    fun `without a pin the field is absent, not null`() {
        val body = bodyOf(ProviderUpdateRequest(model = "deepseek/deepseek-v4.1-flash-1017"))

        assertFalse(body.containsKey("routing"))
    }

    @Test
    fun `an emptied pin is sent as an empty object so the daemon can clear it`() {
        val body = bodyOf(ProviderUpdateRequest(routing = ProviderRoutingDto()))
        val routing = body["routing"]!!.jsonObject

        // `{}` — the daemon deletes the pin; the lists are left out by encodeDefaults=false.
        assertTrue(routing.isEmpty())
    }

    @Test
    fun `an explicit allowFallbacks travels`() {
        val body = bodyOf(ProviderUpdateRequest(routing = ProviderRoutingDto(allowFallbacks = true)))
        val routing = body["routing"]!!.jsonObject

        assertEquals("true", routing["allowFallbacks"].toString())
    }

    @Test
    fun `the listed provider carries the pin back`() {
        val provider = json.decodeFromString(
            ProviderDto.serializer(),
            """
            {"name":"openrouter","hasKey":true,"baseUrl":"https://openrouter.ai/api/v1",
             "model":"deepseek/deepseek-v4.1-flash-1017","enabled":true,
             "routing":{"order":["inference-net"],"quantizations":["fp8"]}}
            """.trimIndent(),
        )

        assertEquals(listOf("inference-net"), provider.routing?.order)
        assertEquals(listOf("fp8"), provider.routing?.quantizations)
    }

    @Test
    fun `a provider that pins nothing reads back as null`() {
        val provider = json.decodeFromString(
            ProviderDto.serializer(),
            """{"name":"deepseek","hasKey":true,"model":"deepseek-flash","enabled":true}""",
        )

        assertNull(provider.routing)
    }
}
