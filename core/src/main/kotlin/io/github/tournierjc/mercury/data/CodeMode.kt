package io.github.tournierjc.mercury.data

/**
 * The four programming modes the daemon runs in.
 *
 * The choice lived in the console, next to the daemon's disk and its plugins — a supervision screen,
 * for a setting that governs what the agent does on the *next message you send*. It belongs to the
 * conversation, and this is the vocabulary both screens agree on.
 */
enum class CodeMode(val wire: String) {
    /** The agent answers; it does not go near the workspace. */
    Off("off"),

    /** It decides by itself whether the request is a programming one. */
    Auto("auto"),

    /** It reads, proposes a plan, and writes nothing. */
    Plan("plan"),

    /** It edits files directly. */
    Execute("execute");

    /** A mode other than [Off] is a decision the owner made, and the screen says so. */
    val engaged: Boolean get() = this != Off

    companion object {
        /** The daemon's own words, whatever their case; anything unrecognised reads as [Off]. */
        fun from(wire: String?): CodeMode =
            entries.firstOrNull { it.wire.equals(wire?.trim(), ignoreCase = true) } ?: Off
    }
}
