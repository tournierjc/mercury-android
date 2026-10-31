package io.github.tournierjc.mercury.bots

import android.content.Context

/**
 * L'ordre de l'équipage, tel que le propriétaire l'a rangé — retenu d'une exécution à l'autre.
 *
 * Le daemon renvoie ses bots dans l'ordre de ses manifests, c'est-à-dire à peu près l'ordre de
 * création : le bot qu'on ouvre tous les jours reste sous trois autres qu'on n'ouvre jamais, et
 * l'ordre se décale encore à la première suppression. Rien dans l'API ne permet de le changer, donc
 * le téléphone retient le sien : une liste d'identifiants, et [io.github.tournierjc.mercury.data.BotOrder]
 * l'applique au roster avant qu'il ne devienne des rangées.
 *
 * Sur le téléphone et pas sur le serveur, assumé : c'est un ordre de lecture, pas une propriété de
 * l'équipage — et un bot qui change de nom ou de modèle ne doit pas faire tomber son rang.
 */
class BotOrderMemory(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("mercury_bot_order", Context.MODE_PRIVATE)

    /** L'ordre retenu, vide tant que le propriétaire n'a rien rangé. */
    fun order(): List<String> =
        prefs.getString(KEY, null)?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()

    fun save(ids: List<String>) {
        prefs.edit().putString(KEY, ids.joinToString(",")).apply()
    }

    private companion object {
        const val KEY = "order"
    }
}
