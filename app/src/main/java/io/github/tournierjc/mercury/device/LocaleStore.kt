package io.github.tournierjc.mercury.device

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * The language the app draws its own chrome in.
 *
 * "System" is not an absence of choice: it is the default, and it means "whatever the phone is
 * set to" — so it is stored as an empty tag and applied by handing the untouched context through.
 *
 * The chosen locale is wrapped into the base context rather than pushed into the system's
 * per-app locale service: one code path, the same behaviour from API 26 to 35, and nothing that
 * depends on the OEM's settings screen.
 */
object LocaleStore {

    private const val FILE = "mercury"
    private const val KEY = "app_language"

    /** The stored choice, as the registry knows it: an unknown or stale tag means "follow". */
    fun language(context: Context): AppLanguage {
        val stored = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY, "").orEmpty()
        return Languages.byTag(stored)
    }

    fun tag(context: Context): String = language(context).tag

    /** Stores the tag the registry knows, so a case or spacing mistake cannot become a new choice. */
    fun set(context: Context, language: AppLanguage) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(KEY, language.tag).apply()
    }

    /**
     * The same context, with the chosen locale — or the untouched context when the app follows
     * the phone, so the system's own choice (including regional variants) is preserved.
     */
    fun wrap(base: Context): Context {
        val language = language(base)
        if (language.followsSystem) return base
        val locale = Locale.forLanguageTag(language.tag)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        return base.createConfigurationContext(config)
    }
}
