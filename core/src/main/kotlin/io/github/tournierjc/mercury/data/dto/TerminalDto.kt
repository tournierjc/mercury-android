package io.github.tournierjc.mercury.data.dto

import kotlinx.serialization.Serializable

/**
 * `POST /api/terminal/exec` answers with the same envelope whether the command
 * succeeded or not: `{success, output, exitCode, cwd}` plus `error` on failure.
 * The command runs in the workspace with a 30 s timeout and a 2 MB buffer.
 */
@Serializable
data class TerminalResult(
    val success: Boolean = false,
    val output: String = "",
    val exitCode: Int = 0,
    val cwd: String = "",
    val error: String = "",
) {
    /** What to show in the output block: stdout/stderr, else the error, else a placeholder. */
    val display: String get() = output.ifBlank { error }.ifBlank { "(no output)" }
}
