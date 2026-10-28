package io.github.tournierjc.mercury.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The roster tree: leads own their crew, everyone else stands alone, and the order the server sent
 * is the order the owner reads. Rendering sits in BotsScreen; the shape is decided here.
 */
class BotFleetTest {

    private fun bot(id: String, role: String = "crew", parent: String = "") =
        Json.parseToJsonElement(
            """{"id":"$id","fleetRole":"$role","parent":"$parent"}""",
        ).let { it as kotlinx.serialization.json.JsonObject }

    @Test
    fun `a lead carries its crew, in server order`() {
        val rows = fleetRows(
            listOf(
                bot("mac-lead", role = "lead"),
                bot("mac-web", parent = "mac-lead"),
                bot("mac-qa", parent = "mac-lead"),
            ),
        )

        assertEquals(
            listOf(FleetRow.Lead("mac-lead", listOf("mac-web", "mac-qa"))),
            rows,
        )
    }

    @Test
    fun `a solo bot stays a row of its own`() {
        val rows = fleetRows(listOf(bot("scout", parent = ""), bot("mac-lead", role = "lead")))

        assertEquals(listOf(FleetRow.Solo("scout"), FleetRow.Lead("mac-lead", emptyList())), rows)
    }

    @Test
    fun `a crew whose lead is not in the list is not hidden behind it`() {
        val rows = fleetRows(listOf(bot("mac-web", parent = "absent-lead")))

        assertEquals(listOf(FleetRow.Solo("mac-web")), rows)
    }

    @Test
    fun `a lead with no crew is still a lead`() {
        val rows = fleetRows(listOf(bot("mac-lead", role = "lead")))

        assertEquals(listOf(FleetRow.Lead("mac-lead", emptyList())), rows)
    }

    @Test
    fun `the top level keeps the server order, crew or not`() {
        val rows = fleetRows(
            listOf(
                bot("scout"),
                bot("mac-lead", role = "lead"),
                bot("mac-web", parent = "mac-lead"),
                bot("watchdog"),
            ),
        )

        assertEquals(
            listOf(FleetRow.Solo("scout"), FleetRow.Lead("mac-lead", listOf("mac-web")), FleetRow.Solo("watchdog")),
            rows,
        )
    }
}
