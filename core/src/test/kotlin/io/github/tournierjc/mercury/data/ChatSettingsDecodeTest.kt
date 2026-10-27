package io.github.tournierjc.mercury.data

import io.github.tournierjc.mercury.data.dto.ChatSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `GET /api/chat/settings` answers `{"bypassPermissions":false,"restrictUser":false,
 * "workspace":"","permissionMode":null}` when no permission mode has been chosen yet.
 *
 * `permissionMode` is declared non-nullable with a default, so an explicit `null` used to abort
 * the decode and surface as a red banner in Réglages ("Un problème" / expected string literal but
 * 'null' was found at path: $.permissionMode). The parser now coerces it to the default.
 */
class ChatSettingsDecodeTest {

    private val json = MercuryEventParser.defaultJson

    @Test
    fun `an explicit null permissionMode falls back to the default instead of failing`() {
        val raw = """{"bypassPermissions":false,"restrictUser":false,"workspace":"","permissionMode":null}"""

        val settings = json.decodeFromString(ChatSettings.serializer(), raw)

        assertEquals("ask-me", settings.permissionMode)
        assertFalse(settings.bypassPermissions)
        assertFalse(settings.autoApproves)
    }

    @Test
    fun `a real permissionMode is kept`() {
        val raw = """{"bypassPermissions":true,"restrictUser":false,"workspace":"/work","permissionMode":"allow-all"}"""

        val settings = json.decodeFromString(ChatSettings.serializer(), raw)

        assertEquals("allow-all", settings.permissionMode)
        assertEquals("/work", settings.workspace)
        // allow-all without bypassPermissions still counts as auto-approving.
        assertTrue(settings.autoApproves)
    }

    @Test
    fun `another absent field does not become a parse error either`() {
        // Same class of bug as permissionMode: the daemon omits or nulls what it does not track.
        val raw = """{"workspace":null}"""

        val settings = json.decodeFromString(ChatSettings.serializer(), raw)

        assertEquals("", settings.workspace)
        assertEquals("ask-me", settings.permissionMode)
    }

    @Test
    fun `an unknown key is ignored rather than fatal`() {
        val raw = """{"permissionMode":"ask-me","somethingNew":42}"""

        assertEquals("ask-me", json.decodeFromString(ChatSettings.serializer(), raw).permissionMode)
    }
}
