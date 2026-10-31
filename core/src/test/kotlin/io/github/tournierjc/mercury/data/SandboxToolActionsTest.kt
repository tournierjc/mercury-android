package io.github.tournierjc.mercury.data

import io.github.tournierjc.mercury.data.dto.SandboxToolDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The row's buttons. Every case here is a row the screen really draws — the owner's report was
 * precisely that one of them (ffmpeg, absent and unblocked) offered nothing.
 */
class SandboxToolActionsTest {

    private fun tool(
        present: Boolean = false,
        installable: Boolean = false,
        installBlocked: String = "",
        auth: String = "unknown",
        removable: Boolean = false,
    ) = SandboxToolDto(
        id = "x",
        label = "x",
        present = present,
        installable = installable,
        installBlocked = installBlocked,
        auth = auth,
        removable = removable,
    )

    @Test
    fun `an absent installable tool offers install`() {
        assertEquals(listOf(SandboxToolAction.Install), SandboxToolActions.of(tool(installable = true)))
    }

    @Test
    fun `an absent tool that cannot be installed offers nothing`() {
        assertTrue(
            "the reason is the feature: no button next to a sentence saying it is impossible",
            SandboxToolActions.of(tool(installBlocked = "Ce conteneur tourne en uid 1000 sans sudo")).isEmpty(),
        )
    }

    @Test
    fun `a connected tool we installed offers sign-out and uninstall`() {
        assertEquals(
            listOf(SandboxToolAction.Disconnect, SandboxToolAction.Remove),
            SandboxToolActions.of(tool(present = true, auth = "connected", removable = true)),
        )
    }

    @Test
    fun `a signed-out tool we installed offers connect and uninstall`() {
        assertEquals(
            listOf(SandboxToolAction.Connect, SandboxToolAction.Remove),
            SandboxToolActions.of(tool(present = true, auth = "no", removable = true)),
        )
    }

    @Test
    fun `a tool shipped by the image has no uninstall and no sign-in`() {
        assertTrue(
            SandboxToolActions.of(tool(present = true, auth = "unknown", removable = false)).isEmpty(),
        )
    }

    @Test
    fun `a tool with no sign-in but ours still offers uninstall`() {
        assertEquals(
            listOf(SandboxToolAction.Remove),
            SandboxToolActions.of(tool(present = true, auth = "unknown", removable = true)),
        )
    }

    @Test
    fun `a present tool is never offered install`() {
        val actions = SandboxToolActions.of(tool(present = true, installable = true, removable = true))
        assertTrue(SandboxToolAction.Install !in actions)
    }
}
