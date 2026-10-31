package io.github.tournierjc.mercury.data

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * L'ordre du roster : rangé une fois, tenu à travers les mises à jour.
 *
 * Ce qui compte, ce sont les cas limites — un bot nouveau, un bot supprimé, un appareil neuf — parce
 * qu'un tri qui les ignore déplace des rangées que le propriétaire avait réglées.
 */
class BotOrderTest {

    private fun bot(id: String, parent: String? = null, lead: Boolean = false): JsonObject =
        buildJsonObject {
            put("id", JsonPrimitive(id))
            if (parent != null) put("parent", JsonPrimitive(parent))
            if (lead) put("fleetRole", JsonPrimitive("lead"))
        }

    @Test
    fun `the saved order wins over the roster order`() {
        val bots = listOf(bot("a"), bot("b"), bot("c"))
        val ordered = BotOrder.apply(bots, listOf("c", "a", "b"))
        assertEquals(listOf("c", "a", "b"), ordered.map { BotJson.id(it) })
    }

    @Test
    fun `a bot the saved order does not know goes last, not first`() {
        val bots = listOf(bot("new"), bot("a"), bot("b"))
        val ordered = BotOrder.apply(bots, listOf("b", "a"))
        assertEquals(listOf("b", "a", "new"), ordered.map { BotJson.id(it) })
    }

    @Test
    fun `an identifier the roster no longer has is ignored`() {
        val bots = listOf(bot("a"), bot("b"))
        val ordered = BotOrder.apply(bots, listOf("gone", "b", "a"))
        assertEquals(listOf("b", "a"), ordered.map { BotJson.id(it) })
    }

    @Test
    fun `no saved order means the roster order`() {
        val bots = listOf(bot("a"), bot("b"))
        assertEquals(listOf("a", "b"), BotOrder.apply(bots, emptyList()).map { BotJson.id(it) })
    }

    @Test
    fun `flatten reads the rows the way the eye sees them`() {
        val rows = listOf(
            FleetRow.Lead("lead1", listOf("m1", "m2")),
            FleetRow.Solo("alone"),
            FleetRow.Lead("lead2", listOf("m3")),
        )
        assertEquals(listOf("lead1", "m1", "m2", "alone", "lead2", "m3"), BotOrder.flatten(rows))
    }

    @Test
    fun `a saved flatten order reproduces the same rows`() {
        val bots = listOf(
            bot("lead1", lead = true),
            bot("m1", parent = "lead1"),
            bot("m2", parent = "lead1"),
            bot("alone"),
        )
        val rows = fleetRows(bots)
        val ids = BotOrder.flatten(rows)
        val again = fleetRows(BotOrder.apply(bots, ids))
        assertEquals(BotOrder.flatten(rows), BotOrder.flatten(again))
    }

    @Test
    fun `moving a row up or down stays inside the list`() {
        val ids = listOf("a", "b", "c")
        assertEquals(listOf("b", "a", "c"), BotOrder.move(ids, 1, 0))
        assertEquals(listOf("a", "c", "b"), BotOrder.move(ids, 1, 2))
        assertEquals(ids, BotOrder.move(ids, 0, -1))
        assertEquals(ids, BotOrder.move(ids, 2, 3))
        assertEquals(ids, BotOrder.move(ids, 7, 0))
    }
}
