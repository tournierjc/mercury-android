package io.github.tournierjc.mercury.data

import kotlinx.serialization.json.JsonObject

/**
 * L'ordre du roster, tel que le propriétaire l'a rangé.
 *
 * Le daemon renvoie ses bots dans l'ordre de ses manifests — un ordre de création, pas de lecture :
 * le bot dont on se sert tous les jours se retrouve sous trois autres qu'on n'ouvre jamais. L'app
 * n'avait aucun moyen de le remonter, et l'ordre changeait même après une suppression suivie d'une
 * création.
 *
 * L'ordre est donc une **liste d'identifiants**, retenue sur le téléphone, appliquée avant que le
 * roster ne devienne des rangées. Deux règles en font un tri sûr :
 *
 *  - un identifiant inconnu (bot supprimé puis recréé, appareil neuf) ne bloque rien : il est ignoré ;
 *  - un bot absent de la liste (nouveau venu) se range **à la fin**, dans l'ordre du daemon — il
 *    apparaît là où on l'attend, plutôt que de bousculer un ordre déjà réglé.
 */
object BotOrder {

    /** Les bots, dans l'ordre retenu ; les inconnus de la liste suivent, dans leur ordre d'origine. */
    fun apply(bots: List<JsonObject>, ids: List<String>): List<JsonObject> {
        if (ids.isEmpty() || bots.isEmpty()) return bots
        val rank = ids.withIndex().associate { (index, id) -> id to index }
        return bots.sortedBy { rank[BotJson.id(it)] ?: Int.MAX_VALUE }
    }

    /**
     * Les rangées d'équipage, aplaties dans l'ordre où elles s'affichent : un leader, puis son
     * équipage, puis la rangée suivante. C'est cette liste-là qu'on retient — un ordre de rangées
     * dit exactement ce que l'œil voit, sans avoir à redériver les groupes.
     */
    fun flatten(rows: List<FleetRow>): List<String> = rows.flatMap { row ->
        when (row) {
            is FleetRow.Lead -> listOf(row.id) + row.crewIds
            is FleetRow.Solo -> listOf(row.id)
        }
    }

    /**
     * Les blocs du roster : un leader avec son équipage, ou un bot seul.
     *
     * C'est l'unité que déplacent les flèches. Déplacer une *ligne* dans la liste aplatie ne marchait
     * pas : sous un leader, la ligne suivante est son propre équipier — l'appui faisait donc
     * s'échanger le leader et son premier membre, et le regroupement annulait le déplacement. Un
     * leader se déplace maintenant avec son équipage, un équipier dans son équipage, jamais ailleurs.
     */
    fun blocks(rows: List<FleetRow>): List<List<String>> = rows.map { row ->
        when (row) {
            is FleetRow.Lead -> listOf(row.id) + row.crewIds
            is FleetRow.Solo -> listOf(row.id)
        }
    }

    /** Si la flèche a quelque chose à déplacer dans cette direction. */
    fun canMove(rows: List<FleetRow>, id: String, delta: Int): Boolean = moveRow(rows, id, delta) !=
        flatten(rows)

    /**
     * Déplace [id] d'un cran : son bloc si c'est un leader ou un bot seul, sa place dans l'équipage
     * si c'est un équipier. Rend la liste aplatie à retenir — un cran hors bornes ne change rien.
     */
    fun moveRow(rows: List<FleetRow>, id: String, delta: Int): List<String> {
        val blocks = blocks(rows).map { it.toMutableList() }.toMutableList()
        val top = blocks.indexOfFirst { it.firstOrNull() == id }
        if (top >= 0) {
            val target = top + delta
            if (target !in blocks.indices) return flatten(rows)
            val moved = blocks.removeAt(top)
            blocks.add(target, moved)
            return blocks.flatten()
        }
        // Un équipier ne sort pas de son bloc : il passe devant ou derrière ses pairs, et la première
        // place du bloc reste celle du leader (l'index 0).
        for (block in blocks) {
            val at = block.indexOf(id)
            if (at <= 0) continue
            val target = at + delta
            if (target !in 1..block.lastIndex) return flatten(rows)
            val moved = block.removeAt(at)
            block.add(target, moved)
            return blocks.flatten()
        }
        return flatten(rows)
    }

    /**
     * Déplace une entrée. `to` est ramené dans les bornes : un appui sur la flèche du haut de la
     * première rangée ne doit pas la renvoyer en bas de la liste.
     */
    fun move(ids: List<String>, from: Int, to: Int): List<String> {
        if (from !in ids.indices) return ids
        val target = to.coerceIn(0, ids.lastIndex)
        if (target == from) return ids
        val mutable = ids.toMutableList()
        val moved = mutable.removeAt(from)
        mutable.add(target, moved)
        return mutable
    }
}
