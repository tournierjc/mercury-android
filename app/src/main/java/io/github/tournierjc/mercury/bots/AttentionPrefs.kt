package io.github.tournierjc.mercury.bots

import android.content.Context

/**
 * Réglages de la vérification de fond, et mémoire de ce qui a déjà été annoncé.
 *
 * Rien de secret ici : l'URL du serveur et les cookies vivent dans les prefs chiffrées
 * de l'application. Ce fichier ne garde que des identifiants d'alerte (`bot|kind|détail`),
 * ce qui suffit à ne pas répéter la même notification toutes les quinze minutes.
 */
class AttentionPrefs(context: Context) {

    private val prefs = context.getSharedPreferences("mercury_attention", Context.MODE_PRIVATE)

    /**
     * La veille périodique est armée par défaut : c'est le repli quand Android refuse le service,
     * et un repli qu'il faut activer ne sert à rien le jour où il sert.
     */
    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    /**
     * Veille continue : service de premier plan qui garde le flux du roster ouvert.
     *
     * Allumée **par défaut** — « pas besoin d'activer un fond » était la demande. Le seul état
     * mémorisé est donc le refus explicite ([watchDisabled]), pas l'accord : une valeur par
     * défaut à `false` sur un réglage qu'on croyait optionnel, c'est une veille qui ne démarre
     * jamais chez quelqu'un qui n'a rien touché.
     */
    val persistent: Boolean
        get() = !watchDisabled

    /** L'équipier a éteint la veille exprès : on ne la rallume ni au boot ni à l'ouverture. */
    var watchDisabled: Boolean
        get() = prefs.getBoolean(KEY_DISABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_DISABLED, value).apply()

    /** Android refuse un travail périodique plus court que 15 minutes. */
    var intervalMinutes: Int
        get() = prefs.getInt(KEY_INTERVAL, MIN_INTERVAL_MINUTES).coerceAtLeast(MIN_INTERVAL_MINUTES)
        set(value) = prefs.edit().putInt(KEY_INTERVAL, value.coerceAtLeast(MIN_INTERVAL_MINUTES)).apply()

    /** Alertes déjà annoncées, pour ne pas les répéter à chaque passe. */
    var seenKeys: Set<String>
        get() = prefs.getStringSet(KEY_SEEN, emptySet())?.toSet() ?: emptySet()
        set(value) = prefs.edit().putStringSet(KEY_SEEN, value).apply()

    fun forget() = prefs.edit().remove(KEY_SEEN).apply()

    companion object {
        const val MIN_INTERVAL_MINUTES = 15
        private const val KEY_ENABLED = "enabled"
        private const val KEY_INTERVAL = "intervalMinutes"
        private const val KEY_DISABLED = "watchDisabled"
        private const val KEY_SEEN = "seenKeys"
    }
}
