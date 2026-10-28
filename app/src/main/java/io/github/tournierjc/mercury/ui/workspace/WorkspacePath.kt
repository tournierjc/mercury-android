package io.github.tournierjc.mercury.ui.workspace

import kotlin.math.roundToLong
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull

/**
 * One row of `GET /api/workspace/tree`.
 *
 * The daemon sends `{name, path, isDirectory, type, size, ext}` and nothing else — no mtime, no
 * permissions. `path` is already relative to the workspace root (`mercury-agent/src/main.ts`), so
 * the screen never rebuilds it from the parent and the two cannot disagree.
 */
data class WorkspaceEntry(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long,
    val ext: String,
    /** The daemon's icon hint: `directory`, `md`, `ts`… or `file` when it knows no better. */
    val kind: String,
) {
    companion object {
        /** Null for an item without a name: an unnameable row is not a row. */
        fun of(json: JsonObject): WorkspaceEntry? {
            val name = json.string("name") ?: return null
            if (name.isBlank()) return null
            val isDirectory = (json["isDirectory"] as? JsonPrimitive)?.contentOrNull == "true"
            return WorkspaceEntry(
                name = name,
                path = json.string("path") ?: name,
                isDirectory = isDirectory,
                size = (json["size"] as? JsonPrimitive)?.longOrNull ?: 0L,
                ext = json.string("ext").orEmpty(),
                kind = json.string("type") ?: if (isDirectory) "directory" else "file",
            )
        }

        private fun JsonObject.string(key: String): String? =
            (this[key] as? JsonPrimitive)?.contentOrNull
    }
}

/** One step of the breadcrumb: what to draw, and the directory to open when it is tapped. */
data class Crumb(val label: String, val path: String)

/**
 * The file browser's arithmetic, done without Android.
 *
 * Paths are workspace-relative and normalised the way the daemon reads them: no leading slash, no
 * `.`/`..`, and the empty string for the root. Everything here is pure so the explorer's rules —
 * which crumb opens what, what "up" means at the root, what order the daemon's items are drawn in —
 * can be tested without a device (see `WorkspacePathTest`).
 */
object WorkspacePath {

    /** The workspace root. Not `"/"`: the daemon's `path` is relative to it. */
    const val ROOT = ""

    /** Collapses `.` and `..`, drops a leading slash, keeps the result workspace-relative. */
    fun normalise(raw: String): String {
        val out = ArrayDeque<String>()
        for (segment in raw.split('/')) {
            when (segment) {
                "", "." -> Unit
                ".." -> out.removeLastOrNull()
                else -> out.addLast(segment)
            }
        }
        return out.joinToString("/")
    }

    /** The parent directory; the root is its own parent, so "up" at the root is not an escape. */
    fun parent(path: String): String = normalise(path).substringBeforeLast('/', "")

    /** A child of [current]; the daemon expects the joined path, not the bare name. */
    fun join(current: String, name: String): String {
        val base = normalise(current)
        return if (base.isEmpty()) normalise(name) else "$base/${normalise(name)}"
    }

    /**
     * The trail from the root to [path], root first.
     *
     * The first crumb is always the workspace itself (`/`, path `""`), so a file two levels deep
     * still offers a one-tap way home; the last crumb is the directory being shown.
     */
    fun breadcrumb(path: String): List<Crumb> {
        val crumbs = mutableListOf(Crumb("/", ROOT))
        var walked = ROOT
        for (segment in normalise(path).split('/')) {
            if (segment.isEmpty()) continue
            walked = if (walked.isEmpty()) segment else "$walked/$segment"
            crumbs += Crumb(segment, walked)
        }
        return crumbs
    }

    /**
     * Directories first, then alphabetical, case-insensitively.
     *
     * The daemon already sorts this way; doing it again is what keeps the order true for a caller
     * that merged two answers (or for a test), and the case-insensitive compare keeps `README` next
     * to `readme` instead of above every lowercase name.
     */
    fun sort(entries: List<WorkspaceEntry>): List<WorkspaceEntry> =
        entries.sortedWith(
            compareBy<WorkspaceEntry> { if (it.isDirectory) 0 else 1 }
                .thenBy { it.name.lowercase() },
        )

    /**
     * A file size a phone can read: `512 B`, `2 kB`, `1.5 kB`, `3.4 MB`.
     *
     * SI-ish units with a dot: they are the same four glyphs in both locales, so no unit word
     * needs a translation. A directory sends 0 and reads as a dash rather than `0 B`.
     */
    fun formatSize(bytes: Long): String {
        if (bytes < 0) return "—"
        if (bytes < 1024) return "$bytes B"
        val units = listOf("kB", "MB", "GB", "TB")
        var value = bytes / 1024.0
        var index = 0
        while (value >= 1024.0 && index < units.lastIndex) {
            value /= 1024.0
            index++
        }
        val tenths = (value * 10).roundToLong()
        return if (tenths % 10 == 0L) {
            "${tenths / 10} ${units[index]}"
        } else {
            "${tenths / 10}.${tenths % 10} ${units[index]}"
        }
    }

    /**
     * Whether a preview should refuse to print the bytes.
     *
     * `GET /api/workspace/file` always decodes as UTF-8 and carries no flag for this, so the file
     * says it itself: a NUL byte, or a body where one character in twenty did not decode. A UTF-8
     * text file may hold a stray replacement character; a PNG holds thousands.
     */
    fun looksBinary(content: String): Boolean {
        if (content.isEmpty()) return false
        if (content.contains('\u0000')) return true
        val undecodable = content.count { it == '\uFFFD' }
        return undecodedRatio(content, undecodable) > 0.05
    }

    private fun undecodedRatio(content: String, undecodable: Int): Double =
        undecodable.toDouble() / content.length.coerceAtLeast(1)
}
