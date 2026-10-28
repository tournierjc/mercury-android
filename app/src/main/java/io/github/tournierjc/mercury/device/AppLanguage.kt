package io.github.tournierjc.mercury.device

/**
 * A language the app can draw its own chrome in.
 *
 * The list of languages used to live in the settings screen, the resource folders next to it and
 * the parity test in a third place, so adding one meant finding all three. [Languages.all] is now
 * the only enumeration: the picker reads it, the stored tag is validated against it, and the tests
 * walk it to check that every folder matches. One row and one folder is the whole job.
 */
data class AppLanguage(
    /** BCP 47 tag, or empty for "whatever the phone is set to". */
    val tag: String,
    /** The language's name *in that language* — English speakers look for « Français ». */
    val nativeName: String = "",
    /**
     * True for the language the base `values/` folder is written in, so it needs no `values-xx/`
     * sibling: Android falls back to `values/` for it.
     */
    val isBase: Boolean = false,
) {
    /** The app follows the phone rather than a choice. */
    val followsSystem: Boolean get() = tag.isBlank()

    /** The resource folder this language compiles from, relative to `app/src/main/res`. */
    val resourceDir: String get() = if (isBase) "values" else "values-${tag.lowercase()}"
}

/**
 * The languages the app ships. Adding one is two steps:
 *
 * 1. copy `app/src/main/res/values/` to `values-<tag>/` and translate it;
 * 2. add one [AppLanguage] row here.
 *
 * `StringParityTest` fails until every key of the base exists in the new folder, and
 * `LanguagesTest` fails if a folder has no row (or a row has no folder) — so neither side can be
 * forgotten silently.
 */
object Languages {

    /** Follow the phone. Not a missing choice: the default, and it keeps regional variants. */
    val system = AppLanguage(tag = "")

    /**
     * French is the base: `values/` is written in French, which is why it carries `isBase`. Every
     * other language needs its own folder.
     */
    val french = AppLanguage(tag = "fr", nativeName = "Français", isBase = true)
    val english = AppLanguage(tag = "en", nativeName = "English")

    /** Everything the picker offers, phone first. */
    val all: List<AppLanguage> = listOf(system, french, english)

    /** The languages with a translation of their own — what the parity test walks. */
    val translated: List<AppLanguage> = all.filterNot { it.followsSystem }

    /** The choice stored by a phone we no longer ship, or no choice at all, reads as "follow it". */
    fun byTag(tag: String?): AppLanguage =
        all.firstOrNull { !it.followsSystem && it.tag.equals(tag?.trim().orEmpty(), ignoreCase = true) } ?: system
}
