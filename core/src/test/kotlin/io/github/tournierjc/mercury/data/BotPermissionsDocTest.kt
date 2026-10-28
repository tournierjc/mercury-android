package io.github.tournierjc.mercury.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fine permissions, and the rule that keeps an edit from erasing its neighbours.
 *
 * `PUT /api/bots/:id/permissions` replaces the whole document, so a screen that edits one path used
 * to send a document holding only paths — dropping the tier, the command lists and every key a
 * newer daemon added. These are the four ways that bug would show up.
 */
class BotPermissionsDocTest {

    /** A document shaped like `permissions.yaml` after a tier and a couple of hand edits. */
    private fun document(): JsonObject = buildJsonObject {
        put("tier", JsonPrimitive("builder"))
        put(
            "paths",
            JsonArray(
                listOf(
                    buildJsonObject {
                        put("scope", JsonPrimitive("self"))
                        put("read", JsonPrimitive(true))
                        put("write", JsonPrimitive(true))
                    },
                    buildJsonObject {
                        put("scope", JsonPrimitive("agents"))
                        put("read", JsonPrimitive(true))
                    },
                ),
            ),
        )
        put("blockedCommands", JsonArray(listOf(JsonPrimitive("rm -rf"))))
        put(
            "tools",
            buildJsonObject {
                put("deny", JsonArray(listOf(JsonPrimitive("Bash"))))
            },
        )
        // Something this build has never heard of, e.g. a key added by a later daemon.
        put("futureKey", JsonPrimitive("kept"))
    }

    @Test
    fun `the tier survives a path edit`() {
        val merged = BotPermissionsDoc.withPath(document(), BotPermissionsDoc.PathGrant("/tmp", read = true))

        assertEquals(JsonPrimitive("builder"), merged["tier"])
    }

    @Test
    fun `an unknown key survives every edit`() {
        val merged = BotPermissionsDoc.withPath(document(), BotPermissionsDoc.PathGrant("/tmp", read = true))
            .let { BotPermissionsDoc.withoutPath(it, "self") }
            .let { BotPermissionsDoc.withCommands(it, BotPermissionsDoc.BLOCKED_COMMANDS, listOf("git push")) }
            .let { BotPermissionsDoc.withTools(it, BotPermissionsDoc.DENY, listOf("Write")) }

        assertEquals(JsonPrimitive("kept"), merged["futureKey"])
        assertEquals(JsonPrimitive("builder"), merged["tier"])
    }

    @Test
    fun `a path added twice appears once, with the last flags`() {
        val once = BotPermissionsDoc.withPath(document(), BotPermissionsDoc.PathGrant("/tmp", read = true))
        val twice = BotPermissionsDoc.withPath(once, BotPermissionsDoc.PathGrant("/tmp", write = true))

        val tmp = twice.getValue("paths").jsonArray.filter { it.jsonObject["scope"] == JsonPrimitive("/tmp") }
        assertEquals(1, tmp.size)
        assertEquals(JsonPrimitive(false), tmp.single().jsonObject["read"])
        assertEquals(JsonPrimitive(true), tmp.single().jsonObject["write"])
    }

    @Test
    fun `a blank scope is not a path`() {
        val before = document()

        val after = BotPermissionsDoc.withPath(before, BotPermissionsDoc.PathGrant("   ", read = true))

        assertEquals(before.getValue("paths").jsonArray.size, after.getValue("paths").jsonArray.size)
        assertTrue(after.getValue("paths").jsonArray.none { it.jsonObject["scope"]!!.jsonPrimitive.content.isBlank() })
    }

    @Test
    fun `editing a path keeps the other paths, in order`() {
        val merged = BotPermissionsDoc.withPath(
            document(),
            BotPermissionsDoc.PathGrant("agents", read = true, execute = true),
        )

        val scopes = merged.getValue("paths").jsonArray.map { it.jsonObject.getValue("scope").jsonPrimitive.content }
        assertEquals(listOf("self", "agents"), scopes)
        assertEquals(JsonPrimitive(true), merged.getValue("paths").jsonArray[1].jsonObject["execute"])
    }

    @Test
    fun `removing a path keeps the rest and an emptied list stays explicit`() {
        val one = BotPermissionsDoc.withoutPath(document(), "self")
        assertEquals(1, one.getValue("paths").jsonArray.size)

        val none = BotPermissionsDoc.withoutPath(one, "agents")
        assertEquals(0, none.getValue("paths").jsonArray.size)
        // `paths` is the document's backbone: "no path" is a state the owner asked for.
        assertTrue(none.containsKey("paths"))
    }

    @Test
    fun `an emptied command list removes the key`() {
        // A missing list is how the daemon spells "no rule"; an empty array is a key nobody reads.
        val cleared = BotPermissionsDoc.withCommands(document(), BotPermissionsDoc.BLOCKED_COMMANDS, emptyList())

        assertNull(cleared[BotPermissionsDoc.BLOCKED_COMMANDS])
    }

    @Test
    fun `a command list is trimmed, deduplicated and blanks dropped`() {
        val merged = BotPermissionsDoc.withCommands(
            document(),
            BotPermissionsDoc.AUTO_APPROVE_COMMANDS,
            listOf(" npm test ", "npm test", "", "  "),
        )

        assertEquals(
            listOf("npm test"),
            merged.getValue("autoApproveCommands").jsonArray.map { it.jsonPrimitive.content },
        )
    }

    @Test
    fun `editing the deny list keeps the allow list`() {
        val allowed = BotPermissionsDoc.withTools(document(), BotPermissionsDoc.ALLOW, listOf("Read"))
        val merged = BotPermissionsDoc.withTools(allowed, BotPermissionsDoc.DENY, listOf("Bash", "Write"))

        val tools = merged.getValue("tools").jsonObject
        assertEquals(listOf("Read"), tools.getValue("allow").jsonArray.map { it.jsonPrimitive.content })
        assertEquals(listOf("Bash", "Write"), tools.getValue("deny").jsonArray.map { it.jsonPrimitive.content })
    }

    @Test
    fun `tools disappear when both lists are emptied`() {
        val merged = BotPermissionsDoc.withTools(document(), BotPermissionsDoc.DENY, emptyList())

        assertNull(merged["tools"])
    }

    @Test
    fun `reading a document that grants nothing answers empty lists, not a crash`() {
        assertTrue(BotPermissionsDoc.paths(null).isEmpty())
        assertTrue(BotPermissionsDoc.commands(null, BotPermissionsDoc.BLOCKED_COMMANDS).isEmpty())
        assertTrue(BotPermissionsDoc.tools(null, BotPermissionsDoc.DENY).isEmpty())

        // A flag the file omitted means "not granted", never "granted by default".
        val onlyScope = buildJsonObject {
            put("paths", JsonArray(listOf(buildJsonObject { put("scope", JsonPrimitive("*")) })))
        }
        assertEquals(
            BotPermissionsDoc.PathGrant("*", read = false, write = false, execute = false),
            BotPermissionsDoc.paths(onlyScope).single(),
        )
    }
}
