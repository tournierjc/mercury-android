package io.github.tournierjc.mercury.data

import io.github.tournierjc.mercury.data.dto.IdentityDto
import io.github.tournierjc.mercury.data.dto.InstanceConfig
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The instance PUT bodies are differences, not form dumps.
 *
 * `PUT /api/config` writes what it receives: an unchanged field echoed back still rewrites the
 * daemon's config file, so a patch has to be empty when nothing moved and carry one key when one
 * did. A blank field is the owner mid-edit, never a request to erase the value.
 */
class InstancePatchTest {

    private val current = InstanceConfig(
        identity = IdentityDto(name = "Mercury", owner = "JC"),
        defaultProvider = "deepseek",
        tokenBudget = 200_000,
    )

    private fun draft(
        name: String = "Mercury",
        owner: String = "JC",
        provider: String = "deepseek",
        budget: String = "200000",
    ) = InstanceDraft(name = name, owner = owner, defaultProvider = provider, tokenBudget = budget)

    @Test
    fun `an untouched form builds no patch at all`() {
        assertEquals(JsonObject(emptyMap()), InstancePatch.config(current, draft()))
    }

    @Test
    fun `a renamed instance sends only the name`() {
        val body = InstancePatch.config(current, draft(name = "Mercury 2"))

        assertEquals("Mercury 2", body.jsonObject.getValue("identity").jsonObject.getValue("name").jsonPrimitive.content)
        assertEquals(setOf("identity"), body.keys)
        assertEquals(setOf("name"), body.jsonObject.getValue("identity").jsonObject.keys)
    }

    @Test
    fun `a new owner travels without the name`() {
        val identity = InstancePatch.config(current, draft(owner = "Jeanne")).jsonObject.getValue("identity").jsonObject

        assertEquals(setOf("owner"), identity.keys)
        assertEquals("Jeanne", identity.getValue("owner").jsonPrimitive.content)
    }

    @Test
    fun `a cleared field is not sent`() {
        // The daemon would store an empty name: the only way out of a mid-typed field must be
        // "leave it alone", so a blank or whitespace-only value never leaves the form.
        assertEquals(JsonObject(emptyMap()), InstancePatch.config(current, draft(name = "  ", owner = "")))
    }

    @Test
    fun `the default provider is a key of its own, beside identity`() {
        val body = InstancePatch.config(current, draft(provider = "openrouter"))

        assertEquals(setOf("defaultProvider"), body.keys)
        assertEquals("openrouter", body.getValue("defaultProvider").jsonPrimitive.content)
    }

    @Test
    fun `a changed budget is a number on the wire, not a string`() {
        val body = InstancePatch.config(current, draft(budget = "50000"))

        assertEquals("50000", body.getValue("tokenBudget").jsonPrimitive.content)
        assertTrue("tokenBudget must not be quoted", body.getValue("tokenBudget").jsonPrimitive.isString.not())
    }

    @Test
    fun `zero is a value - it lifts the ceiling - so it is sent`() {
        val unlimited = InstanceConfig(identity = IdentityDto("Mercury", "JC"), defaultProvider = "deepseek", tokenBudget = 0)

        assertEquals("200000", InstancePatch.config(unlimited, draft(budget = "200000")).getValue("tokenBudget").jsonPrimitive.content)
        assertEquals("0", InstancePatch.config(current, draft(budget = "0")).getValue("tokenBudget").jsonPrimitive.content)
    }

    @Test
    fun `an emptied field lifts the ceiling too, because zero and empty mean the same thing`() {
        assertEquals("0", InstancePatch.config(current, draft(budget = "")).getValue("tokenBudget").jsonPrimitive.content)
        assertEquals("0", InstancePatch.config(current, draft(budget = "   ")).getValue("tokenBudget").jsonPrimitive.content)
    }

    @Test
    fun `an instance already without a ceiling has nothing to lift`() {
        // Writing 0 over 0 would be a config rewrite that changes nothing — the same rule that
        // keeps an untouched form from patching at all.
        val unlimited = InstanceConfig(identity = IdentityDto("Mercury", "JC"), defaultProvider = "deepseek", tokenBudget = 0)

        assertEquals(JsonObject(emptyMap()), InstancePatch.config(unlimited, draft(budget = "")))
        assertEquals(JsonObject(emptyMap()), InstancePatch.config(unlimited, draft(budget = "0")))
    }

    @Test
    fun `no ceiling reads as an empty field, and zero typed into it empties it`() {
        // A `0` in a field labelled "budget" reads as a limit of none; the absence of a ceiling is
        // the absence of a number.
        assertEquals("200000", InstancePatch.budgetField(200_000))
        assertEquals("", InstancePatch.budgetField(0))

        assertEquals("", InstancePatch.budgetText("0"))
        assertEquals("", InstancePatch.budgetText(" 0 "))
        assertEquals("50000", InstancePatch.budgetText("50000"))
        assertEquals("", InstancePatch.budgetText(""))
    }

    @Test
    fun `a field that is not a number keeps the form, not the daemon`() {
        // The screen refuses to submit it; the builder must not invent a token count either.
        assertEquals(JsonObject(emptyMap()), InstancePatch.config(current, draft(budget = "douze mille")))
        assertEquals(JsonObject(emptyMap()), InstancePatch.config(current, draft(budget = "-3")))
    }

    @Test
    fun `every changed key is sent together`() {
        val body = InstancePatch.config(
            current,
            draft(name = "Atelier", owner = "Jeanne", provider = "openrouter", budget = "0"),
        )

        assertEquals(setOf("identity", "defaultProvider", "tokenBudget"), body.keys)
        assertEquals(setOf("name", "owner"), body.jsonObject.getValue("identity").jsonObject.keys)
    }

    @Test
    fun `the workspace is sent alone, trimmed, or not at all`() {
        assertNull(InstancePatch.workspace("/work", "/work"))
        assertNull(InstancePatch.workspace("/work", "   "))
        assertEquals("/srv/projet", InstancePatch.workspace("/work", "  /srv/projet "))
    }
}
