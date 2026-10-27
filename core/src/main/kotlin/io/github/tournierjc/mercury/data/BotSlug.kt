package io.github.tournierjc.mercury.data

/**
 * The id a bot gets when the owner types a name.
 *
 * Mercury's store requires `^[a-z0-9][a-z0-9-]{0,47}$` and refuses anything else — so a name
 * with accents, spaces or capitals has to be turned into an id before the create call, and the
 * owner should see the result rather than an English error. The id stays editable in the form:
 * this is a proposal, not a decision.
 */
object BotSlug {

    private val NOT_ALLOWED = Regex("[^a-z0-9]+")
    private const val MAX_LENGTH = 48

    fun from(name: String): String {
        val folded = java.text.Normalizer.normalize(name, java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .lowercase()
            .replace(NOT_ALLOWED, "-")
            .trim('-')
        val trimmed = folded.take(MAX_LENGTH).trim('-')
        return trimmed.ifEmpty { "bot" }
    }
}
