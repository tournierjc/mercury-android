package io.github.tournierjc.mercury.markdown

/**
 * A deliberately small markdown subset, rendered without pulling a renderer
 * dependency: fenced code blocks, headings, bullet lists, `inline code`,
 * **bold**, *italic*, and `[links](https://…)` — plus a bare `https://…`,
 * which an agent writes as often as a bracketed link. Anything else stays
 * literal, which is the honest behaviour for an agent that can emit arbitrary
 * text.
 */
sealed interface MdBlock {
    data class Paragraph(val spans: List<MdSpan>) : MdBlock
    data class Heading(val level: Int, val spans: List<MdSpan>) : MdBlock
    data class Bullets(val items: List<List<MdSpan>>) : MdBlock
    data class Code(val language: String, val content: String) : MdBlock

    /**
     * A pipe table, the shape a model writes when it compares things — and the shape the phone
     * used to print as raw `| a | b |` rows.
     *
     * [header] is the first row and is drawn as a label; [rows] are the body. Cells keep their
     * inline spans, so a link or a code fragment inside a cell still renders as one.
     */
    data class Table(val header: List<List<MdSpan>>, val rows: List<List<List<MdSpan>>>) : MdBlock
}

data class MdSpan(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val code: Boolean = false,
    /**
     * Where the span points when it is a link. Only `http(s)` ever lands here: the renderer hands
     * it to the platform's browser, so a `javascript:` or `intent:` target stays literal text.
     */
    val url: String? = null,
)

object MiniMarkdown {

    // Ordered as it is for a reason: at one position, the first alternative that matches wins. A
    // backtick before `[` keeps `` `[a](b)` `` code, and a link before the bare URL keeps the label
    // the author wrote instead of cutting the span at the scheme.
    private val inlinePattern = Regex(
        "(\\[[^\\]\\n]+\\]\\([^()\\s]+\\)|`[^`]+`|\\*\\*[^*]+\\*\\*|\\*[^*]+\\*|https?://[^\\s<>()\\[\\]]+)",
    )

    /** A target the platform may open. Anything else — `javascript:`, `intent:`, `file:` — is text. */
    fun isWebUrl(target: String): Boolean {
        val lowered = target.trim().lowercase()
        return lowered.startsWith("http://") || lowered.startsWith("https://")
    }

    /** Trailing punctuation belongs to the sentence, not to the URL it follows. */
    private const val URL_TAIL = ".,;:!?»\"'"

    /** A separator row: `| --- | :---: |` and the like. It is what makes a pipe line a table. */
    private val tableSeparator = Regex("^\\|?\\s*:?-{2,}:?\\s*(\\|\\s*:?-{2,}:?\\s*)*\\|?$")

    private fun isTableSeparator(line: String): Boolean =
        line.contains('|') && tableSeparator.matches(line.trim())

    private fun cells(line: String): List<String> =
        line.trim().removePrefix("|").removeSuffix("|").split('|').map { it.trim() }

    fun parse(text: String): List<MdBlock> {
        val blocks = ArrayList<MdBlock>()
        val lines = text.replace("\r\n", "\n").split('\n')
        var index = 0
        val paragraph = ArrayList<String>()
        val bullets = ArrayList<String>()

        fun flushParagraph() {
            if (paragraph.isEmpty()) return
            blocks += MdBlock.Paragraph(inline(paragraph.joinToString(" ")))
            paragraph.clear()
        }

        fun flushBullets() {
            if (bullets.isEmpty()) return
            blocks += MdBlock.Bullets(bullets.map { inline(it) })
            bullets.clear()
        }

        while (index < lines.size) {
            val line = lines[index]
            val trimmed = line.trimStart()
            when {
                // A pipe table, recognised by its separator row: a lone `|` in prose stays prose,
                // because nothing follows it that looks like `|---|---|`.
                trimmed.startsWith("|") && index + 1 < lines.size && isTableSeparator(lines[index + 1]) -> {
                    flushParagraph()
                    flushBullets()
                    val header = cells(trimmed).map { inline(it) }
                    val rows = ArrayList<List<List<MdSpan>>>()
                    index += 2
                    while (index < lines.size && lines[index].trimStart().startsWith("|")) {
                        rows += cells(lines[index]).map { inline(it) }
                        index++
                    }
                    index-- // the outer loop steps past the line that ended the table
                    blocks += MdBlock.Table(header = header, rows = rows)
                }

                trimmed.startsWith("```") -> {
                    flushParagraph()
                    flushBullets()
                    val language = trimmed.removePrefix("```").trim()
                    val code = StringBuilder()
                    index++
                    while (index < lines.size && !lines[index].trimStart().startsWith("```")) {
                        if (code.isNotEmpty()) code.append('\n')
                        code.append(lines[index])
                        index++
                    }
                    blocks += MdBlock.Code(language = language, content = code.toString())
                }

                trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("• ") -> {
                    flushParagraph()
                    bullets += trimmed.substring(2).trim()
                }

                trimmed.startsWith("#") -> {
                    flushParagraph()
                    flushBullets()
                    val hashes = trimmed.takeWhile { it == '#' }.length
                    val level = hashes.coerceAtMost(3)
                    val title = trimmed.dropWhile { it == '#' }.trim()
                    blocks += MdBlock.Heading(level, inline(title))
                }

                trimmed.isEmpty() -> {
                    flushParagraph()
                    flushBullets()
                }

                else -> {
                    flushBullets()
                    paragraph += trimmed
                }
            }
            index++
        }
        flushParagraph()
        flushBullets()
        return blocks
    }

    fun inline(text: String): List<MdSpan> {
        if (text.isEmpty()) return emptyList()
        val spans = ArrayList<MdSpan>()
        var cursor = 0
        inlinePattern.findAll(text).forEach { match ->
            if (match.range.first > cursor) {
                spans += MdSpan(text.substring(cursor, match.range.first))
            }
            val token = match.value
            when {
                token.startsWith("[") && token.contains("](") -> {
                    val label = token.substringAfter("[").substringBefore("]")
                    val target = token.substringAfter("](").removeSuffix(")")
                    // A target the browser cannot open is not a link: the markdown stays as typed,
                    // which is more honest than a tap that does nothing or opens something else.
                    if (isWebUrl(target)) {
                        spans += MdSpan(label, url = target)
                    } else {
                        spans += MdSpan(token)
                    }
                }

                token.startsWith("`") && token.endsWith("`") ->
                    spans += MdSpan(token.removeSurrounding("`"), code = true)

                token.startsWith("**") && token.endsWith("**") ->
                    spans += MdSpan(token.removeSurrounding("**"), bold = true)

                token.startsWith("http") -> {
                    // « Voir https://idealo.fr/x. » — the full stop ends the sentence, not the link.
                    val url = token.trimEnd { it in URL_TAIL }
                    spans += MdSpan(url, url = url)
                    val tail = token.substring(url.length)
                    if (tail.isNotEmpty()) spans += MdSpan(tail)
                }

                else -> spans += MdSpan(token.removeSurrounding("*"), italic = true)
            }
            cursor = match.range.last + 1
        }
        if (cursor < text.length) spans += MdSpan(text.substring(cursor))
        return spans
    }
}
