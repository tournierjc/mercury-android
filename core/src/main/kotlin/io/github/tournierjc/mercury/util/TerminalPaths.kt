package io.github.tournierjc.mercury.util

/**
 * The prompt's directory arithmetic, done on the phone.
 *
 * The terminal screen handles `cd` locally: moving through the workspace costs no request, and
 * the daemon's `POST /api/terminal/exec` is only worth a round trip for a real command. The paths
 * are relative to the Mercury workspace, normalised to "no leading slash, no `.`/`..`, empty for
 * the workspace root" — the shape the exec route takes as `cwd`.
 */
object TerminalPaths {

    /** Resolves `cd <target>` typed at `current` (workspace-relative, "" = root). */
    fun resolve(current: String, target: String): String = when {
        target.isBlank() || target == "~" || target == "/" -> ""
        target == "-" -> current.substringBeforeLast('/', "")
        target.startsWith("/") -> normalise(target)
        else -> normalise(if (current.isBlank()) target else "$current/$target")
    }

    /** Collapses `.` and `..`, drops a leading slash, keeps the result workspace-relative. */
    fun normalise(path: String): String {
        val out = ArrayDeque<String>()
        for (segment in path.split('/')) {
            when (segment) {
                "", "." -> Unit
                ".." -> out.removeLastOrNull()
                else -> out.addLast(segment)
            }
        }
        return out.joinToString("/")
    }

    /** What the prompt shows: `mercury~`, or `mercury/repo/src`. */
    fun prompt(current: String): String = "mercury" + if (current.isBlank()) "~" else "/$current"
}
