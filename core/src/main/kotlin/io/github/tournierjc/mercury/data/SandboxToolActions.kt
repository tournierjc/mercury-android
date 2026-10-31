package io.github.tournierjc.mercury.data

import io.github.tournierjc.mercury.data.dto.SandboxToolDto

/** The buttons a tool's row may offer. */
enum class SandboxToolAction {
    /** Put a binary we know how to fetch into the tools dir. */
    Install,

    /** Start the tool's own device flow. */
    Connect,

    /** End that session for every bot at once. */
    Disconnect,

    /** Delete what an install put on disk. */
    Remove,
}

/**
 * Which buttons a row draws — the whole rule, in one place, with no Compose around it.
 *
 * The screen used to decide this inline in a `when` chain, where the branches were one mutually
 * exclusive button: that shape could not express a tool that is *connected* and *ours*, which is
 * three quarters of the interesting cases (gh, az). Install and remove are exclusive because a tool
 * is either there or not; connecting is not, because a session is a third thing.
 */
object SandboxToolActions {

    fun of(tool: SandboxToolDto): List<SandboxToolAction> {
        val actions = mutableListOf<SandboxToolAction>()
        if (!tool.present) {
            // A tool that cannot be installed here still gets a row: the sentence explaining why is
            // the feature, and no button may pretend otherwise.
            if (tool.installable) actions += SandboxToolAction.Install
            return actions
        }
        when (tool.auth) {
            "no" -> actions += SandboxToolAction.Connect
            "connected" -> actions += SandboxToolAction.Disconnect
            // `unknown` is a tool with no sign-in at all (git, jq): nothing to connect, and a
            // disable button would be an affordance over a session that does not exist.
        }
        if (tool.removable) actions += SandboxToolAction.Remove
        return actions
    }
}
