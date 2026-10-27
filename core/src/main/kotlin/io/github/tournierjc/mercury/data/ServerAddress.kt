package io.github.tournierjc.mercury.data

/**
 * The Mercury address the owner types: a host, optionally with a scheme and a port.
 *
 * Two screens accept it — the sign-in screen and Réglages — so the rule lives here rather than in a
 * composable. [normalize] is what gets stored and what the HTTP client is pointed at; [validate] is
 * the only place that decides whether a string is an address at all, and it answers with a reason
 * the UI can translate instead of a ready-made sentence.
 */
object ServerAddress {

    enum class Problem { Empty, UnsupportedScheme, MissingHost }

    /** Any `scheme://` prefix; `spark:8443` is a host and a port, not a scheme. */
    private val anyScheme = Regex("^[A-Za-z][A-Za-z0-9+.-]*://")

    fun validate(raw: String): Problem? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return Problem.Empty
        val hasScheme = anyScheme.containsMatchIn(trimmed)
        if (hasScheme && !isHttp(trimmed)) return Problem.UnsupportedScheme
        val rest = if (hasScheme) anyScheme.replace(trimmed, "") else trimmed
        val host = rest.substringBefore('/').substringBefore('?').substringBefore('#')
        if (host.isBlank() || host.all { it == ':' }) return Problem.MissingHost
        return null
    }

    /** Same rule the sign-in screen has always applied: a missing scheme means https, no trailing `/`. */
    fun normalize(raw: String): String = MercuryApi.normalizeBaseUrl(raw)

    private fun isHttp(value: String): Boolean {
        val lower = value.lowercase()
        return lower.startsWith("http://") || lower.startsWith("https://")
    }
}
