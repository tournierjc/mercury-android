package io.github.tournierjc.mercury.data.dto

import io.github.tournierjc.mercury.data.BotJson
import io.github.tournierjc.mercury.data.FleetRow
import io.github.tournierjc.mercury.data.fleetRows
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What a manual refresh, and the answer to a rename, actually put on screen.
 *
 * The drawer and the header read the roster; the bug was that a rename reached the bot's own page
 * and never the roster, so these tests pin the two steps that were silently missing: the body
 * carries the new name, and the queue counters survive the trip between the two shapes.
 */
class RosterRefreshTest {

    private val json = Json { ignoreUnknownKeys = true }

    private val body = """
        {"available":true,
         "queue":{"pending":2,"claimed":1,"dlq":0},
         "bots":[
           {"id":"mac-lead","name":"Cockpit","fleetRole":"lead"},
           {"id":"mac-qa","name":"Gardien","fleetRole":"crew","parent":"mac-lead"},
           {"id":"mac-web","name":"Œil renommé","fleetRole":"crew","parent":"mac-lead"}
         ]}
    """.trimIndent()

    @Test
    fun `a refreshed roster carries the new name, and the drawer reads it`() {
        val page = json.decodeFromString(BotsResponse.serializer(), body)

        val web = page.bots.first { BotJson.id(it) == "mac-web" }
        assertEquals("Œil renommé", BotJson.name(web))

        val lead = fleetRows(page.bots).filterIsInstance<FleetRow.Lead>().single()
        assertEquals(listOf("mac-qa", "mac-web"), lead.crewIds)
    }

    @Test
    fun `the queue counters keep their meaning across the two shapes`() {
        val page = json.decodeFromString(BotsResponse.serializer(), body)

        val snapshot = page.queue.asSnapshot()

        assertEquals(2, snapshot.pending)
        assertEquals(1, snapshot.claimed)
        assertEquals(0, snapshot.dlq)
    }
}
