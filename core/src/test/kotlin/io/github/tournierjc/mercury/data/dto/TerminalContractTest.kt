package io.github.tournierjc.mercury.data.dto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import kotlinx.serialization.json.Json
import org.junit.Test

/**
 * Shapes taken from the handlers of `POST /api/terminal/exec` and
 * `GET /api/bots/:id/crew` in mercury 1.4.2.
 */
class TerminalContractTest {

    private val testJson = Json { ignoreUnknownKeys = true; explicitNulls = false }

    @Test
    fun `successful command keeps output and cwd`() {
        val result = testJson.decodeFromString(
            TerminalResult.serializer(),
            """{"success":true,"output":"mercury  Up 2 hours\n","exitCode":0,"cwd":"/work"}""",
        )

        assertTrue(result.success)
        assertEquals("mercury  Up 2 hours\n", result.output)
        assertEquals(0, result.exitCode)
        assertEquals("/work", result.cwd)
        assertEquals("mercury  Up 2 hours\n", result.display)
    }

    @Test
    fun `failed command merges stdout stderr and keeps the exit code`() {
        val result = testJson.decodeFromString(
            TerminalResult.serializer(),
            """{"success":false,"output":"ls: cannot access 'x'\n","exitCode":2,"error":"Command failed: ls x","cwd":"/work"}""",
        )

        assertFalse(result.success)
        assertEquals(2, result.exitCode)
        assertEquals("ls: cannot access 'x'\n", result.display)
        assertEquals("Command failed: ls x", result.error)
    }

    @Test
    fun `missing output falls back to the error then to a placeholder`() {
        val withError = testJson.decodeFromString(
            TerminalResult.serializer(),
            """{"success":false,"exitCode":1,"error":"command is required"}""",
        )
        val empty = testJson.decodeFromString(TerminalResult.serializer(), """{"success":true}""")

        assertEquals("command is required", withError.display)
        assertEquals("(no output)", empty.display)
        assertEquals(0, empty.exitCode)
    }

    @Test
    fun `crew response carries the lead, its members and the cap`() {
        val crew = testJson.decodeFromString(
            BotCrewResponse.serializer(),
            """{"lead":{"id":"lead","name":"Lead","fleetRole":"lead"},"crew":[{"id":"w1","name":"Writer"}],"maxCrew":6}""",
        )

        assertEquals(1, crew.crew.size)
        assertEquals(6, crew.maxCrew)
        assertEquals("lead", (crew.lead?.get("id") as kotlinx.serialization.json.JsonPrimitive).content)
    }

    @Test
    fun `not a lead answers an error and an empty crew`() {
        val crew = testJson.decodeFromString(
            BotCrewResponse.serializer(),
            """{"error":"Not a fleet lead"}""",
        )

        assertEquals("Not a fleet lead", crew.error)
        assertTrue(crew.crew.isEmpty())
        assertEquals(0, crew.maxCrew)
    }
}
