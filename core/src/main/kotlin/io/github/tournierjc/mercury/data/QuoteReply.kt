package io.github.tournierjc.mercury.data

/**
 * Quoting a message when you answer it.
 *
 * Mercury's chat route takes a message, not a reply-with-parent: there is no field to hang a
 * quoted turn on. So the quote travels in the text, as a markdown blockquote — the form every
 * model already reads — and the app parses it back to draw it as a quote instead of literal
 * `>` characters. Plain, portable, and it survives a reload because the quote is part of the
 * message the server stored.
 */
object QuoteReply {

    /** What the daemon receives: the quote as a blockquote, then the answer. */
    fun compose(quote: String, body: String): String {
        val quoted = quote.trim().lines().joinToString("\n") { "> $it" }
        return if (body.isBlank()) quoted else "$quoted\n\n${body.trim()}"
    }

    /**
     * A message read as (quote, body).
     *
     * `quote` is empty when the message does not start with a blockquote. Only the leading
     * quote block is lifted: a `>` inside the answer is code or prose, not a quote.
     */
    fun parse(text: String): QuotedMessage {
        val lines = text.lines()
        val quoted = mutableListOf<String>()
        var index = 0
        while (index < lines.size && lines[index].trimStart().startsWith(">")) {
            quoted += lines[index].trimStart().removePrefix(">").removePrefix(" ").trimEnd()
            index++
        }
        if (quoted.isEmpty()) return QuotedMessage(quote = "", body = text)
        // The blank line that separates the quote from the answer is part of the shape.
        while (index < lines.size && lines[index].isBlank()) index++
        return QuotedMessage(
            quote = quoted.joinToString("\n").trim(),
            body = lines.drop(index).joinToString("\n").trim(),
        )
    }
}

data class QuotedMessage(val quote: String, val body: String) {
    val isQuote: Boolean get() = quote.isNotEmpty()
}
