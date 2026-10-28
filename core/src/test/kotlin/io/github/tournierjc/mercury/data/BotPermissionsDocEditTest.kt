package io.github.tournierjc.mercury.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two edits the sheet's text fields need: renaming a folder without losing what the row
 * granted, and turning what the owner typed into a list.
 *
 * Both look like formatting until they are not — a rename that drops the flags silently removes
 * write access, and a comma-separated paste that stays one entry means the command never matches.
 */
class BotPermissionsDocEditTest {

    private fun document(): JsonObject = JsonObject(
        linkedMapOf(
            "tier" to JsonPrimitive("builder"),
            "paths" to JsonArray(
                listOf(
                    JsonObject(
                        linkedMapOf(
                            "scope" to JsonPrimitive("/data"),
                            "read" to JsonPrimitive(true),
                            "write" to JsonPrimitive(true),
                            "note" to JsonPrimitive("kept"),
                        ),
                    ),
                    JsonObject(
                        linkedMapOf(
                            "scope" to JsonPrimitive("/tmp"),
                            "read" to JsonPrimitive(true),
                        ),
                    ),
                ),
            ),
            "blockedCommands" to JsonArray(listOf(JsonPrimitive("rm -rf"))),
        ),
    )

    private fun scopes(doc: JsonObject): List<String> =
        doc.getValue("paths").jsonArray.map { it.jsonObject.getValue("scope").jsonPrimitive.content }

    @Test
    fun `a rename keeps the flags, the position and the keys beside them`() {
        val renamed = BotPermissionsDoc.renamePath(document(), "/data", "/srv/data")

        assertEquals(listOf("/srv/data", "/tmp"), scopes(renamed))
        val entry = renamed.getValue("paths").jsonArray.first().jsonObject
        assertEquals(JsonPrimitive(true), entry["write"])
        assertEquals(JsonPrimitive(true), entry["read"])
        // A key this build does not know belongs to the entry, not to the old folder.
        assertEquals(JsonPrimitive("kept"), entry["note"])
        assertEquals(JsonPrimitive("builder"), renamed["tier"])
    }

    @Test
    fun `renaming onto an existing folder leaves one row, not two`() {
        val renamed = BotPermissionsDoc.renamePath(document(), "/tmp", "/data")

        assertEquals(listOf("/data"), scopes(renamed))
        // The folder that already existed keeps its own flags and its own extra keys: folding the
        // rows must not spread the renamed entry's grants onto a folder nobody touched.
        val entry = renamed.getValue("paths").jsonArray.single().jsonObject
        assertEquals(JsonPrimitive(true), entry["write"])
        assertEquals(JsonPrimitive("kept"), entry["note"])
    }

    @Test
    fun `a blank or unchanged name writes the document back untouched`() {
        val before = document()

        assertEquals(before, BotPermissionsDoc.renamePath(before, "/data", "   "))
        assertEquals(before, BotPermissionsDoc.renamePath(before, "/data", "/data"))
    }

    @Test
    fun `a typed list splits on commas and newlines, trimmed and deduplicated`() {
        val parsed = BotPermissionsDoc.splitList(" npm test , git push\n\nnpm test\n  ")

        assertEquals(listOf("npm test", "git push"), parsed)
    }

    @Test
    fun `an empty field is an empty list, and a blank source renames nothing`() {
        assertTrue(BotPermissionsDoc.splitList("   \n , ").isEmpty())
        assertEquals(document(), BotPermissionsDoc.renamePath(document(), "", "/x"))
    }

    @Test
    fun `a draft that says what the file says is not an edit`() {
        // Toggling a switch on and back off must leave the save button grey and the prompt unarmed:
        // keying both on "a draft exists" made them shout about a change that no longer exists.
        val stored = document()

        assertEquals(false, BotPermissionsDoc.isDirty(stored, null))
        assertEquals(false, BotPermissionsDoc.isDirty(stored, stored))
        assertEquals(false, BotPermissionsDoc.isDirty(null, null))
        assertTrue(BotPermissionsDoc.isDirty(stored, BotPermissionsDoc.withPath(stored, BotPermissionsDoc.PathGrant("/srv", read = true))))
        assertTrue(BotPermissionsDoc.isDirty(null, stored))
    }

    @Test
    fun `an edit undone by hand reads as clean again`() {
        val stored = document()
        val edited = BotPermissionsDoc.withPath(stored, BotPermissionsDoc.PathGrant("/srv", read = true))
        val undone = BotPermissionsDoc.withoutPath(edited, "/srv")

        assertTrue(BotPermissionsDoc.isDirty(stored, edited))
        // Removing the row gives back the same document — key order included, which is why the
        // comparison can be structural.
        assertEquals(false, BotPermissionsDoc.isDirty(stored, undone))
    }
}
